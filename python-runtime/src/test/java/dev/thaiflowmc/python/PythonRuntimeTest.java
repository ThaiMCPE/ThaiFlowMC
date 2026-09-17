package dev.thaiflowmc.python;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.thaiflowmc.api.ModHandle;
import dev.thaiflowmc.api.entity.Player;
import dev.thaiflowmc.api.event.SimpleEventBus;
import dev.thaiflowmc.api.item.ItemDefinition;
import dev.thaiflowmc.api.item.ItemRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end tests of the vertical slice this module exists to prove:
 * a Python script executes, registers callbacks, and those callbacks run
 * when Java fires an event - all without leaking Minecraft or GraalPy
 * details to the script itself.
 */
class PythonRuntimeTest {

    @TempDir
    Path modDir;

    private ItemRegistry itemRegistry;
    private PythonRuntime runtime;
    private SimpleEventBus eventBus;

    @BeforeEach
    void setUp() {
        itemRegistry = new ItemRegistry();
        runtime = new PythonRuntime(itemRegistry);
        eventBus = new SimpleEventBus();
    }

    @AfterEach
    void tearDown() {
        runtime.close();
    }

    @Test
    void executesMainPyAndDeliversAJavaFiredEventToPython() throws Exception {
        writeMain(
                """
                from thaiflow import *

                @event("test")
                def on_test(payload):
                    payload.record("received")
                """);

        runtime.execute(handle(), eventBus);

        Recorder recorder = new Recorder();
        eventBus.fire("test", recorder);

        assertEquals(List.of("received"), recorder.entries);
    }

    @Test
    void loadCallbackRunsOnceAfterScriptFinishesExecuting() throws Exception {
        writeMain(
                """
                from thaiflow import *

                item("ruby")

                @load
                def loaded():
                    item("sapphire")
                """);

        runtime.execute(handle(), eventBus);

        assertTrue(itemRegistry.find("testmod:ruby").isPresent());
        assertTrue(itemRegistry.find("testmod:sapphire").isPresent());
    }

    @Test
    void itemAutoDetectsATextureFileNextToTheScript() throws Exception {
        Files.writeString(modDir.resolve("ruby.png"), "fake-png-bytes");
        writeMain(
                """
                from thaiflow import *

                item("ruby")
                """);

        runtime.execute(handle(), eventBus);

        ItemDefinition item = itemRegistry.find("testmod:ruby").orElseThrow();
        assertEquals(modDir.resolve("ruby.png").toString(), item.texturePath());
    }

    @Test
    void itemDefaultsStackSizeTo64() throws Exception {
        writeMain(
                """
                from thaiflow import *

                item("ruby")
                """);

        runtime.execute(handle(), eventBus);

        assertEquals(64, itemRegistry.find("testmod:ruby").orElseThrow().stackSize());
    }

    @Test
    void playerJoinEventExposesAPythonFriendlyPlayer() throws Exception {
        writeMain(
                """
                from thaiflow import *

                @player_join
                def on_join(player):
                    player.say("hi " + player.name)
                """);
        runtime.execute(handle(), eventBus);

        List<String> said = new ArrayList<>();
        Player player = new Player() {
            @Override
            public String getName() {
                return "Steve";
            }

            @Override
            public double getHealth() {
                return 20;
            }

            @Override
            public void say(String message) {
                said.add(message);
            }

            @Override
            public void teleport(double x, double y, double z) {
                // not exercised by this test
            }
        };

        eventBus.fire("player_join", player);

        assertEquals(List.of("hi Steve"), said);
    }

    @Test
    void joinIsAnAliasForPlayerJoin() throws Exception {
        writeMain(
                """
                from thaiflow import *

                @join
                def on_join(player):
                    player.say("Hi!")
                """);
        runtime.execute(handle(), eventBus);

        List<String> said = new ArrayList<>();
        Player player = simplePlayer(said);

        eventBus.fire("player_join", player);

        assertEquals(List.of("Hi!"), said);
    }

    @Test
    void pythonExceptionDuringLoadIsSurfacedAsAFriendlyError() throws Exception {
        writeMain(
                """
                from thaiflow import *

                def broken():
                    return 1 / 0

                broken()
                """);

        PythonScriptException e =
                assertThrows(PythonScriptException.class, () -> runtime.execute(handle(), eventBus));
        assertTrue(e.friendlyDetail().contains("main.py"), e.friendlyDetail());
        assertTrue(e.friendlyDetail().contains("ZeroDivisionError"), e.friendlyDetail());
    }

    @Test
    void exceptionInsideAnEventHandlerIsReportedWithoutCrashingTheProcess() throws Exception {
        writeMain(
                """
                from thaiflow import *

                @event("test")
                def on_test(payload):
                    payload.sya("typo")
                """);
        runtime.execute(handle(), eventBus);

        List<Throwable> failures = new ArrayList<>();
        eventBus.setFailureListener((name, error) -> failures.add(error));

        eventBus.fire("test", new ArrayList<String>());

        assertEquals(1, failures.size());
        assertTrue(failures.get(0) instanceof PythonScriptException);
        assertTrue(((PythonScriptException) failures.get(0)).friendlyDetail().contains("AttributeError"));
    }

    private Player simplePlayer(List<String> said) {
        return new Player() {
            @Override
            public String getName() {
                return "Steve";
            }

            @Override
            public double getHealth() {
                return 20;
            }

            @Override
            public void say(String message) {
                said.add(message);
            }

            @Override
            public void teleport(double x, double y, double z) {
                // not exercised by this test
            }
        };
    }

    private void writeMain(String source) throws IOException {
        Files.writeString(modDir.resolve("main.py"), source);
    }

    private ModHandle handle() {
        return new ModHandle("testmod", modDir, "main.py");
    }
}
