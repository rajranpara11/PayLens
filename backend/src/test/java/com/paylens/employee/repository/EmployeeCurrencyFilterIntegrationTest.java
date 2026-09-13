package com.paylens.employee.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.paylens.employee.entity.Department;
import com.paylens.employee.entity.Employee;
import com.paylens.employee.entity.EmploymentStatus;
import com.paylens.salary.entity.Salary;
import com.paylens.salary.repository.SalaryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:paylens_currency_filter;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
class EmployeeCurrencyFilterIntegrationTest {

    private static final LocalDate ON_DATE = LocalDate.of(2024, 6, 15);

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

        Department eng = new Department();
        eng.setId(UUID.randomUUID());
        eng.setCode("ENG");
        eng.setName("Engineering");
        departmentRepository.save(eng);

        Employee inr = saveEmployee("E-INR", eng);
        saveSalary(inr, "1000000", "INR", LocalDate.of(2023, 1, 1));
        saveSalary(inr, "1100000", "INR", LocalDate.of(2024, 1, 1));

        Employee usd = saveEmployee("E-USD", eng);
        saveSalary(usd, "90000", "USD", LocalDate.of(2023, 1, 1));

        Employee switched = saveEmployee("E-SW", eng);
        saveSalary(switched, "80000", "USD", LocalDate.of(2022, 1, 1));
        saveSalary(switched, "7000000", "INR", LocalDate.of(2024, 1, 1));
    }

    @Test
    void currentCurrencyFilterUsesLatestSalaryOnly() {
        Page<Employee> inrPage = employeeRepository.findAll(
                EmployeeSpecifications.currentCurrencyEquals("INR", ON_DATE),
                PageRequest.of(0, 20)
        );
        Page<Employee> usdPage = employeeRepository.findAll(
                EmployeeSpecifications.currentCurrencyEquals("USD", ON_DATE),
                PageRequest.of(0, 20)
        );

        assertThat(inrPage.getContent()).extracting(Employee::getEmployeeCode)
                .containsExactlyInAnyOrder("E-INR", "E-SW");
        assertThat(usdPage.getContent()).extracting(Employee::getEmployeeCode)
                .containsExactly("E-USD");
    }

    @Test
    void findCurrentByEmployeeIdsSkipsHistoryRows() {
        List<Employee> all = employeeRepository.findAll();
        List<UUID> ids = all.stream().map(Employee::getId).toList();

        List<Salary> current = salaryRepository.findCurrentByEmployeeIds(ids, ON_DATE);

        assertThat(current).hasSize(3);
        assertThat(current).allMatch(salary -> !salary.getEffectiveFrom().isAfter(ON_DATE));
        Salary switched = current.stream()
                .filter(salary -> "INR".equals(salary.getCurrency()) && salary.getAnnualSalary().compareTo(new BigDecimal("7000000.00")) == 0)
                .findFirst()
                .orElseThrow();
        assertThat(switched.getEffectiveFrom()).isEqualTo(LocalDate.of(2024, 1, 1));
    }

    private Employee saveEmployee(String code, Department department) {
        Employee employee = new Employee();
        employee.setId(UUID.randomUUID());
        employee.setEmployeeCode(code);
        employee.setFirstName("Test");
        employee.setLastName(code);
        employee.setEmail(code.toLowerCase() + "@acme.test");
        employee.setCountry("IN");
        employee.setDepartment(department);
        employee.setDesignation("Engineer");
        employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        employee.setJoiningDate(LocalDate.of(2022, 1, 1));
        return employeeRepository.save(employee);
    }

    private void saveSalary(Employee employee, String amount, String currency, LocalDate effectiveFrom) {
        Salary salary = new Salary();
        salary.setId(UUID.randomUUID());
        salary.setEmployee(employee);
        salary.setAnnualSalary(new BigDecimal(amount));
        salary.setCurrency(currency);
        salary.setEffectiveFrom(effectiveFrom);
        salaryRepository.save(salary);
    }
}
