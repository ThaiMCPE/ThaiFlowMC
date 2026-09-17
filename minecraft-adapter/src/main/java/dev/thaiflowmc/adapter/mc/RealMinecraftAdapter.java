package dev.thaiflowmc.adapter.mc;

import dev.thaiflowmc.adapter.MinecraftAdapter;
import dev.thaiflowmc.api.Log;
import dev.thaiflowmc.api.event.EventBus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The real (non-simulated) {@link MinecraftAdapter}: connects {@link
 * MinecraftHooks} - the fixed call targets {@link ServerStartHookTransformer}
 * / {@link ServerStopHookTransformer} inject into real Minecraft bytecode -
 * to ThaiFlowMC's {@link EventBus}.
 *
 * <p>This class does not launch Minecraft itself. Actually running the
 * dedicated server with {@link ThaiFlowAgent} attached is a manual step
 * documented in docs/ROADMAP.md, because it requires accepting Mojang's
 * EULA - the server operator's decision, never made automatically here.
 * {@link #isEulaAccepted} only ever reads that decision; it never writes it.
 */
public final class RealMinecraftAdapter implements MinecraftAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(Log.MINECRAFT);

    private final EventBus eventBus;

    public RealMinecraftAdapter(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void start() {
        MinecraftHooks.setOnServerStarted(() -> {
            LOG.info("Real Minecraft server finished starting");
            eventBus.fire("server_start", new UnconnectedGameServer());
        });
        MinecraftHooks.setOnServerStopping(() -> {
            LOG.info("Real Minecraft server is stopping");
            eventBus.fire("server_stop", new UnconnectedGameServer());
        });
        LOG.info("Hooks installed; waiting for the attached Minecraft server process");
    }

    /** Detaches both hooks - for tests/cleanup, not something real Minecraft calls. */
    @Override
    public void stop() {
        MinecraftHooks.setOnServerStarted(null);
        MinecraftHooks.setOnServerStopping(null);
    }

    /**
     * True only if the server operator has already accepted Mojang's EULA
     * themselves by writing {@code eula=true} into {@code eula.txt} in the
     * server's working directory - exactly what a vanilla server itself
     * requires before it will start. ThaiFlowMC never writes this file.
     */
    public static boolean isEulaAccepted(Path serverDirectory) {
        Path eula = serverDirectory.resolve("eula.txt");
        if (!Files.isRegularFile(eula)) {
            return false;
        }
        try {
            return Files.readAllLines(eula).stream()
                    .map(String::strip)
                    .anyMatch(line -> line.equalsIgnoreCase("eula=true"));
        } catch (IOException e) {
            return false;
        }
    }
}
