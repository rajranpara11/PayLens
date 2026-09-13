package com.paylens.analytics.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.paylens.analytics.dto.AnalyticsOverviewResponse;
import com.paylens.analytics.dto.CountryHeadcountRow;
import com.paylens.analytics.dto.CountryPayrollRow;
import com.paylens.analytics.dto.CurrencyCompensationStats;
import com.paylens.analytics.dto.DepartmentSalaryRow;
import com.paylens.analytics.dto.DesignationSalaryRow;
import com.paylens.analytics.dto.SalaryDistributionBucket;
import com.paylens.analytics.service.AnalyticsService;
import com.paylens.common.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AnalyticsController.class, excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsService analyticsService;

    @Test
    void overviewReturnsCurrencyGroupedStats() throws Exception {
        when(analyticsService.overview()).thenReturn(new AnalyticsOverviewResponse(
                10,
                8,
                List.of(new CurrencyCompensationStats(
                        "USD", 8, new BigDecimal("100000.00"), new BigDecimal("95000.00"),
                        new BigDecimal("80000.00"), new BigDecimal("150000.00"), new BigDecimal("800000.00")
                )),
                AnalyticsService.CURRENCY_NOTE
        ));

        mockMvc.perform(get("/api/v1/analytics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEmployees").value(10))
                .andExpect(jsonPath("$.compensationByCurrency[0].currency").value("USD"))
                .andExpect(jsonPath("$.currencyNote").exists());
    }

    @Test
    void payrollByCountryPreservesCurrency() throws Exception {
        when(analyticsService.payrollByCountry()).thenReturn(List.of(
                new CountryPayrollRow("IN", "INR", 5, new BigDecimal("5000000.00"), new BigDecimal("1000000.00")),
                new CountryPayrollRow("US", "USD", 3, new BigDecimal("300000.00"), new BigDecimal("100000.00"))
        ));

        mockMvc.perform(get("/api/v1/analytics/payroll-by-country"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].country").value("IN"))
                .andExpect(jsonPath("$[0].currency").value("INR"))
                .andExpect(jsonPath("$[1].currency").value("USD"));
    }

    @Test
    void employeeCountByCountry() throws Exception {
        when(analyticsService.employeeCountByCountry()).thenReturn(List.of(
                new CountryHeadcountRow("US", 3)
        ));

        mockMvc.perform(get("/api/v1/analytics/employee-count-by-country"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeCount").value(3));
    }

    @Test
    void salaryByDepartment() throws Exception {
        when(analyticsService.salaryByDepartment()).thenReturn(List.of(
                new DepartmentSalaryRow(
                        "ENG", "Engineering", "USD", 4,
                        new BigDecimal("100000.00"), new BigDecimal("95000.00"),
                        new BigDecimal("80000.00"), new BigDecimal("150000.00"), new BigDecimal("400000.00")
                )
        ));

        mockMvc.perform(get("/api/v1/analytics/salary-by-department"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].departmentCode").value("ENG"))
                .andExpect(jsonPath("$[0].currency").value("USD"));
    }

    @Test
    void salaryByDesignation() throws Exception {
        when(analyticsService.salaryByDesignation()).thenReturn(List.of(
                new DesignationSalaryRow(
                        "Engineer", "USD", 2,
                        new BigDecimal("110000.00"), new BigDecimal("110000.00"),
                        new BigDecimal("100000.00"), new BigDecimal("120000.00"), new BigDecimal("220000.00")
                )
        ));

        mockMvc.perform(get("/api/v1/analytics/salary-by-designation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].designation").value("Engineer"))
                .andExpect(jsonPath("$[0].averageSalary").value(110000.00));
    }

    @Test
    void salaryDistribution() throws Exception {
        when(analyticsService.salaryDistribution()).thenReturn(List.of(
                new SalaryDistributionBucket("USD", 1, 2, new BigDecimal("80000.00"), new BigDecimal("90000.00"))
        ));

        mockMvc.perform(get("/api/v1/analytics/salary-distribution"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bucket").value(1))
                .andExpect(jsonPath("$[0].employeeCount").value(2));
    }
}
