package dev.thaiflowmc.api.item;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of items declared by mods via {@code item(...)}.
 *
 * <p>In the MVP this simply records what mods asked for; once the
 * {@code minecraft-adapter} performs real Minecraft registration, it will
 * read from this registry instead of mods talking to Minecraft directly.
 */
public final class ItemRegistry {

    private final Map<String, ItemDefinition> items = new ConcurrentHashMap<>();

    /**
     * Registers a new item.
     *
     * @throws IllegalStateException if an item with the same full id was already registered
     */
    public void register(ItemDefinition item) {
        ItemDefinition existing = items.putIfAbsent(item.fullId(), item);
        if (existing != null) {
            throw new IllegalStateException("Item \"" + item.fullId() + "\" is already registered");
        }
    }

    public Optional<ItemDefinition> find(String fullId) {
        return Optional.ofNullable(items.get(fullId));
    }

    public Map<String, ItemDefinition> all() {
        return new LinkedHashMap<>(items);
    }
}
