package com.riseup.scheduling;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class SpringJobScheduler implements JobScheduler {
    private final ThreadPoolTaskScheduler scheduler;
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    public SpringJobScheduler() {
        this.scheduler = new ThreadPoolTaskScheduler();
        this.scheduler.setPoolSize(4);
        this.scheduler.setThreadNamePrefix("riseup-job-");
        this.scheduler.initialize();
    }

    @Override
    public void schedule(String jobId, LocalDateTime runAt, Consumer<Map<String, Object>> func, Map<String, Object> kwargs) {
        cancel(jobId);
        ScheduledFuture<?> future =
                scheduler.schedule(
                        () -> {
                            futures.remove(jobId);
                            func.accept(kwargs == null ? Map.of() : kwargs);
                        },
                        runAt.toInstant(ZoneOffset.UTC));
        futures.put(jobId, future);
    }

    @Override
    public void cancel(String jobId) {
        ScheduledFuture<?> f = futures.remove(jobId);
        if (f != null) f.cancel(false);
    }

    @Override
    public boolean hasJob(String jobId) {
        return futures.containsKey(jobId);
    }

    @Override
    public void shutdown() {
        scheduler.shutdown();
    }
}
