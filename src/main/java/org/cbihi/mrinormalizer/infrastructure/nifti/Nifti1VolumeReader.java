package org.cbihi.mrinormalizer.infrastructure.nifti;

import static org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadFailure.*;
import static org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadFinding.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.PushbackInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.cbihi.mrinormalizer.application.dataset.nifti.*;
import org.cbihi.mrinormalizer.application.port.out.NiftiVolumeReader;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;
import org.cbihi.mrinormalizer.domain.model.ImageVolume;
import org.cbihi.mrinormalizer.domain.model.ImmutableVoxelData;

/**
 * Read-only adapter. Continuity observations are not snapshot isolation or digest identity.
 * Configured root aliases are resolved once; discovered entries are never followed.
 */
public final class Nifti1VolumeReader implements NiftiVolumeReader {
    private final Path root;
    private final BasicFileAttributes configuredRoot;
    private final NiftiReadLimits limits;
    private final Access access;

    public Nifti1VolumeReader(Path inputRoot, NiftiReadLimits limits) {
        this(inputRoot, limits, new NativeAccess());
    }

    Nifti1VolumeReader(Path inputRoot, NiftiReadLimits limits, Access access) {
        if (inputRoot == null || limits == null || access == null) throw new IllegalArgumentException("Reading configuration is required");
        try {
            this.root = inputRoot.toRealPath();
            // Configuration identity is native; seams only control observations made by read().
            configuredRoot = Files.readAttributes(root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!configuredRoot.isDirectory()) throw new IllegalArgumentException("Invalid input root");
        } catch (IOException | SecurityException e) { throw new IllegalArgumentException("Invalid input root"); }
        this.limits = limits; this.access = access;
    }

