package dev.thaiflowmc.adapter.mc.fixture;

import java.util.ArrayList;
import java.util.List;

/**
 * Stands in for the shape of real Minecraft's {@code
 * MinecraftServer.stopServer()}: log a "Stopping server" message with a
 * single-argument {@code Logger.info(String)} call (simpler than {@code
 * server_start}'s two-argument shape), then continue - exactly what {@code
 * ServerStopHookTransformerTest} verifies gets a hook call injected right
 * after.
 */
public final class StoppingMessageFixture {

    public static final List<String> RECORDED = new ArrayList<>();

    public static boolean run() {
        record("Stopping server");
        return true;
    }

    private static void record(String message) {
        RECORDED.add(message);
    }

    private StoppingMessageFixture() {
    }
}
