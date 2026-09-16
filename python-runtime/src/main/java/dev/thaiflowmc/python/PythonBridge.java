package dev.thaiflowmc.python;

import dev.thaiflowmc.api.Log;
import dev.thaiflowmc.api.event.EventBus;
import dev.thaiflowmc.api.event.EventHandler;
import dev.thaiflowmc.api.item.ItemDefinition;
import dev.thaiflowmc.api.item.ItemRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The only object a mod's Python context can reach back into Java through.
 * Exposed to Python as {@code __thaiflow_bridge__} and turned into the
 * friendly {@code thaiflow} module by {@code thaiflow_bootstrap.py}.
 *
 * <p>Every public method here is part of the real (if narrow) surface a mod
 * can touch; there is deliberately no generic "call any Java method" escape
 * hatch.
 */
// Must be public: GraalVM's host interop only reflects public members of
// public classes, even when every method here is already `public`.
public final class PythonBridge {

    private static final Logger LOG = LoggerFactory.getLogger(Log.PYTHON);

    private final String modId;
    private final Path modDirectory;
    private final String scriptName;
    private final EventBus eventBus;
    private final ItemRegistry itemRegistry;

    PythonBridge(String modId, Path modDirectory, String scriptName, EventBus eventBus, ItemRegistry itemRegistry) {
        this.modId = modId;
        this.modDirectory = modDirectory;
        this.scriptName = scriptName;
        this.eventBus = eventBus;
        this.itemRegistry = itemRegistry;
    }

    /** Called from the {@code thaiflow.event(name)} decorator. */
    public void subscribe(String eventName, Value callback) {
        EventHandler<Object> handler = payload -> {
            try {
                callback.execute(payload);
            } catch (PolyglotException e) {
                throw new PythonScriptException(modId, PythonErrorFormatter.format(scriptName, e), e);
            }
        };
        eventBus.subscribe(eventName, handler);
    }

    /** Called from the {@code thaiflow.item(id, stack=...)} function. */
    public void registerItem(String id, int stack) {
        String texturePath = findTexture(id);
        itemRegistry.register(new ItemDefinition(modId, id, stack, texturePath));
        LOG.info("Mod {} registered item {}", modId, id);
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
