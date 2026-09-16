# ThaiFlowMC Architecture

## Layers

```
Python Mod
    |
ThaiFlow Python API      (thaiflow_bootstrap.py, turns the bridge into item()/@event/@load/...)
    |
ThaiFlow Core API         (:api - EventBus, Player, GameServer, ItemRegistry, ThaiFlowException)
    |
Minecraft Adapter          (:minecraft-adapter - the only module allowed to know Minecraft internals)
    |
Minecraft
```

`:loader` and `:python-runtime` sit beside this stack rather than in it: the loader drives discovery and lifecycle, and the python-runtime is what turns a mod's `main.py` into calls against `:api`. Neither one imports Minecraft or GraalPy types into the other - see "Module boundaries" below.

## Module responsibilities

| Module | Responsibility | Depends on |
|---|---|---|
| `api` | Stable, engine-agnostic types every other module shares: `EventBus`, `Player`, `GameServer`, `ItemRegistry`, `ModHandle`/`ModEntrypointExecutor` (the loader <-> python-runtime contract), and `ThaiFlowException`. | nothing |
| `loader` | Discovers mods under `mods/`, parses `mod.toml` (or infers metadata for zero-config mods), validates ids/versions/entrypoints, resolves dependency order, and drives the load lifecycle. Never touches GraalPy or Minecraft. | `api` |
| `python-runtime` | Embeds GraalPy, creates one Python context per mod, installs the `thaiflow` module (`thaiflow_bootstrap.py`), executes `main.py`, and translates Python exceptions into friendly errors. | `api` |
| `minecraft-adapter` | The only module allowed to reference Minecraft internals. Today it contains `SimulatedMinecraftAdapter`, a Minecraft-free stand-in used to prove the event pipeline; see `docs/ROADMAP.md` for the real implementation. | `api` |
| `launcher` | Wires everything together: builds the shared `EventBus` and `ItemRegistry`, runs `ModManager`/`ModLoader`, then starts the Minecraft adapter. | all of the above |

### Why `loader` and `python-runtime` don't depend on each other

The loader needs to "run" a mod's entrypoint, and the python-runtime is what actually can - but if `loader` depended on `python-runtime` directly, the loader (whose job is metadata/dependency bookkeeping) would drag in GraalPy, and a future non-Python entrypoint type would be impossible without changing the loader. Instead, `api` defines the narrow contract:

```java
public record ModHandle(String modId, Path sourceDirectory, String entrypoint) {}

public interface ModEntrypointExecutor {
    void execute(ModHandle mod, EventBus eventBus) throws Exception;
}
```

`python-runtime`'s `PythonRuntime` implements `ModEntrypointExecutor`; `loader`'s `ModLoader` only ever calls through that interface. The launcher is what wires a concrete `PythonRuntime` into a `ModLoader`.

## The vertical slice, end to end

This is the exact call sequence `Launcher.run(modsDirectory)` performs, and what `LauncherIntegrationTest` asserts on:

1. `ModDiscoverer.discover(modsDirectory)` lists candidate mod folders (anything with `mod.toml` or `main.py`).
2. `MetadataParser.parse(modDir)` turns each into a `ModMetadata` - either from `mod.toml`, or, for a zero-config mod, inferred entirely from the folder name and defaults.
3. `ModManager.prepare(...)` checks for duplicate ids and asks `DependencyResolver` to validate every `[dependencies]` entry (against other loaded mods, or the `thaiflowmc` platform version) and topologically sort mods so dependencies load first.
4. `ModLoader.loadAll(...)` calls the `ModEntrypointExecutor` (in practice, `PythonRuntime`) for each mod in order.
5. `PythonRuntime.execute(...)` builds a fresh GraalPy `Context`, evaluates `thaiflow_bootstrap.py` to get an `_install_thaiflow_module` function, calls it with the mod's `PythonBridge` to register `thaiflow` in `sys.modules`, then evaluates the mod's `main.py`. Any `@load` callbacks the script registered run once, immediately after.
6. From here on, the mod's `@event(name)` / `@player_join` / etc. handlers are subscribed on the shared `EventBus`. Whenever Java (today, `SimulatedMinecraftAdapter`; eventually the real Minecraft hook) calls `eventBus.fire(name, payload)`, every matching Python callback runs.
7. Errors at any step - a bad `mod.toml`, a missing dependency, a Python exception - are caught, turned into a short readable message (see "Error handling" below), and do not stop unrelated mods from loading.

