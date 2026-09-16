package dev.thaiflowmc.api.entity;

/**
 * The ThaiFlowMC-native view of a player, handed to Python mods.
 *
 * <p>This is what {@code player_join} / {@code player_leave} handlers
 * receive. It never leaks a raw Minecraft class: every Minecraft version the
 * {@code minecraft-adapter} module supports provides its own implementation
 * of this interface, so a Minecraft update only has to change the adapter,
 * never the mods.
 */
public interface Player {

    /** The player's display name. */
    String getName();

    /** Current health, in the 0-20 half-heart scale Minecraft uses. */
    double getHealth();

    /** Sends {@code message} to the player as an in-game chat message. */
    void say(String message);

    /** Teleports the player to the given world coordinates. */
    void teleport(double x, double y, double z);
}
