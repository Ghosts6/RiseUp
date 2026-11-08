package com.riseup.verification;

import java.util.Map;

/** Strategy interface: every challenge exposes a prompt and a validator. */
public interface VerificationTask {
    String getTaskType();

    Map<String, Object> getTaskPrompt();

    VerificationResult validate(VerificationResponse response);

    Map<String, Object> toState();

    default boolean resetsOnFailure() {
        return false;
    }
}
