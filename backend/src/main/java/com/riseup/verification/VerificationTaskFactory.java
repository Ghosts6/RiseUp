package com.riseup.verification;

import com.riseup.config.RiseUpProperties;
import com.riseup.error.ValidationFailedException;
import java.util.Map;
import java.util.Random;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VerificationTaskFactory {
    public static final int MAX_CAMERA_FAILURES = 2;

    private final boolean cameraAvailable;
    private final ObjectDetector detector;
    private final Random rng;

    @Autowired
    public VerificationTaskFactory(RiseUpProperties properties) {
        this(properties.isCameraAvailable(), new BasicImageDetector(), new Random());
    }

    public VerificationTaskFactory(boolean cameraAvailable, ObjectDetector detector, Random rng) {
        this.cameraAvailable = cameraAvailable;
        this.detector = detector;
        this.rng = rng == null ? new Random() : rng;
    }

    public VerificationTask create(String taskType, String customText, int cameraFailures) {
        ChallengeConfig config = ChallengeConfig.parse(customText);
        return switch (taskType) {
            case "object_detection" -> {
                if (!cameraAvailable || cameraFailures >= MAX_CAMERA_FAILURES) {
                    yield config.questions() > 1
                            ? SeriesTask.start("math", config, rng)
                            : MathTask.generate(rng, config.difficulty());
                }
                yield ObjectDetectionTask.generate(rng, detector);
            }
            case "math" -> config.questions() > 1
                    ? SeriesTask.start("math", config, rng)
                    : MathTask.generate(rng, config.difficulty());
            case "trivia" -> config.questions() > 1
                    ? SeriesTask.start("trivia", config, rng)
                    : TriviaTask.generate(rng);
            case "custom" -> new CustomTask(
                    config.text() == null || config.text().isBlank() ? customText : config.text());
            default -> throw new ValidationFailedException("Unknown verification task type '" + taskType + "'");
        };
    }

    public VerificationTask fromState(Map<String, Object> state) {
        String kind = String.valueOf(state.get("type"));
        return switch (kind) {
            case "series" -> SeriesTask.fromState(state, this);
            case "math" -> new MathTask(
                    ((Number) state.get("a")).intValue(),
                    ((Number) state.get("b")).intValue(),
                    ((Number) state.get("c")).intValue(),
                    String.valueOf(state.get("op")));
            case "trivia" -> new TriviaTask(((Number) state.get("index")).intValue());
            case "custom" -> new CustomTask(String.valueOf(state.getOrDefault("description", "")));
            case "object_detection" -> new ObjectDetectionTask(String.valueOf(state.get("target")), detector);
            default -> throw new ValidationFailedException("Corrupt verification state '" + kind + "'");
        };
    }
}
