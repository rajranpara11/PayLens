package com.paylens.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paylens.employee.dto.CreateEmployeeRequest;
import com.paylens.employee.dto.SalaryRequest;
import com.paylens.employee.dto.UpdateEmployeeRequest;
import com.paylens.employee.entity.Department;
import com.paylens.employee.entity.EmploymentStatus;
import com.paylens.employee.repository.DepartmentRepository;
import com.paylens.employee.repository.EmployeeRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * End-to-end employee lifecycle against a real DB + security.
 * Covers create, duplicate, search, filter, pagination, update, not-found, deactivate.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:paylens_employee_api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
class EmployeeApiIntegrationTest {

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

    @BeforeEach
    void setUp() {
        salaryRepository.deleteAll();
        employeeRepository.deleteAll();
        departmentRepository.deleteAll();

        Department eng = new Department();
        eng.setCode("ENG");
        eng.setName("Engineering");
        departmentRepository.save(eng);

        Department sales = new Department();
        sales.setCode("SAL");
        sales.setName("Sales");
        departmentRepository.save(sales);
    }

    @Test
    void createUpdateSearchFilterPaginateAndDeactivate() throws Exception {
        String aliceId = createEmployee(createBody(
                "E-ALICE", "Alice", "Nguyen", "alice@acme.com", "US", "Engineering", "Engineer", "90000", "USD"
        ));
        createEmployee(createBody(
                "E-BOB", "Bob", "Patel", "bob@acme.com", "IN", "Sales", "Account Executive", "1200000", "INR"
        ));
        createEmployee(createBody(
                "E-CARA", "Cara", "Nguyen", "cara@acme.com", "US", "Engineering", "Senior Engineer", "120000", "USD"
        ));

        mockMvc.perform(post("/api/v1/employees")
                        .with(hr())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody(
                                "E-ALICE", "Dup", "User", "dup@acme.com", "US", "Engineering", "Engineer", "1", "USD"
                        ))))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/employees")
                        .with(hr())
                        .param("search", "Nguyen")
                        .param("employmentStatus", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));

        mockMvc.perform(get("/api/v1/employees")
                        .with(hr())
                        .param("country", "IN")
                        .param("department", "Sales"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].employeeCode").value("E-BOB"));

        mockMvc.perform(get("/api/v1/employees")
                        .with(hr())
                        .param("designation", "Senior Engineer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].employeeCode").value("E-CARA"));

        mockMvc.perform(get("/api/v1/employees")
                        .with(hr())
                        .param("currency", "USD")
                        .param("size", "1")
                        .param("page", "0")
                        .param("sort", "lastName,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get("/api/v1/employees")
                        .with(hr())
                        .param("currency", "USD")
                        .param("size", "1")
                        .param("page", "1")
                        .param("sort", "lastName,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content.length()").value(1));

        UpdateEmployeeRequest update = new UpdateEmployeeRequest(
                "E-ALICE",
                "Alice",
                "Nguyen",
                "alice@acme.com",
                "US",
                "Engineering",
                "Staff Engineer",
                "ACTIVE",
                LocalDate.of(2023, 1, 1),
                null
        );
        mockMvc.perform(put("/api/v1/employees/{id}", aliceId)
                        .with(hr())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designation").value("Staff Engineer"));

        mockMvc.perform(delete("/api/v1/employees/{id}", aliceId).with(hr()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/employees/{id}", aliceId).with(hr()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employmentStatus").value(EmploymentStatus.TERMINATED.name()));

        mockMvc.perform(get("/api/v1/employees/{id}", UUID.randomUUID()).with(hr()))
                .andExpect(status().isNotFound());

        assertThat(employeeRepository.count()).isEqualTo(3);
        assertThat(salaryRepository.count()).isEqualTo(3);
    }

    @Test
    void createRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/v1/employees")
                        .with(hr())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    private String createEmployee(CreateEmployeeRequest body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/employees")
                        .with(hr())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private static CreateEmployeeRequest createBody(
            String code,
            String first,
            String last,
            String email,
            String country,
            String department,
            String designation,
            String salary,
            String currency
    ) {
        return new CreateEmployeeRequest(
                code,
                first,
                last,
                email,
                country,
                department,
                designation,
                "ACTIVE",
                LocalDate.of(2023, 1, 1),
                new SalaryRequest(new BigDecimal(salary), currency, LocalDate.of(2023, 1, 1))
        );
    }

    private static RequestPostProcessor hr() {
        return user("hr.manager").roles("HR_MANAGER");
    }
}
