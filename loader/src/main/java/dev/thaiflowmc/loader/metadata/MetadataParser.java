package dev.thaiflowmc.loader.metadata;

import dev.thaiflowmc.loader.error.InvalidModIdException;
import dev.thaiflowmc.loader.error.InvalidModTomlException;
import dev.thaiflowmc.loader.error.InvalidVersionException;
import dev.thaiflowmc.loader.error.MissingEntrypointException;
import dev.thaiflowmc.loader.model.ModDependency;
import dev.thaiflowmc.loader.model.ModMetadata;
import dev.thaiflowmc.loader.model.Version;
import dev.thaiflowmc.loader.model.VersionRequirement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Turns a mod directory into a {@link ModMetadata}.
 *
 * <p>Two paths are supported, matching ThaiFlowMC's "progressive
 * disclosure" philosophy:
 * <ul>
 *   <li><b>Zero-config</b>: the folder has only a {@code main.py}. The mod
 *       id comes from the folder name, version defaults to {@code 1.0.0},
 *       and the entrypoint is {@code main.py}. No validation ceremony.</li>
 *   <li><b>Described</b>: the folder has a {@code mod.toml}. Every field is
 *       still optional except the entrypoint file's actual presence, but a
 *       field that IS present must be valid (a bad id or version is an
 *       error rather than silently ignored).</li>
 * </ul>
 */
public final class MetadataParser {

    private static final String DEFAULT_VERSION = "1.0.0";
    private static final String DEFAULT_ENTRYPOINT = "main.py";
    private static final Pattern VALID_ID = Pattern.compile("^[a-z][a-z0-9_]*$");

    public ModMetadata parse(Path modDirectory) {
        Path tomlPath = modDirectory.resolve("mod.toml");
        String folderName = modDirectory.getFileName().toString();

        if (!Files.exists(tomlPath)) {
            return zeroConfigMetadata(modDirectory, folderName);
        }
        return describedMetadata(modDirectory, folderName, tomlPath);
    }

    private ModMetadata zeroConfigMetadata(Path modDirectory, String folderName) {
        if (!Files.exists(modDirectory.resolve(DEFAULT_ENTRYPOINT))) {
            throw new MissingEntrypointException(folderName, DEFAULT_ENTRYPOINT);
        }
        String id = sanitizeId(folderName);
        return new ModMetadata(
                id, folderName, Version.parse(DEFAULT_VERSION), DEFAULT_ENTRYPOINT, List.of(), modDirectory, true);
    }

    private ModMetadata describedMetadata(Path modDirectory, String folderName, Path tomlPath) {
        TomlParseResult toml;
        try {
            toml = Toml.parse(tomlPath);
        } catch (IOException e) {
            throw new RuntimeException("Could not read " + tomlPath, e);
        }
        if (toml.hasErrors()) {
            String errors = String.join("\n  ", toml.errors().stream().map(Object::toString).toList());
            throw new InvalidModTomlException(folderName, "  " + errors);
        }

        String rawId = toml.getString("id");
        String errorName = rawId != null ? rawId : folderName;

        String id;
        if (rawId == null || rawId.isBlank()) {
            id = sanitizeId(folderName);
        } else if (!VALID_ID.matcher(rawId).matches()) {
            throw new InvalidModIdException(errorName, rawId);
        } else {
            id = rawId;
        }

        String name = toml.getString("name", () -> id);

        String versionRaw = toml.getString("version", () -> DEFAULT_VERSION);
        Version version;
        try {
            version = Version.parse(versionRaw);
        } catch (IllegalArgumentException e) {
            throw new InvalidVersionException(name, "version", versionRaw);
        }

        String entrypoint = toml.getString("entrypoint", () -> DEFAULT_ENTRYPOINT);
        if (!Files.exists(modDirectory.resolve(entrypoint))) {
            throw new MissingEntrypointException(name, entrypoint);
        }

        List<ModDependency> dependencies = parseDependencies(toml, name);

        return new ModMetadata(id, name, version, entrypoint, dependencies, modDirectory, false);
    }

    private List<ModDependency> parseDependencies(TomlParseResult toml, String modName) {
        List<ModDependency> dependencies = new ArrayList<>();
        TomlTable depsTable = toml.getTable("dependencies");
        if (depsTable == null) {
            return dependencies;
        }
        for (String depId : depsTable.keySet()) {
            String requirementRaw = String.valueOf(depsTable.get(depId));
            try {
                dependencies.add(new ModDependency(depId, VersionRequirement.parse(requirementRaw)));
            } catch (IllegalArgumentException e) {
                throw new InvalidVersionException(modName, "dependency requirement for \"" + depId + "\"", requirementRaw);
            }
        }
        return dependencies;
    }

    private String sanitizeId(String folderName) {
        String id = folderName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (id.isEmpty() || !Character.isLetter(id.charAt(0))) {
            id = "mod_" + id;
        }
        return id;
    }
}
