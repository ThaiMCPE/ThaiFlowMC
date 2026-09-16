package dev.thaiflowmc.loader.error;

public final class MissingDependencyException extends ModLoadException {

    public MissingDependencyException(String modName, String dependencyId, String requirement) {
        super(modName, "Missing dependency:\n  " + dependencyId + " " + requirement
                + "\n\nInstalled:\n  (not installed)");
    }
}
