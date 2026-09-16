package dev.thaiflowmc.loader;

import dev.thaiflowmc.api.Log;
import dev.thaiflowmc.api.ModEntrypointExecutor;
import dev.thaiflowmc.api.ModHandle;
import dev.thaiflowmc.api.ThaiFlowException;
import dev.thaiflowmc.api.event.EventBus;
import dev.thaiflowmc.loader.error.FriendlyErrorFormatter;
import dev.thaiflowmc.loader.error.ModLoadException;
import dev.thaiflowmc.loader.model.ModContainer;
import dev.thaiflowmc.loader.model.ModMetadata;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads mods in the order {@link ModManager} resolved, delegating actual
 * script execution to a {@link ModEntrypointExecutor} (python-runtime).
 *
 * <p>A mod that fails to load is reported and marked failed, but does not
 * stop the remaining mods from being attempted.
 */
public final class ModLoader {

    private static final Logger LOG = LoggerFactory.getLogger(Log.LOADER);

    private final ModEntrypointExecutor executor;
    private final EventBus eventBus;

    public ModLoader(ModEntrypointExecutor executor, EventBus eventBus) {
        this.executor = executor;
        this.eventBus = eventBus;
    }

    public void loadAll(List<ModContainer> containers) {
        for (ModContainer container : containers) {
            load(container);
        }
    }

    public void load(ModContainer container) {
        ModMetadata metadata = container.metadata();
        ModHandle handle = new ModHandle(metadata.id(), metadata.sourceDirectory(), metadata.entrypoint());
        container.markResolved();
        try {
            executor.execute(handle, eventBus);
            container.markLoaded();
            LOG.info("Executed {}/{}", metadata.id(), metadata.entrypoint());
        } catch (ModLoadException e) {
            container.markFailed(e);
            System.err.println(FriendlyErrorFormatter.format(e));
            LOG.debug("Failed to load mod {}", metadata.id(), e);
        } catch (ThaiFlowException e) {
            container.markFailed(e);
            System.err.println(friendlyBlock(metadata, e.friendlyDetail()));
            LOG.debug("Failed to load mod {}", metadata.id(), e);
        } catch (Exception e) {
            container.markFailed(e);
            System.err.println(friendlyBlock(metadata, String.valueOf(e.getMessage())));
            LOG.debug("Failed to load mod {}", metadata.id(), e);
        }
    }

    private String friendlyBlock(ModMetadata metadata, String detail) {
        return "[ThaiFlowMC]\n\nCould not load mod:\n" + metadata.name() + "\n\n" + detail;
    }
}
