package dev.thaiflowmc.python;

import dev.thaiflowmc.api.server.GameServer;
import org.graalvm.polyglot.HostAccess;

/**
 * The only {@code GameServer}-shaped object a mod's Python code can ever
 * reach. See {@link PlayerView} for why this exists as a separate wrapper
 * rather than exposing {@link GameServer} implementations directly.
 */
// Must be public: GraalVM's host interop only reflects public members of
// public classes, even when every exported method is already annotated.
public final class GameServerView {

    private final GameServer delegate;

    GameServerView(GameServer delegate) {
        this.delegate = delegate;
    }

    @HostAccess.Export
    public void broadcast(String message) {
        delegate.broadcast(message);
    }

    @HostAccess.Export
    public int getOnlinePlayerCount() {
        return delegate.getOnlinePlayerCount();
    }
}
