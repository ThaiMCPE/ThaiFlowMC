package dev.thaiflowmc.loader.model;

import dev.thaiflowmc.api.ModPermissions;
import java.nio.file.Path;
import java.util.List;

/**
 * Fully resolved metadata for a mod, whether it came from a {@code mod.toml}
 * or was synthesized because none was present (zero-config mods: a bare
 * {@code main.py} in a folder).
 *
 * @param id              unique mod id
 * @param name            human-readable display name
 * @param version         parsed version
 * @param entrypoint      entrypoint file name, relative to {@code sourceDirectory}
 * @param dependencies    declared dependencies, empty if none
 * @param sourceDirectory the mod's directory on disk
 * @param zeroConfig      true if this mod had no {@code mod.toml} and every
 *                        field above was inferred
 * @param permissions     the mod's declared capabilities; {@link ModPermissions#DENY_ALL}
 *                        unless {@code mod.toml} has a {@code [permissions]} table
 */
public record ModMetadata(
        String id,
        String name,
        Version version,
        String entrypoint,
        List<ModDependency> dependencies,
        Path sourceDirectory,
        boolean zeroConfig,
        ModPermissions permissions) {

    public Path entrypointFile() {
        return sourceDirectory.resolve(entrypoint);
    }
}
