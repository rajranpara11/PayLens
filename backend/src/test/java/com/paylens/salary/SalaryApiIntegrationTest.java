package com.paylens.salary;

import static org.assertj.core.api.Assertions.assertThat;

import com.paylens.employee.dto.SalaryRequest;
import com.paylens.employee.dto.SalaryResponse;
import com.paylens.employee.entity.Department;
import com.paylens.employee.entity.Employee;
import com.paylens.employee.entity.EmploymentStatus;
import com.paylens.employee.repository.DepartmentRepository;
import com.paylens.employee.repository.EmployeeRepository;
import com.paylens.salary.entity.Salary;
import com.paylens.salary.repository.SalaryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SalaryApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private SalaryRepository salaryRepository;

    private UUID employeeId;

    @BeforeEach
    void setUp() {
        salaryRepository.deleteAll();
        employeeRepository.deleteAll();
        departmentRepository.deleteAll();

        Department department = new Department();
        department.setCode("ENG");
        department.setName("Engineering");
        department = departmentRepository.save(department);

        Employee employee = new Employee();
        employee.setEmployeeCode("E-SAL-1");
        employee.setFirstName("Sam");
        employee.setLastName("Lee");
        employee.setEmail("sam.lee@acme.com");
        employee.setCountry("US");
        employee.setDepartment(department);
        employee.setDesignation("Engineer");
        employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        employee.setJoiningDate(LocalDate.of(2023, 1, 1));
        employee = employeeRepository.save(employee);
        employeeId = employee.getId();

        Salary salary = new Salary();
        salary.setEmployee(employee);
        salary.setAnnualSalary(new BigDecimal("90000.00"));
        salary.setCurrency("USD");
        salary.setEffectiveFrom(LocalDate.of(2023, 1, 1));
        salaryRepository.save(salary);
    }

    @Test
    void getCurrentSalary() {
        ResponseEntity<SalaryResponse> response = restTemplate.getForEntity(
                "/api/v1/employees/{id}/salary",
                SalaryResponse.class,
                employeeId
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().annualSalary()).isEqualByComparingTo("90000.00");
        assertThat(response.getBody().currency()).isEqualTo("USD");
    }

    @Test
    void updateSalaryAppendsHistory() {
        SalaryRequest raise = new SalaryRequest(new BigDecimal("105000.00"), "USD", LocalDate.of(2024, 1, 1));

        ResponseEntity<SalaryResponse> updated = restTemplate.exchange(
                "/api/v1/employees/{id}/salary",
                HttpMethod.PUT,
                new HttpEntity<>(raise),
                SalaryResponse.class,
                employeeId
        );

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().annualSalary()).isEqualByComparingTo("105000.00");

        ResponseEntity<SalaryResponse[]> history = restTemplate.getForEntity(
                "/api/v1/employees/{id}/salary-history",
                SalaryResponse[].class,
                employeeId
        );
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(history.getBody()).hasSize(2);
        assertThat(history.getBody()[0].effectiveFrom()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(history.getBody()[1].annualSalary()).isEqualByComparingTo("90000.00");
        assertThat(salaryRepository.findByEmployee_IdOrderByEffectiveFromDesc(employeeId)).hasSize(2);
    }

    @Test
    void retrieveSalaryHistory() {
        ResponseEntity<SalaryResponse[]> history = restTemplate.getForEntity(
                "/api/v1/employees/{id}/salary-history",
                SalaryResponse[].class,
                employeeId
        );

        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(history.getBody()).hasSize(1);
    }

    @Test
    void invalidSalary() {
        SalaryRequest invalid = new SalaryRequest(new BigDecimal("-5"), "USD", LocalDate.of(2024, 2, 1));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/employees/{id}/salary",
                HttpMethod.PUT,
                new HttpEntity<>(invalid),
                String.class,
                employeeId
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(salaryRepository.findByEmployee_IdOrderByEffectiveFromDesc(employeeId)).hasSize(1);
    }

    @Test
    void invalidCurrency() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/employees/{id}/salary",
                HttpMethod.PUT,
                new HttpEntity<>(new SalaryRequest(new BigDecimal("1000"), "ZZZ", LocalDate.of(2024, 2, 1))),
                String.class,
                employeeId
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void nonexistentEmployee() {
        UUID missing = UUID.randomUUID();

        assertThat(restTemplate.getForEntity("/api/v1/employees/{id}/salary", String.class, missing)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.getForEntity("/api/v1/employees/{id}/salary-history", String.class, missing)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
