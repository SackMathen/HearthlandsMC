"""HTTP client for the Hearthlands Paper plugin API.

Wraps :mod:`httpx` for async calls and transparently falls back to the
:class:`~hearthlands_mcp.mock.MockBackend` when ``HEARTHLANDS_MOCK=true``.

Every method maps 1:1 onto an endpoint documented in ``API_CONTRACT.md``.
"""

from __future__ import annotations

import logging
from typing import Any

import httpx

from .config import Config
from .mock import MockBackend

logger = logging.getLogger("hearthlands_mcp")


class HearthlandsError(RuntimeError):
    """Raised when the plugin API returns an error or is unreachable."""


class HearthlandsClient:
    def __init__(self, config: Config) -> None:
        self.config = config
        self._mock = MockBackend() if config.mock else None
        self._client: httpx.AsyncClient | None = None

    async def _http(self) -> httpx.AsyncClient:
        if self._client is None:
            headers = {"Accept": "application/json"}
            if self.config.api_key:
                headers["Authorization"] = f"Bearer {self.config.api_key}"
            self._client = httpx.AsyncClient(
                base_url=self.config.api_url,
                headers=headers,
                timeout=self.config.timeout,
            )
        return self._client

    async def aclose(self) -> None:
        if self._client is not None:
            await self._client.aclose()
            self._client = None

    async def _request(self, method: str, path: str, **kwargs: Any) -> Any:
        client = await self._http()
        try:
            resp = await client.request(method, path, **kwargs)
        except httpx.HTTPError as exc:  # network / timeout
            raise HearthlandsError(
                f"could not reach Hearthlands API at {self.config.api_url}{path}: {exc}"
            ) from exc
        if resp.status_code == 401:
            raise HearthlandsError("authentication failed — check HEARTHLANDS_API_KEY")
        if resp.status_code >= 400:
            detail = resp.text.strip()
            raise HearthlandsError(f"API {method} {path} returned {resp.status_code}: {detail}")
        if resp.headers.get("content-type", "").startswith("application/json"):
            return resp.json()
        return {"raw": resp.text}

    # -- reads --------------------------------------------------------------

    async def get_players(self) -> dict[str, Any]:
        if self._mock is not None:
            return self._mock.get_players()
        return await self._request("GET", "/players")

    async def get_factions(self) -> dict[str, Any]:
        if self._mock is not None:
            return self._mock.get_factions()
        return await self._request("GET", "/factions")

    async def get_territory(self, x: float, z: float) -> dict[str, Any]:
        if self._mock is not None:
            return self._mock.get_territory(x, z)
        return await self._request("GET", "/territory", params={"x": x, "z": z})

    async def get_status(self) -> dict[str, Any]:
        if self._mock is not None:
            return self._mock.get_status()
        return await self._request("GET", "/status")

    # -- writes -------------------------------------------------------------

    def is_destructive(self, command: str) -> bool:
        return MockBackend.is_destructive(command)

    async def run_command(self, command: str) -> dict[str, Any]:
        if self._mock is not None:
            return self._mock.run_command(command)
        return await self._request("POST", "/command", json={"command": command})

    async def teleport_player(
        self, player: str, x: float, y: float, z: float, world: str
    ) -> dict[str, Any]:
        if self._mock is not None:
            return self._mock.teleport_player(player, x, y, z, world)
        return await self._request(
            "POST",
            f"/players/{player}/teleport",
            json={"x": x, "y": y, "z": z, "world": world},
        )

    async def send_message(self, target: str, message: str, kind: str) -> dict[str, Any]:
        if self._mock is not None:
            return self._mock.send_message(target, message, kind)
        return await self._request(
            "POST",
            "/messages",
            json={"target": target, "message": message, "type": kind},
        )

    async def set_block(
        self, x: int, y: int, z: int, material: str, world: str
    ) -> dict[str, Any]:
        if self._mock is not None:
            return self._mock.set_block(x, y, z, material, world)
        return await self._request(
            "POST",
            "/blocks",
            json={"x": x, "y": y, "z": z, "material": material, "world": world},
        )

    async def manage_faction_territory(
        self, faction: str, action: str, amount: int
    ) -> dict[str, Any]:
        if self._mock is not None:
            return self._mock.manage_faction_territory(faction, action, amount)
        return await self._request(
            "POST",
            f"/factions/{faction}/territory",
            json={"action": action, "amount": amount},
        )
