package dev.thaiflowmc.loader.dependency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.thaiflowmc.loader.error.CyclicDependencyException;
import dev.thaiflowmc.loader.error.IncompatibleDependencyException;
import dev.thaiflowmc.loader.error.MissingDependencyException;
import dev.thaiflowmc.loader.model.ModDependency;
import dev.thaiflowmc.loader.model.ModMetadata;
import dev.thaiflowmc.loader.model.Version;
import dev.thaiflowmc.loader.model.VersionRequirement;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DependencyResolverTest {

    private static final Path SOURCE = Path.of(".");
    private final DependencyResolver resolver = new DependencyResolver(Version.parse("0.1.0"));

    private static ModMetadata mod(String id, String... deps) {
        List<ModDependency> dependencies = List.of();
        if (deps.length > 0) {
            dependencies = new java.util.ArrayList<>();
            for (int i = 0; i < deps.length; i += 2) {
                dependencies.add(new ModDependency(deps[i], VersionRequirement.parse(deps[i + 1])));
            }
        }
        return new ModMetadata(id, id, Version.parse("1.0.0"), "main.py", dependencies, SOURCE, false);
    }

    @Test
    void ordersDependenciesBeforeDependents() {
        Map<String, ModMetadata> mods = new LinkedHashMap<>();
        mods.put("b", mod("b", "a", ">=1.0.0"));
        mods.put("a", mod("a"));

        List<ModMetadata> order = resolver.resolveLoadOrder(mods);

        assertEquals(List.of("a", "b"), order.stream().map(ModMetadata::id).toList());
    }

    @Test
    void platformDependencyChecksLauncherVersion() {
        Map<String, ModMetadata> mods = new LinkedHashMap<>();
        mods.put("hello", mod("hello", "thaiflowmc", ">=0.1.0"));

        List<ModMetadata> order = resolver.resolveLoadOrder(mods);

        assertEquals(1, order.size());
    }

    @Test
    void incompatiblePlatformVersionFails() {
        Map<String, ModMetadata> mods = new LinkedHashMap<>();
        mods.put("hello", mod("hello", "thaiflowmc", ">=99.0.0"));

        assertThrows(IncompatibleDependencyException.class, () -> resolver.resolveLoadOrder(mods));
    }

    @Test
    void missingDependencyFails() {
        Map<String, ModMetadata> mods = new LinkedHashMap<>();
        mods.put("hello", mod("hello", "cool_library", ">=2.0.0"));

        assertThrows(MissingDependencyException.class, () -> resolver.resolveLoadOrder(mods));
    }

    @Test
    void incompatibleModVersionFails() {
        Map<String, ModMetadata> mods = new LinkedHashMap<>();
        mods.put("cool_library", mod("cool_library"));
        mods.put("hello", mod("hello", "cool_library", ">=2.0.0"));

        IncompatibleDependencyException e = assertThrows(
                IncompatibleDependencyException.class, () -> resolver.resolveLoadOrder(mods));
        assertTrue(e.friendlyDetail().contains("cool_library >=2.0.0"));
        assertTrue(e.friendlyDetail().contains("1.0.0"));
    }

    @Test
    void cyclicDependencyIsDetected() {
        Map<String, ModMetadata> mods = new LinkedHashMap<>();
        mods.put("a", mod("a", "b", ">=1.0.0"));
        mods.put("b", mod("b", "a", ">=1.0.0"));

        assertThrows(CyclicDependencyException.class, () -> resolver.resolveLoadOrder(mods));
    }
}
