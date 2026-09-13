package com.paylens.common;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paylens.employee.entity.Department;
import com.paylens.employee.entity.Employee;
import com.paylens.employee.entity.EmploymentStatus;
import com.paylens.employee.repository.DepartmentRepository;
import com.paylens.employee.repository.EmployeeRepository;
import com.paylens.salary.entity.Salary;
import com.paylens.salary.repository.SalaryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifies Flyway CHECK / UNIQUE constraints reject bad data at the database.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:paylens_constraints;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
class SchemaConstraintIntegrationTest {

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private SalaryRepository salaryRepository;

    private Department engineering;
    private Employee employee;

    @BeforeEach
    void setUp() {
        salaryRepository.deleteAll();
        employeeRepository.deleteAll();
        departmentRepository.deleteAll();

        engineering = new Department();
        engineering.setCode("ENG");
        engineering.setName("Engineering");
        engineering = departmentRepository.save(engineering);

        employee = saveEmployee("E-OK", "ok@acme.test");
        Salary salary = new Salary();
        salary.setEmployee(employee);
        salary.setAnnualSalary(new BigDecimal("100000.00"));
        salary.setCurrency("USD");
        salary.setEffectiveFrom(LocalDate.of(2023, 1, 1));
        salaryRepository.save(salary);
    }

    @Test
    void rejectsDuplicateEmployeeCode() {
        assertThatThrownBy(() -> saveEmployee("E-OK", "other@acme.test"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateEmail() {
        assertThatThrownBy(() -> saveEmployee("E-OTHER", "ok@acme.test"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateSalaryEffectiveDateForSameEmployee() {
        Salary duplicate = new Salary();
        duplicate.setEmployee(employee);
        duplicate.setAnnualSalary(new BigDecimal("110000.00"));
        duplicate.setCurrency("USD");
        duplicate.setEffectiveFrom(LocalDate.of(2023, 1, 1));

        assertThatThrownBy(() -> salaryRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNonPositiveSalary() {
        Salary bad = new Salary();
        bad.setEmployee(employee);
        bad.setAnnualSalary(new BigDecimal("0.00"));
        bad.setCurrency("USD");
        bad.setEffectiveFrom(LocalDate.of(2024, 1, 1));

        assertThatThrownBy(() -> salaryRepository.saveAndFlush(bad))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateDepartmentCode() {
        Department dup = new Department();
        dup.setCode("ENG");
        dup.setName("Engineering 2");

        assertThatThrownBy(() -> departmentRepository.saveAndFlush(dup))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Employee saveEmployee(String code, String email) {
        Employee next = new Employee();
        next.setEmployeeCode(code);
        next.setFirstName("Test");
        next.setLastName("User");
        next.setEmail(email);
        next.setCountry("US");
        next.setDepartment(engineering);
        next.setDesignation("Engineer");
        next.setEmploymentStatus(EmploymentStatus.ACTIVE);
        next.setJoiningDate(LocalDate.of(2023, 1, 1));
        return employeeRepository.saveAndFlush(next);
    }
}
