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
 *
 * <p><b>Why the callback lives in {@code System.getProperties()} instead of
 * an ordinary static field:</b> this class gets loaded twice, by two
 * different classloaders that never resolve to the same {@code Class}
 * object - {@link RealMinecraftAdapter} (loaded by the application
 * classloader) registers the callback, but the bytecode {@link
 * ServerStartHookTransformer} injects runs inside Mojang's bundler, which
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

    private static final String CALLBACK_PROPERTY = "dev.thaiflowmc.adapter.mc.onServerStarted";

    private MinecraftHooks() {
    }

    /** Registers what runs when real Minecraft signals it has finished starting. */
    public static void setOnServerStarted(Runnable callback) {
        if (callback == null) {
            System.getProperties().remove(CALLBACK_PROPERTY);
        } else {
            System.getProperties().put(CALLBACK_PROPERTY, callback);
        }
    }

    /**
     * Called from bytecode {@link ServerStartHookTransformer} injects into
     * Minecraft's dedicated server startup path. Keep this method's name and
     * signature stable - it is the one thing tying injected bytecode back to
     * ThaiFlowMC.
     */
    public static void fireServerStarted() {
        Object callback = System.getProperties().get(CALLBACK_PROPERTY);
        if (callback instanceof Runnable runnable) {
            runnable.run();
        }
    }
}
