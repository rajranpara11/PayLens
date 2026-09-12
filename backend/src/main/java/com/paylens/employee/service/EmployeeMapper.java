package com.paylens.employee.service;

import com.paylens.employee.dto.DepartmentResponse;
import com.paylens.employee.dto.EmployeeResponse;
import com.paylens.employee.dto.SalaryResponse;
import com.paylens.employee.entity.Department;
import com.paylens.employee.entity.Employee;
import com.paylens.salary.entity.Salary;

final class EmployeeMapper {

    private EmployeeMapper() {
    }

    static EmployeeResponse toResponse(Employee employee, Salary currentSalary) {
        Department department = employee.getDepartment();
        return new EmployeeResponse(
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getEmail(),
                employee.getCountry(),
                new DepartmentResponse(department.getId(), department.getCode(), department.getName()),
                employee.getDesignation(),
                employee.getEmploymentStatus().name(),
                employee.getJoiningDate(),
                currentSalary == null ? null : toSalaryResponse(currentSalary),
                employee.getCreatedAt(),
                employee.getUpdatedAt()
        );
    }

    private static SalaryResponse toSalaryResponse(Salary salary) {
        return new SalaryResponse(
                salary.getId(),
                salary.getAnnualSalary(),
                salary.getCurrency(),
                salary.getEffectiveFrom()
        );
    }
}
