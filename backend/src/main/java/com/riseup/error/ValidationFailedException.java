package com.riseup.error;

import java.util.Map;

public class ValidationFailedException extends DomainException {
    public ValidationFailedException(String message) {
        super(422, "validation_error", message);
    }

    public ValidationFailedException(String message, String field) {
        super(422, "validation_error", message, Map.of("field", field));
    }

    public ValidationFailedException(String message, Map<String, Object> extra) {
        super(422, "validation_error", message, extra);
    }
}
