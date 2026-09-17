package dev.thaiflowmc.launcher;

import dev.thaiflowmc.adapter.mc.RealMinecraftAdapter;
import dev.thaiflowmc.api.Log;
import dev.thaiflowmc.api.ThaiFlowException;
import dev.thaiflowmc.api.event.SimpleEventBus;
import dev.thaiflowmc.api.item.ItemRegistry;
import dev.thaiflowmc.loader.ModLoader;
import dev.thaiflowmc.loader.ModManager;
import dev.thaiflowmc.loader.model.ModContainer;
import dev.thaiflowmc.python.PythonRuntime;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Boots a <b>real</b> Minecraft dedicated server with ThaiFlowMC's mods
 * wired up, via {@link RealMinecraftAdapter} and the {@code ThaiFlowAgent}
 * java agent (see {@code docs/ROADMAP.md}).
 *
 * <p>This is a separate, manual entrypoint from {@link Launcher}'s default
 * simulated run. It must be launched with {@code -javaagent:<path to the
 * built minecraft-adapter jar>} on the command line (an agent can only be
 * attached at JVM startup) and with the real {@code net.minecraft.bundler.Main}
 * class on the classpath (i.e. the official Mojang server jar). The
 * {@code :launcher:realMinecraftIntegration} Gradle task wires both of
 * these up automatically. It also requires the working directory to
 * already have an {@code eula.txt} with {@code eula=true} - ThaiFlowMC
 * never writes that file itself; see {@link RealMinecraftAdapter#isEulaAccepted}.
 */
public final class RealMinecraftLauncher {

    private static final Logger LOG = LoggerFactory.getLogger(Log.LOADER);

    private RealMinecraftLauncher() {
    }

    public static void main(String[] args) throws Exception {
        Path modsDirectory = args.length > 0 ? Paths.get(args[0]) : Paths.get("mods");
        String[] serverArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[] {"nogui"};

        if (!RealMinecraftAdapter.isEulaAccepted(Paths.get("."))) {
            System.err.println(
                    "[ThaiFlowMC]\n\n"
                            + "Cannot start the real Minecraft server: eula.txt does not contain \"eula=true\".\n\n"
                            + "ThaiFlowMC will not accept Mojang's EULA on your behalf. If you agree to it\n"
                            + "(https://aka.ms/MinecraftEULA), create eula.txt yourself with that line.");
            return;
        }

        LOG.info("ThaiFlowMC starting (real Minecraft mode)");

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
        PythonRuntime pythonRuntime = new PythonRuntime(itemRegistry);
        Runtime.getRuntime().addShutdownHook(new Thread(pythonRuntime::close, "thaiflowmc-python-shutdown"));

        try {
            ModManager modManager = new ModManager(Launcher.PLATFORM_VERSION);
            List<ModContainer> mods = modManager.prepare(modsDirectory);

            ModLoader modLoader = new ModLoader(pythonRuntime, eventBus);
            modLoader.loadAll(mods);
        } catch (ThaiFlowException e) {
            System.err.println("[ThaiFlowMC]\n\n" + e.friendlyDetail());
            LOG.debug("Fatal ThaiFlowMC startup error", e);
            return;
        }

        new RealMinecraftAdapter(eventBus).start();

        LOG.info("Handing off to the real Minecraft server");
        // net.minecraft.bundler.Main starts the actual game on its own
        // non-daemon "ServerMain" thread and returns almost immediately;
        // the JVM stays alive (and ThaiFlowMC's hook stays registered)
        // until that thread exits.
        Class<?> bundlerMain = Class.forName("net.minecraft.bundler.Main");
        Method main = bundlerMain.getMethod("main", String[].class);
        main.invoke(null, (Object) serverArgs);
    }
}
