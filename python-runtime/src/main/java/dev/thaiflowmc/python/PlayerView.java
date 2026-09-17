package dev.thaiflowmc.python;

import dev.thaiflowmc.api.entity.Player;
import org.graalvm.polyglot.HostAccess;

/**
 * The only {@code Player}-shaped object a mod's Python code can ever reach.
 *
 * <p>Rather than handing a mod the real {@link Player} implementation
 * directly, every {@code player_join}/{@code player_leave} payload is
 * wrapped in one of these first (see {@link PythonBridge#toSafePayload}).
 * This strictly limits what's reachable to exactly the four methods below,
 * annotated {@link HostAccess.Export} - regardless of what public methods a
 * particular {@link Player} implementation happens to have (today's
 * implementations only have these four anyway, but a future real-Minecraft
 * wrapper might accidentally expose more; this view keeps that from
 * mattering to mod authors' security). Combined with the explicit,
 * export-only {@code HostAccess} policy in {@link PythonRuntime}, a Python
 * mod cannot reflectively discover or call anything beyond what's exported
 * here, on this object or any other host object it's handed.
 */
// Must be public: GraalVM's host interop only reflects public members of
// public classes, even when every exported method is already annotated.
public final class PlayerView {

    private final Player delegate;

    PlayerView(Player delegate) {
        this.delegate = delegate;
    }

    @HostAccess.Export
    public String getName() {
        return delegate.getName();
    }

    @HostAccess.Export
    public double getHealth() {
        return delegate.getHealth();
    }

    @HostAccess.Export
    public void say(String message) {
        delegate.say(message);
    }

    @HostAccess.Export
    public void teleport(double x, double y, double z) {
        delegate.teleport(x, y, z);
    }
}
