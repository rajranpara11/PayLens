package com.paylens.employee.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.paylens.employee.dto.EmployeeSearchCriteria;
import com.paylens.employee.dto.EmployeeResponse;
import com.paylens.employee.entity.Department;
import com.paylens.employee.entity.Employee;
import com.paylens.employee.entity.EmploymentStatus;
import com.paylens.employee.service.EmployeeService;
import com.paylens.salary.entity.Salary;
import com.paylens.salary.repository.SalaryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Service-level list behavior with real Specifications + DB.
 * Complements HTTP lifecycle tests with focused query assertions.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:paylens_employee_list;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
class EmployeeListQueryIntegrationTest {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private SalaryRepository salaryRepository;

    @BeforeEach
    void setUp() {
        salaryRepository.deleteAll();
        employeeRepository.deleteAll();
        departmentRepository.deleteAll();

        Department eng = saveDept("ENG", "Engineering");
        Department hr = saveDept("HR", "Human Resources");

        Employee alice = saveEmployee("E-1", "Alice", "Zebra", "alice.z@acme.test", "US", eng, "Engineer", EmploymentStatus.ACTIVE);
        Employee bob = saveEmployee("E-2", "Bob", "Alpha", "bob.a@acme.test", "IN", eng, "Engineer", EmploymentStatus.ACTIVE);
        Employee cara = saveEmployee("E-3", "Cara", "Beta", "cara.b@acme.test", "US", hr, "HR Partner", EmploymentStatus.ON_LEAVE);
        Employee dana = saveEmployee("E-4", "Dana", "Gamma", "dana.g@acme.test", "US", eng, "Engineer", EmploymentStatus.TERMINATED);

        saveSalary(alice, "100000", "USD", LocalDate.of(2023, 1, 1));
        saveSalary(bob, "900000", "INR", LocalDate.of(2023, 1, 1));
        saveSalary(cara, "85000", "USD", LocalDate.of(2023, 1, 1));
        saveSalary(dana, "70000", "USD", LocalDate.of(2022, 1, 1));
    }

    @Test
    void searchMatchesNameEmailAndCode() {
        assertThat(codes(employeeService.list(criteria("Zebra", null, null, null, null, null), page())))
                .containsExactly("E-1");
        assertThat(codes(employeeService.list(criteria("cara.b", null, null, null, null, null), page())))
                .containsExactly("E-3");
        assertThat(codes(employeeService.list(criteria("E-2", null, null, null, null, null), page())))
                .containsExactly("E-2");
    }

    @Test
    void filtersByCountryDepartmentDesignationAndStatus() {
        assertThat(codes(employeeService.list(criteria(null, "US", null, null, "ACTIVE", null), page())))
                .containsExactly("E-1");
        assertThat(codes(employeeService.list(criteria(null, null, "HR", null, null, null), page())))
                .containsExactly("E-3");
        assertThat(codes(employeeService.list(criteria(null, null, "Human Resources", null, null, null), page())))
                .containsExactly("E-3");
        assertThat(codes(employeeService.list(criteria(null, null, null, "HR Partner", null, null), page())))
                .containsExactly("E-3");
        assertThat(codes(employeeService.list(criteria(null, null, null, null, "TERMINATED", null), page())))
                .containsExactly("E-4");
    }

    @Test
    void paginationReturnsStableSlices() {
        Page<EmployeeResponse> first = employeeService.list(
                criteria(null, null, "ENG", null, null, null),
                PageRequest.of(0, 2, Sort.by("lastName").ascending())
        );
        Page<EmployeeResponse> second = employeeService.list(
                criteria(null, null, "ENG", null, null, null),
                PageRequest.of(1, 2, Sort.by("lastName").ascending())
        );

        // ENG has Alice(ACTIVE), Bob(ACTIVE), Dana(TERMINATED) = 3
        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getContent()).hasSize(2);
        assertThat(second.getContent()).hasSize(1);
        assertThat(codes(first)).doesNotContainAnyElementsOf(codes(second));
    }

    private static EmployeeSearchCriteria criteria(
            String search,
            String country,
            String department,
            String designation,
            String status,
            String currency
    ) {
        return new EmployeeSearchCriteria(search, country, department, designation, status, currency);
    }

    private static PageRequest page() {
        return PageRequest.of(0, 25, Sort.by("lastName").ascending());
    }

    private static List<String> codes(Page<EmployeeResponse> page) {
        return page.getContent().stream().map(EmployeeResponse::employeeCode).toList();
    }

    private Department saveDept(String code, String name) {
        Department department = new Department();
        department.setCode(code);
        department.setName(name);
        return departmentRepository.save(department);
    }

    private Employee saveEmployee(
            String code,
            String first,
            String last,
            String email,
            String country,
            Department department,
            String designation,
            EmploymentStatus status
    ) {
        Employee employee = new Employee();
        employee.setEmployeeCode(code);
        employee.setFirstName(first);
        employee.setLastName(last);
        employee.setEmail(email);
        employee.setCountry(country);
        employee.setDepartment(department);
        employee.setDesignation(designation);
        employee.setEmploymentStatus(status);
        employee.setJoiningDate(LocalDate.of(2022, 1, 1));
        return employeeRepository.save(employee);
    }

    private void saveSalary(Employee employee, String amount, String currency, LocalDate from) {
        Salary salary = new Salary();
        salary.setEmployee(employee);
        salary.setAnnualSalary(new BigDecimal(amount));
        salary.setCurrency(currency);
        salary.setEffectiveFrom(from);
        salaryRepository.save(salary);
    }
}
