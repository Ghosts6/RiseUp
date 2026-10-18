"""Concrete verification strategies. Validation is fully deterministic."""

from __future__ import annotations

import random
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any

from riseup.alarm_system.verification.detector import (
    BasicImageDetector,
    ObjectDetector,
    decode_image,
)


@dataclass
class VerificationResponse:
    answer: str | None = None
    completed: bool = False
    image_base64: str | None = None
    camera_error: bool = False


@dataclass
class VerificationResult:
    success: bool
    message: str
    camera_error: bool = False
    data: dict = field(default_factory=dict)


class VerificationTask(ABC):
    """Strategy interface: every challenge exposes a prompt and a validator."""

    task_type: str = "base"

    @abstractmethod
    def get_task_prompt(self) -> dict[str, Any]:
        """Public description shown to the user (never contains the answer)."""

    @abstractmethod
    def validate(self, response: VerificationResponse) -> VerificationResult: ...

    @abstractmethod
    def to_state(self) -> dict[str, Any]:
        """Serializable state persisted on the alarm while it rings."""

    def resets_on_failure(self) -> bool:
        return False


class MathTask(VerificationTask):
    task_type = "math"

    def __init__(self, a: int, b: int, c: int, op: str) -> None:
        self.a, self.b, self.c, self.op = a, b, c, op

    @classmethod
    def generate(cls, rng: random.Random | None = None) -> "MathTask":
        rng = rng or random.Random()
        op = rng.choice(["+", "-", "*"])
        if op == "*":
            return cls(rng.randint(3, 12), rng.randint(3, 12), rng.randint(1, 20), op)
        return cls(rng.randint(12, 99), rng.randint(12, 99), rng.randint(1, 20), op)

    @property
    def equation(self) -> str:
        return f"({self.a} {self.op} {self.b}) + {self.c}"

    @property
    def answer(self) -> int:
        ops = {"+": self.a + self.b, "-": self.a - self.b, "*": self.a * self.b}
        return ops[self.op] + self.c

    def get_task_prompt(self) -> dict[str, Any]:
        return {"type": self.task_type, "question": f"Solve: {self.equation}", "input": "number"}

    def validate(self, response: VerificationResponse) -> VerificationResult:
        try:
            given = int(str(response.answer).strip())
        except (TypeError, ValueError):
            return VerificationResult(False, "Enter a whole number.")
        if given == self.answer:
            return VerificationResult(True, "Correct!")
        return VerificationResult(False, "Task not valid. Please try again.")

    def to_state(self) -> dict[str, Any]:
        return {"type": self.task_type, "a": self.a, "b": self.b, "c": self.c, "op": self.op}

    def resets_on_failure(self) -> bool:
        return True


TRIVIA_BANK: list[tuple[str, list[str], int]] = [
    ("What is the largest planet in our solar system?", ["Mars", "Jupiter", "Saturn", "Venus"], 1),
    ("How many continents are there?", ["5", "6", "7", "8"], 2),
    (
        "What gas do plants absorb from the air?",
        ["Oxygen", "Nitrogen", "Carbon dioxide", "Helium"],
        2,
    ),
    ("What is the capital of Canada?", ["Toronto", "Ottawa", "Vancouver", "Montreal"], 1),
    ("How many minutes are in a day?", ["1440", "1240", "1640", "960"], 0),
    ("Which language runs RiseUp's backend?", ["Java", "Go", "Python", "Ruby"], 2),
    ("What is H2O commonly called?", ["Salt", "Water", "Hydrogen", "Ozone"], 1),
    ("Which is the smallest prime number?", ["0", "1", "2", "3"], 2),
    ("How many sides does a hexagon have?", ["5", "6", "7", "8"], 1),
    ("Which ocean is the largest?", ["Atlantic", "Indian", "Arctic", "Pacific"], 3),
    ("What is 15% of 200?", ["20", "25", "30", "35"], 2),
    ("Who wrote 'Romeo and Juliet'?", ["Dickens", "Shakespeare", "Austen", "Tolkien"], 1),
]


class TriviaTask(VerificationTask):
    task_type = "trivia"

    def __init__(self, index: int) -> None:
        self.index = index % len(TRIVIA_BANK)

    @classmethod
    def generate(cls, rng: random.Random | None = None) -> "TriviaTask":
        rng = rng or random.Random()
        return cls(rng.randrange(len(TRIVIA_BANK)))

    @property
    def question(self) -> tuple[str, list[str], int]:
        return TRIVIA_BANK[self.index]

    def get_task_prompt(self) -> dict[str, Any]:
        text, choices, _ = self.question
        return {"type": self.task_type, "question": text, "choices": choices, "input": "choice"}

    def validate(self, response: VerificationResponse) -> VerificationResult:
        _, choices, correct = self.question
        answer = str(response.answer or "").strip()
        if answer.isdigit() and int(answer) == correct:
            return VerificationResult(True, "Correct!")
        if answer.lower() == choices[correct].lower():
            return VerificationResult(True, "Correct!")
        return VerificationResult(False, "Task not valid. Please try again.")

    def to_state(self) -> dict[str, Any]:
        return {"type": self.task_type, "index": self.index}

    def resets_on_failure(self) -> bool:
        return True


class CustomTask(VerificationTask):
    task_type = "custom"

    def __init__(self, description: str) -> None:
        self.description = description or "Complete your custom task"

    def get_task_prompt(self) -> dict[str, Any]:
        return {"type": self.task_type, "question": self.description, "input": "confirm"}

    def validate(self, response: VerificationResponse) -> VerificationResult:
        if response.completed:
            return VerificationResult(True, "Task marked as completed.")
        return VerificationResult(False, "Mark the task as completed when done.")

    def to_state(self) -> dict[str, Any]:
        return {"type": self.task_type, "description": self.description}


OBJECT_TARGETS = [
    "bathroom sink",
    "toothbrush",
    "coffee mug",
    "front door",
    "kitchen tap",
    "window",
]


class ObjectDetectionTask(VerificationTask):
    task_type = "object_detection"

    def __init__(self, target: str, detector: ObjectDetector | None = None) -> None:
        self.target = target
        self.detector = detector or BasicImageDetector()

    @classmethod
    def generate(
        cls, rng: random.Random | None = None, detector: ObjectDetector | None = None
    ) -> "ObjectDetectionTask":
        rng = rng or random.Random()
        return cls(rng.choice(OBJECT_TARGETS), detector)

    def get_task_prompt(self) -> dict[str, Any]:
        return {
            "type": self.task_type,
            "question": f"Get out of bed and take a photo of your {self.target}.",
            "input": "photo",
            "target": self.target,
        }

    def validate(self, response: VerificationResponse) -> VerificationResult:
        if response.camera_error or not response.image_base64:
            return VerificationResult(False, "Camera error. Please try again.", camera_error=True)
        try:
            image = decode_image(response.image_base64)
        except ValueError as exc:
            return VerificationResult(False, str(exc))
        result = self.detector.detect(image, self.target)
        return VerificationResult(result.ok, result.reason, data={"detected": result.detected})

    def to_state(self) -> dict[str, Any]:
        return {"type": self.task_type, "target": self.target}
