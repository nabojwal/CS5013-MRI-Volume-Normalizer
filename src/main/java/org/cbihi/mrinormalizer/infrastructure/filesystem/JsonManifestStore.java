package org.cbihi.mrinormalizer.infrastructure.filesystem;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.sun.nio.file.ExtendedOpenOption;

import org.cbihi.mrinormalizer.application.port.out.ManifestStore;
import org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Code;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Phase;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestReceipt;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestReplay;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestState;
import org.cbihi.mrinormalizer.application.provenance.manifest.ProvenanceManifest;
import org.cbihi.mrinormalizer.application.provenance.manifest.ProvenancePersistenceException;
import org.cbihi.mrinormalizer.application.provenance.manifest.ProvenancePersistenceException.PublicationOutcome;

/**
 * Restricted, job-scoped canonical plan/delta persistence. A cooperative exclusive
 * lease covers the complete lifetime, including reads. Local default providers
 * must qualify private creation, complete hard-link visibility and no replacement.
 * This is not a directory-entry, power-loss or network-filesystem durability claim.
 * Privileged owners deliberately bypassing the lease/permissions are outside the
 * cooperative contract. Detected identity, containment or ACL changes fail closed.
 * This adapter never resolves, reads, hashes or deletes a plan's imaging paths.
 */
public final class JsonManifestStore implements ManifestStore {
    private static final LinkOption NOFOLLOW = LinkOption.NOFOLLOW_LINKS;
    private static final Hooks NO_FAULT = (event, path) -> { };
    private final JsonManifestCodec codec = new JsonManifestCodec();
    private final UUID jobId;
    private final Hooks hooks;
    private final Limits limits;
    private final Map<Path, ResourceObservation> directories = new HashMap<>(); // <= roots + 301 buckets.
    private final ArrayList<String> hashes = new ArrayList<>(); // Integrity index, never checkpoint history.
    private Path outputRoot;
    private Path namespace;
    private Path jobDirectory;
    private Path recordsDirectory;
    private UserPrincipal owner;
    private boolean posix;
    private boolean windowsNtfs;
    private FileStore qualifiedStore;
    private FileChannel leaseChannel;
    private FileLock lease;
    private Path ownedStage;
    private OwnedArtifact stageOwnership;
    private boolean closed;
    private boolean poisoned;
    private ProvenancePersistenceException closeFailure;
    private ProvenanceManifest plan;
    private ManifestReplay reducer;
    private ManifestReceipt head;
    private long journalBytes;
    private long planEncodes;
    private long planDecodes;
    private long checkpointEncodes;
    private long journalReads;
    private long views;
    private long prefixPasses;
    private long publishedRecords;

    public JsonManifestStore(Path sourceRoot, Path outputRoot, Path manifestDirectory, UUID jobId) {
        this(sourceRoot, outputRoot, manifestDirectory, jobId, NO_FAULT, Limits.DEFAULT);
    }

    // Test-local faults and smaller limits do not expand the public API or approved limits.
    JsonManifestStore(Path sourceRoot, Path outputRoot, Path manifestDirectory, UUID jobId,
            Hooks hooks, Limits limits) {
        if (sourceRoot == null || outputRoot == null || manifestDirectory == null || jobId == null
                || hooks == null || limits == null) throw error(Code.INVALID_REFERENCE);
        this.jobId = jobId;
        this.hooks = hooks;
        this.limits = limits;
        try {
            establishRoots(sourceRoot, outputRoot, manifestDirectory);
            ensurePrivateDirectory(namespace);
            jobDirectory = namespace.resolve(jobId.toString());
            ensurePrivateDirectory(jobDirectory);
            obtainLease();
            recordsDirectory = jobDirectory.resolve("records");
            ensurePrivateDirectory(recordsDirectory);
            qualifyPublication();
            initialize();
        } catch (ProvenancePersistenceException primary) {
            boolean cleanup = releaseResources(false);
            if (cleanup) throw withCleanup(primary);
            throw primary;
        } catch (IOException | UnsupportedOperationException | SecurityException primary) {
            boolean cleanup = releaseResources(false);
            var codes = new ArrayList<Code>(); codes.add(Code.READ_FAILED);
            if (cleanup) codes.add(Code.CLEANUP_FAILED);
            throw error(codes, PublicationOutcome.NOT_PUBLISHED, Optional.empty());
        }
    }

    @Override public synchronized ManifestReceipt create(ProvenanceManifest proposed) {
        requireMutable();
        if (proposed == null) throw error(Code.INVALID_MANIFEST);
        if (!proposed.jobId().equals(jobId)) throw error(Code.INVALID_REFERENCE);
        byte[] bytes;
        try { planEncodes++; bytes = codec.encode(proposed); }
        catch (IllegalArgumentException invalid) { throw error(Code.RESOURCE_LIMIT); }
        if (bytes.length > limits.planBytes()) throw error(Code.RESOURCE_LIMIT);
        String digest = sha256(bytes);
        if (plan != null) {
            var existing = readPlanBytes();
            if (!sha256(existing).equals(hashes.get(0))) { poisoned = true; throw error(Code.CORRUPT_CHECKPOINT); }
            if (!Arrays.equals(bytes, existing)) throw error(Code.OUTPUT_CONFLICT);
            return receiptAt(0);
        }
        // Prepare the anchor reducer before any authoritative visibility.
        var candidate = new ManifestReplay(proposed, digest);
        var receipt = new ManifestReceipt(jobId, 0, digest, digest);
        publish(jobDirectory.resolve("plan.json"), bytes, receipt, () -> {
            plan = proposed; reducer = candidate; head = receipt; hashes.add(digest);
        });
        return receipt;
    }

