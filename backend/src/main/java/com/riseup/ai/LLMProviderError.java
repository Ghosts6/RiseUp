package com.riseup.ai;

public class LLMProviderError extends RuntimeException {
    public LLMProviderError(String message) {
        super(message);
    }

    public LLMProviderError(String message, Throwable cause) {
        super(message, cause);
    }
}
