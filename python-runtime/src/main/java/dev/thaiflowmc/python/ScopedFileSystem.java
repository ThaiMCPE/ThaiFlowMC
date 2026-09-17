package dev.thaiflowmc.python;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Map;
import java.util.Set;
import org.graalvm.polyglot.io.FileSystem;

/**
 * Confines a mod's filesystem access to a single private directory, granted
 * only when its {@code mod.toml} declares {@code [permissions] storage = true}
 * (see {@link dev.thaiflowmc.api.ModPermissions}).
 *
 * <p>Every operation resolves its target path to an absolute, normalized
 * form and rejects it if that falls outside the mod's directory - whether
 * the guest script tried to escape via {@code ..} segments or simply asked
 * for an unrelated absolute path like {@code /etc/passwd}. There is no
 * separate "is this path safe" pre-check to forget: {@link #confine} is the
 * single choke point every other method routes through.
 */
final class ScopedFileSystem implements FileSystem {

    private final FileSystem delegate = FileSystem.newDefaultFileSystem();
    private final Path root;

    ScopedFileSystem(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public Path parsePath(URI uri) {
        return delegate.parsePath(uri);
    }

    @Override
    public Path parsePath(String path) {
        return delegate.parsePath(path);
    }

    @Override
    public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
        delegate.checkAccess(confine(path), modes, linkOptions);
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        delegate.createDirectory(confine(dir), attrs);
    }

    @Override
    public void delete(Path path) throws IOException {
        delegate.delete(confine(path));
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
            throws IOException {
        return delegate.newByteChannel(confine(path), options, attrs);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter)
            throws IOException {
        return delegate.newDirectoryStream(confine(dir), filter);
    }

    @Override
    public Path toAbsolutePath(Path path) {
        Path resolved = path.isAbsolute() ? path : root.resolve(path);
        return resolved.normalize();
    }

    @Override
    public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
        return delegate.toRealPath(confine(path), linkOptions);
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... linkOptions)
            throws IOException {
        return delegate.readAttributes(confine(path), attributes, linkOptions);
    }

    /** Resolves {@code path} to an absolute form and rejects it if it escapes {@link #root}. */
    private Path confine(Path path) {
        Path absolute = toAbsolutePath(path);
        if (!absolute.equals(root) && !absolute.startsWith(root)) {
            throw new SecurityException("Access outside the mod's storage directory is not permitted: " + path);
        }
        return absolute;
    }
}
