package com.riseup.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

@Component
public class EventBus {
    private final List<StatusObserver> observers = new CopyOnWriteArrayList<>();

    public void register(StatusObserver observer) {
        observers.add(observer);
    }

    public void emit(String subjectType, Integer subjectId, Integer userId, String status, LocalDateTimeSupplier at, Map<String, Object> payload) {
        StatusEvent event = new StatusEvent(subjectType, subjectId, userId, status, at.now(), payload == null ? Map.of() : payload);
        for (StatusObserver o : observers) {
            if (o.caresAbout(subjectType)) {
                try {
                    o.onStatusChanged(event);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @FunctionalInterface
    public interface LocalDateTimeSupplier {
        java.time.LocalDateTime now();
    }

    public List<StatusObserver> getObservers() {
        return new ArrayList<>(observers);
    }
}
