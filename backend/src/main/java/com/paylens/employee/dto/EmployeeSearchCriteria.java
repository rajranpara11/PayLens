package com.paylens.employee.dto;

public record EmployeeSearchCriteria(
        String search,
        String country,
        String department,
        String designation,
        String employmentStatus,
        String currency
) {
}
