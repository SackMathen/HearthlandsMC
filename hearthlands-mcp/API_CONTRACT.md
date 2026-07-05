# Hearthlands Paper Plugin — HTTP API Contract

This document specifies the REST API that the companion Paper plugin must
expose. The MCP server (`hearthlands-mcp`) is written against this contract,
so the plugin can be built to match it. Until the plugin exists, run the MCP
server with `HEARTHLANDS_MOCK=true` to get realistic responses in these exact
shapes.

## Conventions

- **Base URL:** `http://localhost:8080` (configurable, plugin-side and via
  `HEARTHLANDS_API_URL`).
- **Auth:** every request carries `Authorization: Bearer <API_KEY>`. Reject
  missing/incorrect keys with `401`.
- **Content type:** requests and responses are `application/json`.
- **Coordinates:** block/world coordinates are numbers. `world` is a Bukkit
  world name (default `"world"`).
- **Errors:** non-2xx responses SHOULD include a JSON body
  `{"error": "human readable message"}`. `401` means auth failure.
- **Faction ids:** stable snake_case slugs —
  `pathfinders_guild`, `iron_crown`, `deeproot_collective`,
  `archivists_of_starlight`.

---

## Read endpoints

### `GET /players`
List online players.

```json
{
  "players": [
    {
      "name": "Aldric_Vale",
      "uuid": "…",
      "location": { "x": 128.5, "y": 72.0, "z": -340.2, "world": "world" },
      "health": 20.0,
      "faction": "pathfinders_guild"
    }
  ],
  "count": 1
}
```
`faction` is `null` when unknown/unaffiliated.

### `GET /factions`
Return all lore factions.

```json
{
  "factions": [
    {
      "id": "iron_crown",
      "name": "Iron Crown",
      "hq": { "x": -512, "y": 65, "z": 610, "world": "world" },
      "territory": { "min_x": -1024, "min_z": 256, "max_x": -256, "max_z": 1024 }
    }
  ]
}
```
`territory` here is an axis-aligned bounding box. A richer plugin may add a
`polygon` field; the MCP server only requires the bbox fields.

### `GET /territory?x=<x>&z=<z>`
Resolve which faction claims a coordinate.

```json
{
  "x": 100, "z": -300,
  "chunk_x": 6, "chunk_z": -19,
  "faction": "pathfinders_guild",
  "faction_name": "Pathfinders' Guild",
  "claimed": true
}
```
When unclaimed: `"faction": null, "faction_name": null, "claimed": false`.

### `GET /status`
Server health.

```json
{
  "tps": 19.87,
  "player_count": 4,
  "max_players": 40,
  "uptime_seconds": 128400,
  "world_time_ticks": 6000,
  "world_time_label": "Noon",
  "version": "Paper 1.19.2"
}
```

---

## Write endpoints

All write endpoints return `{"success": true, …}` on success or
`{"success": false, "error": "…"}` on a handled failure.

### `POST /command`
Execute a console command. The plugin runs it as the console sender.

Request: `{ "command": "give Aldric_Vale minecraft:bread 4" }`
(leading slash optional).

```json
{ "command": "give …", "success": true, "output": "Gave 4 …" }
```
> Destructive-command confirmation is enforced **MCP-side** (`run_command`
> refuses `stop`, `ban`, `op`, etc. without `confirm_destructive=true`). The
> plugin should still apply its own permission checks.

### `POST /players/{name}/teleport`
Request: `{ "x": 0, "y": 80, "z": 0, "world": "world" }`

```json
{ "success": true, "player": "Aldric_Vale",
  "location": { "x": 0, "y": 80, "z": 0, "world": "world" } }
```
Return `{"success": false, "error": "player '…' is not online"}` if offline.

### `POST /messages`
Send chat or a title card.

Request: `{ "target": "all", "message": "Dusk falls on the Hearthlands.", "type": "chat" }`
- `target`: player name, or `"all"` to broadcast.
- `type`: `"chat"` or `"title"`.

```json
{ "success": true, "target": "all", "kind": "chat",
  "message": "…", "recipients": ["Aldric_Vale", "Mira_Stonehand"] }
```

### `POST /blocks`
Place a block.

Request: `{ "x": 10, "y": 64, "z": 10, "material": "STONE_BRICKS", "world": "world" }`
`material` follows Bukkit's `Material` enum.

```json
{ "success": true, "x": 10, "y": 64, "z": 10,
  "material": "STONE_BRICKS", "world": "world" }
```

### `POST /factions/{id}/territory`
Expand or retract a faction's stored perimeter.

Request: `{ "action": "expand", "amount": 64 }`
- `action`: `"expand"` or `"retract"`.
- `amount`: positive number of blocks applied to every side.

```json
{ "success": true, "faction": "iron_crown", "action": "expand",
  "amount": 64, "territory": { "min_x": -1088, "min_z": 192, "max_x": -192, "max_z": 1088 } }
```
Return `{"success": false, "error": "unknown faction '…'"}` for a bad id.
