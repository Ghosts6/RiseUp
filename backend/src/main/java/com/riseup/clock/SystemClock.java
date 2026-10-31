package com.riseup.clock;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class SystemClock implements Clock {
    @Override
    public LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
