package com.paylens.analytics.dto;

import java.math.BigDecimal;

/**
 * Money metrics for a single currency. Never mix with another currency's row.
 */
public record CurrencyCompensationStats(
        String currency,
        long employeeCount,
        BigDecimal averageSalary,
        BigDecimal medianSalary,
        BigDecimal minSalary,
        BigDecimal maxSalary,
        BigDecimal totalPayroll
) {
}
