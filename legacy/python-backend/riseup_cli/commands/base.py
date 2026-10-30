"""Command pattern core: Command interface, Result value object, interactive IO."""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Any

if TYPE_CHECKING:
    from riseup_cli.api_client import APIClient


class UserInputError(Exception):
    """Bad CLI usage (exit code 1)."""


@dataclass
class Result:
    data: Any = None
    message: str = ""
    title: str = ""
    columns: list[tuple[str, str]] = field(default_factory=list)  # list rendering
    fields: list[tuple[str, str]] = field(default_factory=list)  # single-record rendering
    empty_message: str = "Nothing to show."
    exit_code: int = 0


class IO:
    """Interactive prompts; replaced by a fake in tests."""

    def __init__(self, assume_yes: bool = False, interactive: bool = True) -> None:
        self.assume_yes = assume_yes
        self.interactive = interactive

    def echo(self, text: str) -> None:
        import typer

        typer.echo(text)

    def confirm(self, text: str, default: bool = True) -> bool:
        if self.assume_yes or not self.interactive:
            return self.assume_yes or default
        import typer

        return typer.confirm(text, default=default)

    def prompt(self, text: str) -> str:
        if not self.interactive:
            raise UserInputError(f"{text} (re-run with more detail)")
        import typer

        return typer.prompt(text)


class Command(ABC):
    name: str = ""
    help: str = ""

    @abstractmethod
    def execute(self, args: dict[str, Any], api: "APIClient", io: IO) -> Result: ...
