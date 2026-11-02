package com.riseup.verification;

import java.util.List;

public interface ObjectDetector {
    DetectionResult detect(byte[] imageBytes, String target);
}

record DetectionResult(boolean ok, String reason, List<String> detected) {}
