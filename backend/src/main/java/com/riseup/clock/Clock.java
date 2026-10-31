package com.riseup.clock;

import java.time.LocalDateTime;

/** Time source abstraction. All datetimes inside the backend are naive UTC. */
public interface Clock {
    LocalDateTime now();
}
