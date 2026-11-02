package com.riseup.verification;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ObjectDetectionTask implements VerificationTask {
    public static final List<String> OBJECT_TARGETS =
            List.of("bathroom sink", "toothbrush", "coffee mug", "front door", "kitchen tap", "window");

    private final String target;
    private final ObjectDetector detector;

    public ObjectDetectionTask(String target, ObjectDetector detector) {
        this.target = target;
        this.detector = detector == null ? new BasicImageDetector() : detector;
    }

    public static ObjectDetectionTask generate(Random rng, ObjectDetector detector) {
        return new ObjectDetectionTask(OBJECT_TARGETS.get(rng.nextInt(OBJECT_TARGETS.size())), detector);
    }

    @Override
    public String getTaskType() {
        return "object_detection";
    }

    @Override
    public Map<String, Object> getTaskPrompt() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", getTaskType());
        m.put("question", "Get out of bed and take a photo of your " + target + ".");
        m.put("input", "photo");
        m.put("target", target);
        return m;
    }

    @Override
    public VerificationResult validate(VerificationResponse response) {
        if (response.isCameraError() || response.getImageBase64() == null || response.getImageBase64().isBlank()) {
            return new VerificationResult(false, "Camera error. Please try again.", true);
        }
        try {
            byte[] image = decodeImage(response.getImageBase64());
            DetectionResult result = detector.detect(image, target);
            return new VerificationResult(
                    result.ok(), result.reason(), false, Map.of("detected", result.detected()));
        } catch (IllegalArgumentException e) {
            return new VerificationResult(false, e.getMessage());
        }
    }

    public static byte[] decodeImage(String imageB64) {
        String raw = imageB64;
        if (raw.contains(",") && raw.strip().startsWith("data:")) {
            raw = raw.split(",", 2)[1];
        }
        try {
            return Base64.getDecoder().decode(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Photo could not be decoded", e);
        }
    }

    @Override
    public Map<String, Object> toState() {
        return Map.of("type", getTaskType(), "target", target);
    }
}
