package com.paylens.salary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paylens.common.exception.NotFoundException;
import com.paylens.common.exception.ValidationException;
import com.paylens.employee.dto.SalaryRequest;
import com.paylens.employee.entity.Employee;
import com.paylens.employee.repository.EmployeeRepository;
import com.paylens.salary.entity.Salary;
import com.paylens.salary.repository.SalaryRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalaryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2024, 6, 15);

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private SalaryRepository salaryRepository;

    private SalaryService salaryService;
    private UUID employeeId;
    private Employee employee;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2024-06-15T00:00:00Z"), ZoneOffset.UTC);
        salaryService = new SalaryService(employeeRepository, salaryRepository, clock);
        employeeId = UUID.randomUUID();
        employee = new Employee();
        employee.setId(employeeId);
    }

    @Test
    void currentSalaryIsLatestOnOrBeforeToday() {
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        Salary current = salary(new BigDecimal("100000"), "USD", LocalDate.of(2024, 1, 1));
        when(salaryRepository.findFirstByEmployee_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                employeeId, TODAY
        )).thenReturn(Optional.of(current));

        var response = salaryService.getCurrent(employeeId);

        assertThat(response.annualSalary()).isEqualByComparingTo("100000");
        assertThat(response.effectiveFrom()).isEqualTo(LocalDate.of(2024, 1, 1));
    }

    @Test
    void currentSalaryMissingWhenOnlyFutureRowsExist() {
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(salaryRepository.findFirstByEmployee_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                employeeId, TODAY
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> salaryService.getCurrent(employeeId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("No current salary");
    }

    @Test
    void upsertInsertsNewDateAndLeavesHistory() {
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(salaryRepository.findByEmployee_IdAndEffectiveFrom(employeeId, LocalDate.of(2024, 7, 1)))
                .thenReturn(Optional.empty());
        when(salaryRepository.save(any(Salary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SalaryRequest request = new SalaryRequest(new BigDecimal("150000.00"), "USD", LocalDate.of(2024, 7, 1));
        salaryService.upsert(employeeId, request);

        ArgumentCaptor<Salary> captor = ArgumentCaptor.forClass(Salary.class);
        verify(salaryRepository).save(captor.capture());
        assertThat(captor.getValue().getEffectiveFrom()).isEqualTo(LocalDate.of(2024, 7, 1));
        assertThat(captor.getValue().getAnnualSalary()).isEqualByComparingTo("150000.00");
    }

    @Test
    void upsertSameDateCorrectsInPlace() {
        Salary existing = salary(new BigDecimal("100000"), "USD", LocalDate.of(2024, 1, 1));
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(salaryRepository.findByEmployee_IdAndEffectiveFrom(employeeId, LocalDate.of(2024, 1, 1)))
                .thenReturn(Optional.of(existing));
        when(salaryRepository.save(existing)).thenReturn(existing);

        salaryService.upsert(
                employeeId,
                new SalaryRequest(new BigDecimal("110000.50"), "USD", LocalDate.of(2024, 1, 1))
        );

        assertThat(existing.getAnnualSalary()).isEqualByComparingTo("110000.50");
        verify(salaryRepository).save(existing);
        verify(salaryRepository, never()).delete(any(Salary.class));
    }

    @Test
    void rejectsNegativeSalary() {
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> salaryService.upsert(
                employeeId,
                new SalaryRequest(new BigDecimal("-1"), "USD", TODAY)
        )).isInstanceOf(ValidationException.class)
                .hasMessageContaining("annualSalary");
        verify(salaryRepository, never()).save(any());
    }

    @Test
    void rejectsZeroSalary() {
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> salaryService.upsert(
                employeeId,
                new SalaryRequest(BigDecimal.ZERO, "USD", TODAY)
        )).isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsUnsupportedCurrency() {
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> salaryService.upsert(
                employeeId,
                new SalaryRequest(new BigDecimal("1000"), "ZZZ", TODAY)
        )).isInstanceOf(ValidationException.class)
                .hasMessageContaining("currency");
    }

    @Test
    void rejectsUnknownEmployee() {
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> salaryService.getCurrent(employeeId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Employee not found");
        assertThatThrownBy(() -> salaryService.history(employeeId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> salaryService.upsert(
                employeeId,
                new SalaryRequest(new BigDecimal("1000"), "USD", TODAY)
        )).isInstanceOf(NotFoundException.class);
    }

    @Test
    void historyIsNewestFirst() {
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        Salary older = salary(new BigDecimal("90000"), "USD", LocalDate.of(2023, 1, 1));
        Salary newer = salary(new BigDecimal("100000"), "USD", LocalDate.of(2024, 1, 1));
        when(salaryRepository.findByEmployee_IdOrderByEffectiveFromDesc(employeeId))
                .thenReturn(List.of(newer, older));

        var history = salaryService.history(employeeId);

        assertThat(history).extracting("effectiveFrom")
                .containsExactly(LocalDate.of(2024, 1, 1), LocalDate.of(2023, 1, 1));
    }

    @Test
    void moneyUsesBigDecimalNotFloatingPoint() {
        BigDecimal amount = SalaryService.requirePositiveAmount(new BigDecimal("12345.67"));
        assertThat(amount).isInstanceOf(BigDecimal.class);
        assertThat(amount.scale()).isEqualTo(2);
    }

    private Salary salary(BigDecimal amount, String currency, LocalDate from) {
        Salary salary = new Salary();
        salary.setId(UUID.randomUUID());
        salary.setEmployee(employee);
        salary.setAnnualSalary(amount);
        salary.setCurrency(currency);
        salary.setEffectiveFrom(from);
        return salary;
    }
}
