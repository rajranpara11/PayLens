package com.paylens.employee.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SalaryResponse(
        UUID id,
        BigDecimal annualSalary,
        String currency,
        LocalDate effectiveFrom
) {
}
