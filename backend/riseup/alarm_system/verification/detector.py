"""Object detection backends used by `ObjectDetectionTask` (no LLM involved, AD-02).

Uses Pillow as the vision library: decode the photo, reject blank/tiny/blurry
frames, then apply lightweight scene heuristics keyed to the target label.
A real detector (e.g. YOLO) can still implement `ObjectDetector` and be injected.
"""

from __future__ import annotations

import base64
import binascii
import io
import math
from abc import ABC, abstractmethod
from dataclasses import dataclass

from PIL import Image, ImageFilter, UnidentifiedImageError


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


def _luminance_stats(gray: Image.Image) -> tuple[float, float]:
    hist = gray.histogram()
    total = sum(hist) or 1
    mean = sum(i * c for i, c in enumerate(hist)) / total
    var = sum(((i - mean) ** 2) * c for i, c in enumerate(hist)) / total
    return mean, math.sqrt(var)


def _edge_score(gray: Image.Image) -> float:
    """Mean absolute response of a Laplacian-like edge filter (blur proxy)."""
    edges = gray.filter(ImageFilter.Kernel((3, 3), [-1, -1, -1, -1, 8, -1, -1, -1, -1], scale=1))
    hist = edges.histogram()
    total = sum(hist) or 1
    return sum(i * c for i, c in enumerate(hist)) / total


def _region_brightness(gray: Image.Image, box: tuple[float, float, float, float]) -> float:
    w, h = gray.size
    left, top, right, bottom = box
    crop = gray.crop((int(left * w), int(top * h), int(right * w), int(bottom * h)))
    mean, _ = _luminance_stats(crop)
    return mean


def _center_edge_ratio(gray: Image.Image) -> float:
    w, h = gray.size
    full = _edge_score(gray) or 1.0
    cx0, cy0 = int(w * 0.25), int(h * 0.25)
    cx1, cy1 = int(w * 0.75), int(h * 0.75)
    center = _edge_score(gray.crop((cx0, cy0, cx1, cy1)))
    return center / full


class BasicImageDetector(ObjectDetector):
    """Pillow-backed photo quality + target-aware scene heuristics."""

    MIN_BYTES = 2_000
    MIN_WIDTH = 240
    MIN_HEIGHT = 180
    MIN_STDDEV = 12.0
    MIN_EDGE = 4.0
    SIGNATURES = {
        b"\xff\xd8\xff": "jpeg",
        b"\x89PNG\r\n\x1a\n": "png",
        b"RIFF": "webp",
    }

    def detect(self, image_bytes: bytes, target: str) -> DetectionResult:
        kind = next((k for sig, k in self.SIGNATURES.items() if image_bytes.startswith(sig)), None)
        if kind is None:
            return DetectionResult(False, "Unsupported or corrupt image", [])
        if kind == "webp" and b"WEBP" not in image_bytes[:16]:
            return DetectionResult(False, "Unsupported or corrupt image", [])
        if len(image_bytes) < self.MIN_BYTES:
            return DetectionResult(False, "Photo too small or blurry, please retake", [])

        try:
            with Image.open(io.BytesIO(image_bytes)) as img:
                img.load()
                width, height = img.size
                if width < self.MIN_WIDTH or height < self.MIN_HEIGHT:
                    return DetectionResult(
                        False,
                        f"Photo too small ({width}x{height}); move closer and retake",
                        [],
                    )
                rgb = img.convert("RGB")
                gray = rgb.convert("L")
                mean, stddev = _luminance_stats(gray)
                if stddev < self.MIN_STDDEV:
                    return DetectionResult(
                        False, "Photo looks blank or solid-color; retake of your target", []
                    )
                if mean < 18:
                    return DetectionResult(False, "Photo too dark; turn on a light and retake", [])
                if mean > 245:
                    return DetectionResult(False, "Photo overexposed; retake with less light", [])
                edges = _edge_score(gray)
                if edges < self.MIN_EDGE:
                    return DetectionResult(False, "Photo too small or blurry, please retake", [])
                scene_ok, scene_reason = self._match_target(gray, target.lower().strip())
                if not scene_ok:
                    return DetectionResult(False, scene_reason, [])
                return DetectionResult(
                    True, f"{kind} photo of {target} accepted ({scene_reason})", [target]
                )
        except UnidentifiedImageError:
            return DetectionResult(False, "Unsupported or corrupt image", [])
        except OSError:
            return DetectionResult(False, "Photo could not be decoded", [])

    def _match_target(self, gray: Image.Image, target: str) -> tuple[bool, str]:
        """Deterministic scene heuristics (vision library, not an LLM)."""
        top = _region_brightness(gray, (0.0, 0.0, 1.0, 0.35))
        bottom = _region_brightness(gray, (0.0, 0.65, 1.0, 1.0))
        center_ratio = _center_edge_ratio(gray)
        mean, stddev = _luminance_stats(gray)

        if "window" in target:
            if top <= bottom + 8 and mean < 90:
                return False, f"Doesn't look like a {target}; face a brighter window and retake"
            return True, "bright upper region matches window scene"
        if "door" in target:
            if stddev < 18:
                return False, f"Doesn't look like a {target}; include the door frame and retake"
            return True, "contrast pattern matches door scene"
        if "sink" in target or "tap" in target:
            if mean < 40 or mean > 230:
                return False, f"Doesn't look like a {target}; retake in the bathroom/kitchen"
            return True, "mid-tone scene matches sink/tap"
        if "mug" in target or "toothbrush" in target or "coffee" in target:
            if center_ratio < 0.85:
                return (
                    False,
                    f"Doesn't look like a {target}; center the object in frame and retake",
                )
            return True, "central object contrast matches handheld target"
        # Unknown targets: quality gate alone is enough.
        return True, "scene quality ok"
