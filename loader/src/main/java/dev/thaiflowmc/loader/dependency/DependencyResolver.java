package dev.thaiflowmc.loader.dependency;

import dev.thaiflowmc.loader.error.CyclicDependencyException;
import dev.thaiflowmc.loader.error.IncompatibleDependencyException;
import dev.thaiflowmc.loader.error.MissingDependencyException;
import dev.thaiflowmc.loader.model.ModDependency;
import dev.thaiflowmc.loader.model.ModMetadata;
import dev.thaiflowmc.loader.model.Version;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates each mod's declared dependencies against the mods actually
 * present (plus the running ThaiFlowMC platform version) and orders mods so
 * every dependency loads before its dependent.
 */
public final class DependencyResolver {

    /** Special dependency id referring to the ThaiFlowMC platform itself. */
    public static final String PLATFORM_ID = "thaiflowmc";

    private final Version platformVersion;

    public DependencyResolver(Version platformVersion) {
        this.platformVersion = platformVersion;
    }

    /**
     * @param mods mods keyed by id, already checked for duplicate ids
     * @return mods in an order where every dependency precedes its dependent
     */
    public List<ModMetadata> resolveLoadOrder(Map<String, ModMetadata> mods) {
        for (ModMetadata mod : mods.values()) {
            for (ModDependency dependency : mod.dependencies()) {
                verifyDependency(mod, dependency, mods);
            }
        }
        return topologicalSort(mods);
    }

    private void verifyDependency(ModMetadata mod, ModDependency dependency, Map<String, ModMetadata> mods) {
        Version installed;
        if (dependency.modId().equals(PLATFORM_ID)) {
            installed = platformVersion;
        } else {
            ModMetadata dependencyMod = mods.get(dependency.modId());
            if (dependencyMod == null) {
                throw new MissingDependencyException(mod.name(), dependency.modId(), dependency.requirement().toString());
            }
            installed = dependencyMod.version();
        }
        if (!dependency.requirement().matches(installed)) {
            throw new IncompatibleDependencyException(
                    mod.name(), dependency.modId(), dependency.requirement().toString(), installed.toString());
        }
    }

    private List<ModMetadata> topologicalSort(Map<String, ModMetadata> mods) {
        Map<String, Integer> state = new HashMap<>();
        List<ModMetadata> ordered = new ArrayList<>();
        for (String id : mods.keySet()) {
            visit(id, mods, state, ordered, new ArrayList<>());
        }
        return ordered;
    }

    private void visit(
            String id,
            Map<String, ModMetadata> mods,
            Map<String, Integer> state,
            List<ModMetadata> ordered,
            List<String> path) {
        Integer current = state.get(id);
        if (current != null && current == 2) {
            return;
        }
        if (current != null && current == 1) {
            List<String> cycle = new ArrayList<>(path.subList(path.indexOf(id), path.size()));
            cycle.add(id);
            throw new CyclicDependencyException(cycle);
        }
        ModMetadata mod = mods.get(id);
        if (mod == null) {
            return;
        }
        state.put(id, 1);
        path.add(id);
        for (ModDependency dependency : mod.dependencies()) {
            if (!dependency.modId().equals(PLATFORM_ID)) {
                visit(dependency.modId(), mods, state, ordered, path);
            }
        }
        path.remove(path.size() - 1);
        state.put(id, 2);
        ordered.add(mod);
    }
}
