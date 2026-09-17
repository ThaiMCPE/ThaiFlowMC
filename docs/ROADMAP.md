# Roadmap: from this vertical slice to real Minecraft gameplay

This document is the honest answer to "what's left before ThaiFlowMC mods run inside real Minecraft?" It exists so the gap doesn't get papered over with a hack.

## Where things stand

The full non-Minecraft pipeline works and is tested (see `docs/ARCHITECTURE.md`):

```
discover mod -> parse metadata -> resolve dependencies -> start Python -> execute main.py
-> register callback -> Java fires event -> Python callback runs -> errors reported cleanly
```

`minecraft-adapter` ships two implementations of `MinecraftAdapter`:

- `sim.SimulatedMinecraftAdapter`, which fires `server_start` / `server_stop` / `player_join` / `player_leave` with real `GameServer` / `Player` objects, but without an actual Minecraft server underneath. It exists specifically to let the event pipeline, the Python bridge, and the sugar decorators (`@player_join`, `@server_start`, ...) be built and tested *before* the much harder problem of hooking real Minecraft is solved.
- `mc.RealMinecraftAdapter`, the real hook mechanism described below - built, tested, and **verified live against the actual Minecraft 26.3 dedicated server** (ThaiFlowMC's primary target; also independently verified against 1.21.1). Not yet wired into the default `Launcher`/`./gradlew run` - see `:launcher:realMinecraftIntegration` below, which is intentionally a separate, manual/opt-in task.

## Why real integration took a separate hook mechanism, not Mixin

ThaiFlowMC's constraint is: no Fabric Loader, Fabric API, Forge, NeoForge, or Quilt Loader. Those projects each represent years of work solving exactly the problem "load and patch Minecraft's classes before/while it starts, without redistributing Minecraft itself." The task's own brief allows Mixin or ASM directly. For a single, well-known hook point (a log line every server prints once at startup), a small `java.lang.instrument` agent plus plain ASM turned out to be sufficient and considerably simpler than standing up SpongePowered Mixin's standalone launch machinery (which exists to safely apply many overlapping bytecode patches from many mods - not needed yet for one static call). Mixin remains a reasonable choice once ThaiFlowMC needs to support many mods patching overlapping methods; nothing here forecloses adding it later.

## How the real hook was found and built

1. **Obtaining a Minecraft server jar legitimately.** Mojang publishes a version manifest and per-version metadata (including a server jar download URL and, for older versions, official server-side mappings) at `piston-meta.mojang.com` / `piston-data.mojang.com`. This was fetched the way any dev tool does - directly from Mojang's own CDN, under Mojang's EULA, and is never committed to this repository or redistributed by us. (The server jar is itself a small "bundler" wrapper - `net.minecraft.bundler.Main` - whose `META-INF/versions/<version>/server-<version>.jar` entry holds the actual game code and is what the hook mechanism below actually inspects. True for both 26.3 and 1.21.1.)

2. **Finding a real, version-specific hook point** - for 26.3, by reading real class/method names directly (no obfuscation to defeat, unlike 1.21.1, which needed Mojang's official mappings to deobfuscate). See "Concrete next milestone" below for exactly what was found for both. This has to be redone (or re-verified) for every targeted Minecraft version - this is the concrete meaning of "Minecraft-version-specific issue" the adapter module exists to contain.

3. **A loader/launch mechanism that isn't Fabric/Forge/NeoForge/Quilt.** Built as a `java.lang.instrument` Java agent (`ThaiFlowAgent`) that installs an ASM `ClassFileTransformer` (`ServerStartHookTransformer`). Unlike Fabric's "Knot" or Forge's "ModLauncher" (custom classloaders standing in front of the JVM's own class loading), a Java agent uses the JVM's own supported instrumentation hook to rewrite a class's bytecode right before it's defined - no custom classloader needed for a single hook point.

4. **Wiring that hook to `EventBus.fire(...)`.** Done: `RealMinecraftAdapter` registers a callback with `MinecraftHooks`, which the injected bytecode calls; the callback fires `"server_start"` on the same `EventBus` every other mod hook goes through.

## Primary target: Minecraft 26.3

ThaiFlowMC targets **26.3** (Mojang's current stable release as of this writing, requiring **Java 25** - the project's `--release` compile target was bumped from 21 to 25 to match). As a new project, ThaiFlowMC does not carry compatibility baggage for older versions; 1.21.1 was this project's original proving ground before the primary target moved to 26.3, and is kept only as secondary verification evidence below, not as a supported version.

**A significant discovery from this migration: Mojang has stopped obfuscating the dedicated server's code as of 26.3.** Every one of the ~7,760 classes in the 26.3 server jar lives under its real `net.minecraft.*` package with real names (compare: 1.21.1 obfuscated the vast majority of classes to short names like `apn`, `l`, `dcs`). This also explains why 26.3's version manifest publishes no `server_mappings` at all - there is nothing left to map. If this trend holds, future ThaiFlowMC version bumps get meaningfully easier: no deobfuscation step, and any hook point can be found by reading real class/method names directly.

## Concrete next milestone

In priority order (matching "server start, then player join, then server stop" from the product brief):

1. ~~Pick and pin one Minecraft version~~ **Done: 26.3** (Java 25).
2. ~~Locate the real hook point~~ **Done, verified by hand** against Mojang's official 26.3 server jar (SHA-1 `33680f5f2ac32864d6d7cf5e56a705fdb3e05f4c`, downloaded directly from `piston-data.mojang.com` - the same source any launcher uses, never redistributed by this repo):
   - The dedicated server's "finished starting" signal is `net.minecraft.server.dedicated.DedicatedServer.initServer()` - the real, unobfuscated method name, directly visible in the class file - which logs `"Done ({})! For help, type \"help\""` right before returning `true`.
   - This is the exact log line every player and every other modloader also treats as "the server is up," and is unchanged from 1.21.1 despite the method's name going from obfuscated (`apn.e()`) to plain.
3. ~~Build a non-Fabric/Forge/Quilt hook mechanism~~ **Done**, in `minecraft-adapter`'s `dev.thaiflowmc.adapter.mc` package:
   - `MinecraftHooks` - a small, stable, version-independent call target (`fireServerStarted()`), so Minecraft's own churn (obfuscated or not, names still change every version) never has to touch anything above the adapter.
   - `ServerStartHookTransformer` - a plain ASM `ClassFileTransformer`. Rather than hardcoding a class/method name (which was already wrong going from 1.21.1 to 26.3, obfuscation or no), it scans loaded classes for the literal `"Done ("` string constant and injects `INVOKESTATIC MinecraftHooks.fireServerStarted()V` right after the log call that uses it. **This code is completely unchanged between the 1.21.1 and 26.3 verifications below** - proof the string-matching design choice pays off across versions, exactly as intended. Verified three ways:
     - Unit-tested (`ServerStartHookTransformerTest`) against a small fixture matching the same shape, including that the patched bytecode still passes ASM's `CheckClassAdapter` verification and, loaded and run, actually calls the hook (and that an *unpatched* class and classes without the marker never do).
     - Hand-verified against the **real, extracted `net/minecraft/server/dedicated/DedicatedServer.class`** from the downloaded 26.3 server jar: the transformer correctly finds and patches it, producing (structurally verified) bytecode where the injected call sits exactly here:
       ```
       GETSTATIC net/minecraft/server/dedicated/DedicatedServer.LOGGER : Lorg/slf4j/Logger;
       LDC "Done ({})! For help, type \"help\""
       ALOAD 8
       INVOKEINTERFACE org/slf4j/Logger.info (Ljava/lang/String;Ljava/lang/Object;)V (itf)
       INVOKESTATIC dev/thaiflowmc/adapter/mc/MinecraftHooks.fireServerStarted ()V   <- injected
       ```
     - Also independently re-verified against the equivalent, obfuscated `apn.class` from the 1.21.1 server jar (same injected call, same shape) - see the earlier revision of this document for that transcript. (Neither one-off check is part of the automated test suite - both need a real, network-fetched server jar, which is never downloaded automatically outside the opt-in Gradle task below, or committed.)
   - `ThaiFlowAgent` - the `-javaagent` entry point that installs the transformer. It also solves a real classloading wrinkle: Mojang's server bundler (`net.minecraft.bundler.Main`) loads the actual game through a fresh `URLClassLoader` whose parent deliberately skips the application classloader (to isolate its own library versions). The injected call to `MinecraftHooks` couldn't resolve through that isolated loader at first (`NoClassDefFoundError`, caught live in testing). The fix: extract just the tiny, dependency-free `MinecraftHooks` class into its own in-memory jar and append *only that* to the JVM's bootstrap classloader search path (`Instrumentation.appendToBootstrapClassLoaderSearch`), which every classloader in the JVM ultimately delegates to. Appending the *whole* agent jar was tried first and broke `RealMinecraftAdapter` itself (it needs SLF4J, which bootstrap can't see) - worth calling out since it's an easy mistake to repeat.
   - **A second, subtler classloading bug found only by actually running the live server**, fixed in `MinecraftHooks` itself: even after the fix above made the class *resolvable* from Mojang's isolated classloader, live testing showed the registered callback still silently never fired. Diagnosis (`System.identityHashCode` + `getClassLoader()` printed from both sides) proved the isolated classloader's `MinecraftHooks` and the application classloader's `MinecraftHooks` were two distinct `Class` objects with two independent static fields - `appendToBootstrapClassLoaderSearch` makes the *name* resolvable everywhere, but does not guarantee only one `Class` object gets defined for it. The fix: `MinecraftHooks` no longer stores the callback in a static field at all; it stores it in `System.getProperties()`, the one object guaranteed to be the exact same instance everywhere in the JVM regardless of how many times the class itself is defined. `MinecraftHooksTest.callbackFiresEvenWhenInvokedThroughAnIndependentlyLoadedDuplicateClass` reproduces the duplicate-definition scenario directly (a second `URLClassLoader` with no parent) so this can't silently regress.
   - `RealMinecraftAdapter implements MinecraftAdapter` - wires `MinecraftHooks` to the existing `EventBus`, firing `"server_start"`.
   - `RealMinecraftLauncher` (in `launcher`) and the `:launcher:realMinecraftIntegration` Gradle task - runs the full ThaiFlowMC pipeline (discover mods, start Python, load them) and then hands off to the real `net.minecraft.bundler.Main` in the same JVM, with the agent attached.
