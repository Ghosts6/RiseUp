"""Verification challenges (Strategy) and their factory (Factory Method)."""

from riseup.alarm_system.verification.detector import BasicImageDetector, ObjectDetector
from riseup.alarm_system.verification.factory import VerificationTaskFactory
from riseup.alarm_system.verification.tasks import (
    CustomTask,
    MathTask,
    ObjectDetectionTask,
    TriviaTask,
    VerificationResponse,
    VerificationResult,
    VerificationTask,
)

__all__ = [
    "BasicImageDetector",
    "CustomTask",
    "MathTask",
    "ObjectDetectionTask",
    "ObjectDetector",
    "TriviaTask",
    "VerificationResponse",
    "VerificationResult",
    "VerificationTask",
    "VerificationTaskFactory",
]
