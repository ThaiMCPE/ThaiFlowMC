package dev.thaiflowmc.adapter.mc.fixture;

import java.util.ArrayList;
import java.util.List;

/**
 * Stands in for the shape of real Minecraft's dedicated-server startup
 * method: log a "Done (...)" message, then return - the exact pattern
 * {@code ServerStartHookTransformerTest} verifies gets a hook call injected
 * right after the log call, without needing the real (50MB, network-fetched)
 * Minecraft server jar in the test suite.
 */
public final class DoneMessageFixture {

    public static final List<String> RECORDED = new ArrayList<>();

    public static boolean run() {
        record("Done (1.234s)! For help, type \"help\"");
        return true;
    }

    private static void record(String message) {
        RECORDED.add(message);
    }

    private DoneMessageFixture() {
    }
}
