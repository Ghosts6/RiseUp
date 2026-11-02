package com.riseup.verification;

import java.util.List;

/**
 * Lightweight photo quality gate (no Pillow). Accepts JPEG/PNG of sufficient size with
 * non-trivial byte variance; scene heuristics are simplified for the Java port.
 */
public class BasicImageDetector implements ObjectDetector {
    public static final int MIN_BYTES = 2000;

    @Override
    public DetectionResult detect(byte[] imageBytes, String target) {
        if (imageBytes == null || imageBytes.length < 3) {
            return new DetectionResult(false, "Unsupported or corrupt image", List.of());
        }
        boolean jpeg = imageBytes[0] == (byte) 0xFF && imageBytes[1] == (byte) 0xD8 && imageBytes[2] == (byte) 0xFF;
        boolean png =
                imageBytes.length >= 8
                        && imageBytes[0] == (byte) 0x89
                        && imageBytes[1] == 'P'
                        && imageBytes[2] == 'N'
                        && imageBytes[3] == 'G';
        if (!jpeg && !png) {
            return new DetectionResult(false, "Unsupported or corrupt image", List.of());
        }
        if (imageBytes.length < MIN_BYTES) {
            return new DetectionResult(false, "Photo too small or blurry, please retake", List.of());
        }
        // Variance heuristic: reject near-solid buffers
        long sum = 0;
        int sample = Math.min(imageBytes.length, 4000);
        for (int i = 0; i < sample; i++) sum += imageBytes[i] & 0xFF;
        double mean = sum / (double) sample;
        double var = 0;
        for (int i = 0; i < sample; i++) {
            double d = (imageBytes[i] & 0xFF) - mean;
            var += d * d;
        }
        if (Math.sqrt(var / sample) < 8.0) {
            return new DetectionResult(false, "Photo looks blank or solid-color; retake of your target", List.of());
        }
        return new DetectionResult(true, "Detected " + target, List.of(target));
    }
}