4. ~~Actually launch the real dedicated server~~ **Done, live, end to end, with the full chain confirmed**: with explicit operator authorization to accept Mojang's EULA for local integration testing (`RealMinecraftAdapter.isEulaAccepted(Path)` still only ever *reads* `eula.txt`; nothing in this codebase writes it - the acceptance was a deliberate, explicit action taken once, outside this code path), the real 26.3 dedicated server booted with the agent attached, our mods (`mods/hello`, `mods/ruby_mod`) loaded and ran their Python, and after the real server printed `Done (2.229s)! For help, type "help"`, the console showed:
   ```
   [ThaiFlowMC/Minecraft] Real Minecraft server finished starting
   [ThaiFlowMC/Minecraft] broadcast("hello mod is ready!") ignored: not yet wired to the live Minecraft server (see docs/ROADMAP.md)
   ```
   That second line is `mods/hello/main.py`'s `@server_start` Python handler calling `server.broadcast(...)`, proving the *entire* chain live: **real Minecraft starts -> injected bytecode fires -> `MinecraftHooks` -> `RealMinecraftAdapter` -> `EventBus` -> Python's `@server_start` handler runs** - with no Fabric, Forge, NeoForge, or Quilt anywhere underneath. The server also shut down cleanly afterward via the normal `stop` console command. To reproduce:
   ```bash
   # once, manually, after you have accepted https://aka.ms/MinecraftEULA yourself:
   echo "eula=true" > run/eula.txt
   ./gradlew :launcher:realMinecraftIntegration
   ```
