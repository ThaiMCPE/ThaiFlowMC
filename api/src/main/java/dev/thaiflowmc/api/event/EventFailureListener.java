package dev.thaiflowmc.api.event;

/**
 * Notified when an {@link EventHandler} throws while processing an event.
 * ThaiFlowMC uses this to turn a raw exception (often originating from
 * Python) into a short, readable error instead of a stack trace.
 */
@FunctionalInterface
public interface EventFailureListener {

    void onHandlerFailure(String eventName, Throwable error);
}
