package com.riseup.ai;

import java.util.List;
import java.util.Map;

public interface LLMProvider {
    String getName();

    String getModel();

    String complete(String prompt, String system);

    /** Multi-turn plain-text chat (no JSON response_format). */
    default String completeChat(List<Map<String, String>> messages, String system) {
        String lastUser = "";
        if (messages != null) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                Map<String, String> m = messages.get(i);
                if (m != null && "user".equalsIgnoreCase(String.valueOf(m.get("role")))) {
                    lastUser = String.valueOf(m.getOrDefault("content", ""));
                    break;
                }
            }
        }
        return complete(lastUser, system);
    }

    /** Whether {@link #completeWithImage} is implemented (image understanding). */
    default boolean supportsVision() {
        return false;
    }

    /** Single-turn request with one image (JPEG/PNG bytes) plus a text prompt. */
    default String completeWithImage(String prompt, String system, byte[] image, String mediaType) {
        throw new LLMProviderError(getName() + " does not support image input");
    }

    default String label() {
        return getModel() == null || getModel().isBlank() ? getName() : getName() + ":" + getModel();
    }
}
