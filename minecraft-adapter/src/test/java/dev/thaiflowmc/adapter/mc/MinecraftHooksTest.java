package dev.thaiflowmc.adapter.mc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MinecraftHooksTest {

    @AfterEach
    void resetHook() {
        MinecraftHooks.setOnServerStarted(null);
    }

    @Test
    void fireServerStartedRunsTheRegisteredCallback() {
        AtomicInteger calls = new AtomicInteger();
        MinecraftHooks.setOnServerStarted(calls::incrementAndGet);

        MinecraftHooks.fireServerStarted();

        assertEquals(1, calls.get());
    }

    @Test
    void fireServerStartedIsANoopWithNoCallbackRegistered() {
        MinecraftHooks.fireServerStarted(); // must not throw
    }

    @Test
    void nullClearsThePreviouslyRegisteredCallback() {
        AtomicInteger calls = new AtomicInteger();
        MinecraftHooks.setOnServerStarted(calls::incrementAndGet);
        MinecraftHooks.setOnServerStarted(null);

        MinecraftHooks.fireServerStarted();

        assertEquals(0, calls.get());
    }

    /**
     * Regression test for a real bug hit during live 26.3 integration
     * testing: Mojang's server bundler loads the actual game through its own
     * isolated {@code URLClassLoader}, which ends up defining a *second*,
     * independent {@code MinecraftHooks} class - even after making the class
     * resolvable from that loader via {@code appendToBootstrapClassLoaderSearch}.
     * A callback registered through the "normal" class silently never fired
     * when {@code fireServerStarted()} was invoked through that duplicate
     * definition, because each held its own static field. This test
     * reproduces the duplicate-definition half of that scenario directly (no
     * agent or Minecraft needed) and proves {@link MinecraftHooks} survives
     * it by keeping its state in {@code System.getProperties()} rather than
     * a static field.
     */
    @Test
    void callbackFiresEvenWhenInvokedThroughAnIndependentlyLoadedDuplicateClass() throws Exception {
        URL classesLocation =
                MinecraftHooks.class.getProtectionDomain().getCodeSource().getLocation();
        URLClassLoader duplicateLoader = new URLClassLoader(new URL[] {classesLocation}, null);
        Class<?> duplicateHooksClass = duplicateLoader.loadClass(MinecraftHooks.class.getName());
        assertNotSame(
                MinecraftHooks.class,
                duplicateHooksClass,
                "test setup bug: expected an independently loaded duplicate class, not the same one");

        AtomicInteger calls = new AtomicInteger();
        MinecraftHooks.setOnServerStarted(calls::incrementAndGet);

        Method duplicateFireServerStarted = duplicateHooksClass.getMethod("fireServerStarted");
        duplicateFireServerStarted.invoke(null);

        assertEquals(1, calls.get());
        duplicateLoader.close();
    }
}
