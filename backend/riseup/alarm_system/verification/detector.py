"""Object detection backends used by `ObjectDetectionTask` (no LLM involved, AD-02)."""

from __future__ import annotations

import base64
import binascii
from abc import ABC, abstractmethod
from dataclasses import dataclass


@dataclass
class DetectionResult:
    ok: bool
    reason: str
    detected: list[str]


class ObjectDetector(ABC):
    @abstractmethod
    def detect(self, image_bytes: bytes, target: str) -> DetectionResult: ...


def decode_image(image_b64: str) -> bytes:
    if "," in image_b64 and image_b64.strip().startswith("data:"):
        image_b64 = image_b64.split(",", 1)[1]
    try:
        return base64.b64decode(image_b64, validate=False)
    except (binascii.Error, ValueError) as exc:
        raise ValueError("Photo could not be decoded") from exc


class BasicImageDetector(ObjectDetector):
    """Validates that a real photo was captured (format + minimum size).

    It stands in for a CV model: a real detector (e.g. YOLO) can implement
    `ObjectDetector` and be injected into the factory without other changes.
    """

    MIN_BYTES = 2_000
    SIGNATURES = {
        b"\xff\xd8\xff": "jpeg",
        b"\x89PNG\r\n\x1a\n": "png",
        b"RIFF": "webp",
    }

    def detect(self, image_bytes: bytes, target: str) -> DetectionResult:
        kind = next((k for sig, k in self.SIGNATURES.items() if image_bytes.startswith(sig)), None)
        if kind is None:
            return DetectionResult(False, "Unsupported or corrupt image", [])
        if len(image_bytes) < self.MIN_BYTES:
            return DetectionResult(False, "Photo too small or blurry, please retake", [])
        return DetectionResult(True, f"{kind} photo accepted", [target])
