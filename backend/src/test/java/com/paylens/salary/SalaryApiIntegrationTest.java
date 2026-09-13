package com.paylens.salary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paylens.employee.dto.SalaryRequest;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalaryApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void getCurrentSalary() throws Exception {
        mockMvc.perform(get("/api/v1/employees/{id}/salary", employeeId).with(hr()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annualSalary").value(90000.00))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void updateSalaryAppendsHistory() throws Exception {
        SalaryRequest raise = new SalaryRequest(new BigDecimal("105000.00"), "USD", LocalDate.of(2024, 1, 1));

        mockMvc.perform(put("/api/v1/employees/{id}/salary", employeeId)
                        .with(hr())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(raise)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annualSalary").value(105000.00));

        mockMvc.perform(get("/api/v1/employees/{id}/salary-history", employeeId).with(hr()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].effectiveFrom").value("2024-01-01"))
                .andExpect(jsonPath("$[1].annualSalary").value(90000.00));

        assertThat(salaryRepository.findByEmployee_IdOrderByEffectiveFromDesc(employeeId)).hasSize(2);
    }

    @Test
    void retrieveSalaryHistory() throws Exception {
        mockMvc.perform(get("/api/v1/employees/{id}/salary-history", employeeId).with(hr()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void invalidSalary() throws Exception {
        SalaryRequest invalid = new SalaryRequest(new BigDecimal("-5"), "USD", LocalDate.of(2024, 2, 1));

        mockMvc.perform(put("/api/v1/employees/{id}/salary", employeeId)
                        .with(hr())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isUnprocessableEntity());

        assertThat(salaryRepository.findByEmployee_IdOrderByEffectiveFromDesc(employeeId)).hasSize(1);
    }

    @Test
    void invalidCurrency() throws Exception {
        mockMvc.perform(put("/api/v1/employees/{id}/salary", employeeId)
                        .with(hr())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SalaryRequest(new BigDecimal("1000"), "ZZZ", LocalDate.of(2024, 2, 1)))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void nonexistentEmployee() throws Exception {
        UUID missing = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/employees/{id}/salary", missing).with(hr()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/employees/{id}/salary-history", missing).with(hr()))
                .andExpect(status().isNotFound());
    }

    private static RequestPostProcessor hr() {
        return user("hr.manager").roles("HR_MANAGER");
    }
}
