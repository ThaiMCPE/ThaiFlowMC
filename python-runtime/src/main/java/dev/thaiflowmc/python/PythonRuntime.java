package dev.thaiflowmc.python;

import dev.thaiflowmc.api.Log;
import dev.thaiflowmc.api.ModEntrypointExecutor;
import dev.thaiflowmc.api.ModHandle;
import dev.thaiflowmc.api.ModPermissions;
import dev.thaiflowmc.api.event.EventBus;
import dev.thaiflowmc.api.item.ItemRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ThaiFlowMC's embedded Python runtime.
 *
 * <p>Creates one isolated GraalPy {@link Context} per mod, hardened
 * deny-by-default (see "Security" in docs/ARCHITECTURE.md for the full
 * rationale and its limits), installs the {@code thaiflow} bridge module
 * into it (see {@code thaiflow_bootstrap.py}), and executes the mod's
 * entrypoint. Contexts are kept alive for the process lifetime so that
 * event handlers a mod registered keep working; see "Isolation model" in
 * docs/ARCHITECTURE.md for the trade-offs of this design.
 */
public final class PythonRuntime implements ModEntrypointExecutor, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(Log.PYTHON);
    private static final String BOOTSTRAP_RESOURCE = "/thaiflow_bootstrap.py";
    private static final Duration LOAD_TIMEOUT = Duration.ofSeconds(30);
    private static final long OUTPUT_LIMIT_BYTES = 1_000_000;

    /**
     * The only host access a mod's Python code ever gets: explicitly
     * exported methods, nothing implicit. In particular: no functional
     * interface conversion (mods talk to us via {@code Value.execute}, not
     * by handing us a Python callable typed as a Java interface), no
     * mutable target-type mappings, no public-method reflection on
     * arbitrary objects. Combined with denying host class lookup (below),
     * a mod cannot discover or reach anything beyond {@link PythonBridge},
     * {@link PlayerView}, and {@link GameServerView} - see their Javadoc.
     */
    private static final HostAccess HOST_ACCESS = HostAccess.newBuilder()
            .allowAccessAnnotatedBy(HostAccess.Export.class)
            .allowMutableTargetMappings(new HostAccess.MutableTargetMapping[0])
            .build();

    private final ItemRegistry itemRegistry;
    private final Duration loadTimeout;
    private final Map<String, Context> activeContexts = new ConcurrentHashMap<>();
    private final String bootstrapSource = loadBootstrapSource();

    public PythonRuntime(ItemRegistry itemRegistry) {
        this(itemRegistry, LOAD_TIMEOUT);
    }

    /** Visible for tests that need a much shorter timeout than production's. */
    PythonRuntime(ItemRegistry itemRegistry, Duration loadTimeout) {
        this.itemRegistry = itemRegistry;
        this.loadTimeout = loadTimeout;
    }

    @Override
    public void execute(ModHandle mod, EventBus eventBus) throws Exception {
        LOG.info("Starting Python runtime for {}", mod.modId());

        Context context = Context.newBuilder("python")
                .allowHostAccess(HOST_ACCESS)
                .allowHostClassLookup(className -> false)
                // Deny-by-default: everything below is an explicit, individual
                // restriction. GraalVM's SandboxPolicy would normally enforce all
                // of this (and more) as one named policy, but on this project's
                // current GraalPy artifacts/runtime it refuses to validate for
                // the "python" language stricter than TRUSTED (confirmed
                // empirically, not a settled GraalPy-wide limitation - see
                // docs/ARCHITECTURE.md), so there is no single call that
                // replaces this list today.
                .allowIO(ioAccessFor(mod))
                .allowEnvironmentAccess(EnvironmentAccess.NONE)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .allowNativeAccess(false)
                .out(new CappingOutputStream(System.out, OUTPUT_LIMIT_BYTES))
                .err(new CappingOutputStream(System.err, OUTPUT_LIMIT_BYTES))
                // Stock JDKs (i.e. not a GraalVM JDK) lack JVMCI, so GraalPy can only
                // run in interpreter mode; that's an expected, harmless perf note for
                // mod scripts this small, not something a mod author needs to see.
                .option("engine.WarnInterpreterOnly", "false")
                .build();

        PythonBridge bridge = new PythonBridge(
                mod.modId(), mod.sourceDirectory(), mod.entrypoint(), eventBus, itemRegistry, context);

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
        Value onLoadCallbacks = runGuarded(mod, context, () -> installBridge.execute(bridge));

        String mainSource = readEntrypoint(mod);
        Source mainScript;
        try {
            mainScript = Source.newBuilder("python", mainSource, mod.entrypoint()).build();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not build source for " + mod.entrypointFile(), e);
        }
        runGuarded(mod, context, () -> {
            context.eval(mainScript);
            return null;
        });

        runOnLoadCallbacks(mod, context, onLoadCallbacks);

        activeContexts.put(mod.modId(), context);
    }

    private IOAccess ioAccessFor(ModHandle mod) {
        ModPermissions permissions = mod.permissions();
        if (permissions.filesystem()) {
            // Coarse today: broad host filesystem access, not scoped to any one
            // directory. See ModPermissions' Javadoc - most mods should ask for
            // `storage` instead. Also gates network the same way `storage` does
            // not, since a mod trusted with the whole filesystem is being run in
            // a fundamentally more trusting mode already.
            return IOAccess.newBuilder()
                    .allowHostFileAccess(true)
                    .allowHostSocketAccess(permissions.network())
                    .build();
        }
        if (permissions.storage()) {
            Path storageDir = storageDirectoryFor(mod);
            try {
                Files.createDirectories(storageDir);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not create storage directory for mod " + mod.modId(), e);
            }
            return IOAccess.newBuilder()
                    .fileSystem(new ScopedFileSystem(storageDir))
                    .allowHostSocketAccess(permissions.network())
                    .build();
        }
        return permissions.network() ? IOAccess.newBuilder().allowHostSocketAccess(true).build() : IOAccess.NONE;
    }

    /**
     * Where a mod's private storage directory lives when it declares
     * {@code [permissions] storage = true}: a sibling of the mods/
     * directory, keyed by mod id, never inside the mod's own source folder
     * or any other mod's directory.
     */
    private Path storageDirectoryFor(ModHandle mod) {
        Path modsDirectory = mod.sourceDirectory().getParent();
        Path base = modsDirectory != null ? modsDirectory.resolveSibling("mod_data") : Path.of("mod_data");
        return base.resolve(mod.modId());
    }

    private void runOnLoadCallbacks(ModHandle mod, Context context, Value onLoad) {
        if (onLoad == null || !onLoad.hasArrayElements()) {
            return;
        }
        long count = onLoad.getArraySize();
        for (long i = 0; i < count; i++) {
            Value callback = onLoad.getArrayElement(i);
            runGuarded(mod, context, () -> {
                callback.execute();
                return null;
            });
        }
    }

    /** Runs {@code action} under {@link ExecutionGuard}, translating a timeout or Python error into a friendly one. */
    private Value runGuarded(ModHandle mod, Context context, java.util.function.Supplier<Value> action) {
        Value[] result = new Value[1];
        try {
            ExecutionGuard.run(context, loadTimeout, () -> result[0] = action.get());
        } catch (PolyglotException e) {
            throw new PythonScriptException(mod.modId(), PythonErrorFormatter.format(mod.entrypoint(), e), e);
        }
        return result[0];
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
