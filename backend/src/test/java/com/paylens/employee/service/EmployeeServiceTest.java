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
import com.paylens.salary.service.SalaryService;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private SalaryRepository salaryRepository;

    @Mock
    private SalaryService salaryService;

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
        Clock clock = Clock.fixed(Instant.parse("2024-06-01T00:00:00Z"), ZoneOffset.UTC);
        employeeService = new EmployeeService(
                employeeRepository,
                departmentRepository,
                salaryRepository,
                salaryService,
                clock
        );
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
        when(salaryService.apply(any(Employee.class), any(SalaryRequest.class))).thenReturn(persistedSalary());

        var response = employeeService.create(createRequest());

        assertThat(response.employeeCode()).isEqualTo("E-1");
        assertThat(response.country()).isEqualTo("IN");
        assertThat(response.currentSalary().currency()).isEqualTo("INR");
        verify(salaryService).apply(any(Employee.class), any(SalaryRequest.class));
        verify(employeeRepository, never()).findById(any());
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
        when(salaryService.apply(any(Employee.class), any(SalaryRequest.class)))
                .thenThrow(new ValidationException("annualSalary must be greater than 0"));

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
    void updateChangesIdentityAndKeepsCurrentSalaryWhenSalaryOmitted() {
        Employee employee = persistedEmployee();
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(employeeRepository.existsByEmployeeCodeIgnoreCaseAndIdNot("E-2", employeeId)).thenReturn(false);
        when(employeeRepository.existsByEmailIgnoreCaseAndIdNot("jane@acme.com", employeeId)).thenReturn(false);
        when(departmentRepository.findByCodeIgnoreCase("Engineering")).thenReturn(Optional.of(engineering));
        when(employeeRepository.save(employee)).thenReturn(employee);
        when(salaryService.findCurrent(employeeId)).thenReturn(Optional.of(persistedSalary()));

        UpdateEmployeeRequest request = new UpdateEmployeeRequest(
                "E-2",
                "Jane",
                "Doe",
                "jane@acme.com",
                "US",
                "Engineering",
                "Senior Engineer",
                "ON_LEAVE",
                LocalDate.of(2023, 5, 1),
                null
        );

        var response = employeeService.update(employeeId, request);

        assertThat(response.employeeCode()).isEqualTo("E-2");
        assertThat(response.firstName()).isEqualTo("Jane");
        assertThat(response.country()).isEqualTo("US");
        assertThat(response.designation()).isEqualTo("Senior Engineer");
        assertThat(response.employmentStatus()).isEqualTo("ON_LEAVE");
        assertThat(response.currentSalary().annualSalary()).isEqualByComparingTo("120000.00");
        verify(salaryService, never()).apply(any(), any());
    }

    @Test
    void updateAppliesNewSalaryWhenProvided() {
        Employee employee = persistedEmployee();
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(employeeRepository.existsByEmployeeCodeIgnoreCaseAndIdNot("E-1", employeeId)).thenReturn(false);
        when(employeeRepository.existsByEmailIgnoreCaseAndIdNot("john@acme.com", employeeId)).thenReturn(false);
        when(departmentRepository.findByCodeIgnoreCase("Engineering")).thenReturn(Optional.of(engineering));
        when(employeeRepository.save(employee)).thenReturn(employee);
        Salary raised = persistedSalary();
        raised.setAnnualSalary(new BigDecimal("150000.00"));
        when(salaryService.apply(eq(employee), any(SalaryRequest.class))).thenReturn(raised);

        UpdateEmployeeRequest request = new UpdateEmployeeRequest(
                "E-1",
                "John",
                "Doe",
                "john@acme.com",
                "IN",
                "Engineering",
                "Engineer",
                "ACTIVE",
                LocalDate.of(2024, 1, 15),
                new SalaryRequest(new BigDecimal("150000.00"), "INR", LocalDate.of(2024, 6, 1))
        );

        var response = employeeService.update(employeeId, request);

        assertThat(response.currentSalary().annualSalary()).isEqualByComparingTo("150000.00");
        verify(salaryService).apply(eq(employee), any(SalaryRequest.class));
    }

    @Test
    void updateRejectsDuplicateEmployeeCode() {
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(persistedEmployee()));
        when(employeeRepository.existsByEmployeeCodeIgnoreCaseAndIdNot("E-1", employeeId)).thenReturn(true);

        assertThatThrownBy(() -> employeeService.update(employeeId, updateRequest()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("employeeCode");
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void updateRejectsDuplicateEmail() {
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(persistedEmployee()));
        when(employeeRepository.existsByEmployeeCodeIgnoreCaseAndIdNot("E-1", employeeId)).thenReturn(false);
        when(employeeRepository.existsByEmailIgnoreCaseAndIdNot("john@acme.com", employeeId)).thenReturn(true);

        assertThatThrownBy(() -> employeeService.update(employeeId, updateRequest()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("email");
    }

    @Test
    void createRejectsUnknownCountry() {
        when(employeeRepository.existsByEmployeeCodeIgnoreCase("E-1")).thenReturn(false);
        when(employeeRepository.existsByEmailIgnoreCase("john@acme.com")).thenReturn(false);

        CreateEmployeeRequest request = new CreateEmployeeRequest(
                "E-1",
                "John",
                "Doe",
                "john@acme.com",
                "Narnia",
                "Engineering",
                "Engineer",
                "ACTIVE",
                LocalDate.of(2024, 1, 15),
                new SalaryRequest(new BigDecimal("120000.00"), "INR", LocalDate.of(2024, 1, 15))
        );

        assertThatThrownBy(() -> employeeService.create(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("country");
    }

    @Test
    void createRejectsInvalidEmploymentStatus() {
        when(employeeRepository.existsByEmployeeCodeIgnoreCase("E-1")).thenReturn(false);
        when(employeeRepository.existsByEmailIgnoreCase("john@acme.com")).thenReturn(false);

        CreateEmployeeRequest request = new CreateEmployeeRequest(
                "E-1",
                "John",
                "Doe",
                "john@acme.com",
                "IN",
                "Engineering",
                "Engineer",
                "VACATIONING",
                LocalDate.of(2024, 1, 15),
                new SalaryRequest(new BigDecimal("120000.00"), "INR", LocalDate.of(2024, 1, 15))
        );

        assertThatThrownBy(() -> employeeService.create(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("employmentStatus");
    }

    @Test
    void deactivateThrowsWhenMissing() {
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.deactivate(employeeId))
                .isInstanceOf(NotFoundException.class);
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void listClampsPageSizeToOneHundred() {
        Employee employee = persistedEmployee();
        Pageable requested = PageRequest.of(0, 500);
        Pageable expected = PageRequest.of(0, 100);
        when(employeeRepository.findAll(any(Specification.class), eq(expected)))
                .thenReturn(new PageImpl<>(List.of(employee), expected, 1));
        when(salaryRepository.findCurrentByEmployeeIds(eq(List.of(employeeId)), eq(LocalDate.of(2024, 6, 1))))
                .thenReturn(List.of(persistedSalary()));

        employeeService.list(new EmployeeSearchCriteria(null, null, null, null, null, null), requested);

        verify(employeeRepository).findAll(any(Specification.class), eq(expected));
    }

    @Test
    void listRejectsUnknownCountryFilter() {
        assertThatThrownBy(() -> employeeService.list(
                new EmployeeSearchCriteria(null, "Atlantis", null, null, null, null),
                PageRequest.of(0, 25)
        )).isInstanceOf(ValidationException.class)
                .hasMessageContaining("country");
        verify(employeeRepository, never()).findAll(any(Specification.class), any(Pageable.class));
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
        when(salaryRepository.findCurrentByEmployeeIds(eq(List.of(employeeId)), eq(LocalDate.of(2024, 6, 1))))
                .thenReturn(List.of(persistedSalary()));

        Page<?> page = employeeService.list(new EmployeeSearchCriteria("john", "India", "Engineering", null, null, "INR"), pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        verify(employeeRepository).findAll(any(Specification.class), eq(pageable));
        verify(employeeRepository, never()).findAll();
        verify(salaryRepository).findCurrentByEmployeeIds(eq(List.of(employeeId)), eq(LocalDate.of(2024, 6, 1)));
    }

    @Test
    void listRejectsUnknownSortProperties() {
        Employee employee = persistedEmployee();
        Pageable requested = PageRequest.of(0, 25, Sort.by("passwordHash"));
        Pageable expected = PageRequest.of(0, 25, Sort.by(Sort.Direction.ASC, "lastName"));
        when(employeeRepository.findAll(any(Specification.class), eq(expected)))
                .thenReturn(new PageImpl<>(List.of(employee), expected, 1));
        when(salaryRepository.findCurrentByEmployeeIds(eq(List.of(employeeId)), eq(LocalDate.of(2024, 6, 1))))
                .thenReturn(List.of(persistedSalary()));

        employeeService.list(new EmployeeSearchCriteria(null, null, null, null, null, null), requested);

        verify(employeeRepository).findAll(any(Specification.class), eq(expected));
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