    @Override public NiftiReadResult read(RelativePath source) {
        if (source == null) throw new IllegalArgumentException("Source reference is required");
        var failures = EnumSet.noneOf(NiftiReadFailure.class);
        var findings = EnumSet.noneOf(NiftiReadFinding.class);
        if (source.root() != RelativePath.Root.SOURCE) {
            return new NiftiReadResult(source, Optional.empty(), List.of(PATH_OUTSIDE_ROOT), List.of());
        }
        ImageVolume volume = null;
        InputStream owned = null;
        StrictGzipInputStream gzip = null;
        List<Observation> before = null;
        boolean opened = false;
        Path path;
        try { path = root.resolve(source.path()).normalize(); }
        catch (java.nio.file.InvalidPathException e) {
            return new NiftiReadResult(source, Optional.empty(), List.of(INPUT_UNAVAILABLE), List.of());
        }
        try {
            interrupted();
            require(path.startsWith(root) && !path.equals(root), PATH_OUTSIDE_ROOT);
            before = observe(path, findings);
            require(same(configuredRoot, before.getFirst().attributes()), SOURCE_CHANGED);
            require(before.getLast().attributes().size() <= limits.maxEncodedBytes(), RESOURCE_LIMIT);
            try { owned = access.open(path); opened = true; }
            catch (UnsupportedOperationException e) { throw new Fault(CONTAINMENT_UNPROVEN); }
            // The byte limit sits below all buffering/inflation. Two detection bytes are pushed back.
            var encoded = new Counted(owned, limits.maxEncodedBytes());
            var detection = new PushbackInputStream(encoded, 2);
            byte[] signature = new byte[2];
            int got = 0;
            while (got < 2) {
                int n = progressRead(detection, signature, got, 2 - got);
                if (n == -1) break;
                got += n;
            }
            detection.unread(signature, 0, got);
            InputStream content = detection;
            if (got == 2 && signature[0] == 31 && signature[1] == (byte) 139) {
                gzip = new StrictGzipInputStream(detection, limits.maxGzipHeaderBytes()); content = gzip;
            }
            var decoded = new Counted(content, limits.maxDecodedBytes());
            byte[] header = new byte[348]; readFully(decoded, header, 0, header.length);
            Nifti1Header h = new Nifti1Header(header, limits, findings);
            extensions(decoded, h, findings);
            // Reassert the allocation boundary at the sole working-array allocation.
            require(Math.addExact(Math.multiplyExact(16L, h.count), 1_048_576L) <= limits.maxWorkingBytes(), RESOURCE_LIMIT);
            long[] values = new long[h.count];
            byte[] chunk = new byte[8192];
            int index = 0;
            while (index < h.count) {
                interrupted();
                int n = Math.min(chunk.length / h.bytesPerVoxel, h.count - index);
                readFully(decoded, chunk, 0, n * h.bytesPerVoxel);
                ByteBuffer b = ByteBuffer.wrap(chunk).order(h.order);
                for (int i = 0; i < n; i++) {
                    values[index++] = switch (h.type) {
                        case UINT8 -> Byte.toUnsignedInt(b.get());
                        case INT8 -> b.get();
                        case UINT16 -> Short.toUnsignedInt(b.getShort());
                        case INT16 -> b.getShort();
                    };
                }
            }
            require(decoded.read() == -1, TRAILING_DATA);
            volume = new ImageVolume(h.geometry, new ImmutableVoxelData(h.width, h.height, h.depth, h.type, values), h.scaling);
        } catch (Fault e) { failures.add(e.failure); }
        catch (ClosedByInterruptException | InterruptedIOException e) {
            Thread.currentThread().interrupt(); failures.add(INTERRUPTED);
        } catch (IOException | SecurityException e) {
            failures.add(Thread.currentThread().isInterrupted() ? INTERRUPTED : opened ? READ_FAILED : INPUT_UNAVAILABLE);
        } finally {
            // Independent release attempts; cleanup may not erase a primary failure.
            if (gzip != null) gzip.close();
            if (owned != null) try { owned.close(); }
            catch (IOException | SecurityException e) {
                if (e instanceof InterruptedIOException || e instanceof ClosedByInterruptException) {
                    Thread.currentThread().interrupt(); failures.add(INTERRUPTED);
                }
                failures.add(CLOSE_FAILED);
            }
            if (before != null) {
                try {
                    List<Observation> after = observe(path, findings);
                    require(after.size() == before.size(), SOURCE_CHANGED);
                    for (int i = 0; i < before.size(); i++) require(same(before.get(i).attributes(), after.get(i).attributes()), SOURCE_CHANGED);
                } catch (IOException | SecurityException e) { failures.add(SOURCE_CHANGED); }
            }
        }
        if (Thread.currentThread().isInterrupted()) failures.add(INTERRUPTED);
        if (!failures.isEmpty()) volume = null;
        return new NiftiReadResult(source, Optional.ofNullable(volume), List.copyOf(failures), List.copyOf(findings));
    }

    private List<Observation> observe(Path path, EnumSet<NiftiReadFinding> findings) throws IOException {
        var result = new ArrayList<Observation>();
        Path current = root;
        result.add(observation(current, true, findings));
        Path relative = root.relativize(path);
        for (int i = 0; i < relative.getNameCount(); i++) {
            current = current.resolve(relative.getName(i));
            result.add(observation(current, i != relative.getNameCount() - 1, findings));
        }
        return result;
    }

    private Observation observation(Path p, boolean directory, EnumSet<NiftiReadFinding> findings) throws IOException {
        BasicFileAttributes a;
        try { a = access.attributes(p); }
        catch (UnsupportedOperationException e) { throw new Fault(CONTAINMENT_UNPROVEN); }
        require(!a.isSymbolicLink(), SYMLINK_DISALLOWED);
        require(directory ? a.isDirectory() : a.isRegularFile(), directory ? CONTAINMENT_UNPROVEN : SPECIAL_FILE_UNSUPPORTED);
        if (a.fileKey() == null) findings.add(WEAK_SOURCE_IDENTITY);
        return new Observation(p, a);
    }

