package com.riseup.gui.model;

import com.fasterxml.jackson.databind.JsonNode;

/** Thin view of a WebSocket live event payload. */
public record LiveEvent(
        String type,
        String subjectType,
        int subjectId,
        String status,
        String title,
        String at
) {
    public static LiveEvent from(JsonNode node) {
        if (node == null || node.isNull()) {
            return new LiveEvent("event", "", 0, "", "", "");
        }
        String status = node.path("status").asText(node.path("event").asText(""));
        return new LiveEvent(
                node.path("type").asText("event"),
                node.path("subject_type").asText(""),
                node.path("subject_id").asInt(0),
                status,
                node.path("title").asText(""),
                node.path("at").asText("")
        );
    }

    public String displayLine() {
        String head = title == null || title.isBlank() ? type + " " + status : title;
        return head + (at == null || at.isBlank() ? "" : " · " + at);
    }

    public boolean isRingingAlarm() {
        return "alarm".equals(subjectType)
                && ("ringing".equals(status) || "escalating".equals(status));
    }

    public boolean isCheckIn() {
        return "alarm".equals(subjectType) && "awaiting_checkin".equals(status);
    }
}
