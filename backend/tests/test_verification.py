import base64
import random

import pytest

from riseup.alarm_system.verification import VerificationResponse
from riseup.alarm_system.verification.factory import VerificationTaskFactory
from riseup.alarm_system.verification.tasks import (
    TRIVIA_BANK,
    CustomTask,
    MathTask,
    ObjectDetectionTask,
    TriviaTask,
)
from riseup.errors import ValidationFailed


def b64(data: bytes) -> str:
    return base64.b64encode(data).decode()


class TestMathTask:
    def test_correct_answer_passes(self):
        task = MathTask(12, 30, 5, "+")
        assert task.answer == 47
        assert task.validate(VerificationResponse(answer="47")).success

    def test_wrong_and_non_numeric_answers_fail(self):
        task = MathTask(6, 7, 1, "*")
        assert not task.validate(VerificationResponse(answer="42")).success
        result = task.validate(VerificationResponse(answer="forty-three"))
        assert not result.success and "whole number" in result.message

    def test_prompt_never_leaks_the_answer(self):
        task = MathTask.generate(random.Random(3))
        assert str(task.answer) not in task.get_task_prompt()["question"].split("Solve: ")[0]
        assert "answer" not in task.get_task_prompt()

    def test_new_puzzle_after_failure(self):
        assert MathTask(1, 2, 3, "+").resets_on_failure()


class TestTriviaTask:
    def test_accepts_index_or_choice_text(self):
        index = next(i for i, (_, choices, _) in enumerate(TRIVIA_BANK) if choices[0] == "Mars")
        task = TriviaTask(index)
        assert task.validate(VerificationResponse(answer="1")).success
        assert task.validate(VerificationResponse(answer="jupiter")).success
        assert not task.validate(VerificationResponse(answer="Mars")).success


class TestCustomTask:
    def test_requires_completion_flag(self):
        task = CustomTask("Do 10 push-ups")
        assert not task.validate(VerificationResponse()).success
        assert task.validate(VerificationResponse(completed=True)).success


class TestObjectDetectionTask:
    def test_camera_error_is_flagged(self):
        result = ObjectDetectionTask("toothbrush").validate(VerificationResponse(camera_error=True))
        assert not result.success and result.camera_error

    def test_rejects_tiny_or_unknown_images(self):
        task = ObjectDetectionTask("toothbrush")
        tiny_png = b"\x89PNG\r\n\x1a\n" + b"0" * 100
        assert (
            "too small" in task.validate(VerificationResponse(image_base64=b64(tiny_png))).message
        )
        assert not task.validate(VerificationResponse(image_base64=b64(b"hello" * 1000))).success

    def test_accepts_real_photo(self):
        jpeg = b"\xff\xd8\xff" + b"\x00" * 5000
        result = ObjectDetectionTask("toothbrush").validate(
            VerificationResponse(image_base64="data:image/jpeg;base64," + b64(jpeg))
        )
        assert result.success
        assert result.data["detected"] == ["toothbrush"]


class TestFactory:
    def test_creates_each_type(self):
        factory = VerificationTaskFactory(rng=random.Random(1))
        assert isinstance(factory.create("math"), MathTask)
        assert isinstance(factory.create("trivia"), TriviaTask)
        assert isinstance(factory.create("custom", "Stretch"), CustomTask)
        assert isinstance(factory.create("object_detection"), ObjectDetectionTask)

    def test_falls_back_to_math_without_camera(self):
        assert isinstance(
            VerificationTaskFactory(camera_available=False).create("object_detection"), MathTask
        )

    def test_falls_back_to_math_after_two_camera_failures(self):
        factory = VerificationTaskFactory()
        assert isinstance(
            factory.create("object_detection", camera_failures=1), ObjectDetectionTask
        )
        assert isinstance(factory.create("object_detection", camera_failures=2), MathTask)

    def test_unknown_type_rejected(self):
        with pytest.raises(ValidationFailed):
            VerificationTaskFactory().create("sudoku")

    @pytest.mark.parametrize("kind", ["math", "trivia", "custom", "object_detection"])
    def test_state_round_trip(self, kind):
        factory = VerificationTaskFactory(rng=random.Random(7))
        task = factory.create(kind, "Stretch")
        restored = factory.from_state(task.to_state())
        assert restored.get_task_prompt() == task.get_task_prompt()
