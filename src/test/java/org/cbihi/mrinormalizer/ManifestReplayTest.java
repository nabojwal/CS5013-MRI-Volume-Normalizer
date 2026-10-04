package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.cbihi.mrinormalizer.application.dataset.model.ConversionReadiness;
import org.cbihi.mrinormalizer.application.dataset.model.FormatAssessment;
import org.cbihi.mrinormalizer.application.dataset.model.FormatVariant;
import org.cbihi.mrinormalizer.application.dataset.model.SupportStatus;
import org.cbihi.mrinormalizer.application.dataset.model.ValidityStatus;
import org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord;
import org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.Disposition;
import org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.Kind;
import org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.Observation;
import org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.State;
import org.cbihi.mrinormalizer.application.provenance.manifest.ContentDigest;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Code;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Phase;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestOperation;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestReceipt;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestReplay;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestState;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestState.JobState;
import org.cbihi.mrinormalizer.application.provenance.manifest.ProcessingEvidence;
import org.cbihi.mrinormalizer.application.provenance.manifest.ProcessingEvidence.Scope;
import org.cbihi.mrinormalizer.application.provenance.manifest.ProvenanceManifest;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;
import org.cbihi.mrinormalizer.application.provenance.manifest.SourceFileRecord;
import org.cbihi.mrinormalizer.domain.error.DicomProcessingError;
import org.cbihi.mrinormalizer.domain.error.DicomToNiftiError;
import org.cbihi.mrinormalizer.domain.model.AffineMatrix4;
import org.cbihi.mrinormalizer.domain.model.DetectionDiagnostic;
import org.cbihi.mrinormalizer.domain.model.DetectionOutcome;
import org.cbihi.mrinormalizer.domain.model.DetectionResult;
import org.cbihi.mrinormalizer.domain.model.IntensityTransform;
import org.cbihi.mrinormalizer.domain.model.ImagingFormat;
import org.cbihi.mrinormalizer.domain.model.ScalarType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** L01-L05 pure supplied journal folding; no store, codec, filesystem or recovery execution. */
class ManifestReplayTest {
    private static final UUID JOB = new UUID(0, 1);
    private static final Instant CREATED = Instant.parse("2026-10-04T00:00:00Z");
    private static final Instant START = CREATED.plusSeconds(1);
    private static final Instant FINISH = CREATED.plusSeconds(2);
    private static final Instant TIME = CREATED.plusSeconds(3);
    private static final String PLAN_HASH = "a".repeat(64);
    private static final String RECORD_HASH = "b".repeat(64);
    private static final ContentDigest DIGEST = new ContentDigest(1, "c".repeat(64));
    private static final ManifestFailure FAILURE = new ManifestFailure(Phase.EXECUTION, Code.VERIFICATION_FAILED);
    private static final ManifestFailure RECOVERY = new ManifestFailure(Phase.EXECUTION, Code.RECOVERY_REQUIRED);
    private static final ManifestFailure CLEANUP = new ManifestFailure(Phase.EXECUTION, Code.CLEANUP_FAILED);
    private static final ManifestFailure PERSISTENCE = new ManifestFailure(Phase.PERSISTENCE, Code.WRITE_FAILED);

    // L01
    @Test
    void initialPlanAndConsecutiveRecordsReplayExactly() {
        var plan = plan(ManifestOperation.Kind.COPY, 1, Optional.of(DIGEST), List.of());
        var replay = new ManifestReplay(plan, PLAN_HASH);
        var initial = replay.current();
        assertSame(plan, initial.plan());
        assertEquals(JobState.PLANNED, initial.state());
        assertEquals(CREATED, initial.recordedAt());
        assertEquals(new ManifestReceipt(JOB, 0, PLAN_HASH, PLAN_HASH), initial.receipt());
        assertEquals(State.NOT_STARTED, initial.operations().get(0).observation().state());
        apply(replay, job(replay, JobState.RUNNING, List.of()));
        apply(replay, operation(replay, id(1), observed(State.IN_PROGRESS), List.of()));
        apply(replay, operation(replay, id(1), observed(State.COMPLETED), List.of()));
        var head = apply(replay, job(replay, JobState.COMPLETED, List.of()));
        assertEquals(new ManifestReceipt(JOB, 4, PLAN_HASH, RECORD_HASH), head);
        assertSame(plan, replay.current().plan());
        assertEquals(JobState.COMPLETED, replay.current().state());
        assertSame(DIGEST, replay.current().operations().get(0).observation().outputDigest().orElseThrow());
        assertEquals(State.NOT_STARTED, initial.operations().get(0).observation().state());
        assertEquals(0, initial.receipt().sequence());
        assertEquals(ConversionReadiness.REQUIRES_VALIDATION, plan.sources().get(0).assessment().readiness());
    }

    @Test
    void emptyPlanIsFactualPlannedStateWithoutSyntheticSuccess() {
        var replay = new ManifestReplay(new ProvenanceManifest(1, JOB, CREATED, List.of(), List.of()), PLAN_HASH);
        assertTrue(replay.current().operations().isEmpty());
        rejectAtomic(replay, () -> replay.accept(job(replay, JobState.RUNNING, List.of()), RECORD_HASH));
        rejectAtomic(replay, () -> replay.accept(job(replay, JobState.COMPLETED, List.of()), RECORD_HASH));
        // Explicit persistence failure can still be reported without claiming work or success.
        apply(replay, job(replay, JobState.FAILED, List.of(PERSISTENCE)));
        assertEquals(JobState.FAILED, replay.current().state());
    }

    @Test
    void constructorRejectsNullAndNoncanonicalPlanHashWithFixedMessages() {
        var plan = plan(ManifestOperation.Kind.COPY, 1, Optional.of(DIGEST), List.of());
        reject(() -> new ManifestReplay(null, PLAN_HASH));
        reject(() -> new ManifestReplay(plan, null));
        for (var hash : badHashes()) reject(() -> new ManifestReplay(plan, hash));
        assertEquals("Replay components must be non-null", assertThrowsExactly(IllegalArgumentException.class,
                () -> new ManifestReplay(null, PLAN_HASH)).getMessage());
    }

