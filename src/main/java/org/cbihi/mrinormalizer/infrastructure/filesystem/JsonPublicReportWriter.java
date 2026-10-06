package org.cbihi.mrinormalizer.infrastructure.filesystem;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.sun.nio.file.ExtendedOpenOption;

import org.cbihi.mrinormalizer.application.port.out.PublicReportWriter;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Code;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Phase;
import org.cbihi.mrinormalizer.application.provenance.manifest.ProvenancePersistenceException;
import org.cbihi.mrinormalizer.application.provenance.manifest.ProvenancePersistenceException.PublicationOutcome;
import org.cbihi.mrinormalizer.application.provenance.manifest.PublicJobReport;
import org.cbihi.mrinormalizer.domain.model.OutputTarget;

/**
 * Immutable aggregate-only public export, separate from the restricted journal.
 * Explicit absolute runtime targets require an existing parent inside OUTPUT.
 * Local default providers qualify private creation and hard-link no-replacement
 * in that parent. Windows NTFS ownership uses overlapping NOSHARE_DELETE handles,
 * not null file keys as identity. Creation time is only a substitution check.
 * Detected containment/access/identity changes fail closed. As with the accepted
 * store, deliberate owner-privileged races between revalidation and unlink are
 * outside the cooperative contract. No power-loss, directory-entry durability
 * or network filesystem guarantee is made. No plan, journal or imaging I/O occurs.
 */
public final class JsonPublicReportWriter implements PublicReportWriter {
    private static final LinkOption NOFOLLOW = LinkOption.NOFOLLOW_LINKS;
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private final Path sourceRoot;
    private final Path outputRoot;
    private final Path namespace;
    private final Path configuredNamespace;
    private final FileStore store;
    private final UserPrincipal owner;
    private final Observation rootObservation;
    private final boolean windowsNtfs;
    private final boolean posix;
    private final Hooks hooks;
    private final JsonManifestCodec codec = new JsonManifestCodec();

    public JsonPublicReportWriter(Path sourceRoot, Path outputRoot, Path manifestDirectory) {
        this(sourceRoot, outputRoot, manifestDirectory, (event, path) -> { });
    }

    JsonPublicReportWriter(Path source, Path output, Path manifestDirectory, Hooks hooks) {
        if (source == null || output == null || manifestDirectory == null || hooks == null) throw invalidArguments();
        this.hooks = hooks;
        try {
            if (source.getFileSystem() != FileSystems.getDefault() || output.getFileSystem() != FileSystems.getDefault()
                    || manifestDirectory.getFileSystem() != FileSystems.getDefault()) throw error(Code.PUBLICATION_UNAVAILABLE);
            sourceRoot = source.toRealPath(); outputRoot = output.toRealPath();
            if (!Files.isDirectory(sourceRoot, NOFOLLOW) || !Files.isDirectory(outputRoot, NOFOLLOW)
                    || sourceRoot.startsWith(outputRoot) || outputRoot.startsWith(sourceRoot)) throw error(Code.CONTAINMENT_UNPROVEN);
            configuredNamespace = manifestDirectory.toAbsolutePath().normalize();
            namespace = resolveConfigured(configuredNamespace);
            if (!namespace.startsWith(outputRoot) || namespace.equals(outputRoot)) throw error(Code.CONTAINMENT_UNPROVEN);
            store = Files.getFileStore(outputRoot);
            String provider = outputRoot.getFileSystem().provider().getClass().getName();
            windowsNtfs = provider.equals("sun.nio.fs.WindowsFileSystemProvider") && store.type().equalsIgnoreCase("ntfs");
            boolean localPosix = Set.of("sun.nio.fs.LinuxFileSystemProvider", "sun.nio.fs.MacOSXFileSystemProvider",
                    "sun.nio.fs.BsdFileSystemProvider").contains(provider)
                    && Set.of("ext2", "ext3", "ext4", "xfs", "btrfs", "overlay", "apfs", "hfs", "ufs", "zfs")
                    .contains(store.type().toLowerCase(Locale.ROOT));
            if (!windowsNtfs && !localPosix) throw error(Code.PUBLICATION_UNAVAILABLE);
            if (windowsNtfs) {
                String systemDrive = System.getenv("SystemDrive");
                if (systemDrive == null || !outputRoot.getRoot().toString().equalsIgnoreCase(systemDrive + "\\"))
                    throw error(Code.PUBLICATION_UNAVAILABLE);
            }
            posix = Files.getFileAttributeView(outputRoot, PosixFileAttributeView.class, NOFOLLOW) != null;
            if (!posix && (!windowsNtfs || Files.getFileAttributeView(outputRoot, AclFileAttributeView.class, NOFOLLOW) == null))
                throw error(Code.ACCESS_CONTROL_UNAVAILABLE);
            owner = Files.getOwner(outputRoot, NOFOLLOW);
            rootObservation = observe(directory(outputRoot));
        } catch (IOException | SecurityException unavailable) { throw error(Code.CONTAINMENT_UNPROVEN); }
        catch (UnsupportedOperationException unavailable) { throw error(Code.PUBLICATION_UNAVAILABLE); }
    }

