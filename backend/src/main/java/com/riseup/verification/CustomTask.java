package com.riseup.verification;

import java.util.LinkedHashMap;
import java.util.Map;

public class CustomTask implements VerificationTask {
    private final String description;

    public CustomTask(String description) {
        this.description = (description == null || description.isBlank()) ? "Complete your custom task" : description;
    }

    @Override
    public String getTaskType() {
        return "custom";
    }

    @Override
    public Map<String, Object> getTaskPrompt() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", getTaskType());
        m.put("question", description);
        m.put("input", "confirm");
        return m;
    }

    @Override
    public VerificationResult validate(VerificationResponse response) {
        if (response.isCompleted()) return new VerificationResult(true, "Task marked as completed.");
        return new VerificationResult(false, "Mark the task as completed when done.");
    }

    @Override
    public Map<String, Object> toState() {
        return Map.of("type", getTaskType(), "description", description);
    }
}
