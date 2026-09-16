package dev.thaiflowmc.loader;

import dev.thaiflowmc.api.Log;
import dev.thaiflowmc.loader.dependency.DependencyResolver;
import dev.thaiflowmc.loader.discovery.ModDiscoverer;
import dev.thaiflowmc.loader.error.DuplicateModIdException;
import dev.thaiflowmc.loader.metadata.MetadataParser;
import dev.thaiflowmc.loader.model.ModContainer;
import dev.thaiflowmc.loader.model.ModMetadata;
import dev.thaiflowmc.loader.model.Version;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers mods in a directory, parses their metadata, checks for duplicate
 * ids, and resolves a valid load order. Does not execute any mod code -
 * that is {@link ModLoader}'s job.
 */
public final class ModManager {

    private static final Logger LOG = LoggerFactory.getLogger(Log.LOADER);

    private final ModDiscoverer discoverer = new ModDiscoverer();
    private final MetadataParser parser = new MetadataParser();
    private final DependencyResolver dependencyResolver;

    public ModManager(Version platformVersion) {
        this.dependencyResolver = new DependencyResolver(platformVersion);
    }

    /**
     * Discovers, parses, and orders mods found under {@code modsDirectory}.
     *
     * @return mod containers in the order they should be loaded
     */
    public List<ModContainer> prepare(Path modsDirectory) {
        List<Path> modDirectories = discoverer.discover(modsDirectory);

        Map<String, ModMetadata> byId = new LinkedHashMap<>();
        Map<String, Path> sourceById = new LinkedHashMap<>();
        for (Path modDirectory : modDirectories) {
            ModMetadata metadata = parser.parse(modDirectory);
            Path previous = sourceById.putIfAbsent(metadata.id(), modDirectory);
            if (previous != null) {
                throw new DuplicateModIdException(metadata.id(), previous.toString(), modDirectory.toString());
            }
            byId.put(metadata.id(), metadata);
            LOG.info("Loading {} {}", metadata.id(), metadata.version());
        }

        List<ModMetadata> ordered = dependencyResolver.resolveLoadOrder(byId);
        return ordered.stream().map(ModContainer::new).collect(Collectors.toList());
    }
}
