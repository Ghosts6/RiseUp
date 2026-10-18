from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any


@dataclass
class ToolResult:
    success: bool
    channel: str
    detail: str = ""
    external_id: str | None = None
    data: dict = field(default_factory=dict)


class Tool(ABC):
    """Target interface the domain uses; vendors are adapted behind it."""

    name: str = "tool"
    is_mock: bool = False

    @abstractmethod
    def execute(self, payload: dict[str, Any]) -> ToolResult: ...
