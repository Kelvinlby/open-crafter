"""Binds the function-style op wrappers (``vision``, ``inventory``) to one client."""

from __future__ import annotations

import functools
import inspect
from types import ModuleType, SimpleNamespace
from typing import Any


def bind_module(module: ModuleType, client: Any) -> SimpleNamespace:
    """Returns a namespace of ``module``'s public functions with ``client`` pre-filled."""
    bound = SimpleNamespace()
    for name, value in vars(module).items():
        if _is_op_wrapper(module, name, value):
            setattr(bound, name, functools.partial(value, client))
    return bound


def _is_op_wrapper(module: ModuleType, name: str, value: Any) -> bool:
    return not name.startswith("_") and inspect.isfunction(value) and value.__module__ == module.__name__
