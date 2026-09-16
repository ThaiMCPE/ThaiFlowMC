package dev.thaiflowmc.loader.error;

public final class IncompatibleDependencyException extends ModLoadException {

    public IncompatibleDependencyException(
            String modName, String dependencyId, String requirement, String installedVersion) {
        super(modName, "Missing dependency:\n  " + dependencyId + " " + requirement
                + "\n\nInstalled:\n  " + dependencyId + " " + installedVersion);
    }
}
