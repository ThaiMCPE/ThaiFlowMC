package dev.thaiflowmc.python;

import dev.thaiflowmc.api.Log;
import dev.thaiflowmc.api.ModEntrypointExecutor;
import dev.thaiflowmc.api.ModHandle;
import dev.thaiflowmc.api.event.EventBus;
import dev.thaiflowmc.api.item.ItemRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ThaiFlowMC's embedded Python runtime.
 *
 * <p>Creates one isolated GraalPy {@link Context} per mod, installs the
 * {@code thaiflow} bridge module into it (see {@code thaiflow_bootstrap.py}),
 * and executes the mod's entrypoint. Contexts are kept alive for the process
 * lifetime so that event handlers a mod registered keep working; see
 * {@code docs/ARCHITECTURE.md} for the isolation trade-offs of this design.
 */
public final class PythonRuntime implements ModEntrypointExecutor, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(Log.PYTHON);
    private static final String BOOTSTRAP_RESOURCE = "/thaiflow_bootstrap.py";

    private final ItemRegistry itemRegistry;
    private final Map<String, Context> activeContexts = new ConcurrentHashMap<>();
    private final String bootstrapSource = loadBootstrapSource();

    public PythonRuntime(ItemRegistry itemRegistry) {
        this.itemRegistry = itemRegistry;
    }

    @Override
    public void execute(ModHandle mod, EventBus eventBus) throws Exception {
        LOG.info("Starting Python runtime for {}", mod.modId());

        Context context = Context.newBuilder("python")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> false)
                .out(System.out)
                .err(System.err)
                // Stock JDKs (i.e. not a GraalVM JDK) lack JVMCI, so GraalPy can only
                // run in interpreter mode; that's an expected, harmless perf note for
                // mod scripts this small, not something a mod author needs to see.
                .option("engine.WarnInterpreterOnly", "false")
                .build();

        PythonBridge bridge =
                new PythonBridge(mod.modId(), mod.sourceDirectory(), mod.entrypoint(), eventBus, itemRegistry);

        // The bootstrap script's last expression is the `_install_thaiflow_module`
        // function itself (not a call); eval() returns it as a callable Value, which
        // we then invoke directly with the bridge as its argument. This avoids
        // GraalPy's cross-language bindings (which Python code can only see via
        // `import polyglot; polyglot.import_value(...)`, not as a plain global) in
        // favor of an ordinary function call.
        Value installBridge;
        try {
            installBridge = context.eval(
                    Source.newBuilder("python", bootstrapSource, "thaiflow_bootstrap.py").internal(true).build());
        } catch (PolyglotException e) {
            throw new IllegalStateException("ThaiFlowMC internal error initializing the Python bridge", e);
        }
        Value onLoadCallbacks = installBridge.execute(bridge);

        String mainSource = readEntrypoint(mod);
        try {
            context.eval(Source.newBuilder("python", mainSource, mod.entrypoint()).build());
        } catch (PolyglotException e) {
            throw new PythonScriptException(mod.modId(), PythonErrorFormatter.format(mod.entrypoint(), e), e);
        }

        runOnLoadCallbacks(mod, onLoadCallbacks);

        activeContexts.put(mod.modId(), context);
    }

    private void runOnLoadCallbacks(ModHandle mod, Value onLoad) {
        if (onLoad == null || !onLoad.hasArrayElements()) {
            return;
        }
        long count = onLoad.getArraySize();
        for (long i = 0; i < count; i++) {
            Value callback = onLoad.getArrayElement(i);
            try {
                callback.execute();
            } catch (PolyglotException e) {
                throw new PythonScriptException(mod.modId(), PythonErrorFormatter.format(mod.entrypoint(), e), e);
            }
        }
    }

    private String readEntrypoint(ModHandle mod) {
        try {
            return Files.readString(mod.entrypointFile(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read entrypoint " + mod.entrypointFile(), e);
        }
    }

    private static String loadBootstrapSource() {
        try (InputStream in = PythonRuntime.class.getResourceAsStream(BOOTSTRAP_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing bundled resource " + BOOTSTRAP_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load " + BOOTSTRAP_RESOURCE, e);
        }
    }

    /** Closes every mod context this runtime created. */
    @Override
    public void close() {
        for (Context context : activeContexts.values()) {
            context.close(true);
        }
        activeContexts.clear();
    }
}
