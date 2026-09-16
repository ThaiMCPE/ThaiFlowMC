package dev.thaiflowmc.loader.error;

import java.util.List;

public final class CyclicDependencyException extends ModLoadException {

    public CyclicDependencyException(List<String> cycle) {
        super(String.join(", ", cycle), "Cyclic dependency detected:\n  " + String.join(" -> ", cycle));
    }
}
