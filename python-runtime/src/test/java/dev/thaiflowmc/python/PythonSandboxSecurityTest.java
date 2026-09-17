package dev.thaiflowmc.python;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.thaiflowmc.api.ModHandle;
import dev.thaiflowmc.api.ModPermissions;
import dev.thaiflowmc.api.event.SimpleEventBus;
import dev.thaiflowmc.api.item.ItemRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mods are untrusted by default. Each test here attempts one concrete
 * attack a hostile or merely broken mod might try, and asserts it is
 * blocked - not just "an exception happened somewhere," but specifically
 * that the dangerous operation never took effect. A regression in any of
 * these is a security regression, not a feature change; see "Security" in
 * docs/ARCHITECTURE.md for what these are attempting to enforce and its
 * known limits (this file also documents, rather than fakes coverage for,
 * the one attack this stack genuinely cannot contain today: see the
 * memory-abuse note at the bottom).
 */
class PythonSandboxSecurityTest {

    @TempDir
    Path modDir;

    private ItemRegistry itemRegistry;
    private PythonRuntime runtime;
    private SimpleEventBus eventBus;

    private ItemRegistry itemRegistry() {
        if (itemRegistry == null) {
            itemRegistry = new ItemRegistry();
        }
        return itemRegistry;
    }

    @AfterEach
    void tearDown() {
        if (runtime != null) {
            runtime.close();
        }
    }

    @Test
    void filesystemAccessIsDeniedByDefault() throws Exception {
        writeMain(
                """
                from thaiflow import *

                @load
                def go():
                    open("/etc/passwd")
                """);
        newRuntime();

        PythonScriptException e =
                assertThrows(PythonScriptException.class, () -> runtime.execute(handle(), newEventBus()));
        assertTrue(deniedFilesystemAccess(e), e.friendlyDetail());
    }

    @Test
    void filesystemEscapeFromGrantedStorageIsDenied() throws Exception {
        writeMain(
                """
                from thaiflow import *

                @load
                def go():
                    open("../../../etc/passwd")
                """);
        newRuntime();

        PythonScriptException e = assertThrows(
                PythonScriptException.class,
                () -> runtime.execute(handle(new ModPermissions(true, false, false)), newEventBus()));
        assertTrue(
                e.friendlyDetail().contains("Access outside the mod's storage directory")
                        || deniedFilesystemAccess(e),
                e.friendlyDetail());
    }

    @Test
    void grantedStorageStillWorksForFilesInsideIt() throws Exception {
        // Mirror the real mods/<modId> layout so the storage directory
        // (a sibling of mods/, per PythonRuntime.storageDirectoryFor) lands
        // somewhere predictable to assert on.
        Path modsDir = Files.createDirectory(modDir.resolve("mods"));
        Path thisModDir = Files.createDirectory(modsDir.resolve("testmod"));
        Files.writeString(
                thisModDir.resolve("main.py"),
                """
                from thaiflow import *

                @load
                def go():
                    with open("hello.txt", "w") as f:
                        f.write("hi")
                """);
        newRuntime();

        ModHandle handle = new ModHandle("testmod", thisModDir, "main.py", new ModPermissions(true, false, false));
        runtime.execute(handle, newEventBus());

        Path storageFile = modDir.resolve("mod_data").resolve("testmod").resolve("hello.txt");
        assertTrue(Files.exists(storageFile), "expected " + storageFile + " to exist");
        assertEquals("hi", Files.readString(storageFile));
    }

    private boolean deniedFilesystemAccess(PythonScriptException e) {
        String detail = e.friendlyDetail();
        return detail.contains("IOError")
                || detail.contains("OSError")
                || detail.contains("PermissionError")
                || detail.contains("not permitted");
    }

    @Test
    void networkAccessIsDeniedByDefault() throws Exception {
        writeMain(
                """
                from thaiflow import *

                @load
                def go():
                    import socket
                    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                    s.connect(("example.com", 80))
                """);
        newRuntime();

        // Denied one way or another (socket access, or DNS via denied
        // network/env access) - either way, no connection is made.
        assertThrows(PythonScriptException.class, () -> runtime.execute(handle(), newEventBus()));
    }

    @Test
    void subprocessCreationIsDenied() throws Exception {
        writeMain(
                """
                from thaiflow import *

                @load
                def go():
                    import subprocess
                    subprocess.run(["echo", "hi"])
                """);
        newRuntime();

        assertThrows(PythonScriptException.class, () -> runtime.execute(handle(), newEventBus()));
    }

    @Test
    void javaHostClassLookupIsDenied() throws Exception {
        writeMain(
                """
                from thaiflow import *
                import java

                @load
                def go():
                    java.type("java.lang.Runtime")
                """);
        newRuntime();

        assertThrows(PythonScriptException.class, () -> runtime.execute(handle(), newEventBus()));
    }

