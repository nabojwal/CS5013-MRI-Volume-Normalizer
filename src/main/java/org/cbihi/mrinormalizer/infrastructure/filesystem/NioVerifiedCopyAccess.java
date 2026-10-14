package org.cbihi.mrinormalizer.infrastructure.filesystem;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.util.*;
import com.sun.nio.file.ExtendedOpenOption;
import org.cbihi.mrinormalizer.application.dataset.copy.*;
import org.cbihi.mrinormalizer.application.dataset.dicom.*;
import org.cbihi.mrinormalizer.application.dataset.model.*;
import org.cbihi.mrinormalizer.application.dataset.organization.*;
import org.cbihi.mrinormalizer.application.port.out.VerifiedCopyAccess;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.application.service.*;
import org.cbihi.mrinormalizer.domain.model.*;
import org.cbihi.mrinormalizer.infrastructure.dicom.Dcm4cheMetadataReader;

/**
 * Original-byte access under cooperative quiescent SOURCE and private OUTPUT ownership.
 * No snapshot, hostile-owner immunity, batch transaction or power-loss durability is claimed.
 * All paths/observations and fault seams remain restricted infrastructure state.
 */
public final class NioVerifiedCopyAccess implements VerifiedCopyAccess {
    private static final LinkOption NF = LinkOption.NOFOLLOW_LINKS;
    private final Path source, output, namespace;
    private final CopyLimits limits;
    private final InventoryLimits inventoryLimits;
    private final DicomMetadataLimits metadataLimits;
    private final FormatDetectionService detection;
    private final Hooks hooks;
    private final boolean windows;
    private final UserPrincipal owner;
    private final FileStore outputStore;
    private final Map<Path, Observation> roots = new LinkedHashMap<>();
    private final Map<Path, Observation> outputAncestors = new LinkedHashMap<>();
    private final Map<RelativePath, Observation> sourceFacts = new LinkedHashMap<>();
    private Lifecycle lifecycle = Lifecycle.NEW;
    private SourceDigestBatch batch;
    private Map<RelativePath, ContentDigest> acquiredDigests;
    private Map<RelativePath, DicomSeriesMember> memberFacts = Map.of();
    private OrganizationPlan plan;
    private long readBytes;
    private Prepared active;
    private FileChannel leaseChannel;
    private FileLock lease;
    private Observation leaseObservation;
    private boolean qualified;
    private enum Lifecycle { NEW, ACQUIRED, REVALIDATED, EXECUTING, BLOCKED, CLOSED }
    enum Event { SOURCE_READ, COPY_READ, WRITE, FORCE, BEFORE_STAGE, STAGE_CLOSE, AFTER_STAGE_CLOSE, STAGE_REOPEN, BEFORE_LINK, FINAL_READ, CLEANUP, PROBE_CLEANUP, LEASE_OPEN, LEASE_RELEASE }
    @FunctionalInterface interface Hooks {
        void at(Event event, Path path) throws IOException;
        default int read(Event event, SeekableByteChannel channel, ByteBuffer buffer) throws IOException { return channel.read(buffer); }
        default int write(FileChannel channel, ByteBuffer buffer) throws IOException { return channel.write(buffer); }
        default void createDirectory(Path path, FileAttribute<?>... attributes) throws IOException { Files.createDirectory(path, attributes); }
        default FileChannel openLease(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attributes) throws IOException {
            return FileChannel.open(path, options, attributes);
        }
        default UserPrincipal owner(Path path) throws IOException { return Files.getOwner(path, NF); }
    }

