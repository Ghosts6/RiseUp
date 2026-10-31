package com.riseup.error;

public class NotFoundException extends DomainException {
    public NotFoundException(String message) {
        super(404, "not_found", message);
    }
}
