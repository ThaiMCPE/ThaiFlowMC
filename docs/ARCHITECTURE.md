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
- It keeps the security story simple: a `Context` is also where per-mod permissions (`mod.toml`'s `[permissions]` table - `storage`, `network`, `filesystem`) are enforced, one `Context.Builder` setting at a time. See "Security" below for the full picture.

The trade-off is memory and startup cost: N mods means N GraalPy contexts, each with its own interpreter state. For the scale ThaiFlowMC targets (a modpack's worth of small mods, not hundreds of heavyweight ones) this is an acceptable and reversible choice - nothing about the `ModEntrypointExecutor` contract prevents a future implementation from sharing an `Engine` across contexts for warm-up caching while keeping contexts (and thus globals) separate.

## Security

Mods are **untrusted by default.** Every restriction below applies to every mod unless its `mod.toml` explicitly opts into more (see "Permissions" below) - there is no mod that gets a pass.

### The SandboxPolicy finding

GraalVM's `Context.Builder.sandbox(SandboxPolicy)` is the officially documented way to get a named, validated bundle of restrictions (`CONSTRAINED`, `ISOLATED`, `UNTRUSTED`) - including isolate-based heap separation at the stricter levels. **It does not work for GraalPy.** Confirmed empirically (not from documentation) while building this:

```
java.lang.IllegalArgumentException: The validation for the given sandbox policy CONSTRAINED failed.
The language python can only be used up to the TRUSTED sandbox policy.
```

This is true at every level stricter than `TRUSTED`, for every one of `CONSTRAINED`/`ISOLATED`/`UNTRUSTED`, on the exact GraalPy version this project depends on. Practically, this means:

- **No isolate-based heap limit is available.** Every mod's Python allocations share the host JVM's ordinary heap. Nothing in this stack bounds how much of it one mod can consume - see `PythonSandboxSecurityTest.memoryAbuseIsNotYetContained_knownGap` and "Future: Strict Isolation Mode" in `docs/ROADMAP.md`, which is the planned real fix (an OS process boundary per mod, with real OS-level memory limits).
- Every other restriction `SandboxPolicy` would have bundled together is instead applied **individually**, directly on `Context.Builder`, in `PythonRuntime.execute(...)`. There is no single call that replaces this list; each line is a deliberate, separately-justified restriction.

### What's denied by default, and how

| Restriction | Mechanism |
|---|---|
| Reflective access to arbitrary host objects | A custom `HostAccess` (`PythonRuntime.HOST_ACCESS`): only `@HostAccess.Export`-annotated methods are callable at all - no public-method reflection, no functional-interface conversion, no mutable target-type mappings. See "No raw host objects" below. |
| Reaching arbitrary JVM classes (`java.type(...)`, reflection to escape a view object) | `allowHostClassLookup(name -> false)` |
| Filesystem | `allowIO(IOAccess.NONE)` unless a permission (below) grants otherwise |
| Network sockets | Denied unless `[permissions] network = true` |
| Environment variables | `allowEnvironmentAccess(EnvironmentAccess.NONE)` |
| Spawning threads | `allowCreateThread(false)` |
| Spawning processes / subshells | `allowCreateProcess(false)` |
| Native code / native Python extensions | `allowNativeAccess(false)` - GraalPy native extensions get native access by definition, so this is also what keeps them out |
| Infinite loops / hangs | `ExecutionGuard`: every call into a mod's Python (`@load`, an event handler) runs with a deadline; if it's not back in time, the mod's whole `Context` is force-closed, interrupting execution at the next Truffle safepoint. A runaway mod is treated as unrecoverable, not merely slow - see its Javadoc for why. |
| Flooding stdout/stderr | `CappingOutputStream`: throws once a mod has written more than a fixed budget over its lifetime |
| One mod reading another mod's state | Falls out of the isolation model above (separate `Context`, no shared bindings) - `PythonSandboxSecurityTest.modsCannotSeeEachOthersGlobalState` asserts this directly |

`PythonSandboxSecurityTest` has one test attempting each row above (filesystem escape, network, subprocess, host class lookup, reflection on an unexported object, environment access, infinite loop, excessive output, cross-mod access) - a regression there is a security regression, not a test flake to silence.

### No raw host objects

Mods never receive a real `Player`, `GameServer`, or anything else from `api`/`minecraft-adapter` directly. Every event payload of those types is wrapped first (`PythonBridge.toSafePayload`) in a narrow, `python-runtime`-local view class - `PlayerView`, `GameServerView` - whose only members are the handful of `@HostAccess.Export`-annotated methods a mod is meant to call. This means a future real-Minecraft `Player` implementation that happens to expose more public methods than today's `SimulatedPlayer`/`UnconnectedGameServer` do can never accidentally widen what a mod can reach - the view classes are the only thing Python ever sees, regardless of what the real implementation behind them supports. `PythonBridge` itself is exposed the same way: two `@HostAccess.Export` methods (`subscribe`, `registerItem`), nothing else.

### Permissions

A mod's `mod.toml` may declare:

```toml
[permissions]
storage = true
network = false
filesystem = false
```

Default (no `[permissions]` table at all, including every zero-config mod) is **deny everything** - `ModPermissions.DENY_ALL`. See `ModPermissions`' Javadoc for what each flag does; in short:

- **`storage`**: a private directory just for this mod (today: a sibling of `mods/` named `mod_data/<modId>`), enforced by `ScopedFileSystem` - every path a mod's Python code touches is resolved and checked against that one directory, rejecting `..` escapes and unrelated absolute paths alike. This is the one most mods that need to persist anything should ask for.
- **`network`**: outbound sockets (`allowHostSocketAccess`).
- **`filesystem`**: broad host filesystem access, not scoped to any directory - coarse today, and the most trusting of the three. Prefer `storage`.

There is no separate `TRUSTED` mode/flag implemented today (e.g. a hypothetical `java_interop = true` reverting to unrestricted host access). Every additional escape hatch is exactly the kind of thing this security pass exists to avoid adding speculatively - one hasn't been added without an actual mod that needs it. If one is added later, the rule is the one the product brief states: it must be an explicit, loud, per-mod opt-in, and it must never silently weaken what an `UNTRUSTED` mod (i.e. every mod without that opt-in) gets.

### Honest limits

- **Memory/heap is not bounded.** See the SandboxPolicy finding above.
- **CPU time is bounded only per-call, not cumulatively**, and only via wall-clock deadline + cancellation, not a true CPU-time or statement-count budget (GraalVM's `ResourceLimits.statementLimit` exists and works, but is scoped to a `Context`'s entire lifetime, not per-call - a poor fit for mods whose contexts live for the whole server run; see `ExecutionGuard`'s Javadoc).
- **A timed-out mod is not automatically recoverable** - `ExecutionGuard` force-closes the whole `Context`, ending that mod's ability to handle any future event, not just the one slow call. Restarting a single mod's context without restarting the server isn't implemented.
- **All of the above run in the same JVM process as everything else** (`STANDARD` mode). See "Future: Strict Isolation Mode" in `docs/ROADMAP.md` for the process-boundary design that would close these gaps properly, and for which parts of today's design were deliberately kept transport-agnostic so mods won't need rewriting when it lands.

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