    @Override public synchronized ManifestReceipt append(CheckpointRecord record, ManifestReceipt expectedHead) {
        requireMutable();
        if (record == null) throw error(Code.INVALID_MANIFEST);
        if (expectedHead == null) throw error(Code.INVALID_REFERENCE);
        if (plan == null) throw error(Code.INVALID_MANIFEST);
        if (!knownReceipt(expectedHead) || !record.jobId().equals(jobId)
                || !record.previousRecordSha256().equals(expectedHead.headSha256())
                || record.sequence() != checkedNext(expectedHead.sequence())) throw error(Code.CHECKPOINT_CONFLICT);
        byte[] bytes;
        try { checkpointEncodes++; bytes = codec.encode(record); }
        catch (IllegalArgumentException invalid) { throw error(Code.RESOURCE_LIMIT); }
        if (bytes.length > limits.recordBytes()) throw error(Code.RESOURCE_LIMIT);
        if (record.sequence() <= head.sequence()) {
            Path finalPath = recordPath(record.sequence());
            byte[] existing = readRecordBytes(finalPath);
            if (!sha256(existing).equals(hashes.get((int) record.sequence()))) { poisoned = true; throw error(Code.CORRUPT_CHECKPOINT); }
            decodeRecord(existing); // Closed canonical bytes, including earlier acknowledged retries.
            if (!Arrays.equals(bytes, existing)) throw error(Code.CHECKPOINT_CONFLICT);
            return receiptAt(record.sequence());
        }
        if (!expectedHead.equals(head) || record.sequence() != checkedNext(head.sequence())) throw error(Code.CHECKPOINT_CONFLICT);
        boolean intent = record.observation().map(o -> o.state() == CheckpointRecord.State.IN_PROGRESS).orElse(false);
        // A factual job failure/recovery stop may consume the reserved control
        // slot. It does not admit new normal work; RUNNING still preserves it.
        boolean reservedControl = record.jobState().map(s -> s != ManifestState.JobState.RUNNING).orElse(false);
        checkCapacity(head.sequence(), journalBytes, bytes.length, intent, reservedControl, limits);
        try { reducer.validateNext(record); }
        catch (IllegalArgumentException invalid) { throw error(Code.INVALID_MANIFEST); }
        var digest = sha256(bytes);
        var receipt = new ManifestReceipt(jobId, record.sequence(), head.planSha256(), digest);
        Path finalPath;
        try {
            guard();
            var bucket = recordsDirectory.resolve(bucketName(record.sequence()));
            ensurePrivateDirectory(bucket);
            finalPath = bucket.resolve(recordName(record.sequence()));
        } catch (IOException | UnsupportedOperationException | SecurityException failure) { throw error(Code.WRITE_FAILED); }
        publish(finalPath, bytes, receipt, () -> {
            head = reducer.accept(record, digest);
            hashes.add(digest);
            journalBytes = Math.addExact(journalBytes, bytes.length);
            publishedRecords++;
        });
        return receipt;
    }

    @Override public synchronized Optional<ManifestState> replay(Optional<ManifestReceipt> minimumExpectedHead) {
        requireOpen();
        if (minimumExpectedHead == null) throw error(Code.INVALID_REFERENCE);
        if (poisoned) throw error(Code.RECOVERY_REQUIRED);
        try { guard(); }
        catch (IOException | UnsupportedOperationException | SecurityException failure) { throw error(Code.READ_FAILED); }
        if (minimumExpectedHead.isPresent()) {
            var receipt = minimumExpectedHead.orElseThrow();
            if (!knownReceipt(receipt)) throw error(Code.CHECKPOINT_CONFLICT);
            byte[] bytes = receipt.sequence() == 0 ? readPlanBytes() : readRecordBytes(recordPath(receipt.sequence()));
            if (!sha256(bytes).equals(receipt.headSha256())) { poisoned = true; throw error(Code.CORRUPT_CHECKPOINT); }
            if (receipt.sequence() == 0) decodePlan(bytes);
            else {
                var record = decodeRecord(bytes);
                if (record.sequence() != receipt.sequence() || !record.jobId().equals(jobId)
                        || !record.previousRecordSha256().equals(hashes.get((int) receipt.sequence() - 1))) throw error(Code.CORRUPT_CHECKPOINT);
            }
        }
        if (plan == null) return Optional.empty();
        views++;
        return Optional.of(reducer.current());
    }

    @Override public synchronized void close() {
        if (closed) {
            if (closeFailure != null) throw closeFailure;
            return;
        }
        closed = true;
        boolean cleanup = releaseResources(true);
        if (cleanup) {
            closeFailure = error(Code.CLEANUP_FAILED);
            throw closeFailure;
        }
    }

    private void establishRoots(Path source, Path output, Path configuredNamespace) throws IOException {
        try {
            if (source.getFileSystem() != FileSystems.getDefault() || output.getFileSystem() != FileSystems.getDefault()
                    || configuredNamespace.getFileSystem() != FileSystems.getDefault()) throw error(Code.PUBLICATION_UNAVAILABLE);
            Path canonicalSource = source.toRealPath();
            outputRoot = output.toRealPath();
            if (!Files.isDirectory(canonicalSource, NOFOLLOW) || !Files.isDirectory(outputRoot, NOFOLLOW)
                    || canonicalSource.startsWith(outputRoot) || outputRoot.startsWith(canonicalSource)) throw error(Code.CONTAINMENT_UNPROVEN);
            namespace = resolveConfigured(configuredNamespace);
            if (!namespace.startsWith(outputRoot) || namespace.equals(outputRoot)
                    || namespace.startsWith(canonicalSource)) throw error(Code.CONTAINMENT_UNPROVEN);
            String provider = outputRoot.getFileSystem().provider().getClass().getName();
            String type = Files.getFileStore(outputRoot).type();
            boolean windows = provider.equals("sun.nio.fs.WindowsFileSystemProvider");
            if (!supportedLocalProvider(provider, type, windows)) throw error(Code.PUBLICATION_UNAVAILABLE);
            windowsNtfs = windows;
            // The standard NIO API does not identify arbitrary mapped drives as
            // local. Restrict Windows to its local OS volume; UNC/mapped/other
            // drives require separate qualification, with no remote fallback.
            if (windows) {
                String systemDrive = System.getenv("SystemDrive");
                if (systemDrive == null || !outputRoot.getRoot().toString().equalsIgnoreCase(systemDrive + "\\"))
                    throw error(Code.PUBLICATION_UNAVAILABLE);
            }
            qualifiedStore = Files.getFileStore(outputRoot);
            posix = Files.getFileAttributeView(outputRoot, PosixFileAttributeView.class, NOFOLLOW) != null;
            if (!posix && (!windows || Files.getFileAttributeView(outputRoot, AclFileAttributeView.class, NOFOLLOW) == null))
                throw error(Code.ACCESS_CONTROL_UNAVAILABLE);
            owner = Files.getOwner(outputRoot, NOFOLLOW);
            rememberDirectory(outputRoot, false);
        } catch (IOException failure) {
            throw error(Code.CONTAINMENT_UNPROVEN);
        }
    }

