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
- `mc.RealMinecraftAdapter`, the real hook mechanism described below - built, tested, and verified against the actual Minecraft 1.21.1 server jar, but not yet wired into an automatic "launch Minecraft" command (see "What's not done" below for exactly why).

## Why real integration took a separate hook mechanism, not Mixin

ThaiFlowMC's constraint is: no Fabric Loader, Fabric API, Forge, NeoForge, or Quilt Loader. Those projects each represent years of work solving exactly the problem "load and patch Minecraft's classes before/while it starts, without redistributing Minecraft itself." The task's own brief allows Mixin or ASM directly. For a single, well-known hook point (a log line every server prints once at startup), a small `java.lang.instrument` agent plus plain ASM turned out to be sufficient and considerably simpler than standing up SpongePowered Mixin's standalone launch machinery (which exists to safely apply many overlapping bytecode patches from many mods - not needed yet for one static call). Mixin remains a reasonable choice once ThaiFlowMC needs to support many mods patching overlapping methods; nothing here forecloses adding it later.

## How the real hook was found and built

1. **Obtaining a Minecraft server jar legitimately.** Mojang publishes a version manifest and per-version metadata (including a server jar download URL and, since 1.14.4, official server-side mappings) at `piston-meta.mojang.com` / `piston-data.mojang.com`. This was fetched the way any dev tool does - directly from Mojang's own CDN, under Mojang's EULA, and is never committed to this repository or redistributed by us. (The 1.21.1 server jar is itself a small "bundler" wrapper - `net.minecraft.bundler.Main` - whose `META-INF/versions/1.21.1/server-1.21.1.jar` entry holds the actual game code and is what the hook mechanism below actually inspects.)

2. **Deobfuscating against Mojang's official mappings** to find a real, version-specific hook point. See "Concrete next milestone" below for exactly what was found for 1.21.1. This has to be redone (or re-verified) for every targeted Minecraft version - this is the concrete meaning of "Minecraft-version-specific issue" the adapter module exists to contain.

3. **A loader/launch mechanism that isn't Fabric/Forge/NeoForge/Quilt.** Built as a `java.lang.instrument` Java agent (`ThaiFlowAgent`) that installs an ASM `ClassFileTransformer` (`ServerStartHookTransformer`). Unlike Fabric's "Knot" or Forge's "ModLauncher" (custom classloaders standing in front of the JVM's own class loading), a Java agent uses the JVM's own supported instrumentation hook to rewrite a class's bytecode right before it's defined - no custom classloader needed for a single hook point.

4. **Wiring that hook to `EventBus.fire(...)`.** Done: `RealMinecraftAdapter` registers a callback with `MinecraftHooks`, which the injected bytecode calls; the callback fires `"server_start"` on the same `EventBus` every other mod hook goes through.

## Concrete next milestone

In priority order (matching "server start, then player join, then server stop" from the product brief):

1. ~~Pick and pin one Minecraft version~~ **Done: 1.21.1** (Java 21, matching the `--release 21` compile target already set project-wide).
2. ~~Locate the real hook point~~ **Done, verified by hand** against Mojang's official 1.21.1 server jar and server mappings (SHA-1 `59353fb40c36d304f2035d51e7d6e6baa98dc05c`, downloaded directly from `piston-data.mojang.com` - the same source any launcher uses, never redistributed by this repo):
   - The dedicated server's "finished starting" signal is `net.minecraft.server.dedicated.DedicatedServer.initServer()` (obfuscated in this build to class `apn`, method `e()`), which logs `"Done ({})! For help, type \"help\""` right before returning `true`.
   - This is the exact log line every player and every other modloader also treats as "the server is up."
3. ~~Build a non-Fabric/Forge/Quilt hook mechanism~~ **Done**, in `minecraft-adapter`'s `dev.thaiflowmc.adapter.mc` package:
   - `MinecraftHooks` - a small, stable, version-independent call target (`fireServerStarted()`), so Minecraft's own churn (obfuscated names change every version) never has to touch anything above the adapter.
   - `ServerStartHookTransformer` - a plain ASM `ClassFileTransformer`. Rather than hardcoding the obfuscated class/method name above (which is already wrong for the next Minecraft version), it scans loaded classes for the literal `"Done ("` string constant and injects `INVOKESTATIC MinecraftHooks.fireServerStarted()V` right after the log call that uses it. Verified two ways:
     - Unit-tested (`ServerStartHookTransformerTest`) against a small fixture matching the same shape, including that the patched bytecode still passes ASM's `CheckClassAdapter` verification and, loaded and run, actually calls the hook (and that an *unpatched* class and classes without the marker never do).
     - Additionally hand-verified against the **real, extracted `apn.class`** from the downloaded 1.21.1 server jar: the transformer correctly finds and patches it, producing (structurally verified) bytecode where the injected call sits exactly here:
       ```
       LDC "Done ({})! For help, type \"help\""
       ALOAD 8
       INVOKEINTERFACE org/slf4j/Logger.info (Ljava/lang/String;Ljava/lang/Object;)V (itf)
       INVOKESTATIC dev/thaiflowmc/adapter/mc/MinecraftHooks.fireServerStarted ()V   <- injected
       ```
       (This one-off check isn't part of the automated test suite - it needs the real, 50MB, network-fetched server jar, which is never downloaded automatically or committed.)
   - `ThaiFlowAgent` - the `-javaagent` entry point that installs the transformer; `minecraft-adapter`'s jar manifest already declares `Premain-Class`/`Agent-Class`.
   - `RealMinecraftAdapter implements MinecraftAdapter` - wires `MinecraftHooks` to the existing `EventBus`, firing `"server_start"` (tested end-to-end via `MinecraftHooks.fireServerStarted()`, exactly as the injected bytecode would call it).
4. **Not done, and deliberately not automated:** actually launching the real dedicated server. Running it requires accepting Mojang's EULA (`eula.txt` with `eula=true`) - a legal agreement, and the server operator's decision, not something ThaiFlowMC decides on anyone's behalf. `RealMinecraftAdapter.isEulaAccepted(Path)` only ever *reads* that file; nothing in this codebase writes it. Once a server operator has accepted the EULA themselves, the manual launch command is:
   ```bash
   java -javaagent:minecraft-adapter/build/libs/minecraft-adapter-<version>.jar -jar server.jar nogui
   ```
   That is the one remaining step to see `server_start` fire from a real, running Minecraft server end to end.
5. Once that manual run is confirmed: extend `RealMinecraftAdapter`'s payload to wrap the actual live server instance (today it fires with a placeholder `UnconnectedGameServer` - see its Javadoc) so `server.broadcast(...)` really reaches players, matching what `SimulatedMinecraftAdapter` already fakes.
6. Extend to `player_join` (wrap the real player object behind `Player`), then `server_stop`.
7. Only after that: begin the item/block/entity APIs the top-level spec explicitly says to defer.

## Explicitly out of scope until the above lands

Blocks, items registered into real Minecraft (today `item(...)` only records metadata in the in-memory `ItemRegistry` - see `api.item.ItemRegistry`), entities, rendering, a networking API, a GUI API, world generation, and recipes. All of `api`, `loader`, and `python-runtime` are designed so that none of this requires touching mods once it's built - only `minecraft-adapter` should need to change.
