package com.paylens.analytics.dto;

import java.util.List;

public record AnalyticsOverviewResponse(
        long totalEmployees,
        long employedEmployees,
        List<CurrencyCompensationStats> compensationByCurrency,
        String currencyNote
) {
}
