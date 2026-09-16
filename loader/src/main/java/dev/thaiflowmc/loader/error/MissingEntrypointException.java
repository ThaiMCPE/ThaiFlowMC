package dev.thaiflowmc.loader.error;

public final class MissingEntrypointException extends ModLoadException {

    public MissingEntrypointException(String modName, String entrypoint) {
        super(modName, "Missing entrypoint file:\n  " + entrypoint);
    }
}