    @Test
    void inconsistentJobSequenceLinkAndTimeRejectWithoutChanges() {
        var replay = running(ManifestOperation.Kind.COPY, 1);
        var next = operation(replay, id(1), observed(State.IN_PROGRESS), List.of());
        var head = replay.current().receipt();
        for (long sequence : new long[] {head.sequence(), head.sequence() + 2, Long.MAX_VALUE}) {
            rejectAtomic(replay, () -> replay.accept(new CheckpointRecord(1, JOB, sequence, head.headSha256(), TIME,
                    Kind.OPERATION_OBSERVED, next.operationId(), next.observation(), Optional.empty(), List.of()), RECORD_HASH));
        }
        rejectAtomic(replay, () -> replay.accept(new CheckpointRecord(1, new UUID(0, 2), next.sequence(), next.previousRecordSha256(),
                TIME, next.kind(), next.operationId(), next.observation(), next.jobState(), next.failures()), RECORD_HASH));
        rejectAtomic(replay, () -> replay.accept(new CheckpointRecord(1, JOB, next.sequence(), "d".repeat(64), TIME,
                next.kind(), next.operationId(), next.observation(), next.jobState(), next.failures()), RECORD_HASH));
        rejectAtomic(replay, () -> replay.accept(new CheckpointRecord(1, JOB, next.sequence(), next.previousRecordSha256(),
                FINISH, next.kind(), next.operationId(), next.observation(), next.jobState(), next.failures()), RECORD_HASH));
        var planned = fresh(ManifestOperation.Kind.COPY);
        rejectAtomic(planned, () -> planned.accept(new CheckpointRecord(1, JOB, 1, PLAN_HASH, CREATED.minusNanos(1),
                Kind.JOB_OBSERVED, Optional.empty(), Optional.empty(), Optional.of(JobState.RUNNING), List.of()), RECORD_HASH));
        apply(replay, next); // Equal timestamps are allowed; rejected attempts did not consume sequence.
    }

    @Test
    void sharedCopyDestinationRetainsEveryDistinctSourceMapping() {
        var a = source("restricted/a", Optional.of(DIGEST), List.of());
        var b = source("restricted/b", Optional.of(DIGEST), List.of());
        var out = output("shared");
        var plan = new ProvenanceManifest(1, JOB, CREATED, List.of(b, a), List.of(
                new ManifestOperation(id(2), ManifestOperation.Kind.COPY, List.of(b.source()), out),
                new ManifestOperation(id(1), ManifestOperation.Kind.COPY, List.of(a.source()), out)));
        var replay = new ManifestReplay(plan, PLAN_HASH);
        apply(replay, job(replay, JobState.RUNNING, List.of()));
        for (int n : new int[] {2, 1}) {
            apply(replay, operation(replay, id(n), observed(State.IN_PROGRESS), List.of()));
            apply(replay, operation(replay, id(n), observed(State.IDENTICAL_EXISTING), List.of()));
        }
        apply(replay, job(replay, JobState.COMPLETED, List.of()));
        assertSame(plan, replay.current().plan());
        assertEquals(2, replay.current().plan().sources().size());
        assertEquals(List.of(id(1), id(2)), replay.current().operations().stream().map(s -> s.operationId()).toList());
        assertEquals(a.source(), plan.operations().get(0).sources().get(0));
        assertEquals(b.source(), plan.operations().get(1).sources().get(0));
    }

    // L02
    @Test
    void transitionPairsAndJobStatesAreExhaustive() {
        for (var from : State.values()) {
            for (var to : State.values()) {
                var kind = from == State.WRITTEN_UNVERIFIED || (to == State.WRITTEN_UNVERIFIED
                        && from != State.COMPLETED && from != State.IDENTICAL_EXISTING)
                        ? ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI : ManifestOperation.Kind.COPY;
                var replay = atOperation(from, kind);
                boolean allowed = to == State.RECOVERY_REQUIRED ? from != State.NOT_STARTED
                        : from == State.NOT_STARTED ? List.of(State.IN_PROGRESS, State.BLOCKED, State.SKIPPED_POLICY).contains(to)
                        : from == State.IN_PROGRESS && List.of(State.COMPLETED, State.IDENTICAL_EXISTING, State.WRITTEN_UNVERIFIED, State.FAILED).contains(to);
                allowed &= !(kind == ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI
                        && (to == State.COMPLETED || to == State.IDENTICAL_EXISTING));
                var before = replay.current();
                Executable attempt = () -> {
                    var record = to == State.RECOVERY_REQUIRED ? recovery(replay, id(1), from == State.RECOVERY_REQUIRED)
                            : operation(replay, id(1), observed(to), to == State.FAILED ? List.of(FAILURE) : List.of());
                    apply(replay, record);
                };
                if (allowed) {
                    attemptUnchecked(attempt);
                    assertEquals(to, replay.current().operations().get(0).observation().state());
                } else rejectAtomic(replay, attempt);
                assertSame(before.plan(), replay.current().plan());
            }
        }
        for (var from : JobState.values()) {
            for (var to : JobState.values()) {
                var replay = atJob(from);
                boolean allowed = to == JobState.RECOVERY_REQUIRED
                        || from == JobState.PLANNED && (to == JobState.RUNNING || to == JobState.FAILED)
                        || from == JobState.RUNNING && (to == JobState.COMPLETED || to == JobState.FAILED);
                Executable attempt = () -> {
                    var failures = new ArrayList<ManifestFailure>();
                    if (to == JobState.RECOVERY_REQUIRED) {
                        failures.addAll(replay.current().jobFailures());
                        if (!failures.contains(RECOVERY)) failures.add(RECOVERY);
                        if (from == JobState.RECOVERY_REQUIRED) failures.add(CLEANUP);
                    } else if (to == JobState.FAILED) failures.add(PERSISTENCE);
                    apply(replay, job(replay, to, failures));
                };
                if (allowed) {
                    attemptUnchecked(attempt);
                    assertEquals(to, replay.current().state());
                } else rejectAtomic(replay, attempt);
            }
        }
    }

    @Test
    void runningRecordIsRequiredBeforeNormalOperationObservations() {
        var replay = fresh(ManifestOperation.Kind.COPY);
        for (var state : List.of(State.IN_PROGRESS, State.BLOCKED, State.SKIPPED_POLICY)) {
            rejectAtomic(replay, () -> replay.accept(operation(replay, id(1), observed(state), List.of()), RECORD_HASH));
        }
        apply(replay, job(replay, JobState.RUNNING, List.of()));
        apply(replay, operation(replay, id(1), observed(State.IN_PROGRESS), List.of()));
    }

