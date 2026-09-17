package dev.thaiflowmc.adapter.mc;

/**
 * The stable, un-obfuscated call targets that {@link ServerStartHookTransformer}
 * and {@link ServerStopHookTransformer} inject calls to inside real
 * Minecraft's own bytecode.
 *
 * <p>Minecraft's internal class and method names are obfuscated and change
 * between versions (see {@code docs/ROADMAP.md}), so the injected bytecode
 * can never call something as specific as "ThaiFlowMC's event bus" directly -
 * that binding would break on every Minecraft update. Instead, the injected
 * calls always target these fixed, versionless methods; {@link
 * dev.thaiflowmc.adapter.mc.RealMinecraftAdapter} is what connects them to
 * the actual {@link dev.thaiflowmc.api.event.EventBus} at runtime. A
 * Minecraft update should only ever require re-verifying where to inject
 * the calls to {@link #fireServerStarted()} / {@link #fireServerStopping()},
 * never changing this class.
 *
 * <p><b>Why callbacks live in {@code System.getProperties()} instead of an
 * ordinary static field:</b> this class gets loaded twice, by two different
 * classloaders that never resolve to the same {@code Class} object - {@link
 * RealMinecraftAdapter} (loaded by the application classloader) registers
 * the callback, but the bytecode {@link ServerStartHookTransformer}/{@link
 * ServerStopHookTransformer} inject runs inside Mojang's bundler, which
 * loads the game through its own isolated {@code URLClassLoader}. Even
 * after appending this class to the bootstrap classloader's search path (so
 * both sides can at least find *a* {@code MinecraftHooks} to call), this
 * was verified live to still produce two distinct class definitions with
 * two independent static fields - a genuinely surprising, JPMS-related
 * classloading interaction, not a naming or timing bug. {@code
 * System.getProperties()} is the one place guaranteed to be the exact same
 * object for the entire JVM regardless of how many times this class itself
 * gets defined, since {@code java.lang.System} is always resolved
 * identically everywhere. Calling {@code .run()} on the stored {@link
 * Runnable} works fine across that class-identity split: method dispatch
 * only needs the caller to resolve {@code Runnable} itself (a JDK type,
 * resolved identically everywhere), not the callee's concrete class.
 */
public final class MinecraftHooks {

    private static final String STARTED_CALLBACK_PROPERTY = "dev.thaiflowmc.adapter.mc.onServerStarted";
    private static final String STOPPING_CALLBACK_PROPERTY = "dev.thaiflowmc.adapter.mc.onServerStopping";

    private MinecraftHooks() {
    }

    /** Registers what runs when real Minecraft signals it has finished starting. */
    public static void setOnServerStarted(Runnable callback) {
        setCallback(STARTED_CALLBACK_PROPERTY, callback);
    }

    /**
     * Called from bytecode {@link ServerStartHookTransformer} injects into
     * Minecraft's dedicated server startup path. Keep this method's name and
     * signature stable - it is the one thing tying injected bytecode back to
     * ThaiFlowMC.
     */
    public static void fireServerStarted() {
        runCallback(STARTED_CALLBACK_PROPERTY);
    }

    /** Registers what runs when real Minecraft signals it is about to stop. */
    public static void setOnServerStopping(Runnable callback) {
        setCallback(STOPPING_CALLBACK_PROPERTY, callback);
    }

    /**
     * Called from bytecode {@link ServerStopHookTransformer} injects into
     * Minecraft's server shutdown path. Keep this method's name and
     * signature stable, for the same reason as {@link #fireServerStarted()}.
     */
    public static void fireServerStopping() {
        runCallback(STOPPING_CALLBACK_PROPERTY);
    }

    private static void setCallback(String property, Runnable callback) {
        if (callback == null) {
            System.getProperties().remove(property);
        } else {
            System.getProperties().put(property, callback);
        }
    }

    private static void runCallback(String property) {
        Object callback = System.getProperties().get(property);
        if (callback instanceof Runnable runnable) {
            runnable.run();
        }
    }
}
