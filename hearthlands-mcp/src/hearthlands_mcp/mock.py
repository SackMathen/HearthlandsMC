"""In-memory mock backend for the Hearthlands API.

Enabled with ``HEARTHLANDS_MOCK=true``. It returns realistic fake data and
keeps light mutable state (territory perimeters, placed blocks) so write
tools behave plausibly before the Paper plugin exists.

The shapes returned here are the contract the real plugin must match; see
``API_CONTRACT.md``.
"""

from __future__ import annotations

import copy
import time
from typing import Any

_START_TIME = time.time()

# ---------------------------------------------------------------------------
# Seed data
# ---------------------------------------------------------------------------

_PLAYERS: list[dict[str, Any]] = [
    {
        "name": "Aldric_Vale",
        "uuid": "1f8b2c40-0000-4000-8000-000000000001",
        "location": {"x": 128.5, "y": 72.0, "z": -340.2, "world": "world"},
        "health": 20.0,
        "faction": "pathfinders_guild",
    },
    {
        "name": "Mira_Stonehand",
        "uuid": "1f8b2c40-0000-4000-8000-000000000002",
        "location": {"x": -512.0, "y": 65.0, "z": 610.7, "world": "world"},
        "health": 18.5,
        "faction": "iron_crown",
    },
    {
        "name": "Thessaly_Root",
        "uuid": "1f8b2c40-0000-4000-8000-000000000003",
        "location": {"x": 890.1, "y": 63.0, "z": 1204.3, "world": "world"},
        "health": 20.0,
        "faction": "deeproot_collective",
    },
    {
        "name": "Corvin_Starlight",
        "uuid": "1f8b2c40-0000-4000-8000-000000000004",
        "location": {"x": -1450.0, "y": 90.0, "z": -980.0, "world": "world"},
        "health": 14.0,
        "faction": "archivists_of_starlight",
    },
]

# Territory is expressed as an axis-aligned bounding box in block coords.
# The plugin may use richer polygons; the MCP contract only requires the
# fields consumed here.
_FACTIONS: dict[str, dict[str, Any]] = {
    "pathfinders_guild": {
        "id": "pathfinders_guild",
        "name": "Pathfinders' Guild",
        "hq": {"x": 128, "y": 72, "z": -340, "world": "world"},
        "territory": {"min_x": -256, "min_z": -768, "max_x": 512, "max_z": 0},
    },
    "iron_crown": {
        "id": "iron_crown",
        "name": "Iron Crown",
        "hq": {"x": -512, "y": 65, "z": 610, "world": "world"},
        "territory": {"min_x": -1024, "min_z": 256, "max_x": -256, "max_z": 1024},
    },
    "deeproot_collective": {
        "id": "deeproot_collective",
        "name": "Deeproot Collective",
        "hq": {"x": 890, "y": 63, "z": 1204, "world": "world"},
        "territory": {"min_x": 512, "min_z": 768, "max_x": 1280, "max_z": 1536},
    },
    "archivists_of_starlight": {
        "id": "archivists_of_starlight",
        "name": "Archivists of Starlight",
        "hq": {"x": -1450, "y": 90, "z": -980, "world": "world"},
        "territory": {"min_x": -1792, "min_z": -1280, "max_x": -1024, "max_z": -512},
    },
}

_DESTRUCTIVE_KEYWORDS = ("stop", "op ", "deop", "ban", "kick", "whitelist", "gamerule")


class MockBackend:
    """Mutable in-memory stand-in for the Paper plugin's HTTP API."""

    def __init__(self) -> None:
        self._factions = copy.deepcopy(_FACTIONS)
        self._players = copy.deepcopy(_PLAYERS)
        self._placed_blocks: list[dict[str, Any]] = []

    # -- reads --------------------------------------------------------------

    def get_players(self) -> dict[str, Any]:
        return {"players": copy.deepcopy(self._players), "count": len(self._players)}

    def get_factions(self) -> dict[str, Any]:
        return {"factions": [copy.deepcopy(f) for f in self._factions.values()]}

    def get_territory(self, x: float, z: float) -> dict[str, Any]:
        for fac in self._factions.values():
            t = fac["territory"]
            if t["min_x"] <= x <= t["max_x"] and t["min_z"] <= z <= t["max_z"]:
                return {
                    "x": x,
                    "z": z,
                    "chunk_x": int(x) >> 4,
                    "chunk_z": int(z) >> 4,
                    "faction": fac["id"],
                    "faction_name": fac["name"],
                    "claimed": True,
                }
        return {
            "x": x,
            "z": z,
            "chunk_x": int(x) >> 4,
            "chunk_z": int(z) >> 4,
            "faction": None,
            "faction_name": None,
            "claimed": False,
        }

    def get_status(self) -> dict[str, Any]:
        return {
            "tps": 19.87,
            "player_count": len(self._players),
            "max_players": 40,
            "uptime_seconds": int(time.time() - _START_TIME),
            "world_time_ticks": 6000,
            "world_time_label": "Noon",
            "version": "Paper 26.2 (mock)",
        }

    # -- writes -------------------------------------------------------------

    def run_command(self, command: str) -> dict[str, Any]:
        return {
            "command": command,
            "success": True,
            "output": f"[mock] executed console command: {command}",
        }

    @staticmethod
    def is_destructive(command: str) -> bool:
        lowered = command.lower().lstrip("/")
        return any(kw in lowered for kw in _DESTRUCTIVE_KEYWORDS)

    def teleport_player(self, player: str, x: float, y: float, z: float, world: str) -> dict[str, Any]:
        for p in self._players:
            if p["name"].lower() == player.lower():
                p["location"] = {"x": x, "y": y, "z": z, "world": world}
                return {"success": True, "player": p["name"], "location": p["location"]}
        return {"success": False, "error": f"player '{player}' is not online"}

    def send_message(self, target: str, message: str, kind: str) -> dict[str, Any]:
        recipients = (
            [p["name"] for p in self._players]
            if target.lower() == "all"
            else [target]
        )
        return {
            "success": True,
            "target": target,
            "kind": kind,
            "message": message,
            "recipients": recipients,
        }

    def set_block(self, x: int, y: int, z: int, material: str, world: str) -> dict[str, Any]:
        record = {"x": x, "y": y, "z": z, "material": material.upper(), "world": world}
        self._placed_blocks.append(record)
        return {"success": True, **record}

    def manage_faction_territory(
        self, faction: str, action: str, amount: int
    ) -> dict[str, Any]:
        fac = self._factions.get(faction)
        if fac is None:
            return {"success": False, "error": f"unknown faction '{faction}'"}
        delta = amount if action == "expand" else -amount
        t = fac["territory"]
        t["min_x"] -= delta
        t["min_z"] -= delta
        t["max_x"] += delta
        t["max_z"] += delta
        return {
            "success": True,
            "faction": faction,
            "action": action,
            "amount": amount,
            "territory": copy.deepcopy(t),
        }
