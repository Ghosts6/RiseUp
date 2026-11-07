package com.riseup.insights;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Nightly profile recompute (skipped under the test profile). */
@Component
@Profile("!test")
public class LearningNightlyJob {
    private final LearningService learning;

    public LearningNightlyJob(LearningService learning) {
        this.learning = learning;
    }

    @Scheduled(cron = "0 15 2 * * *")
    public void run() {
        learning.recomputeAllUsers();
    }
}
