# Hearthlands MCP Server

An [MCP](https://modelcontextprotocol.io) server that connects Claude to
**The Hearthlands** — a custom medieval-fantasy Minecraft **Paper** server —
through the server's companion Paper plugin HTTP API.

It exposes read tools (players, factions, territory, status) and write tools
(run commands, teleport, message, place blocks, manage territory) that Claude
can call to observe and act on the live world.

> The Paper plugin API does not exist yet. This server is written against a
> documented, clean REST contract (see [`API_CONTRACT.md`](./API_CONTRACT.md))
> so the plugin can be built to match. Until then, run in **mock mode** for
> realistic fake data.

## Tools

| Tool | Kind | Description |
| --- | --- | --- |
| `get_players` | read | Online players: name, location, health, faction |
| `get_factions` | read | The four factions with HQ + territory bounds |
| `get_territory` | read | Which faction claims a given `(x, z)` |
| `get_server_status` | read | TPS, player count, uptime, world time |
| `run_command` | write | Run a console command (confirms destructive ones) |
| `teleport_player` | write | Teleport a player to `(x, y, z, world)` |
| `send_message` | write | Chat line or title card to one player or all |
| `set_block` | write | Place a block of a material at `(x, y, z)` |
| `manage_faction_territory` | write | Expand/retract a faction's perimeter |

**Destructive-command guard:** `run_command` refuses obviously destructive
commands (`stop`, `ban`, `kick`, `op`/`deop`, `whitelist`, `gamerule`, …)
unless called with `confirm_destructive=true`, forcing an explicit
confirmation step. All write operations are logged (action, arguments, result).

## The four factions

| id | Name |
| --- | --- |
| `pathfinders_guild` | Pathfinders' Guild |
| `iron_crown` | Iron Crown |
| `deeproot_collective` | Deeproot Collective |
| `archivists_of_starlight` | Archivists of Starlight |

## Requirements

- Python 3.11+
- [`mcp`](https://pypi.org/project/mcp/) (FastMCP) and `httpx` (installed via
  the package)

## Install

```bash
cd hearthlands-mcp
python -m venv .venv && source .venv/bin/activate
pip install -e .
```

## Configuration

All configuration is via environment variables:

| Variable | Default | Description |
| --- | --- | --- |
| `HEARTHLANDS_API_URL` | `http://localhost:8080` | Base URL of the plugin API |
| `HEARTHLANDS_API_KEY` | _(none)_ | Bearer token sent as `Authorization` |
| `HEARTHLANDS_MOCK` | `false` | `true` returns realistic fake data, no network |
| `HEARTHLANDS_API_TIMEOUT` | `10` | HTTP timeout in seconds |

## Run it

```bash
# Mock mode — no live server needed
HEARTHLANDS_MOCK=true hearthlands-mcp

# Against a live plugin
HEARTHLANDS_API_URL=http://localhost:8080 \
HEARTHLANDS_API_KEY=your-secret-key \
hearthlands-mcp
```

The server speaks MCP over **stdio**.

## Connect it to Claude

### Cowork / Claude Code

Register it as an MCP server (stdio). For example:

```bash
claude mcp add hearthlands \
  --env HEARTHLANDS_MOCK=true \
  -- hearthlands-mcp
```

Or, against the live server:

```bash
claude mcp add hearthlands \
  --env HEARTHLANDS_API_URL=http://localhost:8080 \
  --env HEARTHLANDS_API_KEY=your-secret-key \
  -- hearthlands-mcp
```

### Claude Desktop (`claude_desktop_config.json`)

On macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`
(Windows: `%APPDATA%\Claude\claude_desktop_config.json`). Add:

```json
{
  "mcpServers": {
    "hearthlands": {
      "command": "hearthlands-mcp",
      "env": {
        "HEARTHLANDS_API_URL": "http://localhost:8080",
        "HEARTHLANDS_API_KEY": "your-secret-key",
        "HEARTHLANDS_MOCK": "false"
      }
    }
  }
}
```

If `hearthlands-mcp` is not on `PATH` (e.g. it lives in a venv), use the
absolute path to the venv's script, or invoke the module:

```json
{
  "mcpServers": {
    "hearthlands": {
      "command": "/path/to/.venv/bin/python",
      "args": ["-m", "hearthlands_mcp"],
      "env": { "HEARTHLANDS_MOCK": "true" }
    }
  }
}
```

Restart Claude Desktop after editing the config.

## Development

```bash
pip install -e ".[dev]"
pytest
```

## Building the Paper plugin

Implement the endpoints in [`API_CONTRACT.md`](./API_CONTRACT.md). The mock
backend in `src/hearthlands_mcp/mock.py` is the reference for the exact JSON
shapes the MCP server expects.
