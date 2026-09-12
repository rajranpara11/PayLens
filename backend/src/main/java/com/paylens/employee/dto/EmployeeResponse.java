package com.paylens.employee.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EmployeeResponse(
        UUID id,
        String employeeCode,
        String firstName,
        String lastName,
        String email,
        String country,
        DepartmentResponse department,
        String designation,
        String employmentStatus,
        LocalDate joiningDate,
        SalaryResponse currentSalary,
        Instant createdAt,
        Instant updatedAt
) {
}
