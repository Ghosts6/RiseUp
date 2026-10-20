"""CommandRegistry: maps command names (e.g. 'alarm.add') to Command objects."""

from __future__ import annotations

from riseup_cli.commands.base import Command


class CommandRegistry:
    def __init__(self) -> None:
        self._commands: dict[str, Command] = {}

    def register(self, name: str, command: Command) -> None:
        if name in self._commands:
            raise ValueError(f"Command '{name}' already registered")
        self._commands[name] = command

    def get(self, name: str) -> Command:
        try:
            return self._commands[name]
        except KeyError as exc:
            raise KeyError(f"Unknown command '{name}'") from exc

    def names(self) -> list[str]:
        return sorted(self._commands)


def build_default_registry() -> CommandRegistry:
    from riseup_cli.commands import alarm, insights, settings, tasks

    registry = CommandRegistry()
    for module in (alarm, tasks, settings, insights):
        for command_cls in module.COMMANDS:
            registry.register(command_cls.name, command_cls())
    return registry
