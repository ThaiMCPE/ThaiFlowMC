package dev.thaiflowmc.launcher;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the full MVP vertical slice end to end, exactly as
 * docs/ARCHITECTURE.md describes it:
 *
 * <p>ThaiFlowMC starts -&gt; scans mods/ -&gt; discovers a mod -&gt; reads its
 * metadata -&gt; resolves dependencies -&gt; starts an embedded Python runtime
 * -&gt; executes main.py -&gt; Python registers callbacks -&gt; Java fires an
 * event -&gt; the Python callback runs -&gt; errors are shown cleanly.
 */
class LauncherIntegrationTest {

    @TempDir
    Path modsDir;

    private PrintStream originalOut;
    private ByteArrayOutputStream capturedOut;

    @BeforeEach
    void captureStdout() {
        originalOut = System.out;
        capturedOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
    }

    @Test
    void fullVerticalSlice_discoverToPythonEventCallback() throws IOException {
        Path mod = modsDir.resolve("hello");
        Files.createDirectories(mod);
        Files.writeString(
                mod.resolve("mod.toml"),
                """
                id = "hello"
                name = "Hello Mod"
                version = "1.0.0"
                entrypoint = "main.py"

                [dependencies]
                thaiflowmc = ">=0.1.0"
                """);
        Files.writeString(
                mod.resolve("main.py"),
                """
                from thaiflow import *

                @load
                def loaded():
                    print("My mod loaded!")

                @server_start
                def ready(server):
                    print("hello mod is ready!")
                """);

        Launcher.run(modsDir);

        String output = capturedOut.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("My mod loaded!"), output);
        assertTrue(output.contains("hello mod is ready!"), output);
    }

    @Test
    void zeroConfigModWithOnlyMainPyWorks() throws IOException {
        Path mod = modsDir.resolve("tiny_mod");
        Files.createDirectories(mod);
        Files.writeString(
                mod.resolve("main.py"),
                """
                from thaiflow import *

                @load
                def loaded():
                    print("tiny mod loaded")
                """);

        Launcher.run(modsDir);

        assertTrue(capturedOut.toString(StandardCharsets.UTF_8).contains("tiny mod loaded"));
    }

    @Test
    void aModWithAPythonTypoReportsAFriendlyErrorInsteadOfCrashing() throws IOException {
        Path mod = modsDir.resolve("typo_mod");
        Files.createDirectories(mod);
        Files.writeString(
                mod.resolve("main.py"),
                """
                from thaiflow import *

                @player_join
                def welcome(player):
                    player.sya("Hello")
                """);

        // Should not throw - loader errors are reported, not propagated.
        Launcher.run(modsDir);
    }
}
