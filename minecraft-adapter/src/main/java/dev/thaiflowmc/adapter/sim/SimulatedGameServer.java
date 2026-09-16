package dev.thaiflowmc.adapter.sim;

import dev.thaiflowmc.api.Log;
import dev.thaiflowmc.api.server.GameServer;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SimulatedGameServer implements GameServer {

    private static final Logger LOG = LoggerFactory.getLogger(Log.MINECRAFT);

    private final AtomicInteger onlinePlayers = new AtomicInteger();

    @Override
    public void broadcast(String message) {
        LOG.info("[broadcast] {}", message);
    }

    @Override
    public int getOnlinePlayerCount() {
        return onlinePlayers.get();
    }

    void playerJoined() {
        onlinePlayers.incrementAndGet();
    }

    void playerLeft() {
        onlinePlayers.decrementAndGet();
    }
}
