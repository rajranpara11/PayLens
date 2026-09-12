package com.paylens.common.exception;

import com.paylens.common.response.ApiErrorResponse.FieldErrorDetail;
import java.util.List;
import org.springframework.http.HttpStatus;

public class ValidationException extends ApiException {

    private final List<FieldErrorDetail> fieldErrors;

    public ValidationException(String message) {
        this(message, List.of());
    }

    public ValidationException(String message, String field) {
        this(message, List.of(new FieldErrorDetail(field, message)));
    }

    public ValidationException(String message, List<FieldErrorDetail> fieldErrors) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public List<FieldErrorDetail> getFieldErrors() {
        return fieldErrors;
    }
}
