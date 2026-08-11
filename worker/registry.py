from typing import Any, Callable

COMMANDS: dict[str, Callable[[dict[str, Any]], dict[str, Any]]] = {}


def register_command(name: str):
    """Register a command handler."""
    def decorator(func: Callable[[dict[str, Any]], dict[str, Any]]):
        COMMANDS[name] = func
        return func
    return decorator
