package dev.thaiflowmc.adapter.mc;

import java.lang.instrument.ClassFileTransformer;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Finds Minecraft's dedicated-server "finished starting" log line and
 * injects a call to {@link MinecraftHooks#fireServerStarted()} right after
 * it - using plain ASM bytecode rewriting via a {@code java.lang.instrument}
 * agent, not Mixin, and not Fabric/Forge/NeoForge/Quilt.
 *
 * <p>This was verified by hand against the official Mojang 1.21.1 server
 * jar (see docs/ROADMAP.md): the log line lives in
 * {@code net.minecraft.server.dedicated.DedicatedServer.initServer()}
 * (obfuscated to class {@code apn}, method {@code e()} in that build).
 * Rather than hardcoding that obfuscated name - which is different for
 * every Minecraft version and would silently stop matching on an update -
 * this transformer looks for the actual log message string constant
 * ({@value #MARKER}), which Mojang has kept stable across many versions and
 * is far less likely to silently change than an obfuscated identifier.
 */
public final class ServerStartHookTransformer implements ClassFileTransformer {

    static final String MARKER = "Done (";

    @Override
    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer) {
        if (!containsMarkerBytes(classfileBuffer)) {
            return null; // tell the JVM to leave this class alone
        }
        try {
            byte[] patched = transform(classfileBuffer);
            return patched == classfileBuffer ? null : patched;
        } catch (RuntimeException e) {
            // A hook we can't install must never bring down class loading -
            // and therefore the whole server - so fail open here.
            return null;
        }
    }

    /**
     * Pure transform: returns patched bytecode with the hook call injected,
     * or {@code classBytes} itself, unchanged, if no injection site was found.
     */
    public byte[] transform(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
        HookInjectingVisitor visitor = new HookInjectingVisitor(writer);
        reader.accept(visitor, 0);
        return visitor.injected ? writer.toByteArray() : classBytes;
    }

    private static boolean containsMarkerBytes(byte[] classBytes) {
        byte[] needle = MARKER.getBytes(StandardCharsets.UTF_8);
        outer:
        for (int i = 0; i <= classBytes.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (classBytes[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static final class HookInjectingVisitor extends ClassVisitor {
        boolean injected;

        HookInjectingVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor parent = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new HookInjectingMethodVisitor(parent, this);
        }
    }

    /**
     * Arms itself on any {@code LDC} of a string containing {@link #MARKER},
     * then injects the hook call immediately after the very next method
     * call - which, for the log line this targets, is the logging call
     * itself (e.g. {@code Logger.info(String, Object)}), always void-
     * returning, so the operand stack is empty and safe to call into.
     */
    private static final class HookInjectingMethodVisitor extends MethodVisitor {
        private final HookInjectingVisitor container;
        private boolean armed;

        HookInjectingMethodVisitor(MethodVisitor mv, HookInjectingVisitor container) {
            super(Opcodes.ASM9, mv);
            this.container = container;
        }

        @Override
        public void visitLdcInsn(Object value) {
            super.visitLdcInsn(value);
            if (value instanceof String text && text.contains(MARKER)) {
                armed = true;
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            if (armed) {
                super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "dev/thaiflowmc/adapter/mc/MinecraftHooks",
                        "fireServerStarted",
                        "()V",
                        false);
                armed = false;
                container.injected = true;
            }
        }
    }
}
