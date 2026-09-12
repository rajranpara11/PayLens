package com.paylens.seed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SeedModels {

    private SeedModels() {
    }

    public record DepartmentSeed(UUID id, String code, String name) {
    }

    public record EmployeeSeed(
            UUID id,
            String employeeCode,
            String firstName,
            String lastName,
            String email,
            String country,
            UUID departmentId,
            String designation,
            String employmentStatus,
            LocalDate joiningDate,
            List<SalarySeed> salaries
    ) {
    }

    public record SalarySeed(
            UUID id,
            BigDecimal annualSalary,
            String currency,
            LocalDate effectiveFrom
    ) {
    }

    public record SeedDataset(
            List<DepartmentSeed> departments,
            List<EmployeeSeed> employees
    ) {
    }
}
