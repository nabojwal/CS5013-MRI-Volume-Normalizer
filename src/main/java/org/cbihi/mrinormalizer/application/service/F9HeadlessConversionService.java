package org.cbihi.mrinormalizer.application.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.*;
import org.cbihi.mrinormalizer.application.dataset.dicom.*;
import org.cbihi.mrinormalizer.application.dataset.model.*;
import org.cbihi.mrinormalizer.application.port.out.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.Kind;
import org.cbihi.mrinormalizer.application.request.F9HeadlessRequest;
import org.cbihi.mrinormalizer.application.result.F9HeadlessResult;
import org.cbihi.mrinormalizer.application.result.F9HeadlessResult.Reason;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestState.JobState;
import org.cbihi.mrinormalizer.domain.model.*;

/** Single-use Windows-first orchestration; only F8 may write/publish conversion output. */
public final class F9HeadlessConversionService implements AutoCloseable {
    private static final long MAX_SOURCE_BYTES = 64L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 512L * 1024 * 1024;
    private final Path sourceRoot;
    private final DatasetScanner scanner;
    private final DicomMetadataReader metadata;
    private final DicomSeriesDiscoveryService discovery;
    private final ManifestStore store;
    private final DicomSeriesService preflightM6;
    private final VerifiedConversionEvidenceService verified;
    private final Clock clock;
    private boolean used, closed;

    public F9HeadlessConversionService(Path sourceRoot, DatasetScanner scanner,
            DicomMetadataReader metadata, DicomSeriesDiscoveryService discovery,
            DicomSeriesService preflightM6, ManifestStore store,
            VerifiedConversionEvidenceService verified, Clock clock) {
        this.sourceRoot = Objects.requireNonNull(sourceRoot).toAbsolutePath().normalize();
        this.scanner = Objects.requireNonNull(scanner);
        this.metadata = Objects.requireNonNull(metadata);
        this.discovery = Objects.requireNonNull(discovery);
        this.preflightM6 = Objects.requireNonNull(preflightM6);
        this.store = Objects.requireNonNull(store);
        this.verified = Objects.requireNonNull(verified);
        this.clock = Objects.requireNonNull(clock);
    }

