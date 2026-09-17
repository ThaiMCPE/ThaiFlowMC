package dev.thaiflowmc.adapter.mc;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * Finds Minecraft's server "stopping" log line and injects a call to {@link
 * MinecraftHooks#fireServerStopping()} right after it.
 *
 * <p>Verified by hand against the official Mojang 26.3 server jar (see
 * docs/ROADMAP.md): the log line lives in {@code
 * net.minecraft.server.MinecraftServer.stopServer()} - the real,
 * unobfuscated method name - as a single-argument {@code
 * Logger.info(String)} call (an even simpler shape than {@code
 * server_start}'s {@code Logger.info(String, Object)}). Unlike {@code
 * player_join} (see docs/ROADMAP.md), this hook needs no Minecraft client
 * to trigger - stopping the server is entirely server-side - so it was
 * verified live, the same way {@code server_start} was.
 */
public final class ServerStopHookTransformer implements ClassFileTransformer {

    static final String MARKER = "Stopping server";

    private final MarkerHookTransformer delegate = new MarkerHookTransformer(
            MARKER, "dev/thaiflowmc/adapter/mc/MinecraftHooks", "fireServerStopping", "()V");

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
