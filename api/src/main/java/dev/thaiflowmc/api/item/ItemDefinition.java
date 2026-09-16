package dev.thaiflowmc.api.item;

/**
 * A Python-declared item, as created by {@code item("ruby")} or
 * {@code item("ruby", stack=64)}.
 *
 * <p>This is intentionally a plain data holder with no Minecraft type in
 * sight. Turning it into an actual in-game item is the
 * {@code minecraft-adapter}'s job.
 *
 * @param modId       id of the mod that declared this item
 * @param id          the item's own id, unique within its mod
 * @param stackSize   maximum stack size (Minecraft default is 64)
 * @param texturePath resolved path to the item's texture file, or
 *                    {@code null} if none was found
 */
public record ItemDefinition(String modId, String id, int stackSize, String texturePath) {

    /** The fully-qualified id mods and the adapter use to refer to this item, e.g. {@code "hello:ruby"}. */
    public String fullId() {
        return modId + ":" + id;
    }
}
