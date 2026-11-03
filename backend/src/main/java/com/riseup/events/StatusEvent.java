package com.riseup.events;

import java.time.LocalDateTime;
import java.util.Map;

public record StatusEvent(
        String subjectType,
        Integer subjectId,
        Integer userId,
        String status,
        LocalDateTime at,
        Map<String, Object> payload) {}
