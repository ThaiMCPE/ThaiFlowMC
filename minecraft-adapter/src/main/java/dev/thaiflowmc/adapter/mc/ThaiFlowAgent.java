package dev.thaiflowmc.adapter.mc;

import java.lang.instrument.Instrumentation;

/**
 * The {@code java.lang.instrument} entry point that installs {@link
 * ServerStartHookTransformer}. This is ThaiFlowMC's own loader mechanism for
 * hooking real Minecraft - a plain Java agent plus ASM bytecode rewriting -
 * with no dependency on Fabric Loader, Forge, NeoForge, or Quilt.
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
    }

    /** Called by the JVM when this jar is attached to an already-running process. */
    public static void agentmain(String agentArgs, Instrumentation instrumentation) {
        premain(agentArgs, instrumentation);
    }
}
