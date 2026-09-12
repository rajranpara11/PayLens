package com.paylens.employee.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SalaryRequest(
        @NotNull
        @DecimalMin(value = "0.01", message = "annualSalary must be greater than 0")
        @Digits(integer = 13, fraction = 2)
        BigDecimal annualSalary,

        @NotBlank
        @Size(min = 3, max = 3)
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "currency must be a 3-letter ISO 4217 code")
        String currency,

        @NotNull
        LocalDate effectiveFrom
) {
}
