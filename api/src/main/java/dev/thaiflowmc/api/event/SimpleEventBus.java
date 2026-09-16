package dev.thaiflowmc.api.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default in-memory {@link EventBus}. Handlers for the same event name are
 * invoked in the order they were subscribed; a throwing handler is isolated
 * so it cannot prevent other handlers (or other mods) from receiving the
 * same event.
 */
public final class SimpleEventBus implements EventBus {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleEventBus.class);

    private final Map<String, List<EventHandler<Object>>> handlers = new ConcurrentHashMap<>();
    private volatile EventFailureListener failureListener;

    @Override
    public void subscribe(String eventName, EventHandler<Object> handler) {
        handlers.computeIfAbsent(eventName, key -> new CopyOnWriteArrayList<>()).add(handler);
    }

    @Override
    public void fire(String eventName, Object payload) {
        List<EventHandler<Object>> subscribers = handlers.get(eventName);
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }
        for (EventHandler<Object> handler : subscribers) {
            try {
                handler.handle(payload);
            } catch (Throwable error) {
                reportFailure(eventName, error);
            }
        }
    }

    @Override
    public void setFailureListener(EventFailureListener listener) {
        this.failureListener = listener;
    }

    private void reportFailure(String eventName, Throwable error) {
        EventFailureListener listener = this.failureListener;
        if (listener != null) {
            listener.onHandlerFailure(eventName, error);
        } else {
            LOG.error("Unhandled error in event '{}' handler", eventName, error);
        }
    }
}