    @Test
    void unknownOperationsAndSecondActiveOperationRejectAtomically() {
        var replay = running(ManifestOperation.Kind.COPY, 2);
        rejectAtomic(replay, () -> replay.accept(operation(replay, id(3), observed(State.IN_PROGRESS), List.of()), RECORD_HASH));
        apply(replay, operation(replay, id(1), observed(State.IN_PROGRESS), List.of()));
        rejectAtomic(replay, () -> replay.accept(operation(replay, id(2), observed(State.IN_PROGRESS), List.of()), RECORD_HASH));
        rejectAtomic(replay, () -> replay.accept(job(replay, JobState.FAILED, List.of(PERSISTENCE)), RECORD_HASH));
        apply(replay, operation(replay, id(1), observed(State.COMPLETED), List.of()));
        apply(replay, operation(replay, id(2), observed(State.IN_PROGRESS), List.of()));
        apply(replay, operation(replay, id(2), observed(State.COMPLETED), List.of()));
        apply(replay, job(replay, JobState.COMPLETED, List.of()));
    }

    @Test
    void kindSpecificSuccessAndProcessingEvidenceAreEnforced() {
        var copy = atOperation(State.IN_PROGRESS, ManifestOperation.Kind.COPY);
        rejectAtomic(copy, () -> copy.accept(operation(copy, id(1), observed(State.WRITTEN_UNVERIFIED), List.of()), RECORD_HASH));
        var failedPhase = new Observation(State.FAILED, Optional.of(START), Optional.of(FINISH), Optional.empty(), 0,
                Optional.empty(), Optional.of(evidence(false)));
        rejectAtomic(copy, () -> copy.accept(operation(copy, id(1), failedPhase, List.of()), RECORD_HASH));
        var conversion = atOperation(State.IN_PROGRESS, ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI);
        for (var state : List.of(State.COMPLETED, State.IDENTICAL_EXISTING)) {
            rejectAtomic(conversion, () -> conversion.accept(operation(conversion, id(1), observed(state), List.of()), RECORD_HASH));
        }
        apply(conversion, operation(conversion, id(1), observed(State.WRITTEN_UNVERIFIED), List.of()));
        rejectAtomic(conversion, () -> conversion.accept(job(conversion, JobState.COMPLETED, List.of()), RECORD_HASH));
        assertEquals(State.WRITTEN_UNVERIFIED, conversion.current().operations().get(0).observation().state());
    }

    @Test
    void matchingCountAndExpectedCopyDigestAreRequired() {
        var replay = atOperation(State.IN_PROGRESS, ManifestOperation.Kind.COPY);
        for (int count : new int[] {2, 100000}) {
            var wrong = new Observation(State.COMPLETED, Optional.of(START), Optional.of(FINISH), Optional.of(DIGEST), count,
                    Optional.empty(), Optional.empty());
            rejectAtomic(replay, () -> replay.accept(operation(replay, id(1), wrong, List.of()), RECORD_HASH));
        }
        for (var digest : List.of(new ContentDigest(2, DIGEST.sha256()), new ContentDigest(1, "d".repeat(64)))) {
            var wrong = new Observation(State.COMPLETED, Optional.of(START), Optional.of(FINISH), Optional.of(digest), 1,
                    Optional.empty(), Optional.empty());
            rejectAtomic(replay, () -> replay.accept(operation(replay, id(1), wrong, List.of()), RECORD_HASH));
        }
        var wrongStart = new Observation(State.COMPLETED, Optional.of(START.plusNanos(1)), Optional.of(FINISH), Optional.of(DIGEST), 1,
                Optional.empty(), Optional.empty());
        rejectAtomic(replay, () -> replay.accept(operation(replay, id(1), wrongStart, List.of()), RECORD_HASH));
        apply(replay, operation(replay, id(1), observed(State.COMPLETED), List.of()));
    }

    @Test
    void everyConversionSourceMustHaveSuppliedDigestAndExactMatchedCount() {
        var a = source("a", Optional.of(DIGEST), List.of());
        var b = source("b", Optional.of(DIGEST), List.of());
        var op = new ManifestOperation(id(1), ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI, List.of(a.source(), b.source()), output("out"));
        var replay = new ManifestReplay(new ProvenanceManifest(1, JOB, CREATED, List.of(a, b), List.of(op)), PLAN_HASH);
        apply(replay, job(replay, JobState.RUNNING, List.of()));
        rejectAtomic(replay, () -> replay.accept(operation(replay, id(1), observed(State.IN_PROGRESS), List.of()), RECORD_HASH));
        var intent = new Observation(State.IN_PROGRESS, Optional.of(START), Optional.empty(), Optional.empty(), 2, Optional.empty(), Optional.empty());
        apply(replay, operation(replay, id(1), intent, List.of()));
        var outcome = new Observation(State.WRITTEN_UNVERIFIED, Optional.of(START), Optional.of(FINISH), Optional.of(DIGEST), 2,
                Optional.empty(), Optional.of(evidence(true)));
        apply(replay, operation(replay, id(1), outcome, List.of()));
        assertEquals(2, replay.current().operations().get(0).observation().matchedExpectedSourceCount());
        var missing = new SourceFileRecord(b.source(), Optional.empty(), b.assessment(), List.of());
        var blocked = new ManifestReplay(new ProvenanceManifest(1, JOB, CREATED, List.of(a, missing), List.of(op)), PLAN_HASH);
        apply(blocked, job(blocked, JobState.RUNNING, List.of()));
        rejectAtomic(blocked, () -> blocked.accept(operation(blocked, id(1), intent, List.of()), RECORD_HASH));
        apply(blocked, operation(blocked, id(1), observed(State.SKIPPED_POLICY), List.of()));
    }

    @Test
    void missingDigestAndSourceFailuresAreNonexecutingFacts() {
        for (var plan : List.of(plan(ManifestOperation.Kind.COPY, 1, Optional.empty(), List.of()),
                plan(ManifestOperation.Kind.COPY, 1, Optional.of(DIGEST), List.of(new ManifestFailure(Phase.HASHING, Code.SOURCE_CHANGED))))) {
            var replay = new ManifestReplay(plan, PLAN_HASH);
            apply(replay, job(replay, JobState.RUNNING, List.of()));
            rejectAtomic(replay, () -> replay.accept(operation(replay, id(1), observed(State.IN_PROGRESS), List.of()), RECORD_HASH));
            apply(replay, operation(replay, id(1), observed(State.BLOCKED), List.of()));
            assertSame(plan, replay.current().plan());
            assertEquals(plan.sources().get(0).digest(), replay.current().plan().sources().get(0).digest());
        }
    }