5. Extend `RealMinecraftAdapter`'s payload to wrap the actual live server instance (today it fires with a placeholder `UnconnectedGameServer` - see its Javadoc, and the log line above) so `server.broadcast(...)` really reaches players, matching what `SimulatedMinecraftAdapter` already fakes.
6. **`player_join`'s real hook point is already found and verified structurally** (same rigor as `server_start` - real 26.3 bytecode, not a guess), but not yet built or live-verified - see "player_join: found, not yet built" below for exactly why and what's left.
7. `server_stop`, once `player_join` is real.
8. Only after that: begin the item/block/entity APIs the top-level spec explicitly says to defer.

## `player_join`: found, not yet built

The real hook point was located the same way `server_start`'s was, against the same real 26.3 server jar:

- **Class/method**: `net.minecraft.server.players.PlayerList.placeNewPlayer(Connection, ServerPlayer, CommonListenerCookie)` - real, unobfuscated names, directly visible in the class file.
- **Log line**: right after the server's own `LOGGER.info("{}[{}] logged in with entity id {} at ({}, {}, {})", ...)` call (bytecode offset 145 in this build) - the same "a player just joined" moment Bukkit/Forge/Fabric-style loaders also hook.
- **The player's name is directly available** at that point via `ServerPlayer.getPlainTextName(): String` (also a real, unobfuscated method) on the `ServerPlayer` parameter (local variable slot 2, i.e. `aload_2` in the disassembly) - no reflection needed just to get a name.

