package dev.thaiflowmc.adapter.mc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * The {@code java.lang.instrument} entry point that installs {@link
 * ServerStartHookTransformer} and {@link ServerStopHookTransformer}. This is
 * ThaiFlowMC's own loader mechanism for hooking real Minecraft - a plain
 * Java agent plus ASM bytecode rewriting - with no dependency on Fabric
 * Loader, Forge, NeoForge, or Quilt.
 *
 * <p>Attach it when launching the real Minecraft dedicated server:
 *
 * <pre>{@code
 * java -javaagent:minecraft-adapter.jar -jar server.jar nogui
 * }</pre>
 *
 * <p>ThaiFlowMC does not launch Minecraft with this attached automatically.
 * Running the dedicated server requires accepting Mojang's EULA (creating
 * {@code eula.txt} with {@code eula=true}), which is the server operator's
 * decision, not something this project makes on anyone's behalf - see
 * docs/ROADMAP.md.
 */
public final class ThaiFlowAgent {

    private ThaiFlowAgent() {
    }

    /** Called by the JVM when this jar is attached with {@code -javaagent} at startup. */
    public static void premain(String agentArgs, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ServerStartHookTransformer());
        instrumentation.addTransformer(new ServerStopHookTransformer());

        // Mojang's own server bundler (net.minecraft.bundler.Main) loads the
        // actual game through a fresh URLClassLoader whose parent deliberately
        // skips the application classloader, to isolate its own library
        // versions from whatever else is on the launching JVM's classpath.
        // That means the bytecode we inject - a call to MinecraftHooks - can't
        // resolve that class through normal delegation.
        //
        // The fix is NOT to append this whole agent jar to the bootstrap
        // classloader's search path: MinecraftHooks would then load via
        // bootstrap, but so would RealMinecraftAdapter (which needs SLF4J)
        // and ServerStartHookTransformer (which needs ASM) - and bootstrap
        // has no access to the application classpath those live on, so
        // *those* classes would themselves fail to load. Instead, extract
        // just MinecraftHooks - it depends on nothing but java.lang.Runnable
        // - into its own tiny jar and append only that. Every classloader in
        // the JVM ultimately delegates to bootstrap, so this one class
        // becomes resolvable everywhere, while everything else in this
        // agent's jar keeps loading normally (and keeps seeing its normal
        // dependencies) via the application classloader.
        appendHooksClassToBootstrapSearch(instrumentation);
    }

    /** Called by the JVM when this jar is attached to an already-running process. */
    public static void agentmain(String agentArgs, Instrumentation instrumentation) {
        premain(agentArgs, instrumentation);
    }

    private static void appendHooksClassToBootstrapSearch(Instrumentation instrumentation) {
        String resourcePath = "/" + MinecraftHooks.class.getName().replace('.', '/') + ".class";
        try {
            byte[] classBytes;
            try (InputStream in = ThaiFlowAgent.class.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    return;
                }
                classBytes = in.readAllBytes();
            }
            File tempJar = File.createTempFile("thaiflowmc-hooks", ".jar");
            tempJar.deleteOnExit();
            try (JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(tempJar))) {
                jarOut.putNextEntry(new JarEntry(resourcePath.substring(1)));
                jarOut.write(classBytes);
                jarOut.closeEntry();
            }
            instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(tempJar));
        } catch (IOException e) {
            // Best-effort: if this fails, the injected hook call simply won't
            // resolve and MinecraftServer logs a NoClassDefFoundError - loud
            // and diagnosable, never silently swallowed.
        }
    }
}
