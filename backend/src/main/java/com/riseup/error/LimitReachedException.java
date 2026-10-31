package com.riseup.error;

public class LimitReachedException extends DomainException {
    public LimitReachedException(String message) {
        super(409, "limit_reached", message);
    }
}
