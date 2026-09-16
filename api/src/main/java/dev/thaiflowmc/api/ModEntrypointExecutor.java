package dev.thaiflowmc.api;

import dev.thaiflowmc.api.event.EventBus;

/**
 * Executes a mod's entrypoint script. The loader calls this once per mod,
 * in dependency order; the python-runtime module is the real implementation,
 * but keeping the contract here means the loader never needs to know
 * anything about GraalPy.
 */
public interface ModEntrypointExecutor {

    /**
     * Runs the mod described by {@code mod}, wiring it up to {@code eventBus}
     * so its decorators (e.g. {@code @load}, {@code @event("test")}) can
     * subscribe to and fire ThaiFlowMC events.
     *
     * @throws Exception if the script fails to execute; the caller is
     *                    responsible for turning this into a friendly error
     */
    void execute(ModHandle mod, EventBus eventBus) throws Exception;
}