    @Test
    void blockedRequiresApplicableSuppliedAssessmentOrFailure() {
        var replay = running(ManifestOperation.Kind.COPY, 1);
        // Pending F0 validation is explicit plan evidence; no fabricated HASH_FAILED is required.
        apply(replay, operation(replay, id(1), observed(State.BLOCKED), List.of()));
        assertTrue(replay.current().operations().get(0).failures().isEmpty());
        rejectAtomic(replay, () -> replay.accept(job(replay, JobState.FAILED, List.of()), RECORD_HASH));
        apply(replay, job(replay, JobState.FAILED, List.of(FAILURE)));
        var plan = plan(ManifestOperation.Kind.COPY, 1, Optional.of(DIGEST), List.of());
        var source = plan.sources().get(0);
        var ready = new FormatAssessment(source.assessment().initialDetection(), ImagingFormat.DICOM,
                FormatVariant.DICOM_UNSPECIFIED, ValidityStatus.VALID, SupportStatus.SUPPORTED,
                ConversionReadiness.READY, List.of());
        var assessed = new SourceFileRecord(source.source(), source.digest(), ready, List.of());
        var unsupportedBlock = new ManifestReplay(new ProvenanceManifest(1, JOB, CREATED, List.of(assessed), plan.operations()), PLAN_HASH);
        apply(unsupportedBlock, job(unsupportedBlock, JobState.RUNNING, List.of()));
        rejectAtomic(unsupportedBlock, () -> unsupportedBlock.accept(operation(unsupportedBlock, id(1), observed(State.BLOCKED), List.of()), RECORD_HASH));
        apply(unsupportedBlock, operation(unsupportedBlock, id(1), observed(State.BLOCKED), List.of(FAILURE)));
    }

    @Test
    void completedRequiresActualCopyAndEveryOperationTerminalWithoutFailures() {
        var replay = running(ManifestOperation.Kind.COPY, 2);
        rejectAtomic(replay, () -> replay.accept(job(replay, JobState.COMPLETED, List.of()), RECORD_HASH));
        apply(replay, operation(replay, id(1), observed(State.IN_PROGRESS), List.of()));
        apply(replay, operation(replay, id(1), observed(State.COMPLETED), List.of()));
        rejectAtomic(replay, () -> replay.accept(job(replay, JobState.COMPLETED, List.of()), RECORD_HASH));
        apply(replay, operation(replay, id(2), observed(State.SKIPPED_POLICY), List.of()));
        apply(replay, job(replay, JobState.COMPLETED, List.of()));
        var skipped = running(ManifestOperation.Kind.COPY, 1);
        apply(skipped, operation(skipped, id(1), observed(State.SKIPPED_POLICY), List.of()));
        rejectAtomic(skipped, () -> skipped.accept(job(skipped, JobState.COMPLETED, List.of()), RECORD_HASH));
    }

    @Test
    void sourceAndJobFailuresCannotBecomeCompleted() {
        var plan = plan(ManifestOperation.Kind.COPY, 2, Optional.of(DIGEST), List.of());
        var failingSource = source("unused", Optional.empty(), List.of(new ManifestFailure(Phase.HASHING, Code.HASH_FAILED)));
        var sources = new ArrayList<>(plan.sources());
        sources.add(failingSource);
        for (boolean sourceFailure : new boolean[] {false, true}) {
            var replay = new ManifestReplay(sourceFailure ? new ProvenanceManifest(1, JOB, CREATED, sources, plan.operations()) : plan, PLAN_HASH);
            apply(replay, job(replay, JobState.RUNNING, sourceFailure ? List.of() : List.of(PERSISTENCE)));
            for (int n : new int[] {1, 2}) {
                apply(replay, operation(replay, id(n), observed(State.IN_PROGRESS), List.of()));
                apply(replay, operation(replay, id(n), observed(State.COMPLETED), List.of()));
            }
            rejectAtomic(replay, () -> replay.accept(job(replay, JobState.COMPLETED, List.of()), RECORD_HASH));
            apply(replay, job(replay, JobState.FAILED, sourceFailure ? List.of() : List.of(PERSISTENCE)));
        }
    }

    @Test
    void failureRequiresRealEvidenceAndRetainsOriginalPhaseAuthority() {
        var replay = atOperation(State.IN_PROGRESS, ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI);
        var evidence = evidence(false);
        var failed = new Observation(State.FAILED, Optional.of(START), Optional.of(FINISH), Optional.empty(), 0,
                Optional.empty(), Optional.of(evidence));
        apply(replay, operation(replay, id(1), failed, List.of()));
        apply(replay, job(replay, JobState.FAILED, List.of()));
        assertSame(evidence, replay.current().operations().get(0).observation().processingEvidence().orElseThrow());
        assertTrue(replay.current().operations().get(0).failures().isEmpty());
        var planned = fresh(ManifestOperation.Kind.COPY);
        rejectAtomic(planned, () -> planned.accept(job(planned, JobState.FAILED, List.of()), RECORD_HASH));
        var source = new SourceFileRecord(new RelativePath(RelativePath.Root.SOURCE, "unavailable"), Optional.empty(),
                FormatAssessment.fromDetection(DetectionResult.unknown(DetectionDiagnostic.INPUT_NOT_FOUND)), List.of());
        var unavailable = new ManifestReplay(new ProvenanceManifest(1, JOB, CREATED, List.of(source), List.of()), PLAN_HASH);
        apply(unavailable, job(unavailable, JobState.FAILED, List.of()));
        assertSame(source.assessment(), unavailable.current().plan().sources().get(0).assessment());
    }

    // L03
    @Test
    void uncertaintyPreservesFactsWithoutRecoveryExecution() {
        var replay = atOperation(State.WRITTEN_UNVERIFIED, ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI);
        var before = replay.current().operations().get(0).observation();
        apply(replay, job(replay, JobState.RECOVERY_REQUIRED, List.of(RECOVERY)));
        apply(replay, recovery(replay, id(1), false));
        var uncertain = replay.current().operations().get(0).observation();
        assertEquals(before.startedAt(), uncertain.startedAt());
        assertEquals(before.finishedAt(), uncertain.finishedAt());
        assertEquals(before.outputDigest(), uncertain.outputDigest());
        assertEquals(before.matchedExpectedSourceCount(), uncertain.matchedExpectedSourceCount());
        assertSame(before.processingEvidence().orElseThrow(), uncertain.processingEvidence().orElseThrow());
        assertEquals(State.RECOVERY_REQUIRED, uncertain.state());
        rejectAtomic(replay, () -> replay.accept(operation(replay, id(1), observed(State.WRITTEN_UNVERIFIED), List.of()), RECORD_HASH));
        rejectAtomic(replay, () -> replay.accept(job(replay, JobState.COMPLETED, List.of()), RECORD_HASH));
        rejectAtomic(replay, () -> replay.accept(job(replay, JobState.RUNNING, List.of()), RECORD_HASH));
    }

