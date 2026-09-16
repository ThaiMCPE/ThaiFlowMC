package dev.thaiflowmc.loader.error;

public final class InvalidVersionException extends ModLoadException {

    public InvalidVersionException(String modName, String field, String raw) {
        super(modName, "Invalid " + field + ": \"" + raw + "\"\n\n"
                + "Versions must look like \"1.0.0\"; requirements may be\n"
                + "prefixed with >=, <=, >, or <.");
    }
}
