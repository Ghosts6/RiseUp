package com.riseup.clock;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Manually controlled clock for tests and simulations. */
public class FixedClock implements Clock {
    private LocalDateTime now;

    public FixedClock(LocalDateTime start) {
        this.now = start;
    }

    @Override
    public LocalDateTime now() {
        return now;
    }

    public void set(LocalDateTime value) {
        this.now = value;
    }

    public LocalDateTime advance(long amount, ChronoUnit unit) {
        now = now.plus(amount, unit);
        return now;
    }

    public LocalDateTime advanceSeconds(long seconds) {
        return advance(seconds, ChronoUnit.SECONDS);
    }

    public LocalDateTime advanceMinutes(long minutes) {
        return advance(minutes, ChronoUnit.MINUTES);
    }

    public LocalDateTime advanceHours(long hours) {
        return advance(hours, ChronoUnit.HOURS);
    }
}