    @Test
    void recoveryCannotEraseOrRewriteKnownTimeDigestCountOrPhaseFacts() {
        var replay = atOperation(State.WRITTEN_UNVERIFIED, ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI);
        var prior = replay.current().operations().get(0).observation();
        var variants = List.of(
                new Observation(State.RECOVERY_REQUIRED, Optional.empty(), prior.finishedAt(), prior.outputDigest(), 1,
                        Optional.of(Disposition.RECOVERY_RECONCILIATION_REQUIRED), prior.processingEvidence()),
                new Observation(State.RECOVERY_REQUIRED, Optional.of(START.plusNanos(1)), prior.finishedAt(), prior.outputDigest(), 1,
                        Optional.of(Disposition.RECOVERY_RECONCILIATION_REQUIRED), prior.processingEvidence()),
                new Observation(State.RECOVERY_REQUIRED, prior.startedAt(), Optional.empty(), prior.outputDigest(), 1,
                        Optional.of(Disposition.RECOVERY_RECONCILIATION_REQUIRED), prior.processingEvidence()),
                new Observation(State.RECOVERY_REQUIRED, prior.startedAt(), Optional.of(FINISH.plusNanos(1)), prior.outputDigest(), 1,
                        Optional.of(Disposition.RECOVERY_RECONCILIATION_REQUIRED), prior.processingEvidence()),
                new Observation(State.RECOVERY_REQUIRED, prior.startedAt(), prior.finishedAt(), Optional.empty(), 1,
                        Optional.of(Disposition.RECOVERY_RECONCILIATION_REQUIRED), prior.processingEvidence()),
                new Observation(State.RECOVERY_REQUIRED, prior.startedAt(), prior.finishedAt(), Optional.of(new ContentDigest(2, DIGEST.sha256())), 1,
                        Optional.of(Disposition.RECOVERY_RECONCILIATION_REQUIRED), prior.processingEvidence()),
                new Observation(State.RECOVERY_REQUIRED, prior.startedAt(), prior.finishedAt(), prior.outputDigest(), 0,
                        Optional.of(Disposition.RECOVERY_RECONCILIATION_REQUIRED), prior.processingEvidence()),
                new Observation(State.RECOVERY_REQUIRED, prior.startedAt(), prior.finishedAt(), prior.outputDigest(), 2,
                        Optional.of(Disposition.RECOVERY_RECONCILIATION_REQUIRED), prior.processingEvidence()),
                new Observation(State.RECOVERY_REQUIRED, prior.startedAt(), prior.finishedAt(), prior.outputDigest(), 1,
                        Optional.of(Disposition.RECOVERY_RECONCILIATION_REQUIRED), Optional.empty()),
                new Observation(State.RECOVERY_REQUIRED, prior.startedAt(), prior.finishedAt(), prior.outputDigest(), 1,
                        Optional.of(Disposition.RECOVERY_RECONCILIATION_REQUIRED), Optional.of(evidence(false))));
        for (var variant : variants) rejectAtomic(replay, () -> replay.accept(operation(replay, id(1), variant, List.of(RECOVERY)), RECORD_HASH));
        apply(replay, recovery(replay, id(1), false));
    }

    @Test
    void uncertaintyRetainsFailuresAndRejectsRepeatedRecordsWithoutNewFacts() {
        var replay = atOperation(State.FAILED, ManifestOperation.Kind.COPY);
        var observed = recovering(replay.current().operations().get(0).observation());
        rejectAtomic(replay, () -> replay.accept(operation(replay, id(1), observed, List.of(RECOVERY)), RECORD_HASH));
        apply(replay, recovery(replay, id(1), false));
        rejectAtomic(replay, () -> replay.accept(recovery(replay, id(1), false), RECORD_HASH));
        apply(replay, recovery(replay, id(1), true));
        apply(replay, job(replay, JobState.RECOVERY_REQUIRED, List.of(RECOVERY)));
        rejectAtomic(replay, () -> replay.accept(job(replay, JobState.RECOVERY_REQUIRED, List.of(RECOVERY)), RECORD_HASH));
        apply(replay, job(replay, JobState.RECOVERY_REQUIRED, List.of(RECOVERY, CLEANUP)));
        rejectAtomic(replay, () -> replay.accept(job(replay, JobState.RECOVERY_REQUIRED, List.of(RECOVERY, PERSISTENCE)), RECORD_HASH));
        assertTrue(replay.current().operations().get(0).failures().containsAll(List.of(FAILURE, RECOVERY, CLEANUP)));
    }

    @Test
    void interruptedIntentRemainsInProgressAndDoesNotAuthorizeContinuation() {
        var replay = atOperation(State.IN_PROGRESS, ManifestOperation.Kind.COPY);
        assertEquals(State.IN_PROGRESS, replay.current().operations().get(0).observation().state());
        assertTrue(replay.current().operations().get(0).observation().outputDigest().isEmpty());
        rejectAtomic(replay, () -> replay.accept(job(replay, JobState.COMPLETED, List.of()), RECORD_HASH));
        apply(replay, job(replay, JobState.RECOVERY_REQUIRED, List.of(RECOVERY)));
        apply(replay, recovery(replay, id(1), false));
        assertTrue(replay.current().operations().get(0).observation().finishedAt().isEmpty());
        assertTrue(replay.current().operations().get(0).observation().outputDigest().isEmpty());
        var suppliedLaterFacts = new Observation(State.RECOVERY_REQUIRED, Optional.of(START), Optional.of(FINISH),
                Optional.of(DIGEST), 1, Optional.of(Disposition.RECOVERY_RECONCILIATION_REQUIRED), Optional.empty());
        apply(replay, operation(replay, id(1), suppliedLaterFacts, List.of(RECOVERY)));
        assertEquals(State.RECOVERY_REQUIRED, replay.current().operations().get(0).observation().state());
        assertEquals(JobState.RECOVERY_REQUIRED, replay.current().state());
        rejectAtomic(replay, () -> replay.accept(job(replay, JobState.COMPLETED, List.of()), RECORD_HASH));
    }

