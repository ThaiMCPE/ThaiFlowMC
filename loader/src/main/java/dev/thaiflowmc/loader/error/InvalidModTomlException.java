package dev.thaiflowmc.loader.error;

public final class InvalidModTomlException extends ModLoadException {

    public InvalidModTomlException(String modName, String parseErrors) {
        super(modName, "Could not parse mod.toml:\n" + parseErrors);
    }
}
