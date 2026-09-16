package dev.thaiflowmc.loader.discovery;

import dev.thaiflowmc.api.Log;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scans a {@code mods/} directory for candidate mod folders. A folder is a
 * candidate mod if it has a {@code mod.toml} or, for zero-config mods, a
 * bare {@code main.py}.
 */
public final class ModDiscoverer {

    private static final Logger LOG = LoggerFactory.getLogger(Log.LOADER);

    public List<Path> discover(Path modsDirectory) {
        List<Path> found = new ArrayList<>();
        if (!Files.isDirectory(modsDirectory)) {
            LOG.info("Mods directory {} does not exist; no mods to load", modsDirectory);
            return found;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDirectory)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry) && looksLikeMod(entry)) {
                    found.add(entry);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not scan mods directory: " + modsDirectory, e);
        }
        found.sort(Comparator.comparing(p -> p.getFileName().toString()));
        LOG.info("Found {} mod{}", found.size(), found.size() == 1 ? "" : "s");
        return found;
    }

    private boolean looksLikeMod(Path directory) {
        return Files.exists(directory.resolve("mod.toml")) || Files.exists(directory.resolve("main.py"));
    }
}
