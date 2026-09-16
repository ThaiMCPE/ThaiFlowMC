package dev.thaiflowmc.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.thaiflowmc.api.ModEntrypointExecutor;
import dev.thaiflowmc.api.ModHandle;
import dev.thaiflowmc.api.event.EventBus;
import dev.thaiflowmc.api.event.SimpleEventBus;
import dev.thaiflowmc.loader.model.ModContainer;
import dev.thaiflowmc.loader.model.ModMetadata;
import dev.thaiflowmc.loader.model.Version;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModLoaderTest {

    private static ModContainer container(String id) {
        return new ModContainer(new ModMetadata(id, id, Version.parse("1.0.0"), "main.py", List.of(), Path.of("."), false));
    }

    @Test
    void aFailingModDoesNotStopLaterModsFromLoading() {
        List<String> executed = new ArrayList<>();
        ModEntrypointExecutor executor = (mod, eventBus) -> {
            if (mod.modId().equals("broken")) {
                throw new RuntimeException("boom");
            }
            executed.add(mod.modId());
        };
        ModLoader loader = new ModLoader(executor, new SimpleEventBus());

        List<ModContainer> containers = List.of(container("broken"), container("healthy"));
        loader.loadAll(containers);

        assertEquals(List.of("healthy"), executed);
        assertEquals(ModContainer.State.FAILED, containers.get(0).state());
        assertEquals(ModContainer.State.LOADED, containers.get(1).state());
    }

    @Test
    void successfulModIsMarkedLoaded() {
        ModEntrypointExecutor executor = (mod, eventBus) -> {};
        ModLoader loader = new ModLoader(executor, new SimpleEventBus());
        ModContainer container = container("hello");

        loader.load(container);

        assertEquals(ModContainer.State.LOADED, container.state());
        assertTrue(container.failure() == null);
    }

    @Test
    void executorReceivesAModHandleMatchingTheMetadata() {
        List<ModHandle> handles = new ArrayList<>();
        ModEntrypointExecutor executor = (mod, eventBus) -> handles.add(mod);
        EventBus eventBus = new SimpleEventBus();
        ModLoader loader = new ModLoader(executor, eventBus);

        loader.load(container("hello"));

        assertEquals(1, handles.size());
        assertEquals("hello", handles.get(0).modId());
        assertEquals("main.py", handles.get(0).entrypoint());
    }
}
