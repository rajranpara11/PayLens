package com.paylens.analytics.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.paylens.analytics.dto.CountryPayrollRow;
import com.paylens.analytics.dto.CurrencyCompensationStats;
import com.paylens.analytics.dto.DepartmentSalaryRow;
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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:paylens_analytics;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
class AnalyticsRepositoryIntegrationTest {

    private static final LocalDate AS_OF = LocalDate.of(2024, 6, 15);

    @Autowired
    private AnalyticsRepository analyticsRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private SalaryRepository salaryRepository;

    @BeforeEach
    void setUp() {
        salaryRepository.deleteAll();
        employeeRepository.deleteAll();
        departmentRepository.deleteAll();

        Department eng = saveDept("ENG", "Engineering");
        Department sales = saveDept("SAL", "Sales");

        // India / INR
        Employee inJunior = saveEmployee("E-IN-1", "IN", eng, "Junior Software Engineer", EmploymentStatus.ACTIVE);
        Employee inSenior = saveEmployee("E-IN-2", "IN", eng, "Senior Software Engineer", EmploymentStatus.ACTIVE);
        saveSalary(inJunior, "500000.00", "INR", LocalDate.of(2023, 1, 1));
        saveSalary(inSenior, "1500000.00", "INR", LocalDate.of(2023, 1, 1));
        saveSalary(inSenior, "1800000.00", "INR", LocalDate.of(2024, 1, 1)); // current

        // USA / USD
        Employee usAe = saveEmployee("E-US-1", "US", sales, "Account Executive", EmploymentStatus.ACTIVE);
        Employee usSae = saveEmployee("E-US-2", "US", sales, "Senior Account Executive", EmploymentStatus.ACTIVE);
        saveSalary(usAe, "90000.00", "USD", LocalDate.of(2023, 6, 1));
        saveSalary(usSae, "140000.00", "USD", LocalDate.of(2023, 6, 1));

        // Terminated — excluded from compensation aggregates
        Employee left = saveEmployee("E-US-3", "US", sales, "Account Executive", EmploymentStatus.TERMINATED);
        saveSalary(left, "999999.00", "USD", LocalDate.of(2022, 1, 1));

        // Future salary must not become current yet
        Employee future = saveEmployee("E-GB-1", "GB", eng, "Software Engineer", EmploymentStatus.ACTIVE);
        saveSalary(future, "50000.00", "GBP", LocalDate.of(2025, 1, 1));
    }

    @Test
    void compensationByCurrencyDoesNotMixInrAndUsd() {
        List<CurrencyCompensationStats> rows = analyticsRepository.compensationByCurrency(AS_OF);

        assertThat(rows).extracting(CurrencyCompensationStats::currency).containsExactly("INR", "USD");

        CurrencyCompensationStats inr = rows.getFirst();
        assertThat(inr.employeeCount()).isEqualTo(2);
        assertThat(inr.minSalary()).isEqualByComparingTo("500000.00");
        assertThat(inr.maxSalary()).isEqualByComparingTo("1800000.00");
        assertThat(inr.totalPayroll()).isEqualByComparingTo("2300000.00");
        assertThat(inr.averageSalary()).isEqualByComparingTo("1150000.00");
        assertThat(inr.medianSalary()).isEqualByComparingTo("1150000.00");

        CurrencyCompensationStats usd = rows.get(1);
        assertThat(usd.employeeCount()).isEqualTo(2);
        assertThat(usd.totalPayroll()).isEqualByComparingTo("230000.00");
        assertThat(usd.maxSalary()).isEqualByComparingTo("140000.00");
    }

    @Test
    void payrollByCountryKeepsCurrencyContext() {
        List<CountryPayrollRow> rows = analyticsRepository.payrollByCountry(AS_OF);

        assertThat(rows).anySatisfy(row -> {
            assertThat(row.country()).isEqualTo("IN");
            assertThat(row.currency()).isEqualTo("INR");
            assertThat(row.totalPayroll()).isEqualByComparingTo("2300000.00");
        });
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.country()).isEqualTo("US");
            assertThat(row.currency()).isEqualTo("USD");
            assertThat(row.totalPayroll()).isEqualByComparingTo("230000.00");
        });
        assertThat(rows).noneMatch(row -> "GBP".equals(row.currency()));
    }

    @Test
    void salaryByDepartmentUsesCurrentSalaryOnly() {
        List<DepartmentSalaryRow> rows = analyticsRepository.salaryByDepartment(AS_OF);

        DepartmentSalaryRow engInr = rows.stream()
                .filter(r -> "ENG".equals(r.departmentCode()) && "INR".equals(r.currency()))
                .findFirst()
                .orElseThrow();
        assertThat(engInr.maxSalary()).isEqualByComparingTo("1800000.00");
        assertThat(engInr.employeeCount()).isEqualTo(2);
    }

    @Test
    void salaryDistributionIsPerCurrency() {
        assertThat(analyticsRepository.salaryDistribution(AS_OF))
                .extracting(b -> b.currency())
                .contains("INR", "USD")
                .doesNotContain("GBP");
    }

    @Test
    void headcountIncludesAllStatuses() {
        assertThat(analyticsRepository.countAllEmployees()).isEqualTo(6);
        assertThat(analyticsRepository.countEmployedEmployees()).isEqualTo(5);
        assertThat(analyticsRepository.employeeCountByCountry())
                .anySatisfy(row -> {
                    assertThat(row.country()).isEqualTo("US");
                    assertThat(row.employeeCount()).isEqualTo(3);
                });
    }

    private Department saveDept(String code, String name) {
        Department department = new Department();
        department.setCode(code);
        department.setName(name);
        return departmentRepository.save(department);
    }

    private Employee saveEmployee(
            String code,
            String country,
            Department department,
            String designation,
            EmploymentStatus status
    ) {
        Employee employee = new Employee();
        employee.setEmployeeCode(code);
        employee.setFirstName("Test");
        employee.setLastName(code);
        employee.setEmail(code.toLowerCase() + "@acme.test");
        employee.setCountry(country);
        employee.setDepartment(department);
        employee.setDesignation(designation);
        employee.setEmploymentStatus(status);
        employee.setJoiningDate(LocalDate.of(2022, 1, 1));
        return employeeRepository.save(employee);
    }

    private void saveSalary(Employee employee, String amount, String currency, LocalDate from) {
        Salary salary = new Salary();
        salary.setId(UUID.randomUUID());
        salary.setEmployee(employee);
        salary.setAnnualSalary(new BigDecimal(amount));
        salary.setCurrency(currency);
        salary.setEffectiveFrom(from);
        salaryRepository.save(salary);
    }
}
