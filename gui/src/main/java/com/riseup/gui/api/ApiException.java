package com.riseup.gui.api;

public class ApiException extends RuntimeException {
    private final int statusCode;
    private final String code;
    private final String detail;

    public ApiException(String message) {
        this(message, 0, null, message, null);
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.code = null;
        this.detail = message;
    }

    public ApiException(String message, int statusCode, String code, String detail, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.code = code;
        this.detail = detail == null ? message : detail;
    }

    public int statusCode() {
        return statusCode;
    }

    public String code() {
        return code;
    }

    /** Human-facing explanation (never a raw JSON dump). */
    public String userMessage() {
        String d = detail == null ? getMessage() : detail;
        if (d == null) {
            return "Something went wrong.";
        }
        String lower = d.toLowerCase();
        if (lower.contains("cannot be disabled") || lower.contains("cannot be deleted")
                || lower.contains("complete the challenge")) {
            return "This alarm is ringing. Answer the challenge to turn it off. "
                    + "You can’t dismiss it from the dashboard.";
        }
        if (lower.contains("not ringing")) {
            return "That alarm isn’t ringing right now.";
        }
        if (lower.contains("snooze limit")) {
            return "You’ve used all your snoozes. Finish the challenge to dismiss it.";
        }
        if (lower.contains("running") && lower.contains("edit")) {
            return "Finish or snooze the ringing alarm before editing it.";
        }
        // Strip accidental JSON wrappers if detail still looks raw
        if (d.trim().startsWith("{") && d.contains("detail")) {
            return "Something went wrong with that action. Try the challenge screen if the alarm is ringing.";
        }
        return d;
    }
}
