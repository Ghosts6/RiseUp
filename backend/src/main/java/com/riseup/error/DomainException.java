package com.riseup.error;

import java.util.LinkedHashMap;
import java.util.Map;

public class DomainException extends RuntimeException {
    private final int statusCode;
    private final String code;
    private final Map<String, Object> extra;

    public DomainException(int statusCode, String code, String message) {
        this(statusCode, code, message, Map.of());
    }

    public DomainException(int statusCode, String code, String message, Map<String, Object> extra) {
        super(message);
        this.statusCode = statusCode;
        this.code = code;
        this.extra = new LinkedHashMap<>(extra);
    }

    public int getStatusCode() { return statusCode; }
    public String getCode() { return code; }
    public Map<String, Object> getExtra() { return extra; }
}
