package dev.thaiflowmc.api;

/**
 * Base type for every error ThaiFlowMC wants to show to a mod author rather
 * than a raw Java stack trace.
 *
 * <p>{@link #friendlyDetail()} is the beginner-friendly, multi-line body
 * shown in the console; {@link #getMessage()}/{@link #getCause()} still
 * carry the full technical detail for debug logs.
 */
public class ThaiFlowException extends RuntimeException {

    public ThaiFlowException(String message) {
        super(message);
    }

    public ThaiFlowException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Human-readable explanation shown to mod authors. Defaults to {@link #getMessage()}. */
    public String friendlyDetail() {
        return getMessage();
    }
}
