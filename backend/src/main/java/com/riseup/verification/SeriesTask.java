package com.riseup.verification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Multi-question math or trivia challenge. Each correct answer advances until the last one
 * finishes the alarm.
 */
public class SeriesTask implements VerificationTask {
    private final String kind; // math | trivia
    private final String difficulty;
    private final int total;
    private final int index; // 0-based current
    private final VerificationTask current;
    private final Random rng;

    public SeriesTask(
            String kind, String difficulty, int total, int index, VerificationTask current, Random rng) {
        this.kind = kind;
        this.difficulty = difficulty == null ? "medium" : difficulty;
        this.total = Math.max(1, total);
        this.index = Math.max(0, index);
        this.current = current;
        this.rng = rng == null ? new Random() : rng;
    }

    public static SeriesTask start(String kind, ChallengeConfig config, Random rng) {
        int total = Math.max(1, Math.min(5, config.questions()));
        VerificationTask first = nextUnit(kind, config.difficulty(), rng);
        return new SeriesTask(kind, config.difficulty(), total, 0, first, rng);
    }

    public static SeriesTask fromState(Map<String, Object> state, VerificationTaskFactory factory) {
        String kind = String.valueOf(state.getOrDefault("kind", "math"));
        String difficulty = String.valueOf(state.getOrDefault("difficulty", "medium"));
        int total = ((Number) state.getOrDefault("total", 1)).intValue();
        int index = ((Number) state.getOrDefault("index", 0)).intValue();
        @SuppressWarnings("unchecked")
        Map<String, Object> cur = (Map<String, Object>) state.get("current");
        VerificationTask current = factory.fromState(cur);
        return new SeriesTask(kind, difficulty, total, index, current, new Random());
    }

    private static VerificationTask nextUnit(String kind, String difficulty, Random rng) {
        if ("trivia".equals(kind)) {
            return TriviaTask.generate(rng);
        }
        return MathTask.generate(rng, difficulty);
    }

    @Override
    public String getTaskType() {
        return kind;
    }

    @Override
    public Map<String, Object> getTaskPrompt() {
        Map<String, Object> prompt = new LinkedHashMap<>(current.getTaskPrompt());
        prompt.put("question", "Question " + (index + 1) + " of " + total + " — " + prompt.get("question"));
        prompt.put("series_index", index + 1);
        prompt.put("series_total", total);
        prompt.put("difficulty", difficulty);
        return prompt;
    }

    @Override
    public VerificationResult validate(VerificationResponse response) {
        VerificationResult step = current.validate(response);
        if (!step.isSuccess()) {
            return step;
        }
        if (index + 1 >= total) {
            return new VerificationResult(true, "All " + total + " questions done — nice work!");
        }
        VerificationTask next = nextUnit(kind, difficulty, rng);
        SeriesTask advanced = new SeriesTask(kind, difficulty, total, index + 1, next, rng);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("continue", true);
        data.put("next_state", advanced.toState());
        int left = total - (index + 1);
        return new VerificationResult(
                true,
                "Correct! " + left + " more to go.",
                false,
                data);
    }

    @Override
    public Map<String, Object> toState() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "series");
        m.put("kind", kind);
        m.put("difficulty", difficulty);
        m.put("total", total);
        m.put("index", index);
        m.put("current", current.toState());
        return m;
    }

    @Override
    public boolean resetsOnFailure() {
        return true;
    }

    /** Rebuild current unit after a wrong answer, keeping progress. */
    public SeriesTask regenerateCurrent() {
        return new SeriesTask(kind, difficulty, total, index, nextUnit(kind, difficulty, rng), rng);
    }

    public List<String> debugParts() {
        List<String> out = new ArrayList<>();
        out.add(kind + " " + (index + 1) + "/" + total);
        return out;
    }
}
