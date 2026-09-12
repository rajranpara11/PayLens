package com.paylens.employee.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateEmployeeRequest(
        @NotBlank
        @Size(max = 32)
        String employeeCode,

        @NotBlank
        @Size(max = 100)
        String firstName,

        @NotBlank
        @Size(max = 100)
        String lastName,

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 2, max = 56)
        String country,

        @NotBlank
        @Size(max = 120)
        String department,

        @NotBlank
        @Size(max = 120)
        String designation,

        @NotBlank
        @Size(max = 20)
        String employmentStatus,

        @NotNull
        LocalDate joiningDate,

        @Valid
        SalaryRequest salary
) {
}
