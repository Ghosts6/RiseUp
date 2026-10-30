import base64
import io
import random

import pytest
from PIL import Image, ImageDraw

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


def sample_photo(kind: str = "window") -> bytes:
    """Build a JPEG that passes Pillow quality + scene heuristics."""
    img = Image.new("RGB", (480, 360), (30, 30, 40))
    draw = ImageDraw.Draw(img)
    rng = random.Random(7)
    if kind == "window":
        draw.rectangle((0, 0, 480, 140), fill=(210, 230, 255))
        draw.rectangle((80, 40, 400, 300), fill=(180, 200, 230))
        for _ in range(400):
            x, y = rng.randint(0, 479), rng.randint(0, 359)
            draw.point((x, y), fill=(rng.randint(40, 220),) * 3)
    elif kind == "toothbrush":
        draw.rectangle((0, 0, 480, 360), fill=(90, 100, 110))
        draw.rectangle((210, 60, 270, 300), fill=(240, 240, 250))
        draw.ellipse((200, 40, 280, 90), fill=(250, 250, 255))
        for _ in range(600):
            x, y = rng.randint(180, 300), rng.randint(40, 320)
            draw.point((x, y), fill=(rng.randint(180, 255),) * 3)
    else:
        for y in range(360):
            for x in range(0, 480, 3):
                draw.point((x, y), fill=(rng.randint(20, 230),) * 3)
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=85)
    return buf.getvalue()


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

    def test_rejects_blank_padded_jpeg(self):
        jpeg = b"\xff\xd8\xff" + b"\x00" * 5000
        result = ObjectDetectionTask("toothbrush").validate(
            VerificationResponse(image_base64=b64(jpeg))
        )
        assert not result.success

    def test_accepts_real_photo(self):
        result = ObjectDetectionTask("toothbrush").validate(
            VerificationResponse(image_base64="data:image/jpeg;base64," + b64(sample_photo("toothbrush")))
        )
        assert result.success
        assert result.data["detected"] == ["toothbrush"]

    def test_window_scene_heuristic(self):
        ok = ObjectDetectionTask("window").validate(
            VerificationResponse(image_base64=b64(sample_photo("window")))
        )
        assert ok.success
        solid = Image.new("RGB", (480, 360), (10, 10, 10))
        buf = io.BytesIO()
        solid.save(buf, format="JPEG", quality=90)
        bad = ObjectDetectionTask("window").validate(
            VerificationResponse(image_base64=b64(buf.getvalue()))
        )
        assert not bad.success


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