    @Override public synchronized void write(PublicJobReport report, OutputTarget target) {
        if (report == null || target == null || target.reference() == null) throw invalidArguments();
        byte[] bytes;
        try { bytes = codec.encode(report); }
        catch (IllegalArgumentException invalid) { throw error(Code.RESOURCE_LIMIT); }
        if (bytes.length > MAX_BYTES) throw error(Code.RESOURCE_LIMIT);
        Context context;
        try { context = target(target.reference()); }
        catch (InvalidPathException invalid) { throw error(Code.INVALID_REFERENCE); }
        catch (IOException | SecurityException unavailable) { throw error(Code.CONTAINMENT_UNPROVEN); }
        catch (UnsupportedOperationException unavailable) { throw error(Code.PUBLICATION_UNAVAILABLE); }
        var codes = EnumSet.noneOf(Code.class);
        var outcome = PublicationOutcome.NOT_PUBLISHED;
        try {
            guard(context);
            if (exists(context.finalPath)) {
                if (equalBytes(context.finalPath, bytes, false)) return;
                throw error(Code.OUTPUT_CONFLICT);
            }
            qualify(context);
            hooks.at(Event.BEFORE_STAGE, context.finalPath); guard(context);
            writeStage(context, bytes);
            if (!equalBytes(context.stage.path, bytes, true)) throw error(Code.WRITE_FAILED);
            hooks.at(Event.BEFORE_PUBLISH, context.finalPath);
            guard(context); verifyOwned(context.stage);
            if (!equalBytes(context.stage.path, bytes, true)) throw error(Code.WRITE_FAILED);
            // Sole publication operation: createLink refuses replacement. No move,
            // copy-over, direct-final write or final-delete fallback exists.
            try { Files.createLink(context.finalPath, context.stage.path); }
            catch (FileAlreadyExistsException conflict) { throw error(Code.OUTPUT_CONFLICT); }
            catch (IOException | UnsupportedOperationException unavailable) { throw error(Code.PUBLICATION_UNAVAILABLE); }
            context.stage.peer = context.finalPath;
            if (!Files.isSameFile(context.stage.path, context.finalPath) || !equalBytes(context.finalPath, bytes, true))
                throw error(Code.RECOVERY_REQUIRED);
            outcome = PublicationOutcome.PUBLISHED;
            hooks.at(Event.AFTER_PUBLISH, context.finalPath);
        } catch (IOException | SecurityException failure) { codes.add(Code.WRITE_FAILED); }
        catch (UnsupportedOperationException failure) { codes.add(Code.PUBLICATION_UNAVAILABLE); }
        catch (ProvenancePersistenceException failure) { for (var fact : failure.failures()) codes.add(fact.code()); }
        if (!codes.isEmpty()) {
            try {
                hooks.at(Event.CLASSIFY, context.finalPath); guard(context);
                if (!exists(context.finalPath)) outcome = PublicationOutcome.NOT_PUBLISHED;
                else if (equalBytes(context.finalPath, bytes, false)) {
                    outcome = PublicationOutcome.PUBLISHED;
                    // Another successful identical publisher satisfies immutable
                    // idempotence. Other faults, including lost acknowledgment, remain.
                    if (codes.equals(EnumSet.of(Code.OUTPUT_CONFLICT))) codes.clear();
                } else { outcome = PublicationOutcome.NOT_PUBLISHED; codes.add(Code.OUTPUT_CONFLICT); }
            } catch (IOException | SecurityException | UnsupportedOperationException | ProvenancePersistenceException unknown) {
                outcome = PublicationOutcome.UNKNOWN; codes.add(Code.RECOVERY_REQUIRED);
            }
        }
        boolean cleanup;
        if (outcome == PublicationOutcome.UNKNOWN && context.stage != null) {
            cleanup = true; closePin(context.stage); // Retain uncertain evidence, never unlink.
        } else cleanup = cleanup(context, context.stage, Event.CLEANUP, true);
        if (cleanup) codes.add(Code.CLEANUP_FAILED);
        if (!codes.isEmpty()) throw error(codes, outcome);
    }