    private static boolean same(BasicFileAttributes a, BasicFileAttributes b) {
        Object ak = a.fileKey(), bk = b.fileKey();
        return (ak == null || bk == null || ak.equals(bk)) && a.isDirectory() == b.isDirectory()
                && a.isRegularFile() == b.isRegularFile() && a.isSymbolicLink() == b.isSymbolicLink()
                && a.size() == b.size() && a.lastModifiedTime().equals(b.lastModifiedTime());
    }

    private static void extensions(InputStream input, Nifti1Header h, EnumSet<NiftiReadFinding> findings) throws IOException {
        byte[] extender = new byte[4]; readFully(input, extender, 0, 4);
        require(extender[1] == 0 && extender[2] == 0 && extender[3] == 0, INVALID_EXTENSION);
        long remaining = h.offset - 352;
        byte[] scratch = new byte[8192];
        if (extender[0] == 0) { discard(input, remaining, scratch); return; }
        findings.add(EXTENSIONS_NOT_INTERPRETED);
        while (remaining > 0) {
            require(remaining >= 16, INVALID_EXTENSION);
            readFully(input, scratch, 0, 8);
            var b = ByteBuffer.wrap(scratch).order(h.order);
            int size = b.getInt(), code = b.getInt();
            require(size >= 16 && size % 16 == 0 && size <= remaining && code >= 0, INVALID_EXTENSION);
            discard(input, size - 8L, scratch); remaining -= size;
        }
    }

    private static void discard(InputStream in, long n, byte[] scratch) throws IOException {
        while (n > 0) { int part = (int) Math.min(n, scratch.length); readFully(in, scratch, 0, part); n -= part; }
    }

    static void readFully(InputStream in, byte[] b, int off, int len) throws IOException {
        while (len > 0) { int n = progressRead(in, b, off, len); require(n >= 0, TRUNCATED_INPUT); off += n; len -= n; }
    }

    static int progressRead(InputStream in, byte[] b, int off, int len) throws IOException {
        for (int tries = 0; tries < 16; tries++) { interrupted(); int n = in.read(b, off, len); if (n != 0) return n; }
        throw new Fault(READ_FAILED);
    }

    static void interrupted() throws Fault { require(!Thread.currentThread().isInterrupted(), INTERRUPTED); }
    static void require(boolean valid, NiftiReadFailure failure) throws Fault { if (!valid) throw new Fault(failure); }

    static final class Fault extends IOException {
        final NiftiReadFailure failure;
        Fault(NiftiReadFailure failure) { super("Controlled reading failure"); this.failure = failure; }
    }

    /** Mechanical seams for deterministic tests; not an additional public API. */
    interface Access {
        BasicFileAttributes attributes(Path path) throws IOException;
        InputStream open(Path path) throws IOException;
    }

    private static final class NativeAccess implements Access {
        @Override public BasicFileAttributes attributes(Path path) throws IOException {
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        }
        @Override public InputStream open(Path path) throws IOException {
            return Channels.newInputStream(Files.newByteChannel(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
        }
    }

    private record Observation(Path path, BasicFileAttributes attributes) { }

    /** Allows at most one byte beyond the bound, solely to distinguish EOF from excess. */
    private static final class Counted extends InputStream {
        private final InputStream input;
        private final long limit;
        private long count;
        private final byte[] one = new byte[1];
        Counted(InputStream input, long limit) { this.input = input; this.limit = limit; }
        @Override public int read() throws IOException { return read(one, 0, 1) == -1 ? -1 : Byte.toUnsignedInt(one[0]); }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            java.util.Objects.checkFromIndexSize(off, len, b.length);
            if (len == 0) return 0;
            interrupted();
            if (count == limit) { int extra = progressRead(input, one, 0, 1); require(extra == -1, RESOURCE_LIMIT); return -1; }
            int n = progressRead(input, b, off, (int) Math.min(len, limit - count));
            if (n > 0) count += n;
            return n;
        }
    }
}