Two things are different enough from `server_start` to be worth building deliberately rather than copy-pasting the pattern:

1. **The log call's shape is different**: `Logger.info(String, Object[])` (a varargs array) here, versus `Logger.info(String, Object)` for `server_start`'s single-argument call. `ServerStartHookTransformer`'s "arm on the marker string, inject after the next method call" logic still applies, but the *marker string itself* (`"{}[{}] logged in with entity id {} at ({}, {}, {})"`) is far more specific and far less likely to be a stable, community-recognized anchor than `"Done ("` - a second transformer (or a generalized one taking the marker and the argument-passing convention as parameters) is the honest way to build this, not reusing `ServerStartHookTransformer` as-is.

2. **This cannot be verified live in this environment the way `server_start` was.** Booting the real dedicated server needs only the server jar - proven above. Triggering `player_join` for real needs an actual Minecraft *client* to connect to it, which this headless environment does not have and cannot reasonably fake (a hand-rolled fake client speaking the real login/handshake protocol is a project of its own). So while the hook point is genuinely verified (real bytecode, real method, real log line, findable the same rigorous way as `server_start`), actually wiring `MinecraftHooks.firePlayerJoined(...)` and confirming a Python `@player_join` handler runs from a real join is **not something this session can honestly claim to have proven live** - it would need to ship as "structurally verified, like server_start's ASM-transform tests were, but not live-confirmed" rather than "proven end to end."

Given that distinction, this is left as researched-and-ready rather than built, so the eventual implementation's test claims stay as precise as everything else in this document.

## Future refactor (not yet): per-version adapter layout

`ServerStartHookTransformer`'s string-matching approach happened to transfer unchanged from 1.21.1 to 26.3, but that is a lucky property of this *one* hook, not a architecture to lean on indefinitely - a future hook (e.g. `player_join`) may need a real, version-specific method signature or field offset that a string constant can't identify. Once more than one hook exists, `minecraft-adapter` should split into something like:

```
minecraft-adapter/
├── common/        # MinecraftAdapter, MinecraftHooks, ThaiFlowAgent - version-independent
└── mc-26.3/       # hooks + any mappings specific to 26.3
```

so that "Minecraft changes -> edit the versioned adapter folder" and "Python mod exists -> never has to change" stay true even as more, less string-friendly hooks are added. Not worth doing yet with a single hook and a single supported version - revisit once `player_join` is real and a second Minecraft version is on the table.

## Future: Strict Isolation Mode (security architecture target)

Today every mod's Python runs as a GraalPy `Context` *inside the same JVM process* as Minecraft (see "Isolation model" in `docs/ARCHITECTURE.md`). That is `STANDARD` mode. ThaiFlowMC's longer-term security target is a second mode:

