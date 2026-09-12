package com.paylens.salary.dto;

import com.paylens.employee.dto.SalaryResponse;
import com.paylens.salary.entity.Salary;

public final class SalaryMapper {

    private SalaryMapper() {
    }

    public static SalaryResponse toResponse(Salary salary) {
        return new SalaryResponse(
                salary.getId(),
                salary.getAnnualSalary(),
                salary.getCurrency(),
                salary.getEffectiveFrom()
        );
    }
}
