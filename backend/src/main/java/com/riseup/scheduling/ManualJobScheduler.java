package com.riseup.scheduling;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Deterministic scheduler: tests advance a clock and call runDue(now). */
public class ManualJobScheduler implements JobScheduler {
    public record ManualJob(String jobId, LocalDateTime runAt, Consumer<Map<String, Object>> func, Map<String, Object> kwargs) {}

    private final Map<String, ManualJob> jobs = new ConcurrentHashMap<>();

    @Override
    public void schedule(String jobId, LocalDateTime runAt, Consumer<Map<String, Object>> func, Map<String, Object> kwargs) {
        jobs.put(jobId, new ManualJob(jobId, runAt, func, kwargs == null ? Map.of() : new LinkedHashMap<>(kwargs)));
    }

    @Override
    public void cancel(String jobId) {
        jobs.remove(jobId);
    }

    @Override
    public boolean hasJob(String jobId) {
        return jobs.containsKey(jobId);
    }

    public Map<String, ManualJob> getJobs() {
        return jobs;
    }

    public void runDue(LocalDateTime now) {
        List<ManualJob> due =
                jobs.values().stream()
                        .filter(j -> !j.runAt().isAfter(now))
                        .sorted(Comparator.comparing(ManualJob::runAt))
                        .toList();
        for (ManualJob job : due) {
            jobs.remove(job.jobId());
            job.func().accept(job.kwargs());
        }
    }
}