    @Test
    void reflectionOnAnUnexportedHostObjectIsDenied() throws Exception {
        // Recorder has exactly one exported method (`record`). Nothing else
        // on it - including inherited Object methods like getClass(),
        // which would otherwise hand back a live java.lang.Class usable to
        // reach arbitrary further reflection - should be callable from
        // Python.
        writeMain(
                """
                from thaiflow import *

                @event("test")
                def on_test(payload):
                    payload.getClass()
                """);
        newRuntime();
        SimpleEventBus bus = newEventBus();
        runtime.execute(handle(), bus);

        java.util.List<Throwable> failures = new java.util.ArrayList<>();
        bus.setFailureListener((name, error) -> failures.add(error));
        bus.fire("test", new Recorder());

        assertEquals(1, failures.size());
        assertTrue(failures.get(0) instanceof PythonScriptException);
    }

    @Test
    void environmentVariableAccessIsDenied() throws Exception {
        writeMain(
                """
                from thaiflow import *

                @load
                def go():
                    import os
                    if os.environ.get("PATH") is not None:
                        raise AssertionError("expected no environment access")
                """);
        newRuntime();

        // Must not throw AssertionError - i.e. os.environ must appear empty,
        // not actually leak the real environment.
        runtime.execute(handle(), newEventBus());
    }

    @Test
    @Timeout(15)
    void anInfiniteLoopIsStoppedRatherThanHangingForever() throws Exception {
        writeMain(
                """
                from thaiflow import *

                @load
                def go():
                    while True:
                        pass
                """);
        // A short timeout so this test doesn't have to wait out production's
        // (deliberately generous, for legitimate mod startup work) 30s budget.
        runtime = new PythonRuntime(itemRegistry(), Duration.ofSeconds(2));

        PythonScriptException e =
                assertThrows(PythonScriptException.class, () -> runtime.execute(handle(), newEventBus()));
        assertTrue(e.friendlyDetail().toLowerCase().contains("too long"), e.friendlyDetail());
    }

    @Test
    void excessiveOutputIsCappedRatherThanUnbounded() throws Exception {
        writeMain(
                """
                from thaiflow import *

                @load
                def go():
                    chunk = "x" * 1000
                    for _ in range(10000):
                        print(chunk)
                """);
        newRuntime();

        // Must fail (the output cap trips) rather than silently succeeding
        // after writing ~10MB.
        assertThrows(PythonScriptException.class, () -> runtime.execute(handle(), newEventBus()));
    }

    @Test
    void modsCannotSeeEachOthersGlobalState() throws Exception {
        Path modADir = Files.createDirectory(modDir.resolve("mod_a"));
        Files.writeString(
                modADir.resolve("main.py"),
                """
                from thaiflow import *
                secret = "mod-a-secret"
                """);
        Path modBDir = Files.createDirectory(modDir.resolve("mod_b"));
        Files.writeString(
                modBDir.resolve("main.py"),
                """
                from thaiflow import *

                @load
                def go():
                    if "secret" in dir() or "secret" in globals():
                        raise AssertionError("mod_b can see mod_a's global state")
                """);
        newRuntime();
        SimpleEventBus bus = newEventBus();

        runtime.execute(new ModHandle("mod_a", modADir, "main.py"), bus);
        runtime.execute(new ModHandle("mod_b", modBDir, "main.py"), bus); // must not throw
    }

    /**
     * Honest gap, not a passing test: GraalVM's {@code SandboxPolicy} -
     * which would offer real, isolate-based heap limits - refuses to
     * validate for the {@code python} language at all (confirmed
     * empirically; see docs/ARCHITECTURE.md). Without it, every mod's
     * Python allocations share the host JVM's ordinary heap, and nothing
     * in this stack bounds how much of it one mod can consume. A mod
     * allocating a huge amount of memory is not contained today - see
     * "Future: Strict Isolation Mode" in docs/ROADMAP.md, where an OS
     * process boundary per mod (with real OS-level memory limits) is the
     * planned fix. Writing a test that pretends otherwise would be worse
     * than writing none.
     */
    @Test
    void memoryAbuseIsNotYetContained_knownGap() {
        assertTrue(true, "documented gap - see Javadoc; tracked in docs/ROADMAP.md, not silently ignored");
    }

    private void writeMain(String source) throws IOException {
        Files.writeString(modDir.resolve("main.py"), source);
    }

    private void newRuntime() {
        runtime = new PythonRuntime(itemRegistry());
    }

    private SimpleEventBus newEventBus() {
        if (eventBus == null) {
            eventBus = new SimpleEventBus();
        }
        return eventBus;
    }

    private ModHandle handle() {
        return handle(ModPermissions.DENY_ALL);
    }

    private ModHandle handle(ModPermissions permissions) {
        return new ModHandle("testmod", modDir, "main.py", permissions);
    }
}
