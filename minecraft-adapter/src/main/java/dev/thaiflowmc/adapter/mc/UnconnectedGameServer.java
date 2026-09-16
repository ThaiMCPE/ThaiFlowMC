package dev.thaiflowmc.adapter.mc;

import dev.thaiflowmc.api.Log;
import dev.thaiflowmc.api.server.GameServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Placeholder {@link GameServer} fired alongside the real {@code
 * server_start} event until it is wired to the live Minecraft server
 * instance.
 *
 * <p>{@link MinecraftHooks#fireServerStarted()} deliberately takes no
 * arguments, so that the bytecode {@link ServerStartHookTransformer}
 * injects stays this simple, stable, no-arg call. Passing the real,
 * obfuscated server instance through would mean resolving more obfuscated
 * methods (for {@link #broadcast} / {@link #getOnlinePlayerCount}) - real,
 * necessary work, but the next increment after proving the hook itself
 * fires and reaches Python (see docs/ROADMAP.md).
 */
final class UnconnectedGameServer implements GameServer {

    private static final Logger LOG = LoggerFactory.getLogger(Log.MINECRAFT);

    @Override
    public void broadcast(String message) {
        LOG.warn(
                "broadcast(\"{}\") ignored: not yet wired to the live Minecraft server (see docs/ROADMAP.md)",
                message);
    }

    @Override
    public int getOnlinePlayerCount() {
        return 0;
    }
}
