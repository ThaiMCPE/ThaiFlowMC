package dev.thaiflowmc.loader.model;

/** One entry from a {@code mod.toml}'s {@code [dependencies]} table. */
public record ModDependency(String modId, VersionRequirement requirement) {
}
