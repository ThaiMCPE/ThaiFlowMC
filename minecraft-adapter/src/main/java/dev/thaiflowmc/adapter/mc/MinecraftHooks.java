package dev.thaiflowmc.adapter.mc;

/**
 * The stable, un-obfuscated call target that {@link ServerStartHookTransformer}
 * injects a call to inside real Minecraft's own bytecode.
 *
 * <p>Minecraft's internal class and method names are obfuscated and change
 * between versions (see {@code docs/ROADMAP.md}), so the injected bytecode
 * can never call something as specific as "ThaiFlowMC's event bus" directly -
 * that binding would break on every Minecraft update. Instead, the injected
 * call always targets this one fixed, versionless method; {@link
 * dev.thaiflowmc.adapter.mc.RealMinecraftAdapter} is what connects it to the
 * actual {@link dev.thaiflowmc.api.event.EventBus} at runtime. A Minecraft
 * update should only ever require re-verifying where to inject the call to
 * {@link #fireServerStarted()}, never changing this class.
 */
public final class MinecraftHooks {

    private static volatile Runnable onServerStarted = () -> {};

    private MinecraftHooks() {
    }

    /** Registers what runs when real Minecraft signals it has finished starting. */
    public static void setOnServerStarted(Runnable callback) {
        onServerStarted = callback != null ? callback : () -> {};
    }

    /**
     * Called from bytecode {@link ServerStartHookTransformer} injects into
     * Minecraft's dedicated server startup path. Keep this method's name and
     * signature stable - it is the one thing tying injected bytecode back to
     * ThaiFlowMC.
     */
    public static void fireServerStarted() {
        onServerStarted.run();
    }
}
