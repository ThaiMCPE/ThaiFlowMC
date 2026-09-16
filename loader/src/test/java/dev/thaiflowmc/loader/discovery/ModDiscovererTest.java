package dev.thaiflowmc.loader.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModDiscovererTest {

    private final ModDiscoverer discoverer = new ModDiscoverer();

    @TempDir
    Path modsDir;

    @Test
    void findsModsWithModToml() throws IOException {
        Path mod = modsDir.resolve("hello");
        Files.createDirectories(mod);
        Files.writeString(mod.resolve("mod.toml"), "id = \"hello\"\n");

        List<Path> found = discoverer.discover(modsDir);

        assertEquals(1, found.size());
        assertEquals(mod, found.get(0));
    }

    @Test
    void findsZeroConfigModsWithOnlyMainPy() throws IOException {
        Path mod = modsDir.resolve("zero_config");
        Files.createDirectories(mod);
        Files.writeString(mod.resolve("main.py"), "");

        List<Path> found = discoverer.discover(modsDir);

        assertEquals(List.of(mod), found);
    }

    @Test
    void ignoresFoldersWithoutModFilesOrPlainFiles() throws IOException {
        Files.createDirectories(modsDir.resolve("not_a_mod"));
        Files.writeString(modsDir.resolve("readme.txt"), "not a mod");

        List<Path> found = discoverer.discover(modsDir);

        assertTrue(found.isEmpty());
    }

    @Test
    void missingModsDirectoryYieldsEmptyList() {
        List<Path> found = discoverer.discover(modsDir.resolve("does_not_exist"));

        assertTrue(found.isEmpty());
    }

    @Test
    void resultIsSortedByFolderName() throws IOException {
        for (String name : List.of("zeta", "alpha", "mid")) {
            Path mod = modsDir.resolve(name);
            Files.createDirectories(mod);
            Files.writeString(mod.resolve("main.py"), "");
        }

        List<Path> found = discoverer.discover(modsDir);

        assertEquals(
                List.of("alpha", "mid", "zeta"),
                found.stream().map(p -> p.getFileName().toString()).toList());
    }
}
