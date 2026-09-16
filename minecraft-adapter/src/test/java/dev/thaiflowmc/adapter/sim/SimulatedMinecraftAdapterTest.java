package dev.thaiflowmc.adapter.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.thaiflowmc.api.entity.Player;
import dev.thaiflowmc.api.event.SimpleEventBus;
import dev.thaiflowmc.api.server.GameServer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulatedMinecraftAdapterTest {

    @Test
    void startFiresServerStartWithAGameServer() {
        SimpleEventBus eventBus = new SimpleEventBus();
        List<GameServer> received = new ArrayList<>();
        eventBus.subscribe("server_start", payload -> received.add((GameServer) payload));

        new SimulatedMinecraftAdapter(eventBus).start();

        assertEquals(1, received.size());
    }

    @Test
    void simulatedPlayerJoinFiresPlayerJoinWithAPlayer() {
        SimpleEventBus eventBus = new SimpleEventBus();
        List<Player> joined = new ArrayList<>();
        eventBus.subscribe("player_join", payload -> joined.add((Player) payload));

        SimulatedMinecraftAdapter adapter = new SimulatedMinecraftAdapter(eventBus);
        SimulatedPlayer player = adapter.simulatePlayerJoin("Steve");

        assertEquals(1, joined.size());
        assertEquals("Steve", joined.get(0).getName());
        assertEquals("Steve", player.getName());
    }

    @Test
    void onlinePlayerCountTracksJoinsAndLeaves() {
        SimpleEventBus eventBus = new SimpleEventBus();
        List<GameServer> servers = new ArrayList<>();
        eventBus.subscribe("server_start", payload -> servers.add((GameServer) payload));

        SimulatedMinecraftAdapter adapter = new SimulatedMinecraftAdapter(eventBus);
        adapter.start();
        GameServer server = servers.get(0);
        assertEquals(0, server.getOnlinePlayerCount());

        SimulatedPlayer player = adapter.simulatePlayerJoin("Alex");
        assertEquals(1, server.getOnlinePlayerCount());

        adapter.simulatePlayerLeave(player);
        assertEquals(0, server.getOnlinePlayerCount());
    }

    @Test
    void sayIsLoggedRatherThanThrowing() {
        SimpleEventBus eventBus = new SimpleEventBus();
        SimulatedMinecraftAdapter adapter = new SimulatedMinecraftAdapter(eventBus);
        SimulatedPlayer player = adapter.simulatePlayerJoin("Steve");

        assertTrue(player.getHealth() > 0);
        player.say("hello");
        player.teleport(1, 2, 3);
    }
}
