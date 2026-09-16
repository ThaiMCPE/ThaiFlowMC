package dev.thaiflowmc.api;

import java.nio.file.Path;

/**
 * The minimal, engine-agnostic description of a mod that the loader passes
 * to whatever executes its entrypoint (in practice, the python-runtime
 * module). Keeping this in {@code api} lets {@code loader} and
 * {@code python-runtime} depend only on this contract, not on each other.
 *
 * @param modId       the mod's unique id
 * @param sourceDirectory the mod's directory on disk, e.g. for locating textures
 * @param entrypoint  path to the mod's entrypoint script, relative to {@code sourceDirectory}
 */
public record ModHandle(String modId, Path sourceDirectory, String entrypoint) {

    public Path entrypointFile() {
        return sourceDirectory.resolve(entrypoint);
    }
}
