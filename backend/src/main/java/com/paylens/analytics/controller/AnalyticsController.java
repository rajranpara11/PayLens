package com.paylens.analytics.controller;

import com.paylens.analytics.dto.AnalyticsOverviewResponse;
import com.paylens.analytics.dto.CountryHeadcountRow;
import com.paylens.analytics.dto.CountryPayrollRow;
import com.paylens.analytics.dto.DepartmentSalaryRow;
import com.paylens.analytics.dto.DesignationSalaryRow;
import com.paylens.analytics.dto.SalaryDistributionBucket;
import com.paylens.analytics.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/overview")
    @Operation(summary = "Org compensation overview, metrics grouped by currency")
    public AnalyticsOverviewResponse overview() {
        return analyticsService.overview();
    }

    @GetMapping("/payroll-by-country")
    @Operation(summary = "Payroll totals by country and currency")
    public List<CountryPayrollRow> payrollByCountry() {
        return analyticsService.payrollByCountry();
    }

    @GetMapping("/salary-by-department")
    @Operation(summary = "Salary stats by department and currency")
    public List<DepartmentSalaryRow> salaryByDepartment() {
        return analyticsService.salaryByDepartment();
    }

    @GetMapping("/salary-by-designation")
    @Operation(summary = "Salary stats by designation and currency")
    public List<DesignationSalaryRow> salaryByDesignation() {
        return analyticsService.salaryByDesignation();
    }

    @GetMapping("/salary-distribution")
    @Operation(summary = "Salary band counts per currency (5 equal-width buckets)")
    public List<SalaryDistributionBucket> salaryDistribution() {
        return analyticsService.salaryDistribution();
    }

    @GetMapping("/employee-count-by-country")
    @Operation(summary = "Headcount by country (non-money)")
    public List<CountryHeadcountRow> employeeCountByCountry() {
        return analyticsService.employeeCountByCountry();
    }
}