    private Context target(String reference) throws IOException {
        Path requested = Path.of(reference);
        if (!requested.isAbsolute() || requested.getFileName() == null || requested.getParent() == null)
            throw error(Code.INVALID_REFERENCE);
        String name = requested.getFileName().toString();
        if (name.equals(".") || name.equals("..") || name.equals("plan.json") || name.equals(".lease")
                || name.startsWith(".stage-") || name.startsWith(".probe-") || name.startsWith(".public-")
                || name.matches("[0-9]{20}\\.json")) throw error(Code.INVALID_REFERENCE);
        Path parent = requested.getParent().toRealPath();
        if (!parent.startsWith(outputRoot) || parent.startsWith(sourceRoot) || parent.startsWith(namespace))
            throw error(Code.CONTAINMENT_UNPROVEN);
        Path finalPath = parent.resolve(name);
        if (finalPath.equals(namespace) || finalPath.startsWith(namespace)) throw error(Code.CONTAINMENT_UNPROVEN);
        var context = new Context(finalPath);
        for (Path directory = parent; ; directory = directory.getParent()) {
            context.directories.put(directory, observe(directory(directory)));
            if (directory.equals(outputRoot)) break;
            if (directory == null || directory.getParent() == null) throw error(Code.CONTAINMENT_UNPROVEN);
        }
        if (!same(rootObservation, directory(outputRoot))) throw error(Code.CONTAINMENT_UNPROVEN);
        // A final entry is never followed, even when its referent has equal bytes.
        if (exists(finalPath)) regular(finalPath, false);
        return context;
    }

    private static Path resolveConfigured(Path configured) throws IOException {
        var missing = new ArrayList<Path>(); Path current = configured;
        while (true) {
            try { Files.readAttributes(current, BasicFileAttributes.class, NOFOLLOW); break; }
            catch (NoSuchFileException absent) {
                if (current.getParent() == null) throw error(Code.CONTAINMENT_UNPROVEN);
                missing.add(current.getFileName()); current = current.getParent();
            }
        }
        Path canonical = current.toRealPath();
        for (int i = missing.size() - 1; i >= 0; i--) canonical = canonical.resolve(missing.get(i));
        return canonical;
    }

    private void guard(Context context) throws IOException {
        if (!same(rootObservation, directory(outputRoot)) || !resolveConfigured(configuredNamespace).equals(namespace))
            throw error(Code.CONTAINMENT_UNPROVEN);
        for (var entry : context.directories.entrySet())
            if (!same(entry.getValue(), directory(entry.getKey()))) throw error(Code.CONTAINMENT_UNPROVEN);
    }

