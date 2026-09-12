package com.paylens.employee.controller;

import com.paylens.employee.dto.DepartmentResponse;
import com.paylens.employee.repository.DepartmentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/departments")
@Tag(name = "Departments")
public class DepartmentController {

    private final DepartmentRepository departmentRepository;

    public DepartmentController(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "List departments for filters and forms")
    public List<DepartmentResponse> list() {
        return departmentRepository.findAllByOrderByNameAsc().stream()
                .map(department -> new DepartmentResponse(
                        department.getId(),
                        department.getCode(),
                        department.getName()
                ))
                .toList();
    }
}
