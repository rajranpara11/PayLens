package com.paylens.analytics.dto;

import java.math.BigDecimal;

public record SalaryDistributionBucket(
        String currency,
        int bucket,
        long employeeCount,
        BigDecimal bandMin,
        BigDecimal bandMax
) {
}
