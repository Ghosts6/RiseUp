package com.riseup.error;

public class ConflictException extends DomainException {
    public ConflictException(String message) {
        super(409, "conflict", message);
    }
}
