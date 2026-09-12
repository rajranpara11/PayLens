package com.paylens.employee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paylens.common.exception.ConflictException;
import com.paylens.common.exception.NotFoundException;
import com.paylens.common.exception.ValidationException;
import com.paylens.employee.dto.CreateEmployeeRequest;
import com.paylens.employee.dto.EmployeeSearchCriteria;
import com.paylens.employee.dto.SalaryRequest;
import com.paylens.employee.dto.UpdateEmployeeRequest;
import com.paylens.employee.entity.Department;
import com.paylens.employee.entity.Employee;
import com.paylens.employee.entity.EmploymentStatus;
import com.paylens.employee.repository.DepartmentRepository;
import com.paylens.employee.repository.EmployeeRepository;
import com.paylens.salary.entity.Salary;
import com.paylens.salary.repository.SalaryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private SalaryRepository salaryRepository;

    @InjectMocks
    private EmployeeService employeeService;

    private Department engineering;
    private UUID employeeId;

    @BeforeEach
    void setUp() {
        engineering = new Department();
        engineering.setId(UUID.randomUUID());
        engineering.setCode("ENG");
        engineering.setName("Engineering");
        employeeId = UUID.randomUUID();
    }

    @Test
    void createPersistsEmployeeAndSalary() {
        when(departmentRepository.findByCodeIgnoreCase("Engineering")).thenReturn(Optional.empty());
        when(departmentRepository.findByNameIgnoreCase("Engineering")).thenReturn(Optional.of(engineering));
        when(employeeRepository.existsByEmployeeCodeIgnoreCase("E-1")).thenReturn(false);
        when(employeeRepository.existsByEmailIgnoreCase("john@acme.com")).thenReturn(false);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> {
            Employee employee = invocation.getArgument(0);
            employee.setId(employeeId);
            return employee;
        });
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(persistedEmployee()));
        when(salaryRepository.findByEmployee_IdAndEffectiveFrom(employeeId, LocalDate.of(2024, 1, 15)))
                .thenReturn(Optional.empty());
        when(salaryRepository.findFirstByEmployee_IdOrderByEffectiveFromDesc(employeeId))
                .thenReturn(Optional.of(persistedSalary()));

        var response = employeeService.create(createRequest());

        assertThat(response.employeeCode()).isEqualTo("E-1");
        assertThat(response.country()).isEqualTo("IN");
        assertThat(response.currentSalary().currency()).isEqualTo("INR");
        verify(salaryRepository).save(any(Salary.class));
        ArgumentCaptor<Salary> salaryCaptor = ArgumentCaptor.forClass(Salary.class);
        verify(salaryRepository).save(salaryCaptor.capture());
        assertThat(salaryCaptor.getValue().getAnnualSalary()).isEqualByComparingTo("120000.00");
    }

    @Test
    void createRejectsDuplicateEmployeeCode() {
        when(employeeRepository.existsByEmployeeCodeIgnoreCase("E-1")).thenReturn(true);

        assertThatThrownBy(() -> employeeService.create(createRequest()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("employeeCode");
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void createRejectsDuplicateEmail() {
        when(employeeRepository.existsByEmployeeCodeIgnoreCase("E-1")).thenReturn(false);
        when(employeeRepository.existsByEmailIgnoreCase("john@acme.com")).thenReturn(true);

        assertThatThrownBy(() -> employeeService.create(createRequest()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("email");
    }

    @Test
    void createRejectsUnknownDepartment() {
        when(employeeRepository.existsByEmployeeCodeIgnoreCase("E-1")).thenReturn(false);
        when(employeeRepository.existsByEmailIgnoreCase("john@acme.com")).thenReturn(false);
        when(departmentRepository.findByCodeIgnoreCase("Engineering")).thenReturn(Optional.empty());
        when(departmentRepository.findByNameIgnoreCase("Engineering")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.create(createRequest()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("department");
    }

    @Test
    void createRejectsNonPositiveSalary() {
        when(departmentRepository.findByCodeIgnoreCase("Engineering")).thenReturn(Optional.of(engineering));
        when(employeeRepository.existsByEmployeeCodeIgnoreCase("E-1")).thenReturn(false);
        when(employeeRepository.existsByEmailIgnoreCase("john@acme.com")).thenReturn(false);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> {
            Employee employee = invocation.getArgument(0);
            employee.setId(employeeId);
            return employee;
        });

        CreateEmployeeRequest request = new CreateEmployeeRequest(
                "E-1",
                "John",
                "Doe",
                "john@acme.com",
                "India",
                "Engineering",
                "Engineer",
                "ACTIVE",
                LocalDate.of(2024, 1, 15),
                new SalaryRequest(BigDecimal.ZERO, "INR", LocalDate.of(2024, 1, 15))
        );

        assertThatThrownBy(() -> employeeService.create(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("annualSalary");
    }

    @Test
    void getThrowsWhenMissing() {
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.get(employeeId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateThrowsWhenMissing() {
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.update(employeeId, updateRequest()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deactivateMarksTerminated() {
        Employee employee = persistedEmployee();
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

        employeeService.deactivate(employeeId);

        assertThat(employee.getEmploymentStatus()).isEqualTo(EmploymentStatus.TERMINATED);
        verify(employeeRepository).save(employee);
        verify(employeeRepository, never()).delete(any(Employee.class));
    }

    @Test
    void listUsesPagedRepositoryAndDoesNotLoadAll() {
        Employee employee = persistedEmployee();
        Pageable pageable = PageRequest.of(0, 25);
        when(employeeRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(employee), pageable, 1));
        when(salaryRepository.findByEmployee_IdIn(List.of(employeeId)))
                .thenReturn(List.of(persistedSalary()));

        Page<?> page = employeeService.list(new EmployeeSearchCriteria("john", "India", "Engineering", null, null, "INR"), pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        verify(employeeRepository).findAll(any(Specification.class), eq(pageable));
        verify(employeeRepository, never()).findAll();
    }

    private CreateEmployeeRequest createRequest() {
        return new CreateEmployeeRequest(
                "E-1",
                "John",
                "Doe",
                "john@acme.com",
                "India",
                "Engineering",
                "Engineer",
                "ACTIVE",
                LocalDate.of(2024, 1, 15),
                new SalaryRequest(new BigDecimal("120000.00"), "INR", LocalDate.of(2024, 1, 15))
        );
    }

    private UpdateEmployeeRequest updateRequest() {
        return new UpdateEmployeeRequest(
                "E-1",
                "John",
                "Doe",
                "john@acme.com",
                "IN",
                "Engineering",
                "Engineer",
                "ACTIVE",
                LocalDate.of(2024, 1, 15),
                null
        );
    }

    private Employee persistedEmployee() {
        Employee employee = new Employee();
        employee.setId(employeeId);
        employee.setEmployeeCode("E-1");
        employee.setFirstName("John");
        employee.setLastName("Doe");
        employee.setEmail("john@acme.com");
        employee.setCountry("IN");
        employee.setDepartment(engineering);
        employee.setDesignation("Engineer");
        employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        employee.setJoiningDate(LocalDate.of(2024, 1, 15));
        return employee;
    }

    private Salary persistedSalary() {
        Salary salary = new Salary();
        salary.setId(UUID.randomUUID());
        salary.setEmployee(persistedEmployee());
        salary.setAnnualSalary(new BigDecimal("120000.00"));
        salary.setCurrency("INR");
        salary.setEffectiveFrom(LocalDate.of(2024, 1, 15));
        return salary;
    }
}
