"""Configuration for the Hearthlands MCP server.

All settings are read from environment variables so the server can be
configured from Cowork, ``claude_desktop_config.json``, or a plain shell.
"""

from __future__ import annotations

import os
from dataclasses import dataclass


def _env_bool(name: str, default: bool = False) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


@dataclass(frozen=True)
class Config:
    """Runtime configuration resolved from the environment."""

    api_url: str
    api_key: str | None
    mock: bool
    timeout: float

    @classmethod
    def from_env(cls) -> "Config":
        return cls(
            api_url=os.getenv("HEARTHLANDS_API_URL", "http://localhost:8080").rstrip("/"),
            api_key=os.getenv("HEARTHLANDS_API_KEY"),
            mock=_env_bool("HEARTHLANDS_MOCK", False),
            timeout=float(os.getenv("HEARTHLANDS_API_TIMEOUT", "10")),
        )
