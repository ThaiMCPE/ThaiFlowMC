package dev.thaiflowmc.loader.error;

import dev.thaiflowmc.api.ThaiFlowException;

/**
 * Base type for every error that prevents a specific mod from loading.
 * Carries the mod's display name so the formatter can print
 * "Could not load mod: &lt;name&gt;" without every call site repeating it.
 */
public abstract class ModLoadException extends ThaiFlowException {

    private final String modName;

    protected ModLoadException(String modName, String message) {
        super(message);
        this.modName = modName;
    }

    public String modName() {
        return modName;
    }
}
