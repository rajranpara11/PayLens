package com.paylens.salary.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paylens.common.exception.GlobalExceptionHandler;
import com.paylens.common.exception.NotFoundException;
import com.paylens.employee.dto.SalaryRequest;
import com.paylens.employee.dto.SalaryResponse;
import com.paylens.salary.service.SalaryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SalaryController.class, excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SalaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SalaryService salaryService;

    @Test
    void getCurrentSalary() throws Exception {
        UUID employeeId = UUID.randomUUID();
        when(salaryService.getCurrent(employeeId)).thenReturn(sampleSalary());

        mockMvc.perform(get("/api/v1/employees/{employeeId}/salary", employeeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.annualSalary").value(100000.00));
    }

    @Test
    void updateSalary() throws Exception {
        UUID employeeId = UUID.randomUUID();
        when(salaryService.upsert(eq(employeeId), any(SalaryRequest.class))).thenReturn(sampleSalary());

        mockMvc.perform(put("/api/v1/employees/{employeeId}/salary", employeeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SalaryRequest(new BigDecimal("100000.00"), "USD", LocalDate.of(2024, 1, 1))
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveFrom").value("2024-01-01"));
    }

    @Test
    void retrieveSalaryHistory() throws Exception {
        UUID employeeId = UUID.randomUUID();
        when(salaryService.history(employeeId)).thenReturn(List.of(sampleSalary()));

        mockMvc.perform(get("/api/v1/employees/{employeeId}/salary-history", employeeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currency").value("USD"));
    }

    @Test
    void invalidSalaryReturns422() throws Exception {
        UUID employeeId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/employees/{employeeId}/salary", employeeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"annualSalary":-10,"currency":"USD","effectiveFrom":"2024-01-01"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void invalidCurrencyReturns422() throws Exception {
        UUID employeeId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/employees/{employeeId}/salary", employeeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"annualSalary":1000,"currency":"US","effectiveFrom":"2024-01-01"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void nonexistentEmployeeReturns404() throws Exception {
        UUID employeeId = UUID.randomUUID();
        when(salaryService.getCurrent(employeeId)).thenThrow(new NotFoundException("Employee not found"));

        mockMvc.perform(get("/api/v1/employees/{employeeId}/salary", employeeId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Employee not found"));
    }

    private SalaryResponse sampleSalary() {
        return new SalaryResponse(
                UUID.randomUUID(),
                new BigDecimal("100000.00"),
                "USD",
                LocalDate.of(2024, 1, 1)
        );
    }
}
