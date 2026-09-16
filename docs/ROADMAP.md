# Roadmap: from this vertical slice to real Minecraft gameplay

This document is the honest answer to "what's left before ThaiFlowMC mods run inside real Minecraft?" It exists so the gap doesn't get papered over with a hack.

## Where things stand

The full non-Minecraft pipeline works and is tested (see `docs/ARCHITECTURE.md`):

```
discover mod -> parse metadata -> resolve dependencies -> start Python -> execute main.py
-> register callback -> Java fires event -> Python callback runs -> errors reported cleanly
```

`minecraft-adapter` currently ships one implementation of `MinecraftAdapter`: `SimulatedMinecraftAdapter`, which fires `server_start` / `server_stop` / `player_join` / `player_leave` with real `GameServer` / `Player` objects, but without an actual Minecraft server underneath. It exists specifically to let the event pipeline, the Python bridge, and the sugar decorators (`@player_join`, `@server_start`, ...) be built and tested *before* the much harder problem of hooking real Minecraft is solved.

## Why real integration isn't in this iteration

ThaiFlowMC's constraint is: no Fabric Loader, Fabric API, Forge, NeoForge, or Quilt Loader. Those projects each represent years of work solving exactly the problem "load and patch Minecraft's classes before/while it starts, without redistributing Minecraft itself." Recreating that from scratch is a real undertaking, not a config change, and rushing it would mean either quietly depending on one of the forbidden loaders or shipping something fragile enough to undermine the "clean Minecraft version isolation" priority. The task explicitly allows documenting this blocker precisely and keeping the core working rather than hacking around it - this is that documentation.

## What real integration actually requires

1. **Obtaining a Minecraft server jar legitimately at dev/build time.** Mojang publishes a version manifest and per-version metadata (including a server jar download URL and, since 1.14.4, official server-side mappings) at `piston-meta.mojang.com`. ThaiFlowMC would fetch this the way any dev tool does - on the developer's machine, under Mojang's EULA, never committed to this repository or redistributed by us.

2. **Deobfuscating against Mojang's official mappings.** The official mappings map obfuscated names (`net.minecraft.class_1234`-style, or ProGuard-style in Mojmaps) to the real method/field names we'd need to target a specific hook point (e.g. "the method that runs once the dedicated server has finished starting"). This has to be redone (or re-verified) for every targeted Minecraft version - this is the concrete meaning of "Minecraft-version-specific issue" the adapter module exists to contain.

3. **A loader/launch mechanism that isn't Fabric/Forge/NeoForge/Quilt.** This is the actual hard part. Fabric and Forge each ship a custom classloader (Fabric's "Knot", Forge's "ModLauncher") that loads Minecraft's classes through a pipeline where Mixin (or their own ASM transformers) can rewrite bytecode before the JVM ever verifies/links the class. ThaiFlowMC needs its own equivalent, at minimum:
   - A bootstrap `main()` that sets up a classloader (or a `java.lang.instrument` Java agent, which sidesteps needing a custom classloader entirely) capable of transforming Minecraft's classes as they load.
   - SpongePowered Mixin configured in one of its standalone modes (Mixin supports being driven by a Java agent via `-javaagent`, not only via Fabric/Forge's launch services) - this repo is allowed to depend on Mixin/ASM directly, just not on a loader that already wires them up for us.
   - At least one Mixin (or, for the very first milestone, a hand-written ASM transformer, which is simpler than authoring a full Mixin config) injecting a single static call at the chosen hook point.

4. **Wiring that hook to `EventBus.fire(...)`.** Once real Minecraft can call into ThaiFlowMC code at all, connecting it to the existing `EventBus` is small - the adapter's real implementation would look like `SimulatedMinecraftAdapter` but call `eventBus.fire("server_start", realGameServerWrapper)` from the injected hook instead of from a Java method Java itself calls.

## Concrete next milestone

In priority order (matching "server start, then player join, then server stop" from the product brief):

1. Pick and pin one Minecraft version (a current Java 21 release, e.g. the 1.21.x line, matching the `--release 21` compile target already set project-wide).
2. Stand up the download-and-deobfuscate step as a Gradle task (fetch server jar + official mappings for the pinned version; never commit the jar).
3. Prototype the minimal non-Fabric/Forge launch mechanism (Java agent + Mixin, or Java agent + hand-written ASM) against that one jar, targeting only the dedicated server's "started" hook.
4. Implement `RealMinecraftAdapter implements MinecraftAdapter` in `minecraft-adapter`, wrapping the real `MinecraftServer` instance behind the existing `GameServer` interface, and fire `server_start` from the injected hook.
5. Extend to `player_join` (wrap the real player object behind `Player`), then `server_stop`.
6. Only after that: begin the item/block/entity APIs the top-level spec explicitly says to defer.

## Explicitly out of scope until the above lands

Blocks, items registered into real Minecraft (today `item(...)` only records metadata in the in-memory `ItemRegistry` - see `api.item.ItemRegistry`), entities, rendering, a networking API, a GUI API, world generation, and recipes. All of `api`, `loader`, and `python-runtime` are designed so that none of this requires touching mods once it's built - only `minecraft-adapter` should need to change.
