package dev.thaiflowmc.python;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.graalvm.polyglot.Context;

/**
 * Bounds how long a single call into a mod's Python code may run, so a
 * broken or malicious mod (an infinite loop, a deliberate hang) cannot
 * freeze the caller - the server's own thread, in real Minecraft use.
 *
 * <p>The GraalPy artifacts and plain-JDK runtime this project currently
 * depends on have no reliable per-call CPU budget (see docs/ARCHITECTURE.md's
 * security section: {@code SandboxPolicy}'s stricter levels, which would
 * offer this, refuse to validate for {@code python} on this specific
 * configuration - not a settled fact about GraalPy in general). This uses
 * the one cancellation mechanism that does work everywhere: closing the
 * mod's {@link Context} from another thread
 * interrupts whatever it's currently running, at the next Truffle
 * safepoint, with a cancellation {@code PolyglotException} on the
 * executing thread. That also ends the mod's ability to handle any future
 * event - a deliberate trade-off (see {@link #run}'s Javadoc) - not a
 * per-call recoverable timeout.
 */
final class ExecutionGuard {

    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory());

    private ExecutionGuard() {
    }

    /**
     * Runs {@code action} against {@code context}; if it has not returned
     * within {@code timeout}, force-closes {@code context}, which
     * interrupts the in-progress call and permanently ends that mod's
     * ability to run any further code (including future events) - a
     * runaway mod is treated as unrecoverable, not merely slow.
     */
    static void run(Context context, Duration timeout, Runnable action) {
        ScheduledFuture<?> killer = WATCHDOG.schedule(() -> context.close(true), timeout.toMillis(), TimeUnit.MILLISECONDS);
        try {
            action.run();
        } finally {
            killer.cancel(false);
        }
    }

    private static ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "thaiflowmc-python-watchdog");
            thread.setDaemon(true);
            return thread;
        };
    }
}
