from __future__ import annotations

import sys
from pathlib import Path
from typing import Callable

import pytest

sys.path.insert(0, str(Path(__file__).parent))

from helpers import Harness, build_harness  # noqa: E402


@pytest.fixture
def make_harness() -> Callable[..., Harness]:
    harnesses: list[Harness] = []

    def factory(*args, **kwargs) -> Harness:
        harness = build_harness(*args, **kwargs)
        harness.client.__enter__()
        harnesses.append(harness)
        return harness

    yield factory
    for harness in harnesses:
        harness.client.__exit__(None, None, None)


@pytest.fixture
def h(make_harness) -> Harness:
    return make_harness()
