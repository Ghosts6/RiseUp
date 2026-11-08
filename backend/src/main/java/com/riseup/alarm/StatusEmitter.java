package com.riseup.alarm;

import com.riseup.clock.Clock;
import com.riseup.events.EventBus;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class StatusEmitter {
    private final EventBus bus;
    private final Clock clock;

    public StatusEmitter(EventBus bus, Clock clock) {
        this.bus = bus;
        this.clock = clock;
    }

    public void emit(String subjectType, Integer subjectId, Integer userId, String status, Map<String, Object> payload) {
        bus.emit(subjectType, subjectId, userId, status, clock::now, payload == null ? Map.of() : new HashMap<>(payload));
    }
}
