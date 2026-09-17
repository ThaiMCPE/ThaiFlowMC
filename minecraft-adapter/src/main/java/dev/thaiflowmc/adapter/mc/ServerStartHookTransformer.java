package dev.thaiflowmc.adapter.mc;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * Finds Minecraft's dedicated-server "finished starting" log line and
 * injects a call to {@link MinecraftHooks#fireServerStarted()} right after
 * it - using plain ASM bytecode rewriting via a {@code java.lang.instrument}
 * agent, not Mixin, and not Fabric/Forge/NeoForge/Quilt.
 *
 * <p>This was verified by hand against the official Mojang server jars for
 * both 26.3 (ThaiFlowMC's primary target) and 1.21.1 (see docs/ROADMAP.md
 * for both): the log line lives in
 * {@code net.minecraft.server.dedicated.DedicatedServer.initServer()} in
 * both. 1.21.1 obfuscates that to class {@code apn}, method {@code e()};
 * 26.3 ships this class under its real name with no obfuscation at all
 * (Mojang has stopped publishing official mappings as of this version,
 * consistent with there being nothing left to map). Rather than hardcoding
 * either name - which is version-specific and would silently stop matching
 * on the next update, obfuscated or not - this transformer looks for the
 * actual log message string constant ({@value #MARKER}), which Mojang has
 * kept stable across both versions checked and is far less likely to
 * silently change than a class or method identifier. {@link
 * ServerStopHookTransformer} applies the same idea to {@code server_stop};
 * both share their actual injection logic via {@link MarkerHookTransformer}.
 */
public final class ServerStartHookTransformer implements ClassFileTransformer {

    static final String MARKER = "Done (";

    private final MarkerHookTransformer delegate = new MarkerHookTransformer(
            MARKER, "dev/thaiflowmc/adapter/mc/MinecraftHooks", "fireServerStarted", "()V");

    @Override
    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer) {
        return delegate.transform(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
    }

    /**
     * Pure transform: returns patched bytecode with the hook call injected,
     * or {@code classBytes} itself, unchanged, if no injection site was found.
     */
    public byte[] transform(byte[] classBytes) {
        return delegate.transform(classBytes);
    }
}
