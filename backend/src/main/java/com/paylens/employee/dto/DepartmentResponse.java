package com.paylens.employee.dto;

import java.util.UUID;

public record DepartmentResponse(
        UUID id,
        String code,
        String name
) {
}