    // Configured ancestors may be symlink aliases. Discovered resources below the
    // canonical namespace may not be links. Resolve the existing ancestor, then
    // create the missing suffix one private directory at a time.
    private static Path resolveConfigured(Path configured) throws IOException {
        Path candidate = configured.toAbsolutePath().normalize();
        var missing = new ArrayList<Path>();
        while (true) {
            try { Files.readAttributes(candidate, BasicFileAttributes.class, NOFOLLOW); break; }
            catch (NoSuchFileException absent) {
                if (candidate.getParent() == null) throw error(Code.CONTAINMENT_UNPROVEN);
                missing.add(candidate.getFileName()); candidate = candidate.getParent();
            }
        }
        Path canonical = candidate.toRealPath();
        for (int i = missing.size() - 1; i >= 0; i--) canonical = canonical.resolve(missing.get(i));
        return canonical;
    }

    static boolean supportedLocalProvider(String provider, String filesystem, boolean windows) {
        String type = filesystem.toLowerCase(Locale.ROOT);
        if (windows) return provider.equals("sun.nio.fs.WindowsFileSystemProvider") && type.equals("ntfs");
        boolean known = provider.equals("sun.nio.fs.LinuxFileSystemProvider") || provider.equals("sun.nio.fs.MacOSXFileSystemProvider")
                || provider.equals("sun.nio.fs.BsdFileSystemProvider");
        return known && Set.of("ext2", "ext3", "ext4", "xfs", "btrfs", "overlay", "apfs", "hfs", "ufs", "zfs").contains(type);
    }

    private void ensurePrivateDirectory(Path path) throws IOException {
        if (!path.startsWith(outputRoot) || path.equals(outputRoot)) throw error(Code.CONTAINMENT_UNPROVEN);
        if (!Files.exists(path, NOFOLLOW)) {
            var parent = path.getParent();
            if (!parent.equals(outputRoot) && !Files.exists(parent, NOFOLLOW)) ensurePrivateDirectory(parent);
            verifyDirectoryIdentity(parent);
            try { Files.createDirectory(path, creationAttributes(true)); }
            catch (FileAlreadyExistsException concurrent) { /* Verify the winner, never repair its access. */ }
            catch (UnsupportedOperationException failure) { throw error(Code.ACCESS_CONTROL_UNAVAILABLE); }
        }
        rememberDirectory(path, true);
    }

