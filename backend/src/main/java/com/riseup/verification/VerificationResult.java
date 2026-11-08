package com.riseup.verification;

import java.util.HashMap;
import java.util.Map;

public class VerificationResult {
    private final boolean success;
    private final String message;
    private final boolean cameraError;
    private final Map<String, Object> data;

    public VerificationResult(boolean success, String message) {
        this(success, message, false, Map.of());
    }

    public VerificationResult(boolean success, String message, boolean cameraError) {
        this(success, message, cameraError, Map.of());
    }

    public VerificationResult(boolean success, String message, boolean cameraError, Map<String, Object> data) {
        this.success = success;
        this.message = message;
        this.cameraError = cameraError;
        this.data = data == null ? new HashMap<>() : new HashMap<>(data);
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public boolean isCameraError() { return cameraError; }
    public Map<String, Object> getData() { return data; }
}