## Isolation model

Each mod gets its **own GraalPy `Context`**, created independently (not sharing an `Engine`, a Python `sys.modules` cache, or any global variables with other mods). This is the simplest possible isolation mechanism and was chosen over one shared interpreter with per-mod namespacing because:

- It's impossible for one mod to accidentally read or overwrite another mod's globals, monkey-patched builtins, or imported module state - there is no shared state to corrupt.
- It keeps the security story simple: a `Context` is also where future per-mod permissions (`filesystem`, `network`, `java_interop` in a future `mod.toml`) would naturally be enforced, one `Context.Builder` setting at a time.

The trade-off is memory and startup cost: N mods means N GraalPy contexts, each with its own interpreter state. For the scale ThaiFlowMC targets (a modpack's worth of small mods, not hundreds of heavyweight ones) this is an acceptable and reversible choice - nothing about the `ModEntrypointExecutor` contract prevents a future implementation from sharing an `Engine` across contexts for warm-up caching while keeping contexts (and thus globals) separate.

Contexts are also **denied host class lookup** (`allowHostClassLookup(name -> false)`): Python code can call methods on objects ThaiFlowMC explicitly hands it (the bridge, a `Player`, a `GameServer`), but cannot do `java.type("java.lang.Runtime")` or otherwise reach arbitrary JVM classes. Combined with the fact that `PythonBridge` only exposes `subscribe(...)` and `registerItem(...)`, a mod's "host surface area" is exactly those two calls - there is no generic escape hatch today.

## Error handling

ThaiFlowMC distinguishes two kinds of failure:

- **Fatal / structural** (duplicate mod id, missing dependency, invalid `mod.toml`): these abort startup entirely via a `ModLoadException` (see `loader/error`), because a topologically invalid mod set can't be partially loaded meaningfully. `FriendlyErrorFormatter` renders these as the short block described in the top-level spec (title, then a specific detail - "Missing dependency: ... / Installed: ...", not a stack trace).
- **Per-mod / runtime** (a Python script that throws, an event handler that fails): these are caught per mod or per handler and reported without stopping other mods (`ModLoader.load`) or other handlers (`SimpleEventBus.fire`). `PythonScriptException.friendlyDetail()` uses GraalPy's `PolyglotException.getSourceLocation()` to show the offending line and its source text alongside the Python exception message, e.g.:

  ```
  main.py line 8:

      player.sya("Hello")

  AttributeError: 'Player' object has no attribute 'sya'
  ```

Every `ThaiFlowException` still carries the original exception as its `cause`, so full stack traces are always available via `LOG.debug(...)` (routed to `logs/thaiflowmc-debug.log` by `launcher/src/main/resources/logback.xml`) even though the console only ever shows the friendly block.

## Logging

Every module logs under one of three fixed logger names, defined once in `api.Log` and reused everywhere rather than duplicated as string literals:

- `ThaiFlowMC/Loader`
- `ThaiFlowMC/Python`
- `ThaiFlowMC/Minecraft`

The console appender only shows `INFO` and above with a bare `[logger] message` pattern; a file appender captures everything (including stack traces) for debugging.

## Progressive disclosure in mod metadata

`MetadataParser` supports two paths, matching the "someone who knows basic Python should be able to make a mod" goal without giving up validation once a mod grows:

- **Zero-config**: a folder with only `main.py`. The id comes from the sanitized folder name, version defaults to `1.0.0`, entrypoint is `main.py`, no dependencies. Nothing here can trigger a validation error except a missing `main.py`.
- **Described** (`mod.toml` present): every field is still optional except that whichever ones ARE present must be valid - a malformed id or version is a hard error rather than silently ignored, because a mod author who bothered to write a `mod.toml` gets the benefit of ThaiFlowMC catching their mistakes.

## What isn't built yet

See [docs/ROADMAP.md](docs/ROADMAP.md) for the precise list of what stands between this vertical slice and real Minecraft gameplay integration.
