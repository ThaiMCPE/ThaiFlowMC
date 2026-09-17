# ThaiFlowMC Python API

Everything a mod uses comes from one import:

```python
from thaiflow import *
```

This module is not a real package on disk - it's installed into your mod's Python context by ThaiFlowMC itself (see `python-runtime/src/main/resources/thaiflow_bootstrap.py`) right before your `main.py` runs. There is no `Registry`, `ResourceLocation`, or any other Java-shaped type anywhere in this API; if you find yourself needing one, that's a bug in ThaiFlowMC, not a missing feature you should work around.

## `@load`

Registers a function to run once, immediately after your mod's script finishes executing top to bottom.

```python
@load
def loaded():
    print("My mod loaded!")
```

## `@event(name)`

The generic event decorator. Registers a function to run whenever ThaiFlowMC fires the named event anywhere in the running instance (not just for your mod).

```python
@event("test")
def on_test(data):
    print(data)
```

## Sugar decorators

Each of these is just a named alias for `@event("...")` - they exist so mod code reads naturally, and so more can be added later without changing how mods are written.

| Decorator | Equivalent to | Payload your function receives |
|---|---|---|
| `@player_join` | `@event("player_join")` | a `Player` |
| `@join` | same as `@player_join` | a `Player` |
| `@player_leave` | `@event("player_leave")` | a `Player` |
| `@server_start` | `@event("server_start")` | a `Server` |
| `@server_stop` | `@event("server_stop")` | a `Server` |
| `@tick` | `@event("tick")` | (reserved for future use) |

```python
@player_join
def welcome(player):
    player.say(f"Welcome, {player.name}!")

@server_start
def ready(server):
    server.broadcast("The server is ready!")
```

## `item(id, stack=64)`

Declares an item. ThaiFlowMC looks for a texture automatically, checking (in order) `textures/<id>.png` and `<id>.png` next to your script:

```python
item("ruby")
```

```
mods/
└── my_cool_mod/
    ├── main.py
    └── ruby.png       # auto-detected as the "ruby" item's texture
```

In this MVP, `item(...)` records the item (id, stack size, resolved texture path) in an in-memory registry; real Minecraft item registration is part of the Minecraft integration work described in `docs/ROADMAP.md`.

## `Player`

Handed to `@player_join` / `@player_leave` handlers.

| Member | Description |
|---|---|
| `player.name` | display name |
| `player.health` | current health (0-20) |
| `player.say(message)` | sends a chat message to the player |
| `player.teleport(x, y, z)` | teleports the player |

## `Server`

Handed to `@server_start` / `@server_stop` handlers.

| Member | Description |
|---|---|
| `server.broadcast(message)` | sends a chat message to every connected player |
| `server.online_player_count` | number of connected players |

## Errors

A mistake in your script - a typo, a bad argument, an unhandled exception - is caught and shown as a short block instead of a raw stack trace:

```
main.py line 8:

    player.sya("Hello")

AttributeError: 'Player' object has no attribute 'sya'
```

A mistake while your mod is loading (its `main.py`, or a `@load` callback) stops *that mod* from loading; other mods still load normally. A mistake inside an `@event`/sugar-decorated handler is reported the same way but doesn't stop future events from being delivered to your other handlers, or to other mods'.

## Mod isolation

Every mod runs in its own, separate Python interpreter. Two mods can both `import thaiflow`, define a `player_join` handler, or use the same variable names, without ever seeing each other's state - there is no shared global namespace between mods. Your mod is also untrusted by default: no filesystem, no network, no subprocesses, no reaching Java classes - see "Security" in `docs/ARCHITECTURE.md` for the full list and why.

## Permissions

If your mod needs to save data, declare it in `mod.toml`:

```toml
[permissions]
storage = true
```

With that, ordinary file I/O just works, scoped to a private directory only your mod can see:

```python
with open("save.txt", "w") as f:
    f.write("hello")
```

Everything you write stays inside your mod's own storage - there's no way to read or write anywhere else, including another mod's storage or your mod's own source files. Leave `[permissions]` out entirely (as every zero-config, `main.py`-only mod does) and you get none of this - which is exactly right for a mod that doesn't need to persist anything.
