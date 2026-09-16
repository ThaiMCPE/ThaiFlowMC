package dev.thaiflowmc.adapter.sim;

import dev.thaiflowmc.adapter.MinecraftAdapter;
import dev.thaiflowmc.api.Log;
import dev.thaiflowmc.api.event.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Minecraft-free stand-in for {@link MinecraftAdapter}, used until real
 * Minecraft integration lands (see {@code docs/ROADMAP.md}). Lets the
 * loader, event bus, and Python runtime be exercised end-to-end - including
 * a real {@code player_join} event carrying a real {@code Player} - without
 * a Minecraft server.
 */
public final class SimulatedMinecraftAdapter implements MinecraftAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(Log.MINECRAFT);

    private final EventBus eventBus;
    private final SimulatedGameServer server = new SimulatedGameServer();

    public SimulatedMinecraftAdapter(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void start() {
        LOG.info("Server started (simulated)");
        eventBus.fire("server_start", server);
    }

    @Override
    public void stop() {
        eventBus.fire("server_stop", server);
        LOG.info("Server stopped (simulated)");
    }

    /** Simulates a player connecting, firing {@code player_join} like a real server would. */
    public SimulatedPlayer simulatePlayerJoin(String playerName) {
        SimulatedPlayer player = new SimulatedPlayer(playerName);
        server.playerJoined();
        eventBus.fire("player_join", player);
        return player;
    }

    /** Simulates a player disconnecting, firing {@code player_leave}. */
    public void simulatePlayerLeave(SimulatedPlayer player) {
        server.playerLeft();
        eventBus.fire("player_leave", player);
    }
}
