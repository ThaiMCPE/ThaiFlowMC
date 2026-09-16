package dev.thaiflowmc.loader.model;

import java.util.Objects;

/**
 * A simple {@code major.minor.patch} version, as used by {@code mod.toml}'s
 * {@code version} field and dependency requirements. Missing components
 * default to zero, so {@code "1"} and {@code "1.0"} both mean {@code 1.0.0}.
 */
public final class Version implements Comparable<Version> {

    private final int major;
    private final int minor;
    private final int patch;

    private Version(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public static Version parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        String[] parts = text.split("\\.", -1);
        if (text.isEmpty() || parts.length > 3) {
            throw new IllegalArgumentException("not a valid version: \"" + raw + "\"");
        }
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            if (major < 0 || minor < 0 || patch < 0) {
                throw new NumberFormatException();
            }
            return new Version(major, minor, patch);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a valid version: \"" + raw + "\"");
        }
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    public int patch() {
        return patch;
    }

    @Override
    public int compareTo(Version other) {
        if (major != other.major) {
            return Integer.compare(major, other.major);
        }
        if (minor != other.minor) {
            return Integer.compare(minor, other.minor);
        }
        return Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Version other && compareTo(other) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch);
    }
}
