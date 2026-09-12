package com.paylens.analytics.dto;

import java.math.BigDecimal;

public record CountryPayrollRow(
        String country,
        String currency,
        long employeeCount,
        BigDecimal totalPayroll,
        BigDecimal averageSalary
) {
}
