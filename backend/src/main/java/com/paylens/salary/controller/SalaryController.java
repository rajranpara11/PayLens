package com.paylens.salary.controller;

import com.paylens.employee.dto.SalaryRequest;
import com.paylens.employee.dto.SalaryResponse;
import com.paylens.salary.service.SalaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employees/{employeeId}")
@Tag(name = "Salaries")
public class SalaryController {

    private final SalaryService salaryService;

    public SalaryController(SalaryService salaryService) {
        this.salaryService = salaryService;
    }

    @GetMapping("/salary")
    @Operation(summary = "Get the employee's current salary (latest effective on or before today)")
    public SalaryResponse getCurrent(@PathVariable UUID employeeId) {
        return salaryService.getCurrent(employeeId);
    }

    @PutMapping("/salary")
    @Operation(summary = "Set salary; a new effective date appends history")
    public SalaryResponse upsert(@PathVariable UUID employeeId, @Valid @RequestBody SalaryRequest request) {
        return salaryService.upsert(employeeId, request);
    }

    @GetMapping("/salary-history")
    @Operation(summary = "List salary history, newest effective date first")
    public List<SalaryResponse> history(@PathVariable UUID employeeId) {
        return salaryService.history(employeeId);
    }
}