```
STANDARD  - GraalPy sandbox inside the Minecraft process (today)
STRICT    - each mod runs as its own OS process (or equivalent strong boundary),
            supervised and spoken to only through a validated IPC protocol
```

`UNTRUSTED` mod trust level + `STRICT` isolation is meant to eventually be the strongest safety combination for a server operator running mods from strangers.

Target architecture for `STRICT`:

```
Minecraft JVM
     |
     | validated IPC / RPC
     v
ThaiFlowMC Mod Supervisor
     +-- Mod Worker A <- Python mod A
     +-- Mod Worker B <- Python mod B
     +-- Mod Worker C <- Python mod C
```

Requirements once `STRICT` is built:

- Each mod runs in its own OS process; a mod crash or hang never crashes or freezes Minecraft or another mod, and a hung worker can be terminated independently.
- Each worker gets independent CPU/memory/time limits.
- Workers never receive raw Java or Minecraft objects - only messages representing stable ThaiFlowMC concepts (see the flow below).
- Every message from a worker is untrusted input: message type, payload size, ids, arguments, permissions, and rate limits are all validated before any Minecraft operation runs.
- Permissions/capabilities (see "Security" in docs/ARCHITECTURE.md) are enforced at the IPC boundary, not just inside the worker.
- Mods cannot talk to other mod workers unless explicitly permitted; per-mod storage stays isolated exactly as it would in-process.
- Worker restart/termination should be possible without restarting Minecraft, where practical.

Example call flow under `STRICT`, contrasted with today's `STANDARD` flow:

```
STANDARD (today):  Python mod -> player.say("Hello") -> PythonBridge (in-process call) -> EventBus/adapter -> Minecraft
STRICT (future):   Python mod -> player.say("Hello") -> IPC request -> permission + argument validation -> Minecraft adapter -> Minecraft
```

**The one rule that matters most for everything built between now and then:** a mod author's Python code must never need to know or care which mode it's running under. `@player_join` / `player.say(...)` must look and behave identically whether the call underneath is an in-process bridge call or a validated IPC round-trip. Concretely, that means designing every new wrapper, event, DTO, permission, and lifecycle interface so it could be serialized across a process boundary, even while today's implementation just calls straight through.

**Honest note on where today's code already satisfies this, and where it doesn't yet:**

- Already transport-friendly: the Python-facing API (`item(...)`, `@event(name)`, `@load`, the sugar decorators) never exposes a Java/GraalPy type to mod authors - see `thaiflow_bootstrap.py`. `EventBus`'s events are plain strings + plain-old-data payloads (`Player`, `GameServer` are already just interfaces with primitive-typed methods, not raw Minecraft objects). The `ModHandle`/`ModEntrypointExecutor` contract between `loader` and `python-runtime` is already just an id, a path, and a filename - trivially serializable.
- Not transport-friendly yet: `PythonBridge.subscribe(String eventName, Value callback)` hands GraalPy a live, in-process `org.graalvm.polyglot.Value` handle to the Java side, and calls `callback.execute(payload)` directly (`PythonBridge.java`). A `Value` cannot cross a process boundary. Moving to `STRICT` means this becomes "the worker declares it wants event X" (a serializable subscription message) with actual dispatch happening as a separate IPC call *into* the worker when Java fires that event - a real, but contained, change localized to `python-runtime`'s bridge layer, not to the Python API mod authors write.

Not blocking current development: `STANDARD` mode (this repository's entire focus today) is a legitimate, real security boundary on its own via GraalPy sandboxing (see "Security" in docs/ARCHITECTURE.md), and nothing above requires `STRICT` to exist yet. This section exists so later work doesn't have to fight today's decisions.

## Explicitly out of scope until the above lands

Blocks, items registered into real Minecraft (today `item(...)` only records metadata in the in-memory `ItemRegistry` - see `api.item.ItemRegistry`), entities, rendering, a networking API, a GUI API, world generation, and recipes. All of `api`, `loader`, and `python-runtime` are designed so that none of this requires touching mods once it's built - only `minecraft-adapter` should need to change.
