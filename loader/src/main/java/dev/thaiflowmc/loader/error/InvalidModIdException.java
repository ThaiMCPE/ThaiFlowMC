package dev.thaiflowmc.loader.error;

public final class InvalidModIdException extends ModLoadException {

    public InvalidModIdException(String modName, String invalidId) {
        super(modName, "Invalid mod id: \"" + invalidId + "\"\n\n"
                + "Mod ids must start with a lowercase letter and contain only\n"
                + "lowercase letters, digits, and underscores.");
    }
}
