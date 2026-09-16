package dev.thaiflowmc.loader.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.thaiflowmc.loader.error.InvalidModIdException;
import dev.thaiflowmc.loader.error.InvalidVersionException;
import dev.thaiflowmc.loader.error.MissingEntrypointException;
import dev.thaiflowmc.loader.model.ModDependency;
import dev.thaiflowmc.loader.model.ModMetadata;
import dev.thaiflowmc.loader.model.Version;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetadataParserTest {

    private final MetadataParser parser = new MetadataParser();

    @TempDir
    Path tempDir;

    @Test
    void zeroConfigModUsesFolderNameAndDefaults() throws IOException {
        Path modDir = tempDir.resolve("MyCoolMod");
        Files.createDirectories(modDir);
        Files.writeString(modDir.resolve("main.py"), "item('ruby')\n");

        ModMetadata metadata = parser.parse(modDir);

        assertEquals("mycoolmod", metadata.id());
        assertEquals("MyCoolMod", metadata.name());
        assertEquals(Version.parse("1.0.0"), metadata.version());
        assertEquals("main.py", metadata.entrypoint());
        assertTrue(metadata.dependencies().isEmpty());
        assertTrue(metadata.zeroConfig());
    }

    @Test
    void zeroConfigSanitizesInvalidFolderNameIntoValidId() throws IOException {
        Path modDir = tempDir.resolve("123-cool mod!");
        Files.createDirectories(modDir);
        Files.writeString(modDir.resolve("main.py"), "");

        ModMetadata metadata = parser.parse(modDir);

        assertTrue(metadata.id().matches("^[a-z][a-z0-9_]*$"), "sanitized id was: " + metadata.id());
    }

    @Test
    void zeroConfigWithoutEntrypointFails() throws IOException {
        Path modDir = tempDir.resolve("empty_mod");
        Files.createDirectories(modDir);
        // No main.py, but a mod.toml-less folder isn't discoverable anyway; parse() is
        // still expected to fail cleanly if called directly on such a folder.
        assertThrows(MissingEntrypointException.class, () -> parser.parse(modDir));
    }

    @Test
    void describedModParsesFullMetadata() throws IOException {
        Path modDir = tempDir.resolve("hello");
        Files.createDirectories(modDir);
        Files.writeString(
                modDir.resolve("mod.toml"),
                """
                id = "hello"
                name = "Hello Mod"
                version = "1.0.0"
                entrypoint = "main.py"

                [dependencies]
                thaiflowmc = ">=0.1.0"
                """);
        Files.writeString(modDir.resolve("main.py"), "");

        ModMetadata metadata = parser.parse(modDir);

        assertEquals("hello", metadata.id());
        assertEquals("Hello Mod", metadata.name());
        assertEquals(Version.parse("1.0.0"), metadata.version());
        assertEquals(1, metadata.dependencies().size());
        ModDependency dependency = metadata.dependencies().get(0);
        assertEquals("thaiflowmc", dependency.modId());
        assertTrue(dependency.requirement().matches(Version.parse("0.1.0")));
        assertTrue(!metadata.zeroConfig());
    }

    @Test
    void describedModDefaultsOptionalFields() throws IOException {
        Path modDir = tempDir.resolve("minimal");
        Files.createDirectories(modDir);
        Files.writeString(modDir.resolve("mod.toml"), "id = \"minimal\"\n");
        Files.writeString(modDir.resolve("main.py"), "");

        ModMetadata metadata = parser.parse(modDir);

        assertEquals("minimal", metadata.name());
        assertEquals(Version.parse("1.0.0"), metadata.version());
        assertEquals("main.py", metadata.entrypoint());
    }

    @Test
    void invalidIdIsRejected() throws IOException {
        Path modDir = tempDir.resolve("bad_id_mod");
        Files.createDirectories(modDir);
        Files.writeString(modDir.resolve("mod.toml"), "id = \"Not Valid!\"\n");
        Files.writeString(modDir.resolve("main.py"), "");

        assertThrows(InvalidModIdException.class, () -> parser.parse(modDir));
    }

    @Test
    void missingEntrypointFileIsRejected() throws IOException {
        Path modDir = tempDir.resolve("no_entry");
        Files.createDirectories(modDir);
        Files.writeString(modDir.resolve("mod.toml"), "id = \"no_entry\"\nentrypoint = \"start.py\"\n");

        assertThrows(MissingEntrypointException.class, () -> parser.parse(modDir));
    }

    @Test
    void invalidVersionIsRejected() throws IOException {
        Path modDir = tempDir.resolve("bad_version");
        Files.createDirectories(modDir);
        Files.writeString(modDir.resolve("mod.toml"), "id = \"bad_version\"\nversion = \"not-a-version\"\n");
        Files.writeString(modDir.resolve("main.py"), "");

        assertThrows(InvalidVersionException.class, () -> parser.parse(modDir));
    }

    @Test
    void invalidDependencyRequirementIsRejected() throws IOException {
        Path modDir = tempDir.resolve("bad_dep");
        Files.createDirectories(modDir);
        Files.writeString(
                modDir.resolve("mod.toml"),
                """
                id = "bad_dep"

                [dependencies]
                other_mod = "~>1.0"
                """);
        Files.writeString(modDir.resolve("main.py"), "");

        assertThrows(InvalidVersionException.class, () -> parser.parse(modDir));
    }
}
