package dev.thaiflowmc.api.event;

/**
 * A single subscriber to a named ThaiFlowMC event.
 *
 * @param <T> the payload type delivered to this handler
 */
@FunctionalInterface
public interface EventHandler<T> {

    void handle(T payload) throws Exception;
}
