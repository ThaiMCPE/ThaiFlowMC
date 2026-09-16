package dev.thaiflowmc.launcher;

import dev.thaiflowmc.adapter.sim.SimulatedMinecraftAdapter;
import dev.thaiflowmc.api.Log;
import dev.thaiflowmc.api.ThaiFlowException;
import dev.thaiflowmc.api.event.SimpleEventBus;
import dev.thaiflowmc.api.item.ItemRegistry;
import dev.thaiflowmc.loader.ModLoader;
import dev.thaiflowmc.loader.ModManager;
import dev.thaiflowmc.loader.model.ModContainer;
import dev.thaiflowmc.loader.model.Version;
import dev.thaiflowmc.python.PythonRuntime;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts Minecraft through ThaiFlowMC: discovers mods under {@code mods/},
 * resolves their dependencies, starts an isolated Python runtime for each,
 * then boots the (currently simulated - see docs/ROADMAP.md) Minecraft
 * adapter so lifecycle events reach mods.
 */
public final class Launcher {

    /** ThaiFlowMC's own version, checked against a mod's {@code [dependencies] thaiflowmc = "..."}. */
    public static final Version PLATFORM_VERSION = Version.parse("0.1.0");

    private static final Logger LOG = LoggerFactory.getLogger(Log.LOADER);

    private Launcher() {
    }

    public static void main(String[] args) {
        Path modsDirectory = args.length > 0 ? Paths.get(args[0]) : Paths.get("mods");
        run(modsDirectory);
    }

    static void run(Path modsDirectory) {
        LOG.info("ThaiFlowMC starting");

        SimpleEventBus eventBus = new SimpleEventBus();
        eventBus.setFailureListener((eventName, error) -> {
            if (error instanceof ThaiFlowException friendly) {
                System.err.println("[ThaiFlowMC]\n\n" + friendly.friendlyDetail());
            } else {
                LOG.error("Unhandled error in event '{}'", eventName, error);
            }
            LOG.debug("Unhandled error in event '{}'", eventName, error);
        });

        ItemRegistry itemRegistry = new ItemRegistry();
        try (PythonRuntime pythonRuntime = new PythonRuntime(itemRegistry)) {
            ModManager modManager = new ModManager(PLATFORM_VERSION);
            List<ModContainer> mods = modManager.prepare(modsDirectory);

            ModLoader modLoader = new ModLoader(pythonRuntime, eventBus);
            modLoader.loadAll(mods);

            SimulatedMinecraftAdapter minecraft = new SimulatedMinecraftAdapter(eventBus);
            minecraft.start();
            minecraft.stop();
        } catch (ThaiFlowException e) {
            System.err.println("[ThaiFlowMC]\n\n" + e.friendlyDetail());
            LOG.debug("Fatal ThaiFlowMC startup error", e);
        }
    }
}