    @Test
    void terminalJobsPermitOnlyCorrespondingUncertaintyRecords() {
        for (var terminal : List.of(JobState.COMPLETED, JobState.FAILED)) {
            var replay = atJob(terminal);
            rejectAtomic(replay, () -> replay.accept(recovery(replay, id(1), false), RECORD_HASH));
            apply(replay, job(replay, JobState.RECOVERY_REQUIRED, terminal == JobState.FAILED ? List.of(PERSISTENCE, RECOVERY) : List.of(RECOVERY)));
            if (terminal == JobState.COMPLETED) apply(replay, recovery(replay, id(1), false));
            else rejectAtomic(replay, () -> replay.accept(recovery(replay, id(1), false), RECORD_HASH)); // Still derived NOT_STARTED.
        }
    }

    @Test
    void operationUncertaintyStopsNormalWorkAndCannotBecomeOrdinaryFailure() {
        var planned = fresh(ManifestOperation.Kind.COPY);
        rejectAtomic(planned, () -> planned.accept(job(planned, JobState.RUNNING, List.of(RECOVERY)), RECORD_HASH));
        rejectAtomic(planned, () -> planned.accept(job(planned, JobState.FAILED, List.of(RECOVERY)), RECORD_HASH));
        var replay = running(ManifestOperation.Kind.COPY, 2);
        rejectAtomic(replay, () -> replay.accept(operation(replay, id(1), observed(State.BLOCKED), List.of(RECOVERY)), RECORD_HASH));
        apply(replay, operation(replay, id(1), observed(State.IN_PROGRESS), List.of()));
        rejectAtomic(replay, () -> replay.accept(operation(replay, id(1), observed(State.FAILED), List.of(RECOVERY)), RECORD_HASH));
        apply(replay, recovery(replay, id(1), false));
        rejectAtomic(replay, () -> replay.accept(operation(replay, id(2), observed(State.IN_PROGRESS), List.of()), RECORD_HASH));
        rejectAtomic(replay, () -> replay.accept(job(replay, JobState.FAILED, List.of(PERSISTENCE)), RECORD_HASH));
        apply(replay, job(replay, JobState.RECOVERY_REQUIRED, List.of(RECOVERY)));
        rejectAtomic(replay, () -> replay.accept(operation(replay, id(2), observed(State.SKIPPED_POLICY), List.of()), RECORD_HASH));
        var uncertainSource = new ManifestReplay(plan(ManifestOperation.Kind.COPY, 1, Optional.of(DIGEST), List.of(RECOVERY)), PLAN_HASH);
        rejectAtomic(uncertainSource, () -> uncertainSource.accept(job(uncertainSource, JobState.RUNNING, List.of()), RECORD_HASH));
        rejectAtomic(uncertainSource, () -> uncertainSource.accept(job(uncertainSource, JobState.FAILED, List.of(PERSISTENCE)), RECORD_HASH));
        apply(uncertainSource, job(uncertainSource, JobState.RECOVERY_REQUIRED, List.of(RECOVERY)));
    }

    // L04
    @Test
    void validateNextIsPureAndAcceptAdvancesOnlyOnce() {
        var replay = running(ManifestOperation.Kind.COPY, 1);
        var record = operation(replay, id(1), observed(State.IN_PROGRESS), List.of());
        var before = replay.current();
        replay.validateNext(record);
        replay.validateNext(record);
        assertEquals(before, replay.current());
        var receipt = replay.accept(record, RECORD_HASH);
        assertEquals(before.receipt().sequence() + 1, receipt.sequence());
        assertEquals(RECORD_HASH, receipt.headSha256());
        rejectAtomic(replay, () -> replay.validateNext(record));
        rejectAtomic(replay, () -> replay.accept(record, RECORD_HASH));
        // Identical already-published retry is handled by S4 before reducer accept, never folded twice here.
        apply(replay, operation(replay, id(1), observed(State.COMPLETED), List.of()));
        apply(replay, job(replay, JobState.COMPLETED, List.of()));
    }

    @Test
    void everyInvalidSuppliedHashAndNullRecordLeavesAllStateUnchanged() {
        var replay = running(ManifestOperation.Kind.COPY, 1);
        var record = operation(replay, id(1), observed(State.IN_PROGRESS), List.of());
        rejectAtomic(replay, () -> replay.validateNext(null));
        rejectAtomic(replay, () -> replay.accept(null, RECORD_HASH));
        rejectAtomic(replay, () -> replay.accept(record, null));
        for (var hash : badHashes()) rejectAtomic(replay, () -> replay.accept(record, hash));
        apply(replay, record);
        apply(replay, operation(replay, id(1), observed(State.COMPLETED), List.of()));
        apply(replay, job(replay, JobState.COMPLETED, List.of()));
    }

    @Test
    void rejectedAttemptsDoNotChangeCountersOrConsumeSequences() {
        var replay = running(ManifestOperation.Kind.COPY, 2);
        for (int i = 0; i < 3; i++) {
            rejectAtomic(replay, () -> replay.validateNext(job(replay, JobState.COMPLETED, List.of())));
            rejectAtomic(replay, () -> replay.accept(operation(replay, id(3), observed(State.IN_PROGRESS), List.of()), RECORD_HASH));
        }
        for (int n : new int[] {1, 2}) {
            apply(replay, operation(replay, id(n), observed(State.IN_PROGRESS), List.of()));
            rejectAtomic(replay, () -> replay.accept(job(replay, JobState.FAILED, List.of(PERSISTENCE)), RECORD_HASH));
            apply(replay, operation(replay, id(n), observed(State.IDENTICAL_EXISTING), List.of()));
        }
        apply(replay, job(replay, JobState.COMPLETED, List.of()));
        assertEquals(6, replay.current().receipt().sequence());
    }

    @Test
    void currentViewsAreImmutableSnapshotsWithoutExposingIndexesOrHistory() {
        var replay = fresh(ManifestOperation.Kind.COPY);
        var old = replay.current();
        assertThrowsExactly(UnsupportedOperationException.class, () -> old.operations().clear());
        assertThrowsExactly(UnsupportedOperationException.class, () -> old.jobFailures().clear());
        assertThrowsExactly(UnsupportedOperationException.class, () -> old.operations().get(0).failures().clear());
        apply(replay, job(replay, JobState.RUNNING, List.of()));
        assertEquals(JobState.PLANNED, old.state());
        assertEquals(0, old.receipt().sequence());
        assertSame(old.plan(), replay.current().plan());
        assertFalse(old == replay.current());
    }

