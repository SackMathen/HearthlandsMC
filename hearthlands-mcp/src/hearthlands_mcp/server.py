"""Hearthlands MCP server.

Exposes the Paper plugin's HTTP API as MCP tools that Claude can call.
Run with ``hearthlands-mcp`` (see ``pyproject.toml``) or
``python -m hearthlands_mcp``.
"""

from __future__ import annotations

import logging
from typing import Any, Literal

from mcp.server.fastmcp import FastMCP

from .client import HearthlandsClient, HearthlandsError
from .config import Config

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
logger = logging.getLogger("hearthlands_mcp")

config = Config.from_env()
client = HearthlandsClient(config)

mcp = FastMCP("hearthlands")

if config.mock:
    logger.info("Hearthlands MCP starting in MOCK mode (no live server calls)")
else:
    logger.info("Hearthlands MCP targeting API at %s", config.api_url)


async def _call(action: str, coro) -> Any:
    """Await a client coroutine, translating errors into MCP-friendly text."""
    try:
        return await coro
    except HearthlandsError as exc:
        logger.error("%s failed: %s", action, exc)
        return {"error": str(exc)}


def _log_write(action: str, result: Any, **details: Any) -> None:
    """Log every write operation and its result (per spec)."""
    logger.info("WRITE %s %s -> %s", action, details, result)


# ===========================================================================
# Read tools
# ===========================================================================


@mcp.tool()
async def get_players() -> dict[str, Any]:
    """List all online players with name, location (x, y, z, world), health,
    and current faction (if known)."""
    return await _call("get_players", client.get_players())


@mcp.tool()
async def get_factions() -> dict[str, Any]:
    """Return the four lore factions (Pathfinders' Guild, Iron Crown,
    Deeproot Collective, Archivists of Starlight) with their HQ coordinates
    and current territory boundaries."""
    return await _call("get_factions", client.get_factions())


@mcp.tool()
async def get_territory(x: float, z: float) -> dict[str, Any]:
    """Given an (x, z) world coordinate, return which faction (if any) claims
    that chunk/region."""
    return await _call("get_territory", client.get_territory(x, z))


@mcp.tool()
async def get_server_status() -> dict[str, Any]:
    """Return server health: TPS, player count, uptime, and current world time."""
    return await _call("get_server_status", client.get_status())


# ===========================================================================
# Write tools
# ===========================================================================


@mcp.tool()
async def run_command(command: str, confirm_destructive: bool = False) -> dict[str, Any]:
    """Execute a server console command (e.g. ``give``, ``tp``, ``broadcast``).

    Commands may be passed with or without a leading slash. Destructive
    commands (stop, ban, kick, op/deop, whitelist, gamerule, ...) are blocked
    unless ``confirm_destructive=True`` is passed, so Claude must explicitly
    confirm the action first.
    """
    if client.is_destructive(command) and not confirm_destructive:
        logger.warning("Blocked destructive command pending confirmation: %s", command)
        return {
            "success": False,
            "requires_confirmation": True,
            "command": command,
            "message": (
                f"'{command}' looks destructive. Re-run with "
                "confirm_destructive=true to execute it."
            ),
        }
    result = await _call("run_command", client.run_command(command))
    _log_write("run_command", result, command=command)
    return result


@mcp.tool()
async def teleport_player(
    player: str, x: float, y: float, z: float, world: str = "world"
) -> dict[str, Any]:
    """Teleport a named player to (x, y, z) in a named world (default ``world``)."""
    result = await _call(
        "teleport_player", client.teleport_player(player, x, y, z, world)
    )
    _log_write("teleport_player", result, player=player, x=x, y=y, z=z, world=world)
    return result


@mcp.tool()
async def send_message(
    target: str,
    message: str,
    kind: Literal["chat", "title"] = "chat",
) -> dict[str, Any]:
    """Send a chat message or title card to one player or everyone.

    ``target`` is a player name, or ``"all"`` to broadcast. ``kind`` is
    ``"chat"`` (default) for a chat line or ``"title"`` for a title card.
    """
    result = await _call("send_message", client.send_message(target, message, kind))
    _log_write("send_message", result, target=target, kind=kind, message=message)
    return result


@mcp.tool()
async def set_block(
    x: int, y: int, z: int, material: str, world: str = "world"
) -> dict[str, Any]:
    """Place a block at (x, y, z) with the given material (e.g.
    ``STONE_BRICKS``, ``OAK_LOG``). Material names follow Bukkit's
    ``Material`` enum."""
    result = await _call("set_block", client.set_block(x, y, z, material, world))
    _log_write("set_block", result, x=x, y=y, z=z, material=material, world=world)
    return result


@mcp.tool()
async def manage_faction_territory(
    faction: str,
    action: Literal["expand", "retract"],
    amount: int,
) -> dict[str, Any]:
    """Expand or retract a faction's territory boundary by ``amount`` blocks
    on every side, updating the plugin's stored perimeter.

    ``faction`` is the faction id (e.g. ``iron_crown``). ``action`` is
    ``"expand"`` or ``"retract"``.
    """
    if amount <= 0:
        return {"success": False, "error": "amount must be a positive number of blocks"}
    result = await _call(
        "manage_faction_territory",
        client.manage_faction_territory(faction, action, amount),
    )
    _log_write(
        "manage_faction_territory", result, faction=faction, action=action, amount=amount
    )
    return result


def main() -> None:
    """Console-script entry point. Serves MCP over stdio."""
    try:
        mcp.run()
    finally:
        import asyncio

        try:
            asyncio.get_event_loop().run_until_complete(client.aclose())
        except Exception:  # noqa: BLE001 - best-effort cleanup on shutdown
            pass


if __name__ == "__main__":
    main()
