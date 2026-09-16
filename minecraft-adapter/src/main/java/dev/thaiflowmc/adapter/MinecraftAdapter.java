package dev.thaiflowmc.adapter;

/**
 * Boundary between ThaiFlowMC and one specific Minecraft version.
 *
 * <p>Every class in this module is the only place allowed to reference
 * Minecraft internals; the loader, api, and python-runtime modules never
 * do. A Minecraft update should only ever require a new implementation of
 * this interface (and its supporting classes), never a change to mods.
 *
 * <p>{@link dev.thaiflowmc.adapter.sim.SimulatedMinecraftAdapter} is the
 * only implementation that exists today - see {@code docs/ROADMAP.md} for
 * why real Minecraft integration is not yet implemented and what it needs.
 */
public interface MinecraftAdapter {

    /** Starts (or attaches to) the Minecraft server and fires {@code "server_start"}. */
    void start();

    /** Stops the Minecraft server and fires {@code "server_stop"}. */
    void stop();
}