    public synchronized F9HeadlessResult executeOne(F9HeadlessRequest request) {
        if (used || closed) throw new IllegalStateException("F9 session is single-use");
        used = true;
        if (request == null) return F9HeadlessResult.failed(Reason.INPUT_OR_SELECTION_INVALID);
        ManifestReceipt known = null;
        boolean planAttempted = false;
        boolean conversionCalled = false;
        boolean published = false;
        try {
            var inventory = scanner.scan();
            if (inventory == null || !inventory.failures().isEmpty() || inventory.entries().size() > 128)
                return F9HeadlessResult.failed(Reason.INVENTORY_INCOMPLETE);
            var entries = new LinkedHashMap<RelativePath, InventoryEntry>();
            for (var entry : inventory.entries()) entries.put(entry.source(), entry);
            if (!entries.keySet().equals(new HashSet<>(request.selectedMembers())))
                return F9HeadlessResult.failed(Reason.SELECTED_MEMBERS_MISMATCH);
            // F2 recognition may be inconclusive for large files; preserve its exact status.
            // Only INPUT_TOO_LARGE is eligible for independent F3 metadata screening.
            for (var entry : inventory.entries()) {
                var result = entry.assessment().initialDetection();
                if (result.outcome() != DetectionOutcome.DICOM
                        && !(result.outcome() == DetectionOutcome.UNKNOWN
                            && result.diagnostic() == DetectionDiagnostic.INPUT_TOO_LARGE))
                    return F9HeadlessResult.failed(Reason.INVENTORY_INCOMPLETE);
            }
            var inspections = new ArrayList<DicomMetadataInspection>();
            for (var selected : request.selectedMembers()) {
                var item = metadata.inspect(selected);
                if (item == null || !selected.equals(item.source()) || item.failure().isPresent())
                    return F9HeadlessResult.failed(Reason.METADATA_OR_SCREENING_FAILED);
                inspections.add(item);
            }
            var groups = discovery.discover(new DicomMetadataCatalog(inspections));
            if (!groups.unassigned().isEmpty() || groups.candidates().size() != 1)
                return F9HeadlessResult.failed(Reason.METADATA_OR_SCREENING_FAILED);
            var candidate = groups.candidates().getFirst();
            if (!candidate.findings().isEmpty() ||
                    !candidate.key().seriesInstanceUid().equals(request.selectedSeriesInstanceUid()))
                return F9HeadlessResult.failed(Reason.METADATA_OR_SCREENING_FAILED);
            var members = new HashSet<RelativePath>();
            for (var member : candidate.members()) members.add(member.source());
            if (!members.equals(entries.keySet()))
                return F9HeadlessResult.failed(Reason.SELECTED_MEMBERS_MISMATCH);

            var sourceRecords = new ArrayList<SourceFileRecord>();
            var absoluteInputs = new ArrayList<InputSource>();
            long consumed = 0;
            for (var member : request.selectedMembers()) {
                Path selected = sourceRoot.resolve(member.path()).normalize();
                if (!selected.startsWith(sourceRoot) || selected.equals(sourceRoot))
                    return F9HeadlessResult.failed(Reason.INPUT_OR_SELECTION_INVALID);
                var before = Files.readAttributes(selected, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!before.isRegularFile() || Files.isSymbolicLink(selected))
                    return F9HeadlessResult.failed(Reason.SOURCE_EVIDENCE_UNAVAILABLE);
                if (before.size() > MAX_SOURCE_BYTES || before.size() > MAX_TOTAL_BYTES - consumed)
                    return F9HeadlessResult.failed(Reason.RESOURCE_LIMIT);
                var hash = MessageDigest.getInstance("SHA-256");
                long count = 0;
                try (InputStream input = Files.newInputStream(selected, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                    byte[] buffer = new byte[65536];
                    int length;
                    while ((length = input.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) return F9HeadlessResult.failed(Reason.INTERRUPTED);
                        count += length;
                        if (count > MAX_SOURCE_BYTES || count > MAX_TOTAL_BYTES - consumed)
                            return F9HeadlessResult.failed(Reason.RESOURCE_LIMIT);
                        hash.update(buffer, 0, length);
                    }
                }
                var after = Files.readAttributes(selected, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!after.isRegularFile() || after.size() != before.size() || count != before.size()
                        || !Objects.equals(after.fileKey(), before.fileKey())
                        || !after.lastModifiedTime().equals(before.lastModifiedTime()))
                    return F9HeadlessResult.failed(Reason.SOURCE_EVIDENCE_UNAVAILABLE);
                consumed += count;
                sourceRecords.add(new SourceFileRecord(member,
                        Optional.of(new ContentDigest(count, HexFormat.of().formatHex(hash.digest()))),
                        entries.get(member).assessment(), List.of()));
                absoluteInputs.add(new InputSource(selected.toString()));
            }
            // F1's v2 journal requires READY before any conversion IN_PROGRESS receipt.
            // Unlike F3/F4, the existing M6 is an owning whole-volume validator.
            // Its preflight performs no output write and never alters frozen M6 logic.
            // F8 must independently call M6 again after its own source/intent binding.
            var preflight = preflightM6.process(new org.cbihi.mrinormalizer.application.request.DicomSeriesRequest(
                    absoluteInputs, request.selectedSeriesInstanceUid()));
            if (preflight == null || !preflight.successful())
                return F9HeadlessResult.failed(Reason.CONVERSION_OR_VERIFICATION_FAILED);
            var assessed = new ArrayList<SourceFileRecord>();
            for (var record : sourceRecords) {
                var original = record.assessment();
                var validated = new FormatAssessment(original.initialDetection(), ImagingFormat.DICOM,
                        FormatVariant.DICOM_UNSPECIFIED, ValidityStatus.VALID,
                        SupportStatus.SUPPORTED, ConversionReadiness.READY, List.of());
                assessed.add(new SourceFileRecord(record.source(), record.digest(), validated, List.of()));
            }
            sourceRecords = assessed;
            // Deterministic, opaque operation identity within this uniquely named job.
            var operationDigest = MessageDigest.getInstance("SHA-256");
            String seed = request.jobId().toString() + "\u0000" + request.destination().path()
                    + "\u0000" + request.selectedSeriesInstanceUid();
            operationDigest.update(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var sorted = new ArrayList<>(request.selectedMembers());
            sorted.sort(Comparator.comparing(RelativePath::path));
            for (var selected : sorted) {
                operationDigest.update((byte)0);
                operationDigest.update(selected.path().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            String operationId = HexFormat.of().formatHex(operationDigest.digest());
            var plan = new ProvenanceManifest(2, request.jobId(), clock.instant(), sourceRecords,
                    List.of(new ManifestOperation(operationId, ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI,
                            request.selectedMembers(), request.destination())));
            planAttempted = true;
            known = store.create(plan);
            if (known.sequence() != 0 || !known.jobId().equals(request.jobId())
                    || store.replay(Optional.of(known)).isEmpty())
                return F9HeadlessResult.uncertain(Reason.PLAN_OR_INTENT_NOT_ACKNOWLEDGED, Optional.of(known));
            known = store.append(new CheckpointRecord(2, request.jobId(), known.sequence() + 1,
                    known.headSha256(), clock.instant(), Kind.JOB_OBSERVED, Optional.empty(), Optional.empty(),
                    Optional.of(JobState.RUNNING), List.of()), known);
            var running = store.replay(Optional.of(known));
            if (running.isEmpty() || !running.orElseThrow().receipt().equals(known)
                    || running.orElseThrow().state() != JobState.RUNNING)
                return F9HeadlessResult.uncertain(Reason.PLAN_OR_INTENT_NOT_ACKNOWLEDGED, Optional.of(known));
            conversionCalled = true;
            var converted = verified.executeOne(new VerifiedConversionRequest(operationId,
                    absoluteInputs, request.selectedSeriesInstanceUid(), request.destination(), known));
            if (converted.status() == VerifiedConversionResult.Status.RECOVERY_REQUIRED
                    || converted.publication() != VerifiedConversionResult.Publication.NOT_PUBLISHED
                        && converted.status() != VerifiedConversionResult.Status.VERIFIED_PUBLISHED)
                return F9HeadlessResult.uncertain(Reason.PUBLICATION_UNCERTAIN, converted.lastAcknowledgedHead());
            if (converted.status() == VerifiedConversionResult.Status.FAILED) {
                if (converted.lastAcknowledgedHead().isEmpty())
                    return F9HeadlessResult.uncertain(Reason.FINAL_JOB_ACK_UNCERTAIN, Optional.empty());
                known = converted.lastAcknowledgedHead().orElseThrow();
                var failedState = store.replay(Optional.of(known));
                if (failedState.isEmpty() || !failedState.orElseThrow().receipt().equals(known)
                        || failedState.orElseThrow().state() != JobState.RUNNING)
                    return F9HeadlessResult.uncertain(Reason.FINAL_JOB_ACK_UNCERTAIN, Optional.of(known));
                // Only a known nonpublication can be finalized as a failed job.
                // The accepted replay reducer checks for live/uncertain operations.
                known = store.append(new CheckpointRecord(2, request.jobId(), known.sequence() + 1,
                        known.headSha256(), clock.instant(), Kind.JOB_OBSERVED, Optional.empty(),
                        Optional.empty(), Optional.of(JobState.FAILED), List.of(new ManifestFailure(
                            ManifestFailure.Phase.EXECUTION, ManifestFailure.Code.VERIFICATION_FAILED))), known);
                var terminalFailure = store.replay(Optional.of(known));
                if (terminalFailure.isEmpty() || !terminalFailure.orElseThrow().receipt().equals(known)
                        || terminalFailure.orElseThrow().state() != JobState.FAILED)
                    return F9HeadlessResult.uncertain(Reason.FINAL_JOB_ACK_UNCERTAIN, Optional.of(known));
                return F9HeadlessResult.failed(Reason.CONVERSION_OR_VERIFICATION_FAILED);
            }
            if (converted.status() != VerifiedConversionResult.Status.VERIFIED_PUBLISHED)
                return F9HeadlessResult.uncertain(Reason.PUBLICATION_UNCERTAIN,
                        converted.lastAcknowledgedHead());
            published = true;
            if (converted.publication() != VerifiedConversionResult.Publication.VERIFIED_AND_ACKNOWLEDGED
                    || converted.lastAcknowledgedHead().isEmpty())
                return F9HeadlessResult.uncertain(Reason.FINAL_JOB_ACK_UNCERTAIN, converted.lastAcknowledgedHead());
            known = converted.lastAcknowledgedHead().orElseThrow();
            var prior = store.replay(Optional.of(known));
            if (prior.isEmpty() || !prior.orElseThrow().receipt().equals(known)
                    || prior.orElseThrow().operations().size() != 1
                    || prior.orElseThrow().operations().getFirst().observation().state()
                        != CheckpointRecord.State.VERIFIED_PUBLISHED)
                return F9HeadlessResult.uncertain(Reason.FINAL_JOB_ACK_UNCERTAIN, Optional.of(known));
            known = store.append(new CheckpointRecord(2, request.jobId(), known.sequence() + 1,
                    known.headSha256(), clock.instant(), Kind.JOB_OBSERVED, Optional.empty(), Optional.empty(),
                    Optional.of(JobState.COMPLETED), List.of()), known);
            var completed = store.replay(Optional.of(known));
            if (completed.isEmpty() || !completed.orElseThrow().receipt().equals(known)
                    || completed.orElseThrow().state() != JobState.COMPLETED)
                return F9HeadlessResult.uncertain(Reason.FINAL_JOB_ACK_UNCERTAIN, Optional.of(known));
            return F9HeadlessResult.success(known);
        } catch (ProvenancePersistenceException ex) {
            if (published || conversionCalled
                    || ex.outcome() != ProvenancePersistenceException.PublicationOutcome.NOT_PUBLISHED)
                return F9HeadlessResult.uncertain(published ? Reason.FINAL_JOB_ACK_UNCERTAIN
                        : Reason.PLAN_OR_INTENT_NOT_ACKNOWLEDGED, Optional.ofNullable(known));
            return F9HeadlessResult.failed(Reason.PLAN_OR_INTENT_NOT_ACKNOWLEDGED);
        } catch (IOException | NoSuchAlgorithmException | SecurityException ex) {
            return planAttempted ? F9HeadlessResult.uncertain(Reason.PUBLICATION_UNCERTAIN, Optional.ofNullable(known))
                    : F9HeadlessResult.failed(Reason.SOURCE_EVIDENCE_UNAVAILABLE);
        } catch (RuntimeException ex) {
            return planAttempted ? F9HeadlessResult.uncertain(Reason.PUBLICATION_UNCERTAIN, Optional.ofNullable(known))
                    : F9HeadlessResult.failed(Reason.INTERNAL_ERROR);
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        try { verified.close(); } finally { store.close(); }
    }
}
