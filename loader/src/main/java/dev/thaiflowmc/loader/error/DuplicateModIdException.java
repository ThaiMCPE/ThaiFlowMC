package dev.thaiflowmc.loader.error;

public final class DuplicateModIdException extends ModLoadException {

    public DuplicateModIdException(String modId, String firstPath, String secondPath) {
        super(modId, "Duplicate mod id \"" + modId + "\"\n\nFound in:\n  " + firstPath + "\n  " + secondPath);
    }
}