    @Test
    void replayPublicApiIsExactlyFrozen() throws Exception {
        assertTrue(Modifier.isFinal(ManifestReplay.class.getModifiers()));
        assertEquals(1, ManifestReplay.class.getDeclaredConstructors().length);
        assertArrayEquals(new Class<?>[] {ProvenanceManifest.class, String.class}, ManifestReplay.class.getDeclaredConstructors()[0].getParameterTypes());
        assertArrayEquals(new String[] {"accept", "current", "validateNext"}, Arrays.stream(ManifestReplay.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers())).map(m -> m.getName()).sorted().toArray(String[]::new));
        assertEquals(void.class, ManifestReplay.class.getDeclaredMethod("validateNext", CheckpointRecord.class).getReturnType());
        assertEquals(ManifestReceipt.class, ManifestReplay.class.getDeclaredMethod("accept", CheckpointRecord.class, String.class).getReturnType());
        assertEquals(ManifestState.class, ManifestReplay.class.getDeclaredMethod("current").getReturnType());
        assertTrue(Modifier.isPublic(ManifestReplay.class.getDeclaredConstructors()[0].getModifiers()));
        for (var method : ManifestReplay.class.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                assertEquals(0, method.getExceptionTypes().length);
                assertFalse(Modifier.isStatic(method.getModifiers()));
            }
        }
        assertEquals("org.cbihi.mrinormalizer.application.provenance.manifest", ManifestReplay.class.getPackageName());
    }

    // L05
    @Test
    void limitsAndCheckedSequenceNeverWrapOrDropFacts() throws Exception {
        var replay = running(ManifestOperation.Kind.COPY, 1);
        seedHeadSequence(replay, 300000);
        var record = operation(replay, id(1), observed(State.BLOCKED), List.of());
        apply(replay, record);
        assertEquals(300001, replay.current().receipt().sequence());
        rejectAtomic(replay, () -> replay.accept(job(replay, JobState.FAILED, List.of(PERSISTENCE)), RECORD_HASH));
        var overflow = fresh(ManifestOperation.Kind.COPY);
        seedHeadSequence(overflow, Long.MAX_VALUE);
        var impossible = new CheckpointRecord(1, JOB, 1, overflow.current().receipt().headSha256(), TIME,
                Kind.JOB_OBSERVED, Optional.empty(), Optional.empty(), Optional.of(JobState.RUNNING), List.of());
        rejectAtomic(overflow, () -> overflow.validateNext(impossible));
        rejectAtomic(overflow, () -> overflow.accept(impossible, RECORD_HASH));
        assertEquals("Replay sequence overflow", assertThrowsExactly(IllegalArgumentException.class,
                () -> overflow.validateNext(impossible)).getMessage());
        assertEquals("Replay sequence overflow", assertThrowsExactly(IllegalArgumentException.class,
                () -> overflow.accept(impossible, RECORD_HASH)).getMessage());
        // White-box arithmetic seams only: the ordinary count cap makes Long.MAX_VALUE unreachable by valid replay.
    }

    @Test
    void inheritedCountReferenceAndFailureBoundsNeverTruncateThePlan() {
        var replay = running(ManifestOperation.Kind.COPY, 1);
        var before = replay.current();
        var failure = new ManifestFailure(Phase.HASHING, Code.HASH_FAILED);
        reject(() -> new CheckpointRecord(1, JOB, 2, RECORD_HASH, TIME, Kind.OPERATION_OBSERVED, Optional.of(id(1)),
                Optional.of(observed(State.BLOCKED)), Optional.empty(), java.util.Collections.nCopies(65, failure)));
        reject(() -> new ProvenanceManifest(1, JOB, CREATED, java.util.Collections.nCopies(100001, before.plan().sources().get(0)), List.of()));
        reject(() -> new ManifestOperation(id(1), ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI,
                java.util.Collections.nCopies(100001, before.plan().sources().get(0).source()), output("out")));
        assertEquals(before, replay.current());
        assertSame(before.plan(), replay.current().plan());
    }

    @Test
    void reducerKeepsOneCurrentObservationRatherThanJournalHistory() throws Exception {
        var replay = running(ManifestOperation.Kind.COPY, 1);
        var original = replay.current().plan();
        apply(replay, operation(replay, id(1), observed(State.IN_PROGRESS), List.of()));
        apply(replay, operation(replay, id(1), observed(State.FAILED), List.of(FAILURE)));
        apply(replay, recovery(replay, id(1), false));
        apply(replay, recovery(replay, id(1), true));
        assertEquals(1, replay.current().operations().size());
        assertSame(original, replay.current().plan());
        for (var field : ManifestReplay.class.getDeclaredFields()) {
            assertFalse(field.getGenericType().getTypeName().contains(CheckpointRecord.class.getName()),
                    "Reducer must not retain supplied records as history");
        }
    }

    private static ManifestReplay fresh(ManifestOperation.Kind kind) {
        return new ManifestReplay(plan(kind, 1, Optional.of(DIGEST), List.of()), PLAN_HASH);
    }

    private static ManifestReplay running(ManifestOperation.Kind kind, int count) {
        var replay = new ManifestReplay(plan(kind, count, Optional.of(DIGEST), List.of()), PLAN_HASH);
        apply(replay, job(replay, JobState.RUNNING, List.of()));
        return replay;
    }

    private static ManifestReplay atOperation(State state, ManifestOperation.Kind kind) {
        var replay = running(kind, 1);
        if (state == State.NOT_STARTED) return replay;
        if (state == State.BLOCKED || state == State.SKIPPED_POLICY) {
            apply(replay, operation(replay, id(1), observed(state), List.of()));
        } else {
            apply(replay, operation(replay, id(1), observed(State.IN_PROGRESS), List.of()));
            if (state == State.RECOVERY_REQUIRED) apply(replay, recovery(replay, id(1), false));
            else if (state != State.IN_PROGRESS) apply(replay, operation(replay, id(1), observed(state), state == State.FAILED ? List.of(FAILURE) : List.of()));
        }
        return replay;
    }

    private static ManifestReplay atJob(JobState state) {
        var replay = fresh(ManifestOperation.Kind.COPY);
        switch (state) {
            case PLANNED -> { }
            case RUNNING, COMPLETED -> {
                apply(replay, job(replay, JobState.RUNNING, List.of()));
                apply(replay, operation(replay, id(1), observed(State.IN_PROGRESS), List.of()));
                apply(replay, operation(replay, id(1), observed(State.COMPLETED), List.of()));
                if (state == JobState.COMPLETED) apply(replay, job(replay, state, List.of()));
            }
            case FAILED -> apply(replay, job(replay, state, List.of(PERSISTENCE)));
            case RECOVERY_REQUIRED -> apply(replay, job(replay, state, List.of(RECOVERY)));
        }
        return replay;
    }

    private static Observation observed(State state) {
        return switch (state) {
            case NOT_STARTED -> new Observation(state, Optional.empty(), Optional.empty(), Optional.empty(), 0, Optional.empty(), Optional.empty());
            case IN_PROGRESS -> new Observation(state, Optional.of(START), Optional.empty(), Optional.empty(), 1, Optional.empty(), Optional.empty());
            case COMPLETED, IDENTICAL_EXISTING -> new Observation(state, Optional.of(START), Optional.of(FINISH), Optional.of(DIGEST), 1, Optional.empty(), Optional.empty());
            case WRITTEN_UNVERIFIED -> new Observation(state, Optional.of(START), Optional.of(FINISH), Optional.of(DIGEST), 1, Optional.empty(), Optional.of(evidence(true)));
            case SKIPPED_POLICY -> new Observation(state, Optional.empty(), Optional.of(FINISH), Optional.empty(), 0, Optional.of(Disposition.NOT_REQUESTED_BY_POLICY), Optional.empty());
            case BLOCKED -> new Observation(state, Optional.empty(), Optional.of(FINISH), Optional.empty(), 0, Optional.of(Disposition.VALIDATION_REQUIRED), Optional.empty());
            case FAILED -> new Observation(state, Optional.of(START), Optional.of(FINISH), Optional.empty(), 0, Optional.empty(), Optional.empty());
            case RECOVERY_REQUIRED -> new Observation(state, Optional.of(START), Optional.empty(), Optional.empty(), 1,
                    Optional.of(Disposition.RECOVERY_RECONCILIATION_REQUIRED), Optional.empty());
        };
    }

    private static Observation recovering(Observation previous) {
        return new Observation(State.RECOVERY_REQUIRED, previous.startedAt(), previous.finishedAt(), previous.outputDigest(),
                previous.matchedExpectedSourceCount(), Optional.of(Disposition.RECOVERY_RECONCILIATION_REQUIRED), previous.processingEvidence());
    }

    private static CheckpointRecord recovery(ManifestReplay replay, String operationId, boolean additionalFact) {
        var previous = replay.current().operations().stream().filter(o -> o.operationId().equals(operationId)).findFirst().orElseThrow();
        var failures = new ArrayList<>(previous.failures());
        if (!failures.contains(RECOVERY)) failures.add(RECOVERY);
        if (additionalFact && !failures.contains(CLEANUP)) failures.add(CLEANUP);
        return operation(replay, operationId, recovering(previous.observation()), failures);
    }

    private static CheckpointRecord operation(ManifestReplay replay, String id, Observation observation, List<ManifestFailure> failures) {
        var receipt = replay.current().receipt();
        return new CheckpointRecord(1, JOB, Math.addExact(receipt.sequence(), 1), receipt.headSha256(), TIME,
                Kind.OPERATION_OBSERVED, Optional.of(id), Optional.of(observation), Optional.empty(), failures);
    }

    private static CheckpointRecord job(ManifestReplay replay, JobState state, List<ManifestFailure> failures) {
        var receipt = replay.current().receipt();
        return new CheckpointRecord(1, JOB, Math.addExact(receipt.sequence(), 1), receipt.headSha256(), TIME,
                Kind.JOB_OBSERVED, Optional.empty(), Optional.empty(), Optional.of(state), failures);
    }

    private static ManifestReceipt apply(ManifestReplay replay, CheckpointRecord record) {
        var before = replay.current();
        replay.validateNext(record);
        assertEquals(before, replay.current());
        return replay.accept(record, RECORD_HASH);
    }

    private static SourceFileRecord source(String path, Optional<ContentDigest> digest, List<ManifestFailure> failures) {
        return new SourceFileRecord(new RelativePath(RelativePath.Root.SOURCE, path), digest,
                FormatAssessment.fromDetection(DetectionResult.identified(DetectionOutcome.DICOM)), failures);
    }

    private static ProvenanceManifest plan(ManifestOperation.Kind kind, int count, Optional<ContentDigest> digest, List<ManifestFailure> failures) {
        var source = source("synthetic/nonexistent/source", digest, failures);
        var operations = new ArrayList<ManifestOperation>();
        for (int n = 1; n <= count; n++) operations.add(new ManifestOperation(id(n), kind, List.of(source.source()), output("out" + n)));
        return new ProvenanceManifest(1, JOB, CREATED, List.of(source), operations);
    }

    private static ProcessingEvidence evidence(boolean successful) {
        var facts = new ProcessingEvidence.ConversionFacts(1, 1, 1, ScalarType.INT16, 1, 1, 1, 0,
                new AffineMatrix4(new double[][] {{1, 0, 0, 0}, {0, 1, 0, 0}, {0, 0, 1, 0}, {0, 0, 0, 1}}),
                new IntensityTransform(false, 1, 0), true, false, false, false);
        return new ProcessingEvidence(Scope.CONVERSION, successful, Optional.empty(),
                successful ? List.of() : List.of(DicomProcessingError.EMPTY_INPUT),
                successful ? List.of() : List.of(DicomToNiftiError.OUTPUT_WRITE_FAILED), successful ? Optional.of(facts) : Optional.empty());
    }

    private static void seedHeadSequence(ManifestReplay replay, long sequence) throws Exception {
        var field = Arrays.stream(ManifestReplay.class.getDeclaredFields()).filter(f -> f.getType() == ManifestReceipt.class).findFirst().orElseThrow();
        field.setAccessible(true);
        var previous = replay.current().receipt();
        field.set(replay, new ManifestReceipt(previous.jobId(), sequence, previous.planSha256(), previous.headSha256()));
    }

    private static RelativePath output(String path) { return new RelativePath(RelativePath.Root.OUTPUT, path); }

    private static String id(int n) { return String.format("%064x", n); }

    private static List<String> badHashes() {
        return List.of("", "a".repeat(63), "a".repeat(65), "A".repeat(64), "g".repeat(64), "a".repeat(63) + "\n", "/private/subject-123");
    }

    private static void rejectAtomic(ManifestReplay replay, Executable attempt) {
        var before = replay.current();
        reject(attempt);
        assertEquals(before, replay.current());
    }

    private static void reject(Executable attempt) {
        var failure = assertThrowsExactly(IllegalArgumentException.class, attempt);
        assertNull(failure.getCause());
        assertTrue(failure.getMessage() != null && !failure.getMessage().isEmpty());
        assertFalse(failure.getMessage().contains("subject-123"));
    }

    private static void attemptUnchecked(Executable attempt) {
        try { attempt.execute(); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new AssertionError(failure); }
    }
}
