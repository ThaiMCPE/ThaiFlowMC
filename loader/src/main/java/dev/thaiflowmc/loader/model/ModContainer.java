package dev.thaiflowmc.loader.model;

/**
 * Tracks a single mod's progress through the loader lifecycle:
 * discovered on disk -&gt; dependencies resolved -&gt; entrypoint loaded,
 * or failed at any of those steps.
 */
public final class ModContainer {

    public enum State { DISCOVERED, RESOLVED, LOADED, FAILED }

    private final ModMetadata metadata;
    private volatile State state = State.DISCOVERED;
    private volatile Throwable failure;

    public ModContainer(ModMetadata metadata) {
        this.metadata = metadata;
    }

    public ModMetadata metadata() {
        return metadata;
    }

    public State state() {
        return state;
    }

    public Throwable failure() {
        return failure;
    }

    public void markResolved() {
        this.state = State.RESOLVED;
    }

    public void markLoaded() {
        this.state = State.LOADED;
    }

    public void markFailed(Throwable failure) {
        this.state = State.FAILED;
        this.failure = failure;
    }
}
