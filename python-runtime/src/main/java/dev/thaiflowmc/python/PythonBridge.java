package dev.thaiflowmc.python;

import dev.thaiflowmc.api.Log;
import dev.thaiflowmc.api.entity.Player;
import dev.thaiflowmc.api.event.EventBus;
import dev.thaiflowmc.api.event.EventHandler;
import dev.thaiflowmc.api.item.ItemDefinition;
import dev.thaiflowmc.api.item.ItemRegistry;
import dev.thaiflowmc.api.server.GameServer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The only object a mod's Python context can reach back into Java through.
 * Exposed to Python as {@code __thaiflow_bridge__} and turned into the
 * friendly {@code thaiflow} module by {@code thaiflow_bootstrap.py}.
 *
 * <p>Every method here is annotated {@link HostAccess.Export} - the
 * {@code HostAccess} policy {@link PythonRuntime} configures exposes
 * nothing else, on this object or any other, to Python. There is
 * deliberately no generic "call any Java method" escape hatch, and no
 * Minecraft/Java object ever reaches a mod directly: see {@link
 * #toSafePayload} and {@link PlayerView}/{@link GameServerView}.
 */
public final class PythonBridge {

    private static final Logger LOG = LoggerFactory.getLogger(Log.PYTHON);
    private static final Duration EVENT_HANDLER_TIMEOUT = Duration.ofSeconds(5);

    private final String modId;
    private final Path modDirectory;
    private final String scriptName;
    private final EventBus eventBus;
    private final ItemRegistry itemRegistry;
    private final Context context;

    PythonBridge(
            String modId,
            Path modDirectory,
            String scriptName,
            EventBus eventBus,
            ItemRegistry itemRegistry,
            Context context) {
        this.modId = modId;
        this.modDirectory = modDirectory;
        this.scriptName = scriptName;
        this.eventBus = eventBus;
        this.itemRegistry = itemRegistry;
        this.context = context;
    }

    /** Called from the {@code thaiflow.event(name)} decorator. */
    @HostAccess.Export
    public void subscribe(String eventName, Value callback) {
        EventHandler<Object> handler = payload -> {
            Object safePayload = toSafePayload(payload);
            try {
                ExecutionGuard.run(context, EVENT_HANDLER_TIMEOUT, () -> callback.execute(safePayload));
            } catch (PolyglotException e) {
                throw new PythonScriptException(modId, PythonErrorFormatter.format(scriptName, e), e);
            } catch (IllegalStateException e) {
                // The context was already closed - almost certainly by a
                // previous ExecutionGuard timeout on this same mod.
                throw new PythonScriptException(
                        modId, "This mod stopped responding earlier and can no longer handle events.", e);
            }
        };
        eventBus.subscribe(eventName, handler);
    }

    /** Called from the {@code thaiflow.item(id, stack=...)} function. */
    @HostAccess.Export
    public void registerItem(String id, int stack) {
        String texturePath = findTexture(id);
        itemRegistry.register(new ItemDefinition(modId, id, stack, texturePath));
        LOG.info("Mod {} registered item {}", modId, id);
    }

    /**
     * Never hand a mod the real host object behind an event payload -
     * only the narrow, explicitly-exported view types.  Payloads that
     * aren't a type we know how to wrap (the generic {@code "test"}
     * event's arbitrary data, primitives, etc.) pass through unchanged.
     */
    private Object toSafePayload(Object payload) {
        if (payload instanceof Player player) {
            return new PlayerView(player);
        }
        if (payload instanceof GameServer server) {
            return new GameServerView(server);
        }
        return payload;
    }

    private String findTexture(String itemId) {
        Path[] candidates = {
            modDirectory.resolve("textures").resolve(itemId + ".png"),
            modDirectory.resolve(itemId + ".png"),
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate.toString();
            }
        }
        return null;
    }
}
