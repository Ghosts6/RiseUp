package com.riseup.cli.api;
import com.fasterxml.jackson.databind.JsonNode;
public class ApiException extends RuntimeException {
    private final int status;
    private final JsonNode data;
    public ApiException(int status, String message) { this(status, message, null); }
    public ApiException(int status, String message, JsonNode data) {
        super(message); this.status = status; this.data = data;
    }
    public ApiException(String message) { this(0, message, null); }
    public ApiException(String message, Throwable cause) { super(message, cause); this.status = 0; this.data = null; }
    public int getStatus() { return status; }
    public JsonNode getData() { return data; }
    public boolean isUserError() { return status >= 400 && status < 500; }
}
