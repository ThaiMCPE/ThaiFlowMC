package dev.thaiflowmc.api;

/**
 * A mod's declared capabilities, from its {@code mod.toml}'s
 * {@code [permissions]} table:
 *
 * <pre>{@code
 * [permissions]
 * storage = true
 * network = false
 * filesystem = false
 * }</pre>
 *
 * <p><b>Default is deny.</b> A mod with no {@code [permissions]} table at
 * all - including every zero-config, {@code main.py}-only mod - gets
 * {@link #DENY_ALL}: no storage, no network, no filesystem. A mod must
 * opt in to each capability explicitly.
 *
 * @param storage    a private, per-mod directory for reading/writing files -
 *                   never the mod's own source directory or any other
 *                   mod's, and never an arbitrary filesystem path
 * @param network    outbound network access
 * @param filesystem broad filesystem access beyond the mod's own private
 *                   storage directory - coarse-grained today (see
 *                   docs/ARCHITECTURE.md's security section); most mods
 *                   should ask for {@link #storage} instead
 */
public record ModPermissions(boolean storage, boolean network, boolean filesystem) {

    /** What every mod gets unless it explicitly asks for more. */
    public static final ModPermissions DENY_ALL = new ModPermissions(false, false, false);
}
