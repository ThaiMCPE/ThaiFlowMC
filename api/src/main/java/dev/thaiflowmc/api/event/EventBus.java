package dev.thaiflowmc.api.event;

/**
 * The central publish/subscribe hub for ThaiFlowMC events.
 *
 * <p>Java code (the loader, the Minecraft adapter, other mods) fires named
 * events with a payload object. Every handler subscribed to that name is
 * invoked with the payload. This is the single mechanism behind both the
 * generic {@code @event("name")} Python decorator and the more specific
 * sugar decorators such as {@code @player_join} built on top of it.
 */
public interface EventBus {

    /**
     * Registers a handler to be invoked whenever {@code eventName} fires.
     */
    void subscribe(String eventName, EventHandler<Object> handler);

    /**
     * Fires {@code eventName} with the given payload, synchronously invoking
     * every subscribed handler in registration order.
     *
     * <p>A handler that throws does not stop other handlers from running;
     * the failure is reported to the configured {@link EventFailureListener}
     * (or logged) instead of propagating.
     */
    void fire(String eventName, Object payload);

    /**
     * Installs the listener notified when a handler throws while handling an
     * event. Only one listener is kept; callers that need to fan out should
     * do so inside their own listener.
     */
    void setFailureListener(EventFailureListener listener);
}