    private BasicFileAttributes directory(Path path) throws IOException {
        var attrs = Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW);
        if (!attrs.isDirectory() || attrs.isSymbolicLink() || !path.startsWith(outputRoot)
                || !path.toRealPath().equals(path) || !usable(attrs)) throw error(Code.CONTAINMENT_UNPROVEN);
        accessAndStore(path); return attrs;
    }

    private BasicFileAttributes regular(Path path, boolean privateAccess) throws IOException {
        var attrs = Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW);
        if (!attrs.isRegularFile() || attrs.isSymbolicLink() || !path.startsWith(outputRoot) || path.startsWith(namespace)
                || !path.toRealPath().equals(path) || !usable(attrs)) throw error(Code.CONTAINMENT_UNPROVEN);
        accessAndStore(path);
        if (privateAccess) verifyPrivate(path);
        return attrs;
    }

    private void accessAndStore(Path path) throws IOException {
        if (!Files.getFileStore(path).equals(store)) throw error(Code.PUBLICATION_UNAVAILABLE);
        if (!Files.getOwner(path, NOFOLLOW).equals(owner)) throw error(Code.ACCESS_CONTROL_UNAVAILABLE);
    }

    private void verifyPrivate(Path path) throws IOException {
        if (posix) {
            var view = Files.getFileAttributeView(path, PosixFileAttributeView.class, NOFOLLOW);
            if (view == null || !view.readAttributes().permissions().equals(PosixFilePermissions.fromString("rw-------")))
                throw error(Code.ACCESS_CONTROL_UNAVAILABLE);
        } else {
            var view = Files.getFileAttributeView(path, AclFileAttributeView.class, NOFOLLOW);
            if (view == null) throw error(Code.ACCESS_CONTROL_UNAVAILABLE);
            boolean full = false;
            for (var entry : view.getAcl()) {
                if (entry.type() == AclEntryType.ALLOW) {
                    if (!entry.principal().equals(owner)) throw error(Code.ACCESS_CONTROL_UNAVAILABLE);
                    if (!entry.flags().contains(AclEntryFlag.INHERIT_ONLY)
                            && entry.permissions().containsAll(EnumSet.allOf(AclEntryPermission.class))) full = true;
                }
                if (entry.type() == AclEntryType.DENY && entry.principal().equals(owner)) throw error(Code.ACCESS_CONTROL_UNAVAILABLE);
            }
            if (!full) throw error(Code.ACCESS_CONTROL_UNAVAILABLE);
        }
    }

    private FileAttribute<?>[] privateAttributes() {
        if (posix) return new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))};
        List<AclEntry> acl = List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build());
        return new FileAttribute<?>[] {new FileAttribute<List<AclEntry>>() {
            @Override public String name() { return "acl:acl"; }
            @Override public List<AclEntry> value() { return acl; }
        }};
    }

    private Set<OpenOption> options(OpenOption... values) {
        var options = new HashSet<OpenOption>(Arrays.asList(values));
        if (windowsNtfs) options.add(ExtendedOpenOption.NOSHARE_DELETE);
        return options;
    }

    private boolean equalBytes(Path path, byte[] expected, boolean privateAccess) throws IOException {
        var before = regular(path, privateAccess);
        if (before.size() != expected.length) return false;
        try (var channel = FileChannel.open(path, options(StandardOpenOption.READ, NOFOLLOW))) {
            if (!same(observe(before), regular(path, privateAccess))) throw error(Code.RECOVERY_REQUIRED);
            byte[] actual = new byte[expected.length]; var buffer = ByteBuffer.wrap(actual);
            while (buffer.hasRemaining()) if (channel.read(buffer) < 0) throw error(Code.RECOVERY_REQUIRED);
            if (channel.read(ByteBuffer.allocate(1)) != -1) throw error(Code.RECOVERY_REQUIRED);
            var after = regular(path, privateAccess);
            if (!same(observe(before), after) || before.size() != after.size()
                    || !before.lastModifiedTime().equals(after.lastModifiedTime())) throw error(Code.RECOVERY_REQUIRED);
            return Arrays.equals(actual, expected);
        }
    }

    private void qualify(Context context) throws IOException {
        var owned = new ArrayList<Artifact>(); var codes = EnumSet.noneOf(Code.class);
        try {
            hooks.at(Event.QUALIFY, context.finalPath); guard(context);
            String token = UUID.randomUUID().toString();
            Artifact first = probe(context, ".public-probe-" + token + "-a", new byte[] {1, 2, 3}, owned);
            Artifact second = probe(context, ".public-probe-" + token + "-b", new byte[] {4, 5, 6}, owned);
            Path link = context.finalPath.resolveSibling(".public-probe-" + token + "-link");
            guard(context); Files.createLink(link, first.path);
            var published = new Artifact(link); owned.add(published); capture(published); published.peer = first.path;
            if (!Files.isSameFile(first.path, link) || !equalBytes(link, new byte[] {1, 2, 3}, true)) throw error(Code.PUBLICATION_UNAVAILABLE);
            boolean refused = false;
            try { Files.createLink(link, second.path); }
            catch (FileAlreadyExistsException expected) { refused = true; }
            if (!refused || !Files.isSameFile(first.path, link) || !equalBytes(link, new byte[] {1, 2, 3}, true))
                throw error(Code.PUBLICATION_UNAVAILABLE);
        } catch (IOException | UnsupportedOperationException | SecurityException failure) { codes.add(Code.PUBLICATION_UNAVAILABLE); }
        catch (ProvenancePersistenceException failure) { for (var fact : failure.failures()) codes.add(fact.code()); }
        // Windows sharing restrictions apply to every link of an inode. Prove all
        // artifacts while pinned, then release ALL pins before unlinking any link.
        for (var artifact : owned) {
            try { guard(context); verifyOwned(artifact); artifact.qualifiedCleanup = true; }
            catch (IOException | UnsupportedOperationException | SecurityException | ProvenancePersistenceException failure) { codes.add(Code.CLEANUP_FAILED); }
        }
        for (var artifact : owned) if (closePin(artifact)) { artifact.qualifiedCleanup = false; codes.add(Code.CLEANUP_FAILED); }
        for (int i = owned.size() - 1; i >= 0; i--) {
            var artifact = owned.get(i);
            if (cleanup(context, artifact, Event.PROBE_CLEANUP, false)) codes.add(Code.CLEANUP_FAILED);
        }
        if (!codes.isEmpty()) throw error(codes, PublicationOutcome.NOT_PUBLISHED);
    }

    private Artifact probe(Context context, String name, byte[] bytes, List<Artifact> owned) throws IOException {
        guard(context); Path path = context.finalPath.resolveSibling(name);
        try (var channel = openStage(path)) {
            var artifact = new Artifact(path); owned.add(artifact); capture(artifact);
            var buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true); return artifact;
        }
    }

    private FileChannel openStage(Path path) throws IOException {
        try { return FileChannel.open(path, options(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, NOFOLLOW), privateAttributes()); }
        catch (UnsupportedOperationException unavailable) { throw error(Code.ACCESS_CONTROL_UNAVAILABLE); }
    }

    private void writeStage(Context context, byte[] bytes) throws IOException {
        Path path = context.finalPath.resolveSibling(".public-stage-" + UUID.randomUUID() + ".tmp");
        FileChannel channel = null; var codes = EnumSet.noneOf(Code.class);
        try {
            guard(context); channel = openStage(path); context.stage = new Artifact(path); capture(context.stage);
            hooks.at(Event.AFTER_STAGE, path);
            var buffer = ByteBuffer.wrap(bytes); int full = buffer.limit(); buffer.limit(Math.max(1, bytes.length / 2));
            while (buffer.hasRemaining()) channel.write(buffer);
            hooks.at(Event.WRITE, path); buffer.limit(full); while (buffer.hasRemaining()) channel.write(buffer);
            hooks.at(Event.BEFORE_FORCE, path); channel.force(true); hooks.at(Event.AFTER_FORCE, path);
        } catch (IOException | SecurityException failure) { codes.add(Code.WRITE_FAILED); }
        catch (UnsupportedOperationException failure) { codes.add(Code.PUBLICATION_UNAVAILABLE); }
        catch (ProvenancePersistenceException failure) { for (var fact : failure.failures()) codes.add(fact.code()); }
        finally {
            if (channel != null) {
                try { hooks.at(Event.STAGE_CLOSE, path); }
                catch (IOException | SecurityException | UnsupportedOperationException failure) { codes.add(Code.CLEANUP_FAILED); }
                try { channel.close(); } catch (IOException failure) { codes.add(Code.CLEANUP_FAILED); }
            }
        }
        if (!codes.isEmpty()) throw error(codes, PublicationOutcome.NOT_PUBLISHED);
    }

    private void capture(Artifact artifact) throws IOException {
        var before = regular(artifact.path, true); artifact.observation = observe(before);
        if (before.fileKey() == null) artifact.pin = FileChannel.open(artifact.path, options(StandardOpenOption.READ, NOFOLLOW));
        if (!same(artifact.observation, regular(artifact.path, true))) throw error(Code.CONTAINMENT_UNPROVEN);
    }

    private void verifyOwned(Artifact artifact) throws IOException {
        if (artifact.observation == null || !same(artifact.observation, regular(artifact.path, true))) throw error(Code.CLEANUP_FAILED);
        if (artifact.observation.key == null && (artifact.pin == null || !artifact.pin.isOpen())) throw error(Code.CLEANUP_FAILED);
        if (artifact.peer != null && (!Files.isSameFile(artifact.path, artifact.peer) || !regular(artifact.peer, true).isRegularFile()))
            throw error(Code.CLEANUP_FAILED);
    }

    private boolean cleanup(Context context, Artifact artifact, Event event, boolean livePin) {
        if (artifact == null) return false;
        try {
            guard(context);
            if (livePin) verifyOwned(artifact);
            else if (!artifact.qualifiedCleanup) return true;
            hooks.at(event, artifact.path);
            if (livePin) verifyOwned(artifact);
            if (closePin(artifact)) return true;
            if (livePin) hooks.at(Event.AFTER_UNPIN, artifact.path);
            guard(context);
            if (artifact.observation == null || !same(artifact.observation, regular(artifact.path, true))) return true;
            if (artifact.peer != null && !Files.isSameFile(artifact.path, artifact.peer)) return true;
            Files.delete(artifact.path); return false;
        } catch (IOException | UnsupportedOperationException | SecurityException | ProvenancePersistenceException failure) { return true; }
        finally { closePin(artifact); }
    }

    private static boolean closePin(Artifact artifact) {
        if (artifact.pin == null) return false;
        try { artifact.pin.close(); artifact.pin = null; return false; }
        catch (IOException failure) { return true; }
    }

    private boolean usable(BasicFileAttributes attrs) {
        return attrs.fileKey() != null || windowsNtfs && !attrs.creationTime().equals(FileTime.fromMillis(0));
    }
    private static Observation observe(BasicFileAttributes attrs) { return new Observation(attrs.fileKey(), attrs.creationTime()); }
    private boolean same(Observation prior, BasicFileAttributes current) {
        if (prior.key != null) return prior.key.equals(current.fileKey());
        return windowsNtfs && current.fileKey() == null && prior.created.equals(current.creationTime());
    }
    private static boolean exists(Path path) throws IOException {
        try { Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW); return true; }
        catch (NoSuchFileException absent) { return false; }
    }
    private static IllegalArgumentException invalidArguments() { return new IllegalArgumentException("Invalid public report arguments"); }
    private static ProvenancePersistenceException error(Code code) { return error(EnumSet.of(code), PublicationOutcome.NOT_PUBLISHED); }
    private static ProvenancePersistenceException error(Set<Code> codes, PublicationOutcome outcome) {
        return new ProvenancePersistenceException(codes.stream().map(code -> new ManifestFailure(Phase.PERSISTENCE, code)).toList(),
                outcome, Optional.empty()); // Public export can NEVER produce a job checkpoint receipt.
    }
    private record Observation(Object key, FileTime created) { }
    private static final class Artifact {
        private final Path path;
        private Observation observation;
        private FileChannel pin;
        private Path peer;
        private boolean qualifiedCleanup;
        private Artifact(Path path) { this.path = path; }
    }
    private static final class Context {
        private final Path finalPath;
        private final LinkedHashMap<Path, Observation> directories = new LinkedHashMap<>();
        private Artifact stage;
        private Context(Path finalPath) { this.finalPath = finalPath; }
    }
    enum Event { QUALIFY, BEFORE_STAGE, AFTER_STAGE, WRITE, BEFORE_FORCE, AFTER_FORCE, STAGE_CLOSE,
        BEFORE_PUBLISH, AFTER_PUBLISH, CLASSIFY, CLEANUP, AFTER_UNPIN, PROBE_CLEANUP }
    @FunctionalInterface interface Hooks { void at(Event event, Path path) throws IOException; }
}