    private FileAttribute<?>[] creationAttributes(boolean directory) {
        if (posix) return new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"))};
        var builder = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if (directory) builder.setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT);
        List<AclEntry> acl = List.of(builder.build());
        return new FileAttribute<?>[] {new FileAttribute<List<AclEntry>>() {
            @Override public String name() { return "acl:acl"; }
            @Override public List<AclEntry> value() { return acl; }
        }};
    }

    private BasicFileAttributes verifyPrivate(Path path, boolean directory) throws IOException {
        var attributes = Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW);
        if (attributes.isSymbolicLink() || (directory ? !attributes.isDirectory() : !attributes.isRegularFile())
                || !usableObservation(attributes) || !path.toRealPath().equals(path)) throw error(Code.CONTAINMENT_UNPROVEN);
        if (!Files.getOwner(path, NOFOLLOW).equals(owner)) throw error(Code.ACCESS_CONTROL_UNAVAILABLE);
        if (!Files.getFileStore(path).equals(qualifiedStore)) throw error(Code.PUBLICATION_UNAVAILABLE);
        try {
            if (posix) {
                var view = Files.getFileAttributeView(path, PosixFileAttributeView.class, NOFOLLOW);
                if (view == null || !view.readAttributes().permissions().equals(PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------")))
                    throw error(Code.ACCESS_CONTROL_UNAVAILABLE);
            } else {
                var view = Files.getFileAttributeView(path, AclFileAttributeView.class, NOFOLLOW);
                if (view == null) throw error(Code.ACCESS_CONTROL_UNAVAILABLE);
                boolean ownerFull = false;
                for (var entry : view.getAcl()) {
                    if (entry.type() == AclEntryType.ALLOW) {
                        if (!entry.principal().equals(owner)) throw error(Code.ACCESS_CONTROL_UNAVAILABLE);
                        if (!entry.flags().contains(AclEntryFlag.INHERIT_ONLY)
                                && entry.permissions().containsAll(EnumSet.allOf(AclEntryPermission.class))) ownerFull = true;
                    }
                    if (entry.type() == AclEntryType.DENY && entry.principal().equals(owner)) throw error(Code.ACCESS_CONTROL_UNAVAILABLE);
                }
                if (!ownerFull) throw error(Code.ACCESS_CONTROL_UNAVAILABLE);
            }
        } catch (UnsupportedOperationException failure) { throw error(Code.ACCESS_CONTROL_UNAVAILABLE); }
        return attributes;
    }

    private void rememberDirectory(Path path, boolean restricted) throws IOException {
        var attributes = restricted ? verifyPrivate(path, true) : Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW);
        if (!attributes.isDirectory() || attributes.isSymbolicLink() || !usableObservation(attributes)
                || !path.toRealPath().equals(path)) throw error(Code.CONTAINMENT_UNPROVEN);
        verifyRootAccessAndStore(path);
        var observation = observe(attributes);
        var prior = directories.putIfAbsent(path, observation);
        if (prior != null && !unchanged(prior, attributes)) throw error(Code.CONTAINMENT_UNPROVEN);
    }

    private void verifyDirectoryIdentity(Path path) throws IOException {
        var attributes = Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW);
        if (!attributes.isDirectory() || attributes.isSymbolicLink() || !usableObservation(attributes)
                || !path.toRealPath().equals(path) || !path.startsWith(outputRoot)) throw error(Code.CONTAINMENT_UNPROVEN);
        var prior = directories.get(path);
        if (prior != null && !unchanged(prior, attributes)) throw error(Code.CONTAINMENT_UNPROVEN);
        directories.putIfAbsent(path, observe(attributes));
        verifyRootAccessAndStore(path);
        if (path.equals(namespace) || jobDirectory != null && path.startsWith(jobDirectory)) verifyPrivate(path, true);
    }

    private void verifyRootAccessAndStore(Path path) throws IOException {
        if (!Files.getOwner(path, NOFOLLOW).equals(owner)) throw error(Code.ACCESS_CONTROL_UNAVAILABLE);
        if (!Files.getFileStore(path).equals(qualifiedStore)) throw error(Code.PUBLICATION_UNAVAILABLE);
    }

    // Birth time is a substitution check, NOT a fabricated file identity. Windows
    // directory qualification additionally relies on canonical/no-follow location,
    // owner/private ACLs, the qualified volume and the cooperative job lease.
    // POSIX keys remain mandatory; a previously supplied key may never disappear.
    private record ResourceObservation(Object key, FileTime created) { }

    private boolean usableObservation(BasicFileAttributes attributes) {
        return attributes.fileKey() != null || windowsNtfs
                && !attributes.creationTime().equals(FileTime.fromMillis(0));
    }

    private static ResourceObservation observe(BasicFileAttributes attributes) {
        return new ResourceObservation(attributes.fileKey(), attributes.creationTime());
    }

    private boolean unchanged(ResourceObservation prior, BasicFileAttributes current) {
        if (prior.key() != null) return prior.key().equals(current.fileKey());
        return windowsNtfs && current.fileKey() == null && prior.created().equals(current.creationTime());
    }

    private Set<OpenOption> channelOptions(OpenOption... options) {
        var result = new java.util.HashSet<OpenOption>(Arrays.asList(options));
        // The JDK's Windows sharing option pins the opened file against unlink/
        // rename while its handle is held. Unsupported options fail qualification;
        // there is no unpinned/null-key cleanup fallback.
        if (windowsNtfs) result.add(ExtendedOpenOption.NOSHARE_DELETE);
        return result;
    }

    private void guard() throws IOException {
        verifyDirectoryIdentity(outputRoot); verifyDirectoryIdentity(namespace);
        verifyDirectoryIdentity(jobDirectory);
        if (recordsDirectory != null) verifyDirectoryIdentity(recordsDirectory);
    }

    private void obtainLease() throws IOException {
        var path = jobDirectory.resolve(".lease");
        try {
            leaseChannel = FileChannel.open(path, channelOptions(StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE, NOFOLLOW), creationAttributes(false));
        } catch (FileAlreadyExistsException exists) {
            var attributes = verifyPrivate(path, false);
            if (attributes.size() != 0) throw error(Code.CORRUPT_CHECKPOINT);
            leaseChannel = FileChannel.open(path, channelOptions(StandardOpenOption.READ, StandardOpenOption.WRITE, NOFOLLOW));
        } catch (UnsupportedOperationException failure) { throw error(Code.ACCESS_CONTROL_UNAVAILABLE); }
        var before = verifyPrivate(path, false);
        try { lease = leaseChannel.tryLock(); }
        catch (OverlappingFileLockException conflict) { throw error(Code.CHECKPOINT_CONFLICT); }
        catch (IOException | UnsupportedOperationException failure) { throw error(Code.PUBLICATION_UNAVAILABLE); }
        if (lease == null) throw error(Code.CHECKPOINT_CONFLICT);
        var after = verifyPrivate(path, false);
        if (!unchanged(observe(before), after) || after.size() != 0) throw error(Code.CONTAINMENT_UNPROVEN);
        // The persistent lock entry is never unlinked, preventing split-inode leases.
    }

    private void qualifyPublication() {
        String token = UUID.randomUUID().toString();
        Path first = jobDirectory.resolve(".probe-" + token + "-a");
        Path second = jobDirectory.resolve(".probe-" + token + "-b");
        Path finalPath = jobDirectory.resolve(".probe-" + token + "-final");
        var owned = new ArrayList<OwnedArtifact>();
        ProvenancePersistenceException primary = null;
        try {
            guard();
            writeProbe(first, new byte[] {1, 2, 3}, owned);
            writeProbe(second, new byte[] {4, 5, 6}, owned);
            Files.createLink(finalPath, first);
            // Only a successfully created entry belongs to this instance.
            var published = new OwnedArtifact(finalPath); owned.add(published);
            captureOwnership(published); published.peer = first;
            if (!Files.isSameFile(first, finalPath) || !Arrays.equals(readBounded(finalPath, 3), new byte[] {1, 2, 3}))
                throw error(Code.PUBLICATION_UNAVAILABLE);
            boolean refused = false;
            try { Files.createLink(finalPath, second); }
            catch (FileAlreadyExistsException expected) { refused = true; }
            if (!refused || !Arrays.equals(readBounded(finalPath, 3), new byte[] {1, 2, 3})
                    || !Files.isSameFile(first, finalPath)) throw error(Code.PUBLICATION_UNAVAILABLE);
        } catch (ProvenancePersistenceException failure) { primary = failure; }
        catch (IOException | UnsupportedOperationException | SecurityException failure) { primary = error(Code.PUBLICATION_UNAVAILABLE); }
        boolean cleanup = false;
        // Qualify every null-key artifact while its real ownership handle is
        // still live, then release ALL handles before deleting any hard link.
        // Windows sharing restrictions apply to every link of the same file.
        for (var probe : owned) {
            try { verifyOwned(probe); probe.cleanupQualified = true; }
            catch (IOException | UnsupportedOperationException | SecurityException | ProvenancePersistenceException failure) { cleanup = true; }
        }
        for (var probe : owned) {
            if (closePin(probe)) { probe.cleanupQualified = false; cleanup = true; }
        }
        for (int i = owned.size() - 1; i >= 0; i--) {
            var probe = owned.get(i);
            try {
                if (existsSafely(probe.path)) {
                    if (!probe.cleanupQualified || !unchanged(probe.observation, verifyPrivate(probe.path, false))) cleanup = true;
                    else if (probe.peer != null && !Files.isSameFile(probe.path, probe.peer)) cleanup = true;
                    else Files.delete(probe.path);
                }
            }
            catch (IOException | UnsupportedOperationException | SecurityException | ProvenancePersistenceException failure) { cleanup = true; }
        }
        if (primary != null) throw cleanup ? withCleanup(primary) : primary;
        if (cleanup) throw error(Code.CLEANUP_FAILED);
    }

    private void writeProbe(Path path, byte[] bytes, List<OwnedArtifact> owned) {
        // A probe has no identifying payload; content is still restricted at creation.
        FileChannel channel = null;
        ProvenancePersistenceException primary = null;
        boolean closeFailed = false;
        try {
            channel = openPrivateStage(path);
            var artifact = new OwnedArtifact(path); owned.add(artifact);
            captureOwnership(artifact);
            var buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        } catch (ProvenancePersistenceException failure) { primary = failure; }
        catch (IOException | UnsupportedOperationException | SecurityException failure) { primary = error(Code.PUBLICATION_UNAVAILABLE); }
        finally {
            if (channel != null) {
                try { channel.close(); }
                catch (IOException failure) { closeFailed = true; }
            }
        }
        if (primary != null) throw closeFailed ? withCleanup(primary) : primary;
        if (closeFailed) throw error(Code.CLEANUP_FAILED);
    }

    private static final class OwnedArtifact {
        private final Path path;
        private ResourceObservation observation;
        private FileChannel pin;
        private Path peer;
        private boolean cleanupQualified;
        private OwnedArtifact(Path path) { this.path = path; }
    }

    // Called ONLY after CREATE_NEW (or this instance's successful createLink).
    // Overlapping Windows no-delete-sharing handles preserve that provenance
    // from exclusive creation through the closed writer and ownership check.
    private void captureOwnership(OwnedArtifact artifact) throws IOException {
        var before = Files.readAttributes(artifact.path, BasicFileAttributes.class, NOFOLLOW);
        artifact.observation = observe(before);
        if (!usableObservation(before)) throw error(Code.CONTAINMENT_UNPROVEN);
        if (before.fileKey() == null) {
            artifact.pin = FileChannel.open(artifact.path, channelOptions(StandardOpenOption.READ, NOFOLLOW));
        }
        if (!unchanged(artifact.observation, verifyPrivate(artifact.path, false))) throw error(Code.CONTAINMENT_UNPROVEN);
    }

    private void verifyOwned(OwnedArtifact artifact) throws IOException {
        if (artifact == null || artifact.observation == null
                || !unchanged(artifact.observation, verifyPrivate(artifact.path, false))) throw error(Code.CLEANUP_FAILED);
        if (artifact.observation.key() == null && (artifact.pin == null || !artifact.pin.isOpen())) throw error(Code.CLEANUP_FAILED);
        if (artifact.peer != null) {
            verifyPrivate(artifact.peer, false);
            if (!Files.isSameFile(artifact.path, artifact.peer)) throw error(Code.CLEANUP_FAILED);
        }
    }

    private static boolean closePin(OwnedArtifact artifact) {
        if (artifact == null || artifact.pin == null) return false;
        try { artifact.pin.close(); artifact.pin = null; return false; }
        catch (IOException failure) { return true; }
    }

    private void initialize() throws IOException {
        prefixPasses++;
        Path planPath = jobDirectory.resolve("plan.json");
        boolean exists = existsSafely(planPath);
        if (exists) {
            byte[] bytes = readBounded(planPath, limits.planBytes());
            plan = decodePlan(bytes);
            if (!plan.jobId().equals(jobId)) throw error(Code.CORRUPT_CHECKPOINT);
            var digest = sha256(bytes); reducer = new ManifestReplay(plan, digest);
            head = new ManifestReceipt(jobId, 0, digest, digest); hashes.add(digest);
        }
        int stages = 0;
        for (Path entry : entries(jobDirectory, 5)) {
            String name = entry.getFileName().toString();
            if (name.equals("plan.json") || name.equals("records") || name.equals(".lease")) continue;
            if (!stageName(name)) throw error(Code.CORRUPT_CHECKPOINT);
            if (++stages > 1) throw error(Code.RESOURCE_LIMIT);
            stageOnly(entry, limits.planBytes());
        }
        var buckets = entries(recordsDirectory, 301);
        buckets.sort(java.util.Comparator.comparing(path -> path.getFileName().toString()));
        int expectedBucket = 0;
        for (var bucket : buckets) {
            String name = bucket.getFileName().toString();
            if (!name.matches("[0-9]{6}") || !name.equals(String.format(Locale.ROOT, "%06d", expectedBucket++))) throw error(Code.CORRUPT_CHECKPOINT);
            rememberDirectory(bucket, true);
            var names = entries(bucket, 1001);
            names.sort(java.util.Comparator.comparing(path -> path.getFileName().toString()));
            int committed = 0;
            for (var entry : names) {
                String filename = entry.getFileName().toString();
                if (stageName(filename)) {
                    if (++stages > 1) throw error(Code.RESOURCE_LIMIT);
                    stageOnly(entry, limits.recordBytes()); continue;
                }
                // Wrong final name/type is corruption; symlinks are containment failures.
                if (Files.isSymbolicLink(entry)) throw error(Code.CONTAINMENT_UNPROVEN);
                if (!Files.isRegularFile(entry, NOFOLLOW) || !filename.matches("[0-9]{20}\\.json") || ++committed > 1000)
                    throw error(Code.CORRUPT_CHECKPOINT);
                long sequence;
                try { sequence = Long.parseLong(filename.substring(0, 20)); }
                catch (NumberFormatException invalid) { throw error(Code.CORRUPT_CHECKPOINT); }
                if (plan == null || sequence != checkedNext(head.sequence()) || !bucketName(sequence).equals(name)) throw error(Code.CORRUPT_CHECKPOINT);
                byte[] bytes = readRecordBytes(entry);
                var record = decodeRecord(bytes);
                if (record.sequence() != sequence || !record.jobId().equals(jobId)) throw error(Code.CORRUPT_CHECKPOINT);
                checkReplayCapacity(sequence, bytes.length);
                String digest = sha256(bytes);
                try { head = reducer.accept(record, digest); }
                catch (IllegalArgumentException invalid) { throw error(Code.CORRUPT_CHECKPOINT); }
                hashes.add(digest); journalBytes += bytes.length;
            }
        }
        long current = head == null ? 0 : head.sequence();
        if (buckets.size() > current / 1000 + 1) throw error(Code.CORRUPT_CHECKPOINT);
    }

    private void checkReplayCapacity(long sequence, int bytes) {
        if (sequence > limits.recordCount() || bytes > limits.recordBytes()
                || journalBytes > limits.journalBytes() - bytes) throw error(Code.RESOURCE_LIMIT);
    }

    private static boolean stageName(String name) {
        return name.matches("\\.stage-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.tmp");
    }

    private void stageOnly(Path stage, int maximum) throws IOException {
        var attributes = verifyPrivate(stage, false);
        if (attributes.size() > maximum) throw error(Code.RESOURCE_LIMIT);
        // Old stages are not this instance's owned artifacts. Retain, never promote.
    }

    private List<Path> entries(Path directory, int maximum) throws IOException {
        verifyDirectoryIdentity(directory);
        var list = new ArrayList<Path>();
        DirectoryStream<Path> stream = null;
        ProvenancePersistenceException primary = null;
        boolean closeFailed = false;
        try {
            stream = Files.newDirectoryStream(directory);
            for (var entry : stream) {
                if (list.size() == maximum) throw error(Code.RESOURCE_LIMIT);
                list.add(entry);
            }
        } catch (ProvenancePersistenceException failure) { primary = failure; }
        catch (IOException | java.nio.file.DirectoryIteratorException | UnsupportedOperationException | SecurityException failure) { primary = error(Code.READ_FAILED); }
        finally {
            if (stream != null) {
                try { stream.close(); }
                catch (IOException failure) { closeFailed = true; }
            }
        }
        if (primary != null) throw closeFailed ? withCleanup(primary) : primary;
        if (closeFailed) throw error(Code.CLEANUP_FAILED);
        return list;
    }

    private ProvenanceManifest decodePlan(byte[] bytes) {
        try { planDecodes++; return codec.decodePlan(bytes); }
        catch (IllegalArgumentException invalid) { poisoned = true; throw error(schemaFailure(bytes, true)); }
    }

    private CheckpointRecord decodeRecord(byte[] bytes) {
        try { return codec.decodeCheckpoint(bytes); }
        catch (IllegalArgumentException invalid) { poisoned = true; throw error(schemaFailure(bytes, false)); }
    }

    private static Code schemaFailure(byte[] bytes, boolean plan) {
        String prefix = "{\"schema\":\"org.cbihi.mrinormalizer.provenance-" + (plan ? "plan" : "checkpoint") + "\",\"schemaVersion\":1,";
        String start = new String(bytes, 0, Math.min(bytes.length, prefix.length() + 16), StandardCharsets.UTF_8);
        return start.startsWith("{\"schema\":") && !start.startsWith(prefix) ? Code.UNSUPPORTED_SCHEMA : Code.CORRUPT_CHECKPOINT;
    }

    private byte[] readPlanBytes() {
        try { guard(); return readBounded(jobDirectory.resolve("plan.json"), limits.planBytes()); }
        catch (NoSuchFileException missing) { poisoned = true; throw error(Code.CHECKPOINT_CONFLICT); }
        catch (IOException | UnsupportedOperationException | SecurityException failure) { throw error(Code.READ_FAILED); }
    }

    private byte[] readRecordBytes(Path path) {
        try { guard(); verifyDirectoryIdentity(path.getParent()); journalReads++; return readBounded(path, limits.recordBytes()); }
        catch (NoSuchFileException missing) { poisoned = true; throw error(Code.CHECKPOINT_CONFLICT); }
        catch (ProvenancePersistenceException failure) { poisoned = true; throw failure; }
        catch (IOException | UnsupportedOperationException | SecurityException failure) { throw error(Code.READ_FAILED); }
    }

    private byte[] readBounded(Path path, int maximum) throws IOException {
        verifyDirectoryIdentity(path.getParent());
        var before = verifyPrivate(path, false);
        if (before.size() > maximum) throw error(Code.RESOURCE_LIMIT);
        byte[] bytes = new byte[(int) before.size()];
        FileChannel channel = null;
        ProvenancePersistenceException primary = null;
        boolean closeFailed = false;
        try {
            channel = FileChannel.open(path, channelOptions(StandardOpenOption.READ, NOFOLLOW));
            var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) throw error(Code.CORRUPT_CHECKPOINT);
            }
            if (channel.read(ByteBuffer.allocate(1)) != -1) throw error(Code.CORRUPT_CHECKPOINT);
        } catch (ProvenancePersistenceException failure) { primary = failure; }
        catch (IOException | UnsupportedOperationException | SecurityException failure) { primary = error(Code.READ_FAILED); }
        finally {
            if (channel != null) {
                try { channel.close(); }
                catch (IOException failure) { closeFailed = true; }
            }
        }
        if (primary != null) throw closeFailed ? withCleanup(primary) : primary;
        if (closeFailed) throw error(Code.CLEANUP_FAILED);
        var after = verifyPrivate(path, false);
        if (!unchanged(observe(before), after) || after.size() != bytes.length
                || !before.lastModifiedTime().equals(after.lastModifiedTime())) throw error(Code.CORRUPT_CHECKPOINT);
        return bytes;
    }

    private boolean existsSafely(Path path) throws IOException {
        verifyDirectoryIdentity(path.getParent());
        try { Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW); return true; }
        catch (NoSuchFileException absent) { return false; }
    }

    private void publish(Path finalPath, byte[] bytes, ManifestReceipt receipt, Runnable accept) {
        Code primary = null;
        var additional = new ArrayList<Code>();
        PublicationOutcome outcome = PublicationOutcome.NOT_PUBLISHED;
        boolean accepted = false;
        try {
            guard(); verifyDirectoryIdentity(finalPath.getParent());
            hooks.at(Event.BEFORE_STAGE, finalPath);
            var stage = finalPath.resolveSibling(".stage-" + UUID.randomUUID() + ".tmp");
            writeStage(stage, bytes);
            var staged = readBounded(ownedStage, bytes.length);
            if (!Arrays.equals(staged, bytes) || !sha256(staged).equals(receipt.headSha256())) throw error(Code.WRITE_FAILED);
            guard(); verifyDirectoryIdentity(finalPath.getParent());
            hooks.at(Event.BEFORE_PUBLISH, finalPath);
            // This is the sole authoritative publication operation. No replace,
            // direct-final write, copy/move fallback, or delete-final path exists.
            Files.createLink(finalPath, ownedStage);
            stageOwnership.peer = finalPath;
            var finalAttributes = verifyPrivate(finalPath, false);
            if (!unchanged(stageOwnership.observation, finalAttributes) || !Files.isSameFile(ownedStage, finalPath)
                    || !Arrays.equals(readBounded(finalPath, bytes.length), bytes)) throw error(Code.RECOVERY_REQUIRED);
            hooks.at(Event.AFTER_PUBLISH, finalPath);
            outcome = PublicationOutcome.PUBLISHED;
            hooks.at(Event.BEFORE_ACCEPT, finalPath);
            accept.run(); accepted = true;
            hooks.at(Event.AFTER_ACCEPT, finalPath);
        } catch (IOException | UnsupportedOperationException | SecurityException | ProvenancePersistenceException failure) {
            primary = failure instanceof FileAlreadyExistsException ? Code.CHECKPOINT_CONFLICT
                    : failure instanceof UnsupportedOperationException ? Code.PUBLICATION_UNAVAILABLE
                    : failure instanceof ProvenancePersistenceException typed ? typed.failures().get(0).code() : Code.WRITE_FAILED;
            if (failure instanceof ProvenancePersistenceException typed)
                additional.addAll(typed.failures().stream().map(ManifestFailure::code).toList());
            try {
                hooks.at(Event.CLASSIFY, finalPath); guard(); verifyDirectoryIdentity(finalPath.getParent());
                if (!existsSafely(finalPath)) outcome = PublicationOutcome.NOT_PUBLISHED;
                else {
                    var attributes = verifyPrivate(finalPath, false);
                    if (attributes.size() == bytes.length && Arrays.equals(readBounded(finalPath, bytes.length), bytes))
                        outcome = PublicationOutcome.PUBLISHED;
                    else { outcome = PublicationOutcome.NOT_PUBLISHED; primary = Code.CHECKPOINT_CONFLICT; }
                }
            } catch (IOException | UnsupportedOperationException | SecurityException | ProvenancePersistenceException unknown) {
                outcome = PublicationOutcome.UNKNOWN; poisoned = true;
            }
            if (outcome == PublicationOutcome.PUBLISHED && !accepted) {
                // Known publication survives lost acknowledgment. Only this exact
                // supplied fact is folded, with no second sequence/capacity charge.
                try { accept.run(); accepted = true; }
                catch (IllegalArgumentException | ArithmeticException contradiction) { primary = Code.RECOVERY_REQUIRED; poisoned = true; }
            }
        }
        boolean cleanup = cleanupStage(true);
        if (primary != null || cleanup) {
            var codes = new ArrayList<Code>();
            if (primary != null) codes.add(primary);
            codes.addAll(additional);
            if (outcome == PublicationOutcome.UNKNOWN && !codes.contains(Code.RECOVERY_REQUIRED)) codes.add(Code.RECOVERY_REQUIRED);
            if (cleanup && !codes.contains(Code.CLEANUP_FAILED)) codes.add(Code.CLEANUP_FAILED);
            throw error(codes, outcome, outcome == PublicationOutcome.PUBLISHED ? Optional.of(receipt) : Optional.empty());
        }
    }

    private void writeStage(Path stage, byte[] bytes) {
        FileChannel channel = null;
        ProvenancePersistenceException primary = null;
        boolean closeFailed = false;
        try {
            channel = openPrivateStage(stage);
            ownedStage = stage;
            stageOwnership = new OwnedArtifact(stage);
            captureOwnership(stageOwnership);
            hooks.at(Event.AFTER_STAGE, stage);
            var buffer = ByteBuffer.wrap(bytes);
            int full = buffer.limit(); buffer.limit(Math.max(1, bytes.length / 2));
            while (buffer.hasRemaining()) channel.write(buffer);
            hooks.at(Event.WRITE, stage);
            buffer.limit(full); while (buffer.hasRemaining()) channel.write(buffer);
            hooks.at(Event.BEFORE_FORCE, stage); channel.force(true); hooks.at(Event.AFTER_FORCE, stage);
        } catch (ProvenancePersistenceException failure) { primary = failure; }
        catch (IOException | UnsupportedOperationException | SecurityException failure) { primary = error(Code.WRITE_FAILED); }
        finally {
            if (channel != null) {
                try { hooks.at(Event.STAGE_CLOSE, stage); }
                catch (IOException failure) { closeFailed = true; }
                try { channel.close(); }
                catch (IOException failure) { closeFailed = true; }
            }
        }
        if (primary != null) throw closeFailed ? withCleanup(primary) : primary;
        if (closeFailed) throw error(Code.CLEANUP_FAILED);
    }

    private FileChannel openPrivateStage(Path stage) throws IOException {
        try { return FileChannel.open(stage, channelOptions(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, NOFOLLOW), creationAttributes(false)); }
        catch (UnsupportedOperationException unavailable) { throw error(Code.ACCESS_CONTROL_UNAVAILABLE); }
    }

    private boolean cleanupStage(boolean useHooks) {
        if (ownedStage == null) return false;
        try {
            guard(); verifyDirectoryIdentity(ownedStage.getParent());
            verifyOwned(stageOwnership);
            if (useHooks) hooks.at(Event.CLEANUP, ownedStage);
            // Recheck after the fault boundary: never remove a substituted entry.
            verifyOwned(stageOwnership);
            if (closePin(stageOwnership)) return true;
            // Windows requires releasing the no-delete-sharing handle before
            // unlink. Revalidate immediately; deliberate owner-privileged races
            // in this release/unlink interval remain outside the lease contract,
            // as do races between a POSIX key check and unlink.
            if (!unchanged(stageOwnership.observation, verifyPrivate(ownedStage, false))) return true;
            if (stageOwnership.peer != null && !Files.isSameFile(ownedStage, stageOwnership.peer)) return true;
            Files.delete(ownedStage); ownedStage = null; stageOwnership = null;
            return false;
        } catch (IOException | UnsupportedOperationException | SecurityException | ProvenancePersistenceException failure) { return true; }
    }

    private boolean releaseResources(boolean useHooks) {
        boolean failed = cleanupStage(useHooks);
        // Retain uncertain artifacts, but never leak their ownership handles.
        if (closePin(stageOwnership)) failed = true;
        if (lease != null) {
            // An injected error must not prevent the actual release attempt or the
            // independent channel close; both failures remain application-visible.
            if (useHooks) {
                try { hooks.at(Event.LEASE_RELEASE, jobDirectory.resolve(".lease")); }
                catch (IOException failure) { failed = true; }
            }
            try { lease.release(); }
            catch (IOException failure) { failed = true; }
            lease = null;
        }
        if (leaseChannel != null) {
            if (useHooks) {
                try { hooks.at(Event.CHANNEL_CLOSE, jobDirectory.resolve(".lease")); }
                catch (IOException failure) { failed = true; }
            }
            try { leaseChannel.close(); }
            catch (IOException failure) { failed = true; }
            leaseChannel = null;
        }
        return failed;
    }

    private boolean knownReceipt(ManifestReceipt receipt) {
        return head != null && receipt.jobId().equals(jobId) && receipt.planSha256().equals(head.planSha256())
                && receipt.sequence() <= head.sequence() && receipt.sequence() < hashes.size()
                && receipt.headSha256().equals(hashes.get((int) receipt.sequence()));
    }

    private ManifestReceipt receiptAt(long sequence) {
        return new ManifestReceipt(jobId, sequence, hashes.get(0), hashes.get((int) sequence));
    }

    private Path recordPath(long sequence) {
        return recordsDirectory.resolve(bucketName(sequence)).resolve(recordName(sequence));
    }

    static long checkedNext(long sequence) {
        try { return Math.addExact(sequence, 1); }
        catch (ArithmeticException overflow) { throw error(Code.RESOURCE_LIMIT); }
    }

    static String bucketName(long sequence) {
        if (sequence <= 0 || sequence > Limits.DEFAULT.recordCount()) throw error(Code.RESOURCE_LIMIT);
        return String.format(Locale.ROOT, "%06d", (sequence - 1) / 1000);
    }

    private static String recordName(long sequence) {
        return String.format(Locale.ROOT, "%020d.json", sequence);
    }

    static void checkCapacity(long count, long currentBytes, int newBytes, boolean intent, boolean terminal, Limits limits) {
        int reserveRecords = terminal ? 0 : intent ? 2 : 1;
        long reserveBytes = (long) reserveRecords * 16384; // Fixed accepted maxima, even for smaller test limits.
        try {
            long next = checkedNext(count);
            long requiredCount = Math.addExact(next, reserveRecords);
            long requiredBytes = Math.addExact(Math.addExact(currentBytes, newBytes), reserveBytes);
            if (count < 0 || currentBytes < 0 || newBytes <= 0 || newBytes > limits.recordBytes()
                    || requiredCount > limits.recordCount() || requiredBytes > limits.journalBytes()) throw error(Code.RESOURCE_LIMIT);
        } catch (ArithmeticException overflow) { throw error(Code.RESOURCE_LIMIT); }
    }

    private void requireOpen() {
        if (closed || lease == null || !lease.isValid()) throw error(Code.RECOVERY_REQUIRED);
    }

    private void requireMutable() {
        requireOpen();
        if (poisoned || ownedStage != null) throw error(Code.RECOVERY_REQUIRED);
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException unavailable) { throw error(Code.PUBLICATION_UNAVAILABLE); }
    }

    private static ProvenancePersistenceException error(Code code) {
        return error(List.of(code), PublicationOutcome.NOT_PUBLISHED, Optional.empty());
    }

    private static ProvenancePersistenceException error(List<Code> codes, PublicationOutcome outcome, Optional<ManifestReceipt> receipt) {
        return new ProvenancePersistenceException(codes.stream().distinct().map(code -> new ManifestFailure(Phase.PERSISTENCE, code)).toList(), outcome, receipt);
    }

    private static ProvenancePersistenceException withCleanup(ProvenancePersistenceException primary) {
        var codes = new ArrayList<>(primary.failures().stream().map(ManifestFailure::code).toList());
        if (!codes.contains(Code.CLEANUP_FAILED)) codes.add(Code.CLEANUP_FAILED);
        return error(codes, primary.outcome(), primary.knownPublication());
    }

    enum Event { BEFORE_STAGE, AFTER_STAGE, WRITE, BEFORE_FORCE, AFTER_FORCE, BEFORE_PUBLISH,
        AFTER_PUBLISH, BEFORE_ACCEPT, AFTER_ACCEPT, CLASSIFY, CLEANUP, STAGE_CLOSE, LEASE_RELEASE, CHANNEL_CLOSE }
    @FunctionalInterface interface Hooks { void at(Event event, Path path) throws IOException; }

    record Limits(int planBytes, int recordBytes, long recordCount, long journalBytes) {
        static final Limits DEFAULT = new Limits(64 * 1024 * 1024, 16 * 1024, 300001, 256L * 1024 * 1024);
        Limits {
            if (planBytes <= 0 || planBytes > 64 * 1024 * 1024 || recordBytes <= 0 || recordBytes > 16384
                    || recordCount <= 0 || recordCount > 300001 || journalBytes <= 0 || journalBytes > 256L * 1024 * 1024)
                throw new IllegalArgumentException("Invalid restricted persistence test limits");
        }
    }

    record Metrics(long planEncodes, long planDecodes, long checkpointEncodes, long journalReads,
        long views, long prefixPasses, long publishedRecords, int retainedHashes) { }
    synchronized Metrics metrics() {
        return new Metrics(planEncodes, planDecodes, checkpointEncodes, journalReads, views, prefixPasses, publishedRecords, hashes.size());
    }
}
