package com.paylens.employee.controller;

import com.paylens.common.response.PageResponse;
import com.paylens.employee.dto.CreateEmployeeRequest;
import com.paylens.employee.dto.EmployeeResponse;
import com.paylens.employee.dto.EmployeeSearchCriteria;
import com.paylens.employee.dto.UpdateEmployeeRequest;
import com.paylens.employee.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employees")
@Tag(name = "Employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    @Operation(summary = "List employees with pagination, search, and filters")
    public PageResponse<EmployeeResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String designation,
            @RequestParam(required = false) String employmentStatus,
            @RequestParam(required = false) String currency,
            @PageableDefault(size = 25, sort = "lastName", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        EmployeeSearchCriteria criteria = new EmployeeSearchCriteria(
                search,
                country,
                department,
                designation,
                employmentStatus,
                currency
        );
        return PageResponse.from(employeeService.list(criteria, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get employee by id")
    public EmployeeResponse get(@PathVariable UUID id) {
        return employeeService.get(id);
    }

    @PostMapping
    @Operation(summary = "Create employee and first salary")
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody CreateEmployeeRequest request) {
        EmployeeResponse created = employeeService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/employees/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update employee")
    public EmployeeResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateEmployeeRequest request) {
        return employeeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate employee (soft delete)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        employeeService.deactivate(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
