package dev.thaiflowmc.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.thaiflowmc.loader.error.DuplicateModIdException;
import dev.thaiflowmc.loader.error.MissingDependencyException;
import dev.thaiflowmc.loader.model.ModContainer;
import dev.thaiflowmc.loader.model.Version;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModManagerTest {

    private final ModManager modManager = new ModManager(Version.parse("0.1.0"));

    @TempDir
    Path modsDir;

    @Test
    void discoversParsesAndOrdersMods() throws IOException {
        writeMod("hello", "id = \"hello\"\n");

        List<ModContainer> containers = modManager.prepare(modsDir);

        assertEquals(1, containers.size());
        assertEquals("hello", containers.get(0).metadata().id());
        assertEquals(ModContainer.State.DISCOVERED, containers.get(0).state());
    }

    @Test
    void twoFoldersWithTheSameIdAreRejected() throws IOException {
        writeMod("hello_a", "id = \"hello\"\n");
        writeMod("hello_b", "id = \"hello\"\n");

        assertThrows(DuplicateModIdException.class, () -> modManager.prepare(modsDir));
    }

    @Test
    void missingDependencyAcrossModsIsReported() throws IOException {
        writeMod(
                "hello",
                """
                id = "hello"

                [dependencies]
                cool_library = ">=2.0.0"
                """);

        MissingDependencyException e =
                assertThrows(MissingDependencyException.class, () -> modManager.prepare(modsDir));
        assertTrue(e.friendlyDetail().contains("cool_library"));
    }

    @Test
    void dependencyOrderIsRespected() throws IOException {
        writeMod(
                "consumer",
                """
                id = "consumer"

                [dependencies]
                provider = ">=1.0.0"
                """);
        writeMod("provider", "id = \"provider\"\n");

        List<ModContainer> containers = modManager.prepare(modsDir);

        assertEquals(
                List.of("provider", "consumer"),
                containers.stream().map(c -> c.metadata().id()).toList());
    }

    private void writeMod(String folderName, String toml) throws IOException {
        Path mod = modsDir.resolve(folderName);
        Files.createDirectories(mod);
        Files.writeString(mod.resolve("mod.toml"), toml);
        Files.writeString(mod.resolve("main.py"), "");
    }
}
