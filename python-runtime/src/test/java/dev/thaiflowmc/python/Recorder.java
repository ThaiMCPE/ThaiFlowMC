package dev.thaiflowmc.python;

import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.HostAccess;

/**
 * A minimal, explicitly-exported test double standing in for a real event
 * payload DTO. Tests fire this as an event payload and have a mod's Python
 * handler call {@link #record}, proving delivery without relying on the
 * broad reflective host access ThaiFlowMC no longer grants mods (see
 * {@link PythonRuntime#HOST_ACCESS}) - a plain, unannotated {@code
 * java.util.List} is no longer callable from Python at all.
 */
public final class Recorder {

    public final List<String> entries = new ArrayList<>();

    @HostAccess.Export
    public void record(String value) {
        entries.add(value);
    }
}
