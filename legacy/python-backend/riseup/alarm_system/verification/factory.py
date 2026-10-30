"""Factory Method for verification challenges (centralises defaults and fallbacks)."""

from __future__ import annotations

import random
from typing import Any

from riseup.alarm_system.verification.detector import ObjectDetector
from riseup.alarm_system.verification.tasks import (
    CustomTask,
    MathTask,
    ObjectDetectionTask,
    TriviaTask,
    VerificationTask,
)
from riseup.errors import ValidationFailed

MAX_CAMERA_FAILURES = 2


class VerificationTaskFactory:
    def __init__(
        self,
        camera_available: bool = True,
        detector: ObjectDetector | None = None,
        rng: random.Random | None = None,
    ) -> None:
        self.camera_available = camera_available
        self.detector = detector
        self.rng = rng or random.Random()

    def create(
        self, task_type: str, custom_text: str | None = None, camera_failures: int = 0
    ) -> VerificationTask:
        """Create a fresh challenge; camera problems fall back to `MathTask`."""
        if task_type == "object_detection":
            if not self.camera_available or camera_failures >= MAX_CAMERA_FAILURES:
                return MathTask.generate(self.rng)
            return ObjectDetectionTask.generate(self.rng, self.detector)
        if task_type == "math":
            return MathTask.generate(self.rng)
        if task_type == "trivia":
            return TriviaTask.generate(self.rng)
        if task_type == "custom":
            return CustomTask(custom_text or "")
        raise ValidationFailed(f"Unknown verification task type '{task_type}'")

    def from_state(self, state: dict[str, Any]) -> VerificationTask:
        kind = state.get("type")
        if kind == "math":
            return MathTask(state["a"], state["b"], state["c"], state["op"])
        if kind == "trivia":
            return TriviaTask(state["index"])
        if kind == "custom":
            return CustomTask(state.get("description", ""))
        if kind == "object_detection":
            return ObjectDetectionTask(state["target"], self.detector)
        raise ValidationFailed(f"Corrupt verification state '{kind}'")
