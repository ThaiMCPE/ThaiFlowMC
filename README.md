# ThaiFlowMC

ThaiFlowMC is a Python-first modding platform for Minecraft Java Edition focused on making mod creation dramatically simpler.

> **Status: experimental.** ThaiFlowMC is an early, from-scratch modding platform. It does not depend on Fabric, Forge, NeoForge, or Quilt, and real Minecraft integration is still in progress (see [docs/ROADMAP.md](docs/ROADMAP.md)). Expect breaking changes.

Someone who knows basic Python should be able to make a Minecraft mod without needing to understand Java, Gradle, Mixin, registries, mappings, or Minecraft internals. A mod is just a Python script:

```python
from thaiflow import *

item("ruby")

@player_join
def hello(player):
    player.say("Hello!")
```

## What works today

ThaiFlowMC's core loop - discover a mod, read its metadata, resolve dependencies, run its Python, and let Java and Python fire events at each other - works end to end, independent of Minecraft:

```
ThaiFlowMC starts
       |
scans mods/
       |
discovers a mod, reads mod.toml (or infers everything from the folder)
       |
resolves dependencies
       |
starts an isolated embedded Python runtime for the mod
       |
executes main.py, which registers callbacks
       |
Java fires an event (e.g. a simulated player joining)
       |
the Python callback runs
```

Minecraft itself is not wired up yet; a `SimulatedMinecraftAdapter` stands in for it so the rest of the pipeline can be built and tested now. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for how the modules fit together and [docs/ROADMAP.md](docs/ROADMAP.md) for exactly what's left before real Minecraft gameplay integration.

## Try it

Requires JDK 21+ (developed and tested on JDK 25; no separate GraalVM install needed - GraalPy is pulled in as a regular Maven dependency).

```bash
./gradlew build   # compiles every module and runs the test suite
./gradlew test    # tests only
```

Run the launcher against the bundled example mods:

```bash
./gradlew :launcher:run --args="mods"
```

You should see something like:

```
[ThaiFlowMC/Loader] ThaiFlowMC starting
[ThaiFlowMC/Loader] Found 2 mods
[ThaiFlowMC/Loader] Loading hello 1.0.0
[ThaiFlowMC/Python] Starting Python runtime for hello
My mod loaded!
[ThaiFlowMC/Loader] Executed hello/main.py
...
[ThaiFlowMC/Minecraft] Server started (simulated)
[ThaiFlowMC/Minecraft] [broadcast] hello mod is ready!
```

## Writing a mod

The simplest possible mod is a single file - no `mod.toml` required:

```
mods/
└── my_cool_mod/
    └── main.py
```

```python
from thaiflow import *

@load
def hello():
    print("Hello from ThaiFlowMC!")
```

The mod's id comes from the folder name, its version defaults to `1.0.0`, and its entrypoint is assumed to be `main.py`. Once a mod needs a stable id, a real version, or dependencies, add a `mod.toml`:

```toml
id = "hello"
name = "Hello Mod"
version = "1.0.0"
entrypoint = "main.py"

[dependencies]
thaiflowmc = ">=0.1.0"
```

See [docs/PYTHON_API.md](docs/PYTHON_API.md) for the full (small, deliberately so) Python API.

## Repository layout

```
thaiflowmc/
├── launcher/          starts Minecraft through ThaiFlowMC
├── loader/            discovers mods, parses mod.toml, resolves dependencies, runs the lifecycle
├── api/                the stable API surface mods and the adapter share (never raw Minecraft classes)
├── python-runtime/    embeds GraalPy, runs main.py, bridges the API into Python
├── minecraft-adapter/ the only module allowed to know about Minecraft internals
├── example-mod/       the tiniest possible mod, used in docs
├── mods/              example mods the launcher runs by default
└── docs/              architecture, roadmap, and Python API reference
```

## License

See [LICENSE](LICENSE).
