package dev.thaiflowmc.adapter.mc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.thaiflowmc.adapter.mc.fixture.StoppingMessageFixture;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.util.CheckClassAdapter;

/**
 * Mirrors {@link ServerStartHookTransformerTest}'s rigor for the {@code
 * server_stop} hook: real bytecode in, patched bytecode out, running the
 * patched class fires {@link MinecraftHooks#fireServerStopping()}.
 */
class ServerStopHookTransformerTest {

    private final ServerStopHookTransformer transformer = new ServerStopHookTransformer();

    @AfterEach
    void resetHook() {
        MinecraftHooks.setOnServerStopping(null);
        StoppingMessageFixture.RECORDED.clear();
    }

    @Test
    void injectsAHookCallRightAfterTheStoppingLogLine() throws Exception {
        byte[] original = readClassBytes(StoppingMessageFixture.class);
        byte[] patched = transformer.transform(original);

        assertTrue(!java.util.Arrays.equals(original, patched), "expected the bytecode to change");
        assertBytecodeIsVerifiable(patched);

        AtomicInteger hookCalls = new AtomicInteger();
        MinecraftHooks.setOnServerStopping(hookCalls::incrementAndGet);

        Class<?> patchedClass = new ByteArrayClassLoader(getClass().getClassLoader())
                .define(StoppingMessageFixture.class.getName(), patched);
        Method run = patchedClass.getMethod("run");

        Object result = run.invoke(null);

        assertEquals(Boolean.TRUE, result);
        assertEquals(1, hookCalls.get(), "hook should fire exactly once");
    }

    @Test
    void originalUnpatchedClassNeverFiresTheHook() {
        AtomicInteger hookCalls = new AtomicInteger();
        MinecraftHooks.setOnServerStopping(hookCalls::incrementAndGet);

        boolean result = StoppingMessageFixture.run();

        assertTrue(result);
        assertEquals(0, hookCalls.get(), "unpatched code must never call the hook");
    }

    private static void assertBytecodeIsVerifiable(byte[] classBytes) {
        java.io.StringWriter output = new java.io.StringWriter();
        CheckClassAdapter.verify(new ClassReader(classBytes), false, new java.io.PrintWriter(output));
        assertTrue(output.toString().isEmpty(), "patched bytecode failed verification:\n" + output);
    }

    private static byte[] readClassBytes(Class<?> clazz) throws IOException {
        String resource = clazz.getSimpleName() + ".class";
        try (InputStream in = clazz.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Could not find " + resource + " on the test classpath");
            }
            return in.readAllBytes();
        }
    }

    private static final class ByteArrayClassLoader extends ClassLoader {
        ByteArrayClassLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
