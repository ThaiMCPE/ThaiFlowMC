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
 * Generic engine behind {@link ServerStartHookTransformer} and {@link
 * ServerStopHookTransformer}: finds any class containing a given log
 * message string constant and injects a call to a fixed static hook method
 * right after the very next method call following that constant's load.
 *
 * <p>See {@link ServerStartHookTransformer}'s Javadoc for why matching on
 * the log message text - rather than a class or method name, which changes
 * (obfuscated or not) between Minecraft versions - is the deliberate design
 * here, confirmed to transfer unchanged across two real Minecraft versions
 * for {@code server_start}.
 */
final class MarkerHookTransformer implements ClassFileTransformer {

    private final String marker;
    private final String hookOwner;
    private final String hookName;
    private final String hookDescriptor;

    MarkerHookTransformer(String marker, String hookOwner, String hookName, String hookDescriptor) {
        this.marker = marker;
        this.hookOwner = hookOwner;
        this.hookName = hookName;
        this.hookDescriptor = hookDescriptor;
    }

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
            byte[] patched = transform(classfileBuffer, loader);
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
    byte[] transform(byte[] classBytes) {
        return transform(classBytes, null);
    }

    /**
     * Same as {@link #transform(byte[])}, but resolves common superclasses
     * (needed for {@code COMPUTE_FRAMES}) using {@code targetClassLoader} -
     * the classloader that's actually defining the class being patched -
     * rather than {@code ClassWriter}'s default of "whatever classloader
     * loaded ASM itself".
     *
     * <p>This matters in practice, not just in theory: Mojang's server
     * bundler defines Minecraft's classes through its own isolated {@code
     * URLClassLoader} (see {@link ThaiFlowAgent}'s Javadoc), which is not
     * the classloader that loaded this agent/ASM. Without this override,
     * {@code ClassWriter} tries to resolve a Minecraft-internal type via the
     * *agent's* classloader - which was never going to have it - and throws
     * {@code TypeNotPresentException}. This didn't surface against {@code
     * DedicatedServer} (that method's specific control flow never happened
     * to need a stack-map-frame merge involving an external type), but did
     * against the larger {@code MinecraftServer} while building the {@code
     * server_stop} hook - a latent bug in the original {@code
     * server_start}-only version of this code, fixed here for both.
     */
    byte[] transform(byte[] classBytes, ClassLoader targetClassLoader) {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected ClassLoader getClassLoader() {
                return targetClassLoader != null ? targetClassLoader : super.getClassLoader();
            }
        };
        HookInjectingVisitor visitor = new HookInjectingVisitor(writer);
        reader.accept(visitor, 0);
        return visitor.injected ? writer.toByteArray() : classBytes;
    }

    private boolean containsMarkerBytes(byte[] classBytes) {
        byte[] needle = marker.getBytes(StandardCharsets.UTF_8);
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

    private final class HookInjectingVisitor extends ClassVisitor {
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
     * Arms itself on any {@code LDC} of a string containing {@link #marker},
     * then injects the hook call immediately after the very next method
     * call - which, for the log lines this targets, is the logging call
     * itself, always void-returning, so the operand stack is empty and
     * safe to call into.
     */
    private final class HookInjectingMethodVisitor extends MethodVisitor {
        private final HookInjectingVisitor container;
        private boolean armed;

        HookInjectingMethodVisitor(MethodVisitor mv, HookInjectingVisitor container) {
            super(Opcodes.ASM9, mv);
            this.container = container;
        }

        @Override
        public void visitLdcInsn(Object value) {
            super.visitLdcInsn(value);
            if (value instanceof String text && text.contains(marker)) {
                armed = true;
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            if (armed) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, hookOwner, hookName, hookDescriptor, false);
                armed = false;
                container.injected = true;
            }
        }
    }
}
