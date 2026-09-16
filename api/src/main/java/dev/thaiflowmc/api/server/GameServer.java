package dev.thaiflowmc.api.server;

/**
 * The ThaiFlowMC-native view of the running Minecraft server, handed to
 * {@code @server_start} / {@code @server_stop} handlers.
 */
public interface GameServer {

    /** Sends {@code message} as a chat message to every connected player. */
    void broadcast(String message);

    /** Number of players currently connected. */
    int getOnlinePlayerCount();
}
