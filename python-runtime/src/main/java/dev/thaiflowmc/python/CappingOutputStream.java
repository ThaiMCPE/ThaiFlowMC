package dev.thaiflowmc.python;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Wraps an {@link OutputStream} (in practice, the process's real stdout/
 * stderr) and throws once a mod has written more than {@code limitBytes}
 * to it over its whole lifetime - containment against a mod that floods
 * output (accidentally, e.g. a runaway print loop, or deliberately) rather
 * than a per-call limit.
 */
final class CappingOutputStream extends OutputStream {

    private final OutputStream delegate;
    private final long limitBytes;
    private final AtomicLong written = new AtomicLong();

    CappingOutputStream(OutputStream delegate, long limitBytes) {
        this.delegate = delegate;
        this.limitBytes = limitBytes;
    }

    @Override
    public void write(int b) throws IOException {
        checkLimit(1);
        delegate.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        checkLimit(len);
        delegate.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    private void checkLimit(int additional) throws IOException {
        long total = written.addAndGet(additional);
        if (total > limitBytes) {
            throw new IOException("Output limit of " + limitBytes + " bytes exceeded");
        }
    }
}
