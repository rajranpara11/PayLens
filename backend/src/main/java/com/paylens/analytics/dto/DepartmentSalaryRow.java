package com.paylens.analytics.dto;

import java.math.BigDecimal;

public record DepartmentSalaryRow(
        String departmentCode,
        String departmentName,
        String currency,
        long employeeCount,
        BigDecimal averageSalary,
        BigDecimal medianSalary,
        BigDecimal minSalary,
        BigDecimal maxSalary,
        BigDecimal totalPayroll
) {
}
