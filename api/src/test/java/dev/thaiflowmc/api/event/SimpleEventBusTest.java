package dev.thaiflowmc.api.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimpleEventBusTest {

    @Test
    void firesAllHandlersForAnEvent() {
        SimpleEventBus bus = new SimpleEventBus();
        List<Object> received = new ArrayList<>();
        bus.subscribe("test", received::add);
        bus.subscribe("test", payload -> received.add("second:" + payload));

        bus.fire("test", "hello");

        assertEquals(List.of("hello", "second:hello"), received);
    }

    @Test
    void firingAnEventWithNoSubscribersIsANoop() {
        SimpleEventBus bus = new SimpleEventBus();
        bus.fire("nothing_subscribed", "payload");
    }

    @Test
    void aThrowingHandlerDoesNotStopOtherHandlers() {
        SimpleEventBus bus = new SimpleEventBus();
        List<String> received = new ArrayList<>();
        bus.subscribe("test", payload -> {
            throw new RuntimeException("boom");
        });
        bus.subscribe("test", payload -> received.add("still ran"));

        bus.fire("test", "payload");

        assertEquals(List.of("still ran"), received);
    }

    @Test
    void failureListenerReceivesHandlerExceptions() {
        SimpleEventBus bus = new SimpleEventBus();
        List<Throwable> failures = new ArrayList<>();
        bus.setFailureListener((eventName, error) -> failures.add(error));
        bus.subscribe("test", payload -> {
            throw new IllegalStateException("boom");
        });

        bus.fire("test", "payload");

        assertEquals(1, failures.size());
        assertTrue(failures.get(0) instanceof IllegalStateException);
    }
}
