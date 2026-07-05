"""Tests for the MCP tool layer (confirmation guard, validation)."""

import importlib
import os

import pytest


@pytest.fixture
def server(monkeypatch):
    monkeypatch.setenv("HEARTHLANDS_MOCK", "true")
    import hearthlands_mcp.server as srv

    return importlib.reload(srv)


async def test_run_command_blocks_destructive_without_confirm(server):
    result = await server.run_command("stop")
    assert result["success"] is False
    assert result["requires_confirmation"] is True


async def test_run_command_allows_destructive_with_confirm(server):
    result = await server.run_command("stop", confirm_destructive=True)
    assert result["success"] is True


async def test_run_command_allows_safe_command(server):
    result = await server.run_command("give Aldric_Vale bread 1")
    assert result["success"] is True


async def test_manage_faction_territory_rejects_nonpositive(server):
    result = await server.manage_faction_territory("iron_crown", "expand", 0)
    assert result["success"] is False


async def test_read_tools_return_data(server):
    players = await server.get_players()
    assert players["count"] == 4
    status = await server.get_server_status()
    assert status["player_count"] == 4
