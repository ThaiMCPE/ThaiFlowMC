package dev.thaiflowmc.adapter.mc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.thaiflowmc.adapter.mc.fixture.DoneMessageFixture;
import dev.thaiflowmc.adapter.mc.fixture.NoMarkerFixture;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;

/**
 * Proves {@link ServerStartHookTransformer} actually works: real bytecode
 * goes in, patched bytecode comes out, and running the patched class fires
 * {@link MinecraftHooks}. The exact real-Minecraft injection site (the
 * "Done (...)" log line in {@code DedicatedServer.initServer()}) was
 * verified by hand against the official server jars for both 26.3
 * (ThaiFlowMC's primary target) and 1.21.1 - see docs/ROADMAP.md - so this
 * test uses a small fixture matching that shape instead of depending on a
 * network download in the test suite.
 */
class ServerStartHookTransformerTest {

    private final ServerStartHookTransformer transformer = new ServerStartHookTransformer();

    @AfterEach
    void resetHook() {
        MinecraftHooks.setOnServerStarted(null);
        DoneMessageFixture.RECORDED.clear();
    }

    @Test
    void injectsAHookCallRightAfterTheDoneLogLine() throws Exception {
        byte[] original = readClassBytes(DoneMessageFixture.class);
        byte[] patched = transformer.transform(original);

        assertFalse(java.util.Arrays.equals(original, patched), "expected the bytecode to change");
        assertBytecodeIsVerifiable(patched);

        AtomicInteger hookCalls = new AtomicInteger();
        MinecraftHooks.setOnServerStarted(hookCalls::incrementAndGet);

        Class<?> patchedClass =
                new ByteArrayClassLoader(getClass().getClassLoader()).define(DoneMessageFixture.class.getName(), patched);
        Method run = patchedClass.getMethod("run");

        Object result = run.invoke(null);

        assertEquals(Boolean.TRUE, result);
        assertEquals(1, hookCalls.get(), "hook should fire exactly once");
    }

    @Test
    void originalUnpatchedClassNeverFiresTheHook() {
        AtomicInteger hookCalls = new AtomicInteger();
        MinecraftHooks.setOnServerStarted(hookCalls::incrementAndGet);

        boolean result = DoneMessageFixture.run();

        assertTrue(result);
        assertEquals(0, hookCalls.get(), "unpatched code must never call the hook");
    }

    @Test
    void classesWithoutTheMarkerAreLeftByteForByteUnchanged() throws IOException {
        byte[] original = readClassBytes(NoMarkerFixture.class);

        byte[] result = transformer.transform(original);

        assertArrayEquals(original, result);
    }

    @Test
    void instrumentationApiSkipsClassesWithoutTheMarker() throws IOException {
        byte[] original = readClassBytes(NoMarkerFixture.class);

        byte[] result = transformer.transform(null, "NoMarkerFixture", null, null, original);

        assertEquals(null, result, "returning null tells the JVM to leave the class untouched");
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
