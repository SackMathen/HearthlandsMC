"""Tests for the mock backend and the client's mock dispatch.

These exercise the full read/write surface without a live server, and lock in
the JSON shapes documented in API_CONTRACT.md.
"""

import pytest

from hearthlands_mcp.client import HearthlandsClient
from hearthlands_mcp.config import Config


@pytest.fixture
def client() -> HearthlandsClient:
    return HearthlandsClient(Config(api_url="http://x", api_key=None, mock=True, timeout=5))


async def test_get_players(client):
    data = await client.get_players()
    assert data["count"] == len(data["players"]) == 4
    p = data["players"][0]
    assert set(p["location"]) == {"x", "y", "z", "world"}
    assert "health" in p and "faction" in p


async def test_get_factions_has_four_lore_factions(client):
    ids = {f["id"] for f in (await client.get_factions())["factions"]}
    assert ids == {
        "pathfinders_guild",
        "iron_crown",
        "deeproot_collective",
        "archivists_of_starlight",
    }


async def test_get_territory_claimed_and_unclaimed(client):
    claimed = await client.get_territory(128, -340)
    assert claimed["claimed"] is True
    assert claimed["faction"] == "pathfinders_guild"

    unclaimed = await client.get_territory(100000, 100000)
    assert unclaimed["claimed"] is False
    assert unclaimed["faction"] is None


async def test_get_status(client):
    s = await client.get_status()
    assert s["tps"] > 0
    assert s["player_count"] == 4
    assert "uptime_seconds" in s and "world_time_ticks" in s


async def test_destructive_detection(client):
    assert client.is_destructive("stop")
    assert client.is_destructive("/ban Griefer")
    assert client.is_destructive("op Someone")
    assert not client.is_destructive("give Aldric_Vale bread 1")
    assert not client.is_destructive("broadcast hello")


async def test_teleport_online_and_offline(client):
    ok = await client.teleport_player("Aldric_Vale", 0, 80, 0, "world")
    assert ok["success"] is True
    assert ok["location"] == {"x": 0, "y": 80, "z": 0, "world": "world"}

    bad = await client.teleport_player("Ghost", 0, 80, 0, "world")
    assert bad["success"] is False


async def test_send_message_all_vs_single(client):
    everyone = await client.send_message("all", "hi", "chat")
    assert len(everyone["recipients"]) == 4
    single = await client.send_message("Aldric_Vale", "hi", "title")
    assert single["recipients"] == ["Aldric_Vale"]
    assert single["kind"] == "title"


async def test_set_block(client):
    r = await client.set_block(10, 64, 10, "stone_bricks", "world")
    assert r["success"] is True
    assert r["material"] == "STONE_BRICKS"


async def test_manage_faction_territory_expand_and_retract(client):
    before = (await client.get_factions())["factions"]
    iron_before = next(f for f in before if f["id"] == "iron_crown")["territory"]

    expanded = await client.manage_faction_territory("iron_crown", "expand", 64)
    assert expanded["success"] is True
    assert expanded["territory"]["max_x"] == iron_before["max_x"] + 64
    assert expanded["territory"]["min_x"] == iron_before["min_x"] - 64

    retracted = await client.manage_faction_territory("iron_crown", "retract", 64)
    assert retracted["territory"] == iron_before  # back to start

    bad = await client.manage_faction_territory("nope", "expand", 10)
    assert bad["success"] is False
