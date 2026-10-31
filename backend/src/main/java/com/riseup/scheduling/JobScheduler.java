package com.riseup.scheduling;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Consumer;

public interface JobScheduler {
    void schedule(String jobId, LocalDateTime runAt, Consumer<Map<String, Object>> func, Map<String, Object> kwargs);

    void cancel(String jobId);

    boolean hasJob(String jobId);

    default void start() {}

    default void shutdown() {}
}
