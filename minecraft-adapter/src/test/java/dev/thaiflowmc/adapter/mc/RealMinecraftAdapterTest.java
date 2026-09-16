package dev.thaiflowmc.adapter.mc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.thaiflowmc.api.event.SimpleEventBus;
import dev.thaiflowmc.api.server.GameServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RealMinecraftAdapterTest {

    @TempDir
    Path serverDirectory;

    @AfterEach
    void resetHook() {
        MinecraftHooks.setOnServerStarted(null);
    }

    @Test
    void startWiresTheHookToServerStartOnTheEventBus() {
        SimpleEventBus eventBus = new SimpleEventBus();
        List<GameServer> received = new ArrayList<>();
        eventBus.subscribe("server_start", payload -> received.add((GameServer) payload));

        new RealMinecraftAdapter(eventBus).start();
        MinecraftHooks.fireServerStarted();

        assertEquals(1, received.size());
    }

    @Test
    void stopDisconnectsTheHook() {
        SimpleEventBus eventBus = new SimpleEventBus();
        List<GameServer> received = new ArrayList<>();
        eventBus.subscribe("server_start", payload -> received.add((GameServer) payload));

        RealMinecraftAdapter adapter = new RealMinecraftAdapter(eventBus);
        adapter.start();
        adapter.stop();
        MinecraftHooks.fireServerStarted();

        assertTrue(received.isEmpty());
    }

    @Test
    void eulaIsNotAcceptedWhenFileIsMissing() {
        assertFalse(RealMinecraftAdapter.isEulaAccepted(serverDirectory));
    }

    @Test
    void eulaIsNotAcceptedWhenFalse() throws IOException {
        Files.writeString(serverDirectory.resolve("eula.txt"), "eula=false\n");
        assertFalse(RealMinecraftAdapter.isEulaAccepted(serverDirectory));
    }

    @Test
    void eulaIsAcceptedWhenTrue() throws IOException {
        Files.writeString(serverDirectory.resolve("eula.txt"), "#comment\neula=true\n");
        assertTrue(RealMinecraftAdapter.isEulaAccepted(serverDirectory));
    }

    @Test
    void thaiFlowMcNeverWritesTheEulaFileItself() {
        // isEulaAccepted must be read-only - this is the one legal/consent
        // boundary ThaiFlowMC does not cross automatically.
        RealMinecraftAdapter.isEulaAccepted(serverDirectory);
        assertFalse(Files.exists(serverDirectory.resolve("eula.txt")));
    }
}