    public NioVerifiedCopyAccess(Path sourceRoot, Path outputRoot, Path manifestNamespace, CopyLimits limits,
            InventoryLimits inventoryLimits, DicomMetadataLimits metadataLimits, FormatDetectionService detection) {
        this(sourceRoot, outputRoot, manifestNamespace, limits, inventoryLimits, metadataLimits, detection, (event, path) -> {});
    }
    NioVerifiedCopyAccess(Path sourceRoot, Path outputRoot, Path manifestNamespace, CopyLimits limits,
            InventoryLimits inventoryLimits, DicomMetadataLimits metadataLimits, FormatDetectionService detection, Hooks hooks) {
        if (sourceRoot == null || outputRoot == null || manifestNamespace == null || limits == null || inventoryLimits == null
                || metadataLimits == null || detection == null || hooks == null) invalid();
        this.limits = limits; this.inventoryLimits = inventoryLimits; this.metadataLimits = metadataLimits; this.detection = detection; this.hooks = hooks;
        try {
            if (sourceRoot.getFileSystem() != FileSystems.getDefault() || outputRoot.getFileSystem() != FileSystems.getDefault()
                    || manifestNamespace.getFileSystem() != FileSystems.getDefault()) throw fault(CopyFailure.Stage.DIGEST_ACQUISITION, CopyFailure.Code.PROVIDER_UNQUALIFIED, null);
            source = sourceRoot.toRealPath(); output = outputRoot.toRealPath(); namespace = canonicalConfigured(manifestNamespace);
            if (source.startsWith(output) || output.startsWith(source) || providerAncestor(source, output) || providerAncestor(output, source)
                    || !namespace.startsWith(output) || namespace.equals(output) || namespace.startsWith(output.resolve("layout-v1"))
                    || output.resolve("layout-v1").startsWith(namespace) || namespace.startsWith(source)
                    || namespace.equals(output.resolve(".f6-copy.lease"))) throw fault(CopyFailure.Stage.DIGEST_ACQUISITION, CopyFailure.Code.CONTAINMENT_UNPROVEN, null);
            String provider = output.getFileSystem().provider().getClass().getName();
            windows = provider.equals("sun.nio.fs.WindowsFileSystemProvider"); outputStore = Files.getFileStore(output);
            String type = outputStore.type().toLowerCase(Locale.ROOT);
            if (windows) {
                String drive = System.getenv("SystemDrive");
                if (!type.equals("ntfs") || !Files.getFileStore(source).type().equalsIgnoreCase("ntfs") || drive == null || !output.getRoot().toString().equalsIgnoreCase(drive + "\\")
                        || !source.getRoot().toString().equalsIgnoreCase(drive + "\\")) throw fault(CopyFailure.Stage.DIGEST_ACQUISITION, CopyFailure.Code.PROVIDER_UNQUALIFIED, null);
            } else if (!provider.equals("sun.nio.fs.LinuxFileSystemProvider")
                    || !Set.of("ext2", "ext3", "ext4", "xfs", "btrfs", "overlay", "zfs").contains(type)
                    || !Set.of("ext2", "ext3", "ext4", "xfs", "btrfs", "overlay", "zfs", "tmpfs").contains(Files.getFileStore(source).type().toLowerCase(Locale.ROOT))
                    || Files.getFileAttributeView(source, PosixFileAttributeView.class, NF) == null)
                throw fault(CopyFailure.Stage.DIGEST_ACQUISITION, CopyFailure.Code.PROVIDER_UNQUALIFIED, null);
            owner = Files.getOwner(output, NF);
            roots.put(source, observe(source, true, false)); roots.put(output, observe(output, true, true));
        } catch (AccessFailure failure) { throw failure; }
        catch (IOException | UnsupportedOperationException | SecurityException failure) {
            throw fault(CopyFailure.Stage.DIGEST_ACQUISITION, CopyFailure.Code.CONTAINMENT_UNPROVEN, null);
        }
    }
    @Override public synchronized SourceDigestBatch acquire(DatasetInventory inventory, DicomSeriesDiscovery discovery) {
        if (inventory == null || discovery == null) invalid(); require(Lifecycle.NEW);
        var digests = new ArrayList<SourceDigestBatch.DigestFact>(); var failures = new ArrayList<CopyFailure>();
        try {
            // Structural validation only. F5 must never be invoked with invented or partial digest evidence.
            new SourceDigestBatch(SourceDigestBatch.Status.BLOCKED, inventory, discovery, List.of(), List.of());
            rememberMembers(discovery);
            boolean predecessorBlocked = !inventory.complete() || !discovery.unassigned().isEmpty();
            for (var entry : inventory.entries()) if (entry.assessment().validity() == ValidityStatus.INVALID || !eligible(entry, discovery)
                    && (entry.assessment().initialDetection().outcome() != DetectionOutcome.UNKNOWN
                            || entry.assessment().initialDetection().diagnostic() != DetectionDiagnostic.UNSUPPORTED_FORMAT)) predecessorBlocked = true;
            if (predecessorBlocked) {
                lifecycle = Lifecycle.BLOCKED; return new SourceDigestBatch(SourceDigestBatch.Status.BLOCKED, inventory, discovery, digests, failures);
            }
            compareDiscovery(inventory, discovery, CopyFailure.Stage.DIGEST_ACQUISITION);
            for (var entry : inventory.entries()) {
                checkInterrupt(CopyFailure.Stage.DIGEST_ACQUISITION, entry.source());
                Path path = source.resolve(entry.source().path());
                sourceFacts.put(entry.source(), observe(path, false, false));
                if (!eligible(entry, discovery)) continue;
                try (var handle = openSource(entry.source(), CopyFailure.Stage.DIGEST_ACQUISITION)) {
                    compareOne(entry, discovery, CopyFailure.Stage.DIGEST_ACQUISITION);
                    var digest = hash(handle.channel, CopyFailure.Stage.DIGEST_ACQUISITION, entry.source(), Event.SOURCE_READ, path);
                    handle.verify(); compareOne(entry, discovery, CopyFailure.Stage.DIGEST_ACQUISITION);
                    digests.add(new SourceDigestBatch.DigestFact(entry.source(), digest));
                }
            }
            compareDiscovery(inventory, discovery, CopyFailure.Stage.DIGEST_ACQUISITION); verifySourceFacts(CopyFailure.Stage.DIGEST_ACQUISITION);
            batch = new SourceDigestBatch(SourceDigestBatch.Status.COMPLETE, inventory, discovery, digests, failures);
            acquiredDigests = batch.suppliedDigests();
            lifecycle = Lifecycle.ACQUIRED; return batch;
        } catch (AccessFailure failure) { retain(failures, restage(failure.failures(), CopyFailure.Stage.DIGEST_ACQUISITION), CopyFailure.Stage.DIGEST_ACQUISITION); lifecycle = Lifecycle.BLOCKED; }
        catch (IOException | UnsupportedOperationException | SecurityException failure) { retain(failures, List.of(fact(CopyFailure.Stage.DIGEST_ACQUISITION, CopyFailure.Code.READ_FAILED, null, null)), CopyFailure.Stage.DIGEST_ACQUISITION); lifecycle = Lifecycle.BLOCKED; }
        return new SourceDigestBatch(SourceDigestBatch.Status.BLOCKED, inventory, discovery, digests, failures);
    }
    @Override public synchronized List<CopyFailure> revalidate(OrganizationPlan candidate, SourceDigestBatch evidence) {
        if (candidate == null || evidence == null) invalid(); require(Lifecycle.ACQUIRED);
        var failures = new ArrayList<CopyFailure>();
        try {
            if (evidence != batch || candidate.status() != OrganizationPlan.Status.COMPLETE || !candidate.inventory().equals(batch.inventory())
                    || !candidate.dicomDiscovery().equals(batch.discovery())
                    || !new OrganizationPlanningService().plan(batch.inventory(), batch.discovery(), acquiredDigests).equals(candidate))
                throw fault(CopyFailure.Stage.REVALIDATION, CopyFailure.Code.DISCOVERY_BINDING_UNPROVEN, null);
            compareDiscovery(batch.inventory(), batch.discovery(), CopyFailure.Stage.REVALIDATION); verifySourceFacts(CopyFailure.Stage.REVALIDATION);
            for (var entry : batch.inventory().entries()) {
                if (!eligible(entry, batch.discovery())) continue;
                try (var handle = openSource(entry.source(), CopyFailure.Stage.REVALIDATION)) {
                    compareOne(entry, batch.discovery(), CopyFailure.Stage.REVALIDATION);
                    var digest = hash(handle.channel, CopyFailure.Stage.REVALIDATION, entry.source(), Event.SOURCE_READ, source.resolve(entry.source().path()));
                    if (!digest.equals(acquiredDigests.get(entry.source()))) throw fault(CopyFailure.Stage.REVALIDATION, CopyFailure.Code.SOURCE_CHANGED, entry.source());
                    handle.verify(); compareOne(entry, batch.discovery(), CopyFailure.Stage.REVALIDATION);
                }
            }
            compareDiscovery(batch.inventory(), batch.discovery(), CopyFailure.Stage.REVALIDATION); verifySourceFacts(CopyFailure.Stage.REVALIDATION);
            plan = candidate; lifecycle = Lifecycle.REVALIDATED;
        } catch (AccessFailure failure) { retain(failures, restage(failure.failures(), CopyFailure.Stage.REVALIDATION), CopyFailure.Stage.REVALIDATION); lifecycle = Lifecycle.BLOCKED; }
        catch (IOException | UnsupportedOperationException | SecurityException failure) { retain(failures, List.of(fact(CopyFailure.Stage.REVALIDATION, CopyFailure.Code.READ_FAILED, null, null)), CopyFailure.Stage.REVALIDATION); lifecycle = Lifecycle.BLOCKED; }
        return List.copyOf(failures);
    }
    @Override public synchronized PreparedCopy prepare(OrganizationCopyOperation operation) {
        if (operation == null) invalid();
        if (lifecycle != Lifecycle.REVALIDATED && lifecycle != Lifecycle.EXECUTING || active != null) unavailable();
        if (!plan.copies().contains(operation)) invalid();
        SourceHandle handle = null;
        try {
            compareDiscovery(batch.inventory(), batch.discovery(), CopyFailure.Stage.REVALIDATION); verifySourceFacts(CopyFailure.Stage.REVALIDATION);
            handle = openSource(operation.source(), CopyFailure.Stage.COPY);
            var entry = batch.inventory().entries().stream().filter(e -> e.source().equals(operation.source())).findFirst().orElseThrow();
            compareOne(entry, batch.discovery(), CopyFailure.Stage.COPY);
            var digest = hash(handle.channel, CopyFailure.Stage.COPY, operation.source(), Event.SOURCE_READ, source.resolve(operation.source().path()));
            if (!digest.equals(operation.expectedDigest())) throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.SOURCE_CHANGED, operation.source());
            handle.verify(); compareOne(entry, batch.discovery(), CopyFailure.Stage.COPY); verifySourceFacts(CopyFailure.Stage.COPY); handle.channel.position(0);
            active = new Prepared(operation, handle); lifecycle = Lifecycle.EXECUTING; return active;
        } catch (AccessFailure failure) { lifecycle = Lifecycle.BLOCKED; closeFailedPreparation(handle, failure); throw failure; }
        catch (IOException | UnsupportedOperationException | SecurityException failure) { lifecycle = Lifecycle.BLOCKED; var controlled = fault(CopyFailure.Stage.COPY, CopyFailure.Code.READ_FAILED, operation.source()); closeFailedPreparation(handle, controlled); throw controlled; }
    }
    @Override public synchronized CopyExecutionResult inspect(OrganizationPlan candidate, ManifestState replayed) {
        if (candidate == null || replayed == null) invalid(); require(Lifecycle.NEW);
        plan = candidate; rememberMembers(candidate.dicomDiscovery());
        lifecycle = Lifecycle.BLOCKED; var facts = new ArrayList<CopyFailure>(); var results = new ArrayList<CopyExecutionResult.OperationResult>();
        boolean uncertain = replayed.state() != ManifestState.JobState.COMPLETED
                && !(candidate.copies().isEmpty() && replayed.state() == ManifestState.JobState.PLANNED);
        var expected = candidate.copies().stream().map(c -> new ManifestOperation(c.operationId(), ManifestOperation.Kind.COPY, List.of(c.source()), c.destination())).toList();
        if (!expected.equals(replayed.plan().operations()) || !candidate.inventory().entries().stream().map(InventoryEntry::source).toList()
                .equals(replayed.plan().sources().stream().map(SourceFileRecord::source).toList())) invalid();
        for (var entry : candidate.inventory().entries()) {
            var original = replayed.plan().sources().stream().filter(s -> s.source().equals(entry.source())).findFirst().orElseThrow();
            var digest = candidate.copies().stream().filter(c -> c.source().equals(entry.source())).map(OrganizationCopyOperation::expectedDigest).findFirst();
            if (!original.assessment().equals(entry.assessment()) || !original.digest().equals(digest)) invalid();
        }
        try {
            compareDiscovery(candidate.inventory(), candidate.dicomDiscovery(), CopyFailure.Stage.RECOVERY);
            for (var operation : candidate.copies()) {
                Optional<ContentDigest> known = Optional.empty();
                try (var handle = openSource(operation.source(), CopyFailure.Stage.RECOVERY)) {
                    var current = hash(handle.channel, CopyFailure.Stage.RECOVERY, operation.source(), Event.SOURCE_READ, source.resolve(operation.source().path())); handle.verify();
                    if (!current.equals(operation.expectedDigest())) throw fault(CopyFailure.Stage.RECOVERY, CopyFailure.Code.SOURCE_CHANGED, operation.source());
                    Path target = output.resolve(operation.destination().path()); verifyOutputChain(target.getParent());
                    if (Files.exists(target, NF)) {
                        known = Optional.of(measureFinal(target, operation, CopyFailure.Stage.RECOVERY));
                        if (!known.get().equals(operation.expectedDigest())) throw fault(CopyFailure.Stage.RECOVERY, CopyFailure.Code.VERIFICATION_FAILED, operation.source());
                    }
                    else uncertain = true;
                } catch (AccessFailure failure) { retain(facts, restage(failure.failures(), CopyFailure.Stage.RECOVERY), CopyFailure.Stage.RECOVERY); uncertain = true; }
                var old = replayed.operations().stream().filter(o -> o.operationId().equals(operation.operationId())).findFirst().orElseThrow();
                boolean verified = known.filter(operation.expectedDigest()::equals).isPresent() && (old.observation().state() == CheckpointRecord.State.COMPLETED || old.observation().state() == CheckpointRecord.State.IDENTICAL_EXISTING);
                results.add(new CopyExecutionResult.OperationResult(operation.operationId(), verified
                        ? old.observation().state() == CheckpointRecord.State.COMPLETED ? CopyExecutionResult.Outcome.COMPLETED : CopyExecutionResult.Outcome.IDENTICAL_EXISTING
                        : CopyExecutionResult.Outcome.RECOVERY_REQUIRED, known));
                if (facts.stream().anyMatch(f -> f.code() == CopyFailure.Code.RESOURCE_LIMIT)) break;
            }
            var parents = new LinkedHashSet<Path>();
            for (var operation : candidate.copies()) parents.add(output.resolve(operation.destination().path()).getParent());
            // Inspect only the original plan's stage namespaces, sequentially and without recursive OUTPUT traversal.
            for (var parent : parents) if (Files.exists(parent, NF)) {
                verifyOutputChain(parent);
                try (var entries = Files.newDirectoryStream(parent)) {
                    for (var path : entries) if (path.getFileName().toString().startsWith(".f6-stage-")
                            || path.getFileName().toString().startsWith(".f6-probe-")) { uncertain = true; break; }
                }
            }
            compareDiscovery(candidate.inventory(), candidate.dicomDiscovery(), CopyFailure.Stage.RECOVERY);
        } catch (AccessFailure failure) { retain(facts, restage(failure.failures(), CopyFailure.Stage.RECOVERY), CopyFailure.Stage.RECOVERY); uncertain = true; }
        catch (IOException | UnsupportedOperationException | SecurityException failure) { retain(facts, List.of(fact(CopyFailure.Stage.RECOVERY, CopyFailure.Code.READ_FAILED, null, null)), CopyFailure.Stage.RECOVERY); uncertain = true; }
        if (results.size() != candidate.copies().size()) {
            for (var op : candidate.copies()) if (results.stream().noneMatch(o -> o.operationId().equals(op.operationId())))
                results.add(new CopyExecutionResult.OperationResult(op.operationId(), CopyExecutionResult.Outcome.RECOVERY_REQUIRED, Optional.empty()));
        }
        if (uncertain) retain(facts, List.of(fact(CopyFailure.Stage.RECOVERY, CopyFailure.Code.RECOVERY_REQUIRED, null, null)), CopyFailure.Stage.RECOVERY);
        return new CopyExecutionResult(uncertain ? CopyExecutionResult.Status.RECOVERY_REQUIRED
                : candidate.copies().isEmpty() ? CopyExecutionResult.Status.NOTHING_TO_COPY : CopyExecutionResult.Status.JOURNAL_COMPLETED,
                Optional.of(replayed), results, facts);
    }
    @Override public synchronized void close() {
        if (lifecycle == Lifecycle.CLOSED) return;
        var failures = new ArrayList<CopyFailure>();
        if (active != null) try { active.close(); } catch (AccessFailure failure) { retain(failures, failure.failures(), CopyFailure.Stage.CLEANUP); }
        boolean fault = false;
        if (lease != null) {
            try { hooks.at(Event.LEASE_RELEASE, output.resolve(".f6-copy.lease")); verifyLease(); } catch (IOException | AccessFailure | SecurityException e) { fault = true; }
            try { lease.release(); } catch (IOException e) { fault = true; } lease = null;
        }
        if (leaseChannel != null) { try { leaseChannel.close(); } catch (IOException e) { fault = true; } leaseChannel = null; }
        lifecycle = Lifecycle.CLOSED;
        if (fault) retain(failures, List.of(fact(CopyFailure.Stage.CLEANUP, CopyFailure.Code.CLEANUP_FAILED, null, null),
                fact(CopyFailure.Stage.CLEANUP, CopyFailure.Code.RECOVERY_REQUIRED, null, null)), CopyFailure.Stage.CLEANUP);
        if (!failures.isEmpty()) throw new AccessFailure(failures);
    }
    private final class Prepared implements PreparedCopy {
        private final OrganizationCopyOperation operation;
        private final SourceHandle sourceHandle;
        private boolean closed, used;
        private Path stage;
        private Observation stageObservation;
        private FileChannel stagePin;
        private boolean qualificationCleanupUncertain;
        private CopyExecutionService.AcknowledgedCopyIntent intent;
        private Prepared(OrganizationCopyOperation operation, SourceHandle handle) { this.operation = operation; sourceHandle = handle; }
        @Override public ContentDigest verifiedSourceDigest() {
            synchronized (NioVerifiedCopyAccess.this) { if (closed || active != this) unavailable(); return operation.expectedDigest(); }
        }
        @Override public CopyOutcome publish(CopyExecutionService.AcknowledgedCopyIntent intent) {
            synchronized (NioVerifiedCopyAccess.this) {
                if (intent == null) invalid(); if (closed || used || active != this || lifecycle != Lifecycle.EXECUTING) unavailable();
                intent.claim(this, operation); used = true; this.intent = intent;
                var failures = new ArrayList<CopyFailure>(); Publication publication = Publication.NOT_PUBLISHED; Optional<ContentDigest> known = Optional.empty();
                boolean cleanupUncertain = false;
                Path target = output.resolve(operation.destination().path());
                CopyExecutionResult.Outcome outcome = CopyExecutionResult.Outcome.FAILED;
                try {
                    live(); obtainLease(); live(); ensureParents(target.getParent()); verifyOutputChain(target.getParent());
                    live(); qualify(target.getParent()); live();
                    if (Files.exists(target, NF)) {
                        known = Optional.of(verifyFinal(target, operation, CopyFailure.Stage.COPY)); publication = Publication.VERIFIED; outcome = CopyExecutionResult.Outcome.IDENTICAL_EXISTING;
                    } else {
                        live(); stage = target.getParent().resolve(".f6-stage-" + UUID.randomUUID());
                        hooks.at(Event.BEFORE_STAGE, stage); boolean closing = false;
                        try (var channel = FileChannel.open(stage, options(StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE, NF), attributes(false))) {
                            stageObservation = observe(stage, false, true);
                            if (windows) stagePin = FileChannel.open(stage, Set.of(StandardOpenOption.READ, NF, ExtendedOpenOption.NOSHARE_DELETE));
                            stream(channel); live(); hooks.at(Event.FORCE, stage); checkInterrupt(CopyFailure.Stage.COPY, operation.source()); channel.force(true);
                            hooks.at(Event.STAGE_CLOSE, stage); checkInterrupt(CopyFailure.Stage.COPY, operation.source()); closing = true;
                        } catch (AccessFailure e) { throw e; }
                        catch (FileAlreadyExistsException competitor) { stage = null; throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.WRITE_FAILED, operation.source()); }
                        catch (ClosedByInterruptException interrupted) { Thread.currentThread().interrupt(); throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.INTERRUPTED, operation.source()); }
                        catch (IOException | UnsupportedOperationException e) {
                            if (closing) throw uncertainClose(operation);
                            throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.WRITE_FAILED, operation.source());
                        }
                        try { hooks.at(Event.AFTER_STAGE_CLOSE, stage); } catch (IOException closeEffectsUnknown) { throw uncertainClose(operation); }
                        if (windows) {
                            // Overlap the original no-delete pin with the now write-denying read pin.
                            var strengthened = FileChannel.open(stage, options(StandardOpenOption.READ, NF));
                            stagePin.close(); stagePin = strengthened;
                        }
                        hooks.at(Event.STAGE_REOPEN, stage); verifyOwnedStage();
                        try (var channel = FileChannel.open(stage, options(StandardOpenOption.READ, NF))) {
                            if (!hash(channel, CopyFailure.Stage.OUTPUT_VERIFICATION, operation.source(), null, stage).equals(operation.expectedDigest()))
                                throw fault(CopyFailure.Stage.OUTPUT_VERIFICATION, CopyFailure.Code.VERIFICATION_FAILED, operation.source());
                        }
                        live(); verifyOwnedStage(); try { hooks.at(Event.BEFORE_LINK, target); } catch (UnsupportedOperationException unavailable) { throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.PUBLICATION_UNPROVEN, operation.source()); }
                        try {
                            live(); Files.createLink(target, stage); publication = Publication.UNVERIFIED;
                            verifyOutputChain(target.getParent()); verifyOwnedStage(); var finalObservation = observe(target, false, true);
                            if (stage.equals(target) || !Files.isSameFile(stage, target)
                                    || !same(stageObservation, finalObservation)) throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.PUBLICATION_UNPROVEN, operation.source());
                            hooks.at(Event.FINAL_READ, target); known = Optional.of(verifyFinal(target, operation, CopyFailure.Stage.OUTPUT_VERIFICATION));
                            publication = Publication.VERIFIED; outcome = CopyExecutionResult.Outcome.COMPLETED;
                        } catch (FileAlreadyExistsException winner) {
                            known = Optional.of(verifyFinal(target, operation, CopyFailure.Stage.COPY)); publication = Publication.VERIFIED; outcome = CopyExecutionResult.Outcome.IDENTICAL_EXISTING;
                        } catch (AccessFailure e) { throw e; }
                        catch (UnsupportedOperationException e) { throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.PUBLICATION_UNPROVEN, operation.source()); }
                        catch (IOException e) {
                            publication = Files.exists(target, NF) ? Publication.UNKNOWN : Publication.NOT_PUBLISHED;
                            throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.PUBLICATION_UNPROVEN, operation.source());
                        }
                    }
                } catch (AccessFailure error) {
                    cleanupUncertain = qualificationCleanupUncertain || error.failures().stream().anyMatch(f -> f.code() == CopyFailure.Code.CLEANUP_FAILED || f.code() == CopyFailure.Code.RECOVERY_REQUIRED);
                    if (publication == Publication.UNVERIFIED && error.failures().stream().anyMatch(f -> f.code() == CopyFailure.Code.CONTAINMENT_UNPROVEN || f.code() == CopyFailure.Code.PUBLICATION_UNPROVEN)) publication = Publication.UNKNOWN;
                    retain(failures, withOperation(error.failures(), operation), CopyFailure.Stage.COPY);
                }
                catch (IOException | SecurityException error) { retain(failures, List.of(fact(CopyFailure.Stage.COPY, CopyFailure.Code.READ_FAILED, operation.source(), operation.operationId())), CopyFailure.Stage.COPY); }
                try { if (!cleanupUncertain && publication != Publication.UNKNOWN) cleanupStage(); } catch (AccessFailure failure) {
                    cleanupUncertain = true;
                    retain(failures, withOperation(failure.failures(), operation), CopyFailure.Stage.CLEANUP);
                    retain(failures, List.of(fact(CopyFailure.Stage.CLEANUP, CopyFailure.Code.RECOVERY_REQUIRED, operation.source(), operation.operationId())), CopyFailure.Stage.CLEANUP);
                }
                if (failures.isEmpty()) try { live(); } catch (AccessFailure failure) { retain(failures, withOperation(failure.failures(), operation), CopyFailure.Stage.COPY); }
                try { close(); } catch (AccessFailure failure) { cleanupUncertain = true; retain(failures, withOperation(failure.failures(), operation), CopyFailure.Stage.CLEANUP); }
                if (!failures.isEmpty()) {
                    lifecycle = Lifecycle.BLOCKED;
                    boolean uncertain = cleanupUncertain || publication == Publication.UNKNOWN || failures.stream().anyMatch(f -> f.code() == CopyFailure.Code.RECOVERY_REQUIRED || f.code() == CopyFailure.Code.CLEANUP_FAILED);
                    outcome = uncertain ? CopyExecutionResult.Outcome.RECOVERY_REQUIRED : CopyExecutionResult.Outcome.FAILED;
                }
                return new CopyOutcome(new CopyExecutionResult.OperationResult(operation.operationId(), outcome, known), publication, failures);
            }
        }
        private void stream(FileChannel channel) throws IOException {
            var digest = sha(); var buffer = ByteBuffer.allocate(limits.bufferBytes()); long count = 0;
            try { sourceHandle.channel.position(0); }
            catch (IOException e) { throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.READ_FAILED, operation.source()); }
            while (true) {
                live(); buffer.clear(); int n = read(sourceHandle.channel, buffer, CopyFailure.Stage.COPY, operation.source(), Event.COPY_READ);
                if (n < 0) break; if (n == 0) throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.READ_FAILED, operation.source());
                count = addCount(count, n, CopyFailure.Stage.COPY, operation.source()); buffer.flip(); digest.update(buffer.asReadOnlyBuffer());
                try { hooks.at(Event.COPY_READ, source.resolve(operation.source().path())); }
                catch (IOException e) { throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.READ_FAILED, operation.source()); }
                while (buffer.hasRemaining()) { live(); hooks.at(Event.WRITE, stage); checkInterrupt(CopyFailure.Stage.COPY, operation.source()); if (write(channel, buffer, operation.source()) <= 0) throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.WRITE_FAILED, operation.source()); }
                sourceHandle.verify();
            }
            sourceHandle.verify(); verifySourceFacts(CopyFailure.Stage.COPY); var actual = new ContentDigest(count, HexFormat.of().formatHex(digest.digest()));
            if (!actual.equals(operation.expectedDigest())) throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.SOURCE_CHANGED, operation.source());
        }
        private void live() { intent.checkLive(this); checkInterrupt(CopyFailure.Stage.COPY, operation.source()); verifyRoots(); if (lease != null) verifyLease(); sourceHandle.verify(); }
        private void verifyOwnedStage() {
            try { verifyOutputChain(stage.getParent()); if (!same(stageObservation, observe(stage, false, true))) throw fault(CopyFailure.Stage.CLEANUP, CopyFailure.Code.CLEANUP_FAILED, operation.source()); }
            catch (IOException e) { throw fault(CopyFailure.Stage.CLEANUP, CopyFailure.Code.CLEANUP_FAILED, operation.source()); }
        }
        private void cleanupStage() {
            if (stage == null) return;
            try {
                // Pins prove ownership while live; release-to-delete is confined to the cooperative private namespace.
                verifyLease(); verifyOwnedStage(); var beforeDelete = observe(stage, false, true);
                if (stagePin != null) { stagePin.close(); stagePin = null; }
                hooks.at(Event.CLEANUP, stage); verifyOwnedStage(); verifyLease();
                // NTFS may tunnel a reused name's creation time while keys are null. Changed size/time is still uncertainty.
                if (!stable(beforeDelete, observe(stage, false, true), false))
                    throw fault(CopyFailure.Stage.CLEANUP, CopyFailure.Code.CLEANUP_FAILED, operation.source());
                intent.checkLive(this); Files.delete(stage); stage = null;
            } catch (IOException | AccessFailure | SecurityException error) { throw fault(CopyFailure.Stage.CLEANUP, CopyFailure.Code.CLEANUP_FAILED, operation.source()); }
        }
        @Override public void close() {
            synchronized (NioVerifiedCopyAccess.this) {
                if (closed) return; closed = true; boolean failed = false;
                if (stagePin != null) { try { stagePin.close(); } catch (IOException e) { failed = true; } stagePin = null; }
                try { sourceHandle.close(); } catch (IOException | AccessFailure e) { failed = true; }
                active = null;
                if (failed) { lifecycle = Lifecycle.BLOCKED; throw fault(CopyFailure.Stage.CLEANUP, CopyFailure.Code.CLEANUP_FAILED, operation.source()); }
            }
        }
    }
    private ContentDigest verifyFinal(Path path, OrganizationCopyOperation operation, CopyFailure.Stage stage) throws IOException {
        var measured = measureFinal(path, operation, stage);
        if (!measured.equals(operation.expectedDigest())) throw fault(stage,
                stage == CopyFailure.Stage.COPY ? CopyFailure.Code.OUTPUT_CONFLICT : CopyFailure.Code.VERIFICATION_FAILED, operation.source());
        return measured;
    }
    private ContentDigest measureFinal(Path path, OrganizationCopyOperation operation, CopyFailure.Stage stage) throws IOException {
        verifyOutputChain(path.getParent()); var before = observe(path, false, true);
        for (var entry : (plan == null ? List.<InventoryEntry>of() : plan.inventory().entries()))
            if (Files.isSameFile(path, source.resolve(entry.source().path()))) throw fault(stage, CopyFailure.Code.CONTAINMENT_UNPROVEN, operation.source());
        try (var channel = FileChannel.open(path, options(StandardOpenOption.READ, NF))) {
            var digest = hash(channel, stage, operation.source(), null, path);
            if (!stable(before, observe(path, false, true), false)) throw fault(stage, CopyFailure.Code.CONTAINMENT_UNPROVEN, operation.source());
            verifyOutputChain(path.getParent()); return digest;
        }
    }
    private void obtainLease() throws IOException {
        if (lease != null) { verifyLease(); return; }
        verifyRoots(); var path = output.resolve(".f6-copy.lease"); Observation existing = null;
        try { leaseChannel = openLease(path, options(StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE, NF), attributes(false)); }
        catch (FileAlreadyExistsException exists) {
            existing = observe(path, false, true);
            for (var entry : batch.inventory().entries()) if (Files.isSameFile(path, source.resolve(entry.source().path())))
                throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.CONTAINMENT_UNPROVEN, active.operation.source());
            try { leaseChannel = openLease(path, options(StandardOpenOption.READ, StandardOpenOption.WRITE, NF)); }
            catch (IOException deniedOrContended) { throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.OUTPUT_CONFLICT, active.operation.source()); }
        }
        hooks.at(Event.LEASE_OPEN, path); leaseObservation = observe(path, false, true);
        if (existing != null && !same(existing, leaseObservation)) throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.CONTAINMENT_UNPROVEN, active.operation.source());
        try { lease = leaseChannel.tryLock(); } catch (OverlappingFileLockException e) { throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.OUTPUT_CONFLICT, active.operation.source()); }
        if (lease == null) throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.OUTPUT_CONFLICT, active.operation.source());
        verifyLease();
    }
    private void verifyLease() {
        try { if (lease == null || !lease.isValid() || !same(leaseObservation, observe(output.resolve(".f6-copy.lease"), false, true)))
            throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.CONTAINMENT_UNPROVEN, active == null ? null : active.operation.source()); }
        catch (IOException e) { throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.CONTAINMENT_UNPROVEN, active == null ? null : active.operation.source()); }
    }
    private void qualify(Path parent) throws IOException {
        if (qualified) return;
        // Synthetic payloads consume the same read budget. Qualification is after actual intent and lease.
        Path probe = parent.resolve(".f6-probe-" + UUID.randomUUID()); Path link = parent.resolve(".f6-probe-" + UUID.randomUUID());
        Observation owned = null; Observation linked = null; FileChannel pin = null;
        var failures = new ArrayList<CopyFailure>();
        try {
            active.live(); verifyOutputChain(parent); try (var channel = FileChannel.open(probe, options(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, NF), attributes(false))) {
                channel.write(ByteBuffer.wrap(new byte[] {37})); channel.force(true);
            }
            owned = observe(probe, false, true); if (windows) pin = FileChannel.open(probe, options(StandardOpenOption.READ, NF));
            active.live(); verifyOutputChain(parent); Files.createLink(link, probe); linked = observe(link, false, true);
            if (probe.equals(link) || !Files.isSameFile(probe, link) || !same(owned, linked)) throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.PROVIDER_UNQUALIFIED, active.operation.source());
            active.live(); verifyOutputChain(parent);
            boolean refused = false; try { Files.createLink(link, probe); } catch (FileAlreadyExistsException e) { refused = true; }
            if (!refused) throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.PROVIDER_UNQUALIFIED, active.operation.source());
            try (var channel = FileChannel.open(link, options(StandardOpenOption.READ, NF))) {
                // Fixed independent SHA-256 vector for byte 0x25; every verification read is charged.
                if (!hash(channel, CopyFailure.Stage.OUTPUT_VERIFICATION, active.operation.source(), null, link).equals(new ContentDigest(1,
                        "bbf3f11cb5b43e700273a78d12de55e4a7eab741ed2abf13787a4d2dc832b8ec")))
                    throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.PROVIDER_UNQUALIFIED, active.operation.source());
            }
            qualified = true;
        } catch (AccessFailure primary) { retain(failures, primary.failures(), CopyFailure.Stage.COPY); }
        catch (IOException | UnsupportedOperationException | SecurityException primary) {
            retain(failures, List.of(fact(CopyFailure.Stage.COPY, CopyFailure.Code.PROVIDER_UNQUALIFIED,
                    active.operation.source(), active.operation.operationId())), CopyFailure.Stage.COPY);
        }
        finally {
            boolean cleanupFailed = false;
            if (pin != null) try { pin.close(); } catch (IOException e) { cleanupFailed = true; }
            boolean ownershipUncertain = cleanupFailed;
            for (int i = 0; i < 2; i++) {
                Path path = i == 0 ? link : probe; var observation = i == 0 ? linked : owned;
                try {
                    if (ownershipUncertain) continue; // Failed pin release cannot authorize deletion.
                    verifyOutputChain(parent); verifyLease();
                    if (observation != null && stable(observation, observe(path, false, true), false)) {
                        hooks.at(Event.PROBE_CLEANUP, path); verifyOutputChain(parent); verifyLease();
                        if (!stable(observation, observe(path, false, true), false)) { cleanupFailed = true; continue; }
                        active.intent.checkLive(active); Files.delete(path);
                    } else if (observation != null || Files.exists(path, NF)) cleanupFailed = true;
                } catch (IOException | AccessFailure | SecurityException e) { cleanupFailed = true; }
            }
            if (cleanupFailed) {
                // Certainty survives even if the caller's fact budget admits only primary + RESOURCE_LIMIT.
                active.qualificationCleanupUncertain = true;
                retain(failures, List.of(
                        fact(CopyFailure.Stage.CLEANUP, CopyFailure.Code.CLEANUP_FAILED, active.operation.source(), active.operation.operationId()),
                        fact(CopyFailure.Stage.CLEANUP, CopyFailure.Code.RECOVERY_REQUIRED, active.operation.source(), active.operation.operationId())), CopyFailure.Stage.CLEANUP);
            }
        }
        if (!failures.isEmpty()) throw new AccessFailure(failures);
    }
    private void ensureParents(Path parent) throws IOException {
        if (!parent.startsWith(output) || parent.equals(namespace) || parent.startsWith(namespace)) throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.CONTAINMENT_UNPROVEN, active.operation.source());
        Path current = output;
        for (var segment : output.relativize(parent)) {
            active.live(); current = current.resolve(segment);
            try { hooks.createDirectory(current, attributes(true)); }
            catch (FileAlreadyExistsException e) { /* Inspect winner; never repair. */ }
            catch (ClosedByInterruptException e) { Thread.currentThread().interrupt(); throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.INTERRUPTED, active.operation.source()); }
            catch (IOException e) { throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.WRITE_FAILED, active.operation.source()); }
            rememberOutputAncestor(current);
        }
    }
    private void verifyOutputChain(Path parent) throws IOException {
        verifyRoots(); if (!parent.startsWith(output)) throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.CONTAINMENT_UNPROVEN, null);
        Path current = output; for (var segment : output.relativize(parent)) { current = current.resolve(segment); rememberOutputAncestor(current); }
    }
    private void rememberOutputAncestor(Path path) throws IOException {
        var measured = observe(path, true, true); var original = outputAncestors.putIfAbsent(path, measured);
        if (original != null && !same(original, measured)) throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.CONTAINMENT_UNPROVEN, null);
        if (outputAncestors.size() > 400002) throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.RESOURCE_LIMIT, null);
    }
    private void compareDiscovery(DatasetInventory inventory, DicomSeriesDiscovery discovery, CopyFailure.Stage stage) {
        verifyRoots(); checkInterrupt(stage, null);
        var fresh = new NioDatasetScanner(source, output, inventoryLimits, detection).scan();
        if (!fresh.equals(inventory)) throw fault(stage == CopyFailure.Stage.RECOVERY ? CopyFailure.Stage.RECOVERY : stage,
                stage == CopyFailure.Stage.RECOVERY ? CopyFailure.Code.SOURCE_CHANGED : CopyFailure.Code.DISCOVERY_BINDING_UNPROVEN, null);
        var catalog = new DicomMetadataInspectionService(new Dcm4cheMetadataReader(source, metadataLimits)).inspect(fresh);
        if (!new DicomSeriesDiscoveryService().discover(catalog).equals(discovery)) throw fault(stage,
                stage == CopyFailure.Stage.RECOVERY ? CopyFailure.Code.SOURCE_CHANGED : CopyFailure.Code.DISCOVERY_BINDING_UNPROVEN, null);
        verifyRoots();
    }
    private void rememberMembers(DicomSeriesDiscovery discovery) {
        var members = new HashMap<RelativePath, DicomSeriesMember>();
        for (var candidate : discovery.candidates()) for (var member : candidate.members()) members.put(member.source(), member);
        memberFacts = Collections.unmodifiableMap(members);
    }
    private void verifySourceFacts(CopyFailure.Stage stage) throws IOException {
        for (var entry : sourceFacts.entrySet()) if (!stable(entry.getValue(), observe(source.resolve(entry.getKey().path()), false, false), false))
            throw fault(stage, CopyFailure.Code.SOURCE_CHANGED, entry.getKey());
    }
    private void compareOne(InventoryEntry entry, DicomSeriesDiscovery discovery, CopyFailure.Stage stage) {
        verifyRoots(); var detected = FormatAssessment.fromDetection(detection.detect(new InputSource(source.resolve(entry.source().path()).toString())));
        if (!detected.equals(entry.assessment())) throw fault(stage, CopyFailure.Code.SOURCE_CHANGED, entry.source());
        if (entry.assessment().initialDetection().outcome() == DetectionOutcome.DICOM) {
            var measured = new Dcm4cheMetadataReader(source, metadataLimits).inspect(entry.source());
            var expected = memberFacts.get(entry.source());
            if (expected == null || !measured.metadata().equals(Optional.of(expected.metadata())) || measured.failure().isPresent())
                throw fault(stage, CopyFailure.Code.SOURCE_CHANGED, entry.source());
        }
        verifyRoots();
    }
    private SourceHandle openSource(RelativePath reference, CopyFailure.Stage stage) throws IOException {
        verifyRoots(); if (reference == null || reference.root() != RelativePath.Root.SOURCE) invalid();
        var guards = new LinkedHashMap<Path, Observation>(); Path current = source; guards.put(current, observe(current, true, false));
        var components = Path.of(reference.path()); var directories = new ArrayList<DirectoryStream<Path>>(); SeekableByteChannel channel = null;
        try {
            SecureDirectoryStream<Path> anchored = null;
            if (!windows) { var root = Files.newDirectoryStream(source); directories.add(root);
                if (!(root instanceof SecureDirectoryStream<Path> secure)) throw fault(stage, CopyFailure.Code.PROVIDER_UNQUALIFIED, reference); anchored = secure;
            }
            for (int i = 0; i < components.getNameCount() - 1; i++) {
                current = current.resolve(components.getName(i)); guards.put(current, observe(current, true, false));
                if (anchored != null) { var next = anchored.newDirectoryStream(components.getName(i), NF); directories.add(next); anchored = next; }
            }
            Path path = source.resolve(reference.path()); var before = observe(path, false, false); guards.put(path, before);
            if (sourceFacts.containsKey(reference) && !stable(sourceFacts.get(reference), before, false)) throw fault(stage, CopyFailure.Code.SOURCE_CHANGED, reference);
            if (before.size > limits.maxFileBytes()) throw fault(stage, CopyFailure.Code.RESOURCE_LIMIT, reference);
            channel = anchored == null ? FileChannel.open(path, options(StandardOpenOption.READ, NF))
                    : anchored.newByteChannel(components.getFileName(), options(StandardOpenOption.READ, NF));
            var handle = new SourceHandle(reference, channel, guards, directories, stage); handle.verify(); return handle;
        } catch (IOException | RuntimeException e) {
            boolean uncertainClose = false;
            if (channel != null) try { channel.close(); } catch (IOException closeFailure) { uncertainClose = true; }
            for (int i = directories.size() - 1; i >= 0; i--) try { directories.get(i).close(); } catch (IOException closeFailure) { uncertainClose = true; }
            if (uncertainClose) {
                var facts = new ArrayList<CopyFailure>();
                if (e instanceof AccessFailure controlled) retain(facts, controlled.failures(), stage);
                else retain(facts, List.of(fact(stage, CopyFailure.Code.READ_FAILED, reference, null)), stage);
                retain(facts, List.of(fact(stage, CopyFailure.Code.CLEANUP_FAILED, reference, null)), stage);
                throw new AccessFailure(facts);
            }
            throw e;
        }
    }
    private final class SourceHandle implements AutoCloseable {
        private final RelativePath reference; private final SeekableByteChannel channel;
        private final Map<Path, Observation> guards; private final List<DirectoryStream<Path>> directories; private final CopyFailure.Stage stage;
        private SourceHandle(RelativePath reference, SeekableByteChannel channel, Map<Path, Observation> guards,
                List<DirectoryStream<Path>> directories, CopyFailure.Stage stage) { this.reference = reference; this.channel = channel; this.guards = guards; this.directories = directories; this.stage = stage; }
        private void verify() {
            verifyRoots();
            try { for (var g : guards.entrySet()) if (!stable(g.getValue(), observe(g.getKey(), g.getValue().directory, false), false))
                throw fault(stage, CopyFailure.Code.SOURCE_CHANGED, reference); }
            catch (IOException e) { throw fault(stage, CopyFailure.Code.INPUT_UNAVAILABLE, reference); }
        }
        @Override public void close() throws IOException {
            boolean failed = false; try { channel.close(); } catch (IOException e) { failed = true; }
            for (int i = directories.size() - 1; i >= 0; i--) try { directories.get(i).close(); } catch (IOException e) { failed = true; }
            if (failed) throw fault(stage, CopyFailure.Code.CLEANUP_FAILED, reference);
        }
    }
    private ContentDigest hash(SeekableByteChannel channel, CopyFailure.Stage stage, RelativePath source, Event event, Path path) throws IOException {
        var digest = sha(); var buffer = ByteBuffer.allocate(limits.bufferBytes()); long count = 0;
        while (true) {
            checkInterrupt(stage, source); if (event != null) hooks.at(event, path); buffer.clear();
            int n = read(channel, buffer, stage, source, event); if (n < 0) break;
            if (n == 0) throw fault(stage, CopyFailure.Code.READ_FAILED, source);
            count = addCount(count, n, stage, source); buffer.flip(); digest.update(buffer);
        }
        return new ContentDigest(count, HexFormat.of().formatHex(digest.digest()));
    }
    private int read(SeekableByteChannel channel, ByteBuffer buffer, CopyFailure.Stage stage, RelativePath source, Event event) throws IOException {
        try {
        long remaining = limits.maxReadBytes() - readBytes;
        if (remaining == 0) { if (channel.position() == channel.size()) return -1; throw fault(stage, CopyFailure.Code.RESOURCE_LIMIT, source); }
        buffer.limit((int)Math.min(buffer.capacity(), remaining)); int n;
        try { n = hooks.read(event, channel, buffer); }
        catch (ClosedByInterruptException interrupted) { Thread.currentThread().interrupt(); throw fault(stage, CopyFailure.Code.INTERRUPTED, source); }
        if (n > 0) { try { readBytes = Math.addExact(readBytes, n); } catch (ArithmeticException e) { throw fault(stage, CopyFailure.Code.RESOURCE_LIMIT, source); } }
        return n;
        } catch (IOException e) { throw fault(stage, CopyFailure.Code.READ_FAILED, source); }
    }
    private int write(FileChannel channel, ByteBuffer buffer, RelativePath reference) throws IOException {
        try { return hooks.write(channel, buffer); }
        catch (ClosedByInterruptException interrupted) { Thread.currentThread().interrupt(); throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.INTERRUPTED, reference); }
        catch (IOException e) { throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.WRITE_FAILED, reference); }
    }
    private long addCount(long count, int n, CopyFailure.Stage stage, RelativePath source) {
        try { long total = Math.addExact(count, n); if (total > limits.maxFileBytes()) throw fault(stage, CopyFailure.Code.RESOURCE_LIMIT, source); return total; }
        catch (ArithmeticException e) { throw fault(stage, CopyFailure.Code.RESOURCE_LIMIT, source); }
    }
    private record Observation(Object key, FileTime created, FileTime modified, long size, boolean directory, UserPrincipal owner, FileStore store, Object access) {}
    private Observation observe(Path path, boolean directory, boolean restricted) throws IOException {
        var attrs = Files.readAttributes(path, BasicFileAttributes.class, NF);
        if (attrs.isSymbolicLink() || (directory ? !attrs.isDirectory() : !attrs.isRegularFile()) || !path.toRealPath().equals(path))
            throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.CONTAINMENT_UNPROVEN, null);
        if (!windows && attrs.fileKey() == null) throw fault(CopyFailure.Stage.DIGEST_ACQUISITION, CopyFailure.Code.PROVIDER_UNQUALIFIED, null);
        var store = Files.getFileStore(path); var principal = hooks.owner(path); Object access;
        if (path.startsWith(source) && roots.containsKey(source) && !store.equals(roots.get(source).store))
            throw fault(CopyFailure.Stage.DIGEST_ACQUISITION, CopyFailure.Code.PROVIDER_UNQUALIFIED, null);
        if (!windows) {
            var posix = Files.readAttributes(path, PosixFileAttributes.class, NF); access = Set.copyOf(posix.permissions());
            if (restricted && (!principal.equals(owner) || !posix.permissions().equals(PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"))))
                throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.ACCESS_CONTROL_UNAVAILABLE, null);
        } else {
            var view = Files.getFileAttributeView(path, AclFileAttributeView.class, NF);
            if (view == null) throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.ACCESS_CONTROL_UNAVAILABLE, null);
            var acl = view.getAcl(); access = List.copyOf(acl);
            if (restricted && (!principal.equals(owner) || acl.stream().anyMatch(e -> e.type() == AclEntryType.ALLOW && !e.principal().equals(owner))
                    || acl.stream().noneMatch(e -> e.type() == AclEntryType.ALLOW && e.principal().equals(owner) && e.permissions().contains(AclEntryPermission.READ_DATA))))
                throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.ACCESS_CONTROL_UNAVAILABLE, null);
        }
        if (restricted && !store.equals(outputStore)) throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.PROVIDER_UNQUALIFIED, null);
        return new Observation(attrs.fileKey(), attrs.creationTime(), attrs.lastModifiedTime(), attrs.size(), directory, principal, store, access);
    }
    private boolean same(Observation before, Observation after) {
        if (before == null || after == null || before.directory != after.directory || !before.owner.equals(after.owner)
                || !before.store.equals(after.store) || !before.access.equals(after.access) || !before.created.equals(after.created)) return false;
        if (before.key != null || after.key != null) return before.key != null && before.key.equals(after.key);
        // Null keys are supplementary substitution observations only. Windows retained objects require sharing pins.
        return windows;
    }
    private boolean stable(Observation before, Observation after, boolean directory) {
        return same(before, after) && (directory || before.size == after.size && before.modified.equals(after.modified));
    }
    private void verifyRoots() {
        try { for (var root : roots.entrySet()) if (!same(root.getValue(), observe(root.getKey(), true, root.getKey().equals(output))))
            throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.CONTAINMENT_UNPROVEN, null); }
        catch (IOException e) { throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.CONTAINMENT_UNPROVEN, null); }
    }
    private FileAttribute<?>[] attributes(boolean directory) {
        if (!windows) return new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"))};
        var acl = List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build());
        return new FileAttribute<?>[] {new FileAttribute<List<AclEntry>>() { public String name() { return "acl:acl"; } public List<AclEntry> value() { return acl; } }};
    }
    private FileChannel openLease(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attributes) throws IOException {
        try { return hooks.openLease(path, options, attributes); }
        catch (UnsupportedOperationException e) { throw fault(CopyFailure.Stage.COPY, CopyFailure.Code.PROVIDER_UNQUALIFIED, active.operation.source()); }
    }
    private Set<OpenOption> options(OpenOption... options) {
        var result = new HashSet<OpenOption>(Arrays.asList(options));
        if (windows) { result.add(ExtendedOpenOption.NOSHARE_DELETE); result.add(ExtendedOpenOption.NOSHARE_WRITE); }
        return result;
    }
    private static Path canonicalConfigured(Path path) throws IOException {
        var pending = new ArrayList<Path>(); var current = path.toAbsolutePath().normalize();
        while (!Files.exists(current, NF)) { if (current.getParent() == null) throw new IOException(); pending.add(current.getFileName()); current = current.getParent(); }
        current = current.toRealPath(); for (int i = pending.size() - 1; i >= 0; i--) current = current.resolve(pending.get(i)); return current;
    }
    private static boolean providerAncestor(Path ancestor, Path candidate) throws IOException {
        // Canonical strings do not reveal bind-mount ancestry. Compare actual ancestor objects as well.
        for (Path current = candidate; current != null; current = current.getParent()) if (Files.isSameFile(ancestor, current)) return true;
        return false;
    }
    private boolean eligible(InventoryEntry entry, DicomSeriesDiscovery discovery) {
        var outcome = entry.assessment().initialDetection().outcome();
        return entry.assessment().validity() != ValidityStatus.INVALID && (outcome == DetectionOutcome.NIFTI || outcome == DetectionOutcome.NIFTI_GZ
                || outcome == DetectionOutcome.DICOM && memberFacts.containsKey(entry.source()));
    }
    private static MessageDigest sha() {
        try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private void retain(List<CopyFailure> destination, List<CopyFailure> incoming, CopyFailure.Stage stage) {
        for (var fact : incoming) {
            if (destination.contains(fact)) continue;
            if (destination.size() < limits.maxFailures()) destination.add(fact);
            else { if (destination.stream().noneMatch(f -> f.code() == CopyFailure.Code.RESOURCE_LIMIT))
                    destination.add(fact(stage, CopyFailure.Code.RESOURCE_LIMIT, fact.source().orElse(null), fact.operationId().orElse(null))); break; }
        }
    }
    private static AccessFailure uncertainClose(OrganizationCopyOperation op) {
        return new AccessFailure(List.of(fact(CopyFailure.Stage.CLEANUP, CopyFailure.Code.CLEANUP_FAILED, op.source(), op.operationId()),
                fact(CopyFailure.Stage.CLEANUP, CopyFailure.Code.RECOVERY_REQUIRED, op.source(), op.operationId())));
    }
    private static List<CopyFailure> restage(List<CopyFailure> facts, CopyFailure.Stage stage) {
        return facts.stream().map(f -> new CopyFailure(stage, f.code(), f.source(), f.operationId())).toList();
    }
    private static List<CopyFailure> withOperation(List<CopyFailure> facts, OrganizationCopyOperation op) {
        return facts.stream().map(f -> new CopyFailure(f.stage(), f.code(), Optional.of(op.source()), Optional.of(op.operationId()))).toList();
    }
    private static CopyFailure fact(CopyFailure.Stage stage, CopyFailure.Code code, RelativePath source, String operation) {
        return new CopyFailure(stage, code, Optional.ofNullable(source), Optional.ofNullable(operation));
    }
    private static AccessFailure fault(CopyFailure.Stage stage, CopyFailure.Code code, RelativePath source) { return new AccessFailure(List.of(fact(stage, code, source, null))); }
    private static void checkInterrupt(CopyFailure.Stage stage, RelativePath source) { if (Thread.currentThread().isInterrupted()) throw fault(stage, CopyFailure.Code.INTERRUPTED, source); }
    private void require(Lifecycle expected) { if (lifecycle != expected) unavailable(); }
    private static void unavailable() { throw new IllegalStateException("COPY access session unavailable"); }
    private static void invalid() { throw new IllegalArgumentException("Invalid COPY access contract"); }
    private void closeFailedPreparation(SourceHandle handle, AccessFailure primary) {
        if (handle == null) return;
        try { handle.close(); } catch (IOException | AccessFailure error) {
            var facts = new ArrayList<>(primary.failures()); retain(facts, List.of(fact(CopyFailure.Stage.COPY, CopyFailure.Code.CLEANUP_FAILED, null, null)), CopyFailure.Stage.COPY);
            throw new AccessFailure(facts);
        }
    }
}
