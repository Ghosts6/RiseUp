package com.riseup.ai;

public interface LLMProvider {
    String getName();

    String getModel();

    String complete(String prompt, String system);

    default String label() {
        return getModel() == null || getModel().isBlank() ? getName() : getName() + ":" + getModel();
    }
}
