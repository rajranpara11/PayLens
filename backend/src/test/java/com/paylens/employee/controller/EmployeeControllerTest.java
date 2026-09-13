package com.paylens.employee.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paylens.common.exception.GlobalExceptionHandler;
import com.paylens.common.exception.NotFoundException;
import com.paylens.employee.dto.CreateEmployeeRequest;
import com.paylens.employee.dto.DepartmentResponse;
import com.paylens.employee.dto.EmployeeResponse;
import com.paylens.employee.dto.EmployeeSearchCriteria;
import com.paylens.employee.dto.SalaryRequest;
import com.paylens.employee.dto.SalaryResponse;
import com.paylens.employee.dto.UpdateEmployeeRequest;
import com.paylens.employee.service.EmployeeService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = EmployeeController.class, excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmployeeService employeeService;

    @Test
    void listReturnsPage() throws Exception {
        EmployeeResponse employee = sampleEmployee();
        when(employeeService.list(any(EmployeeSearchCriteria.class), any()))
                .thenReturn(new PageImpl<>(List.of(employee), PageRequest.of(0, 25), 1));

        mockMvc.perform(get("/api/v1/employees")
                        .param("page", "0")
                        .param("size", "25")
                        .param("search", "john")
                        .param("country", "India")
                        .param("department", "Engineering"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].employeeCode").value("E-1"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void getByIdReturnsEmployee() throws Exception {
        EmployeeResponse employee = sampleEmployee();
        when(employeeService.get(employee.id())).thenReturn(employee);

        mockMvc.perform(get("/api/v1/employees/{id}", employee.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john@acme.com"));
    }

    @Test
    void getByIdReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(employeeService.get(id)).thenThrow(new NotFoundException("Employee not found"));

        mockMvc.perform(get("/api/v1/employees/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Employee not found"));
    }

    @Test
    void createReturns201() throws Exception {
        EmployeeResponse employee = sampleEmployee();
        when(employeeService.create(any(CreateEmployeeRequest.class))).thenReturn(employee);

        mockMvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(employee.id().toString()));
    }

    @Test
    void createReturns422WhenInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void updateReturns200() throws Exception {
        EmployeeResponse employee = sampleEmployee();
        when(employeeService.update(eq(employee.id()), any(UpdateEmployeeRequest.class))).thenReturn(employee);

        mockMvc.perform(put("/api/v1/employees/{id}", employee.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeCode").value("E-1"));
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/employees/{id}", id))
                .andExpect(status().isNoContent());

        verify(employeeService).deactivate(id);
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

    private EmployeeResponse sampleEmployee() {
        Instant now = Instant.parse("2024-01-15T00:00:00Z");
        return new EmployeeResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "E-1",
                "John",
                "Doe",
                "john@acme.com",
                "IN",
                new DepartmentResponse(UUID.randomUUID(), "ENG", "Engineering"),
                "Engineer",
                "ACTIVE",
                LocalDate.of(2024, 1, 15),
                new SalaryResponse(UUID.randomUUID(), new BigDecimal("120000.00"), "INR", LocalDate.of(2024, 1, 15)),
                now,
                now
        );
    }
}
