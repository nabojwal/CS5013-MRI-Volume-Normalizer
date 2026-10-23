package org.cbihi.mrinormalizer.application.provenance.manifest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** One supplied factual journal value; links, transitions and execution eligibility are checked by replay. */
public record CheckpointRecord(
        int schemaVersion, UUID jobId, long sequence, String previousRecordSha256,
        Instant recordedAt, Kind kind, Optional<String> operationId,
        Optional<Observation> observation, Optional<ManifestState.JobState> jobState,
        List<ManifestFailure> failures
) {
    public enum Kind { OPERATION_OBSERVED, JOB_OBSERVED }

    public enum State {
        NOT_STARTED, IN_PROGRESS, COMPLETED, IDENTICAL_EXISTING,
        WRITTEN_UNVERIFIED, SKIPPED_POLICY, BLOCKED, FAILED, RECOVERY_REQUIRED,
        STAGED_VERIFIED, VERIFIED_PUBLISHED
    }

    public enum ConversionFault {
        PLAN_MISMATCH, SOURCE_BINDING_UNPROVEN, SOURCE_DIGEST_UNPROVEN,
        INTENT_NOT_ACKNOWLEDGED, M6_FAILED, M6_CAPTURE_INVALID, M7_FAILED,
        STAGE_CREATE_FAILED, STAGE_CLOSE_FAILED, STAGE_DIGEST_FAILED,
        REOPEN_FAILED, WEAK_IDENTITY_UNQUALIFIED, REPORT_INCONSISTENT,
        DIMENSIONS_DIFFER, SCALAR_TYPE_DIFFERS, VOXELS_DIFFER,
        RAS_AFFINE_DIFFERS, SPACING_DIFFERS, HANDEDNESS_DIFFERS,
        SCALING_DIFFERS, INVALID_EXPECTED_FLOAT32, RESOURCE_LIMIT,
        INTERRUPTED, STAGE_CHANGED, STAGED_ACK_FAILED, OUTPUT_CONFLICT,
        PUBLICATION_UNAVAILABLE, PUBLICATION_UNKNOWN, FINAL_DIGEST_MISMATCH,
        FINAL_ACK_FAILED, CLEANUP_FAILED, RELEASE_FAILED
    }

    public record ConversionVerification(
            UUID sessionId, ContentDigest stagedDigest, long comparedVoxelCount,
            int comparedCornerCount, double maxWorldDeviationMm,
            boolean weakSourceIdentityObserved, String policyId) {
        public ConversionVerification {
            if (sessionId == null || stagedDigest == null || comparedVoxelCount <= 0
                    || comparedCornerCount != 8 || !Double.isFinite(maxWorldDeviationMm)
                    || maxWorldDeviationMm < 0 || !"F8-NIFTI1-F32-1".equals(policyId)) {
                throw new IllegalArgumentException("Invalid post-write verification");
            }
        }
    }

    public enum Disposition {
        NOT_REQUESTED_BY_POLICY, VALIDATION_REQUIRED, UNSUPPORTED_INPUT,
        INPUT_UNAVAILABLE, RECOVERY_RECONCILIATION_REQUIRED
    }

    public CheckpointRecord {
        if (jobId == null || previousRecordSha256 == null || recordedAt == null || kind == null
                || operationId == null || observation == null || jobState == null || failures == null) {
            throw new IllegalArgumentException("Checkpoint components must be non-null");
        }
        if (schemaVersion != 1 && schemaVersion != 2) {
            throw new IllegalArgumentException("Unsupported checkpoint schema version");
        }
        if (sequence <= 0) {
            throw new IllegalArgumentException("Checkpoint sequence must be positive");
        }
        if (!isHash(previousRecordSha256)) {
            throw new IllegalArgumentException("Checkpoint hash must be lowercase SHA-256");
        }
        boolean exclusive = kind == Kind.OPERATION_OBSERVED
                ? operationId.isPresent() && observation.isPresent() && jobState.isEmpty()
                : operationId.isEmpty() && observation.isEmpty() && jobState.isPresent();
        if (!exclusive) {
            throw new IllegalArgumentException("Checkpoint fields do not match kind");
        }
        if (operationId.isPresent() && !isHash(operationId.orElseThrow())) {
            throw new IllegalArgumentException("Checkpoint operation ID must be lowercase SHA-256");
        }
        failures = orderedFailures(failures);
        if (kind == Kind.OPERATION_OBSERVED) {
            var observed = observation.orElseThrow();
            if (observed.state() == State.NOT_STARTED) {
                throw new IllegalArgumentException("Initial operation state cannot be checkpointed");
            }
            if (occursAfter(observed, recordedAt)) {
                throw new IllegalArgumentException("Checkpoint chronology is invalid");
            }
            if (schemaVersion == 1 && (!observed.postWriteVerification().isEmpty()
                    || !observed.postWriteFaults().isEmpty()
                    || observed.state() == State.STAGED_VERIFIED || observed.state() == State.VERIFIED_PUBLISHED)) {
                throw new IllegalArgumentException("V2 observation not permitted in v1");
            }
            if (schemaVersion == 1 && observed.state() == State.FAILED
                    && !(observed.finishedAt().isPresent() && observed.outputDigest().isEmpty()
                    && observed.matchedExpectedSourceCount() == 0 && observed.disposition().isEmpty()))
                throw new IllegalArgumentException("Legacy failed-state shape changed");
            boolean coherent = switch (observed.state()) {
                case NOT_STARTED, IN_PROGRESS, COMPLETED, IDENTICAL_EXISTING, WRITTEN_UNVERIFIED, SKIPPED_POLICY,
                        STAGED_VERIFIED, VERIFIED_PUBLISHED -> failures.isEmpty() && observed.postWriteFaults().isEmpty();
                case BLOCKED -> true;
                case FAILED -> !failures.isEmpty() || !observed.postWriteFaults().isEmpty() || hasProcessingErrors(observed);
                case RECOVERY_REQUIRED -> failures.stream().anyMatch(f -> f.code() == ManifestFailure.Code.RECOVERY_REQUIRED);
            };
            if (!coherent) {
                throw new IllegalArgumentException("Checkpoint failures do not match observation");
            }
        } else {
            if (jobState.orElseThrow() == ManifestState.JobState.PLANNED) {
                throw new IllegalArgumentException("Initial job state cannot be checkpointed");
            }
            boolean coherent = switch (jobState.orElseThrow()) {
                case PLANNED, RUNNING, FAILED -> true; // Prior operation evidence is a replay responsibility.
                case COMPLETED -> failures.isEmpty();
                case RECOVERY_REQUIRED -> failures.stream().anyMatch(f -> f.code() == ManifestFailure.Code.RECOVERY_REQUIRED);
            };
            if (!coherent) {
                throw new IllegalArgumentException("Checkpoint failures do not match job state");
            }
        }
    }

    /** Supplied local facts only; no plan cardinality, operation kind or prior state is inferred. */
    public record Observation(
            State state, Optional<Instant> startedAt, Optional<Instant> finishedAt,
            Optional<ContentDigest> outputDigest, int matchedExpectedSourceCount,
            Optional<Disposition> disposition, Optional<ProcessingEvidence> processingEvidence,
            Optional<ConversionVerification> postWriteVerification, List<ConversionFault> postWriteFaults
    ) {
        public Observation(State state, Optional<Instant> startedAt, Optional<Instant> finishedAt,
                Optional<ContentDigest> outputDigest, int matchedExpectedSourceCount,
                Optional<Disposition> disposition, Optional<ProcessingEvidence> processingEvidence) {
            this(state, startedAt, finishedAt, outputDigest, matchedExpectedSourceCount,
                    disposition, processingEvidence, Optional.empty(), List.of());
        }
        public Observation {
            if (state == null || startedAt == null || finishedAt == null || outputDigest == null
                    || disposition == null || processingEvidence == null || postWriteVerification == null
                    || postWriteFaults == null) {
                throw new IllegalArgumentException("Observation components must be non-null");
            }
            if (matchedExpectedSourceCount < 0 || matchedExpectedSourceCount > 100000) {
                throw new IllegalArgumentException("Observation source count is invalid");
            }
            if (startedAt.isPresent() && finishedAt.isPresent()
                    && startedAt.orElseThrow().isAfter(finishedAt.orElseThrow())) {
                throw new IllegalArgumentException("Observation chronology is invalid");
            }
            if (finishedAt.isPresent() && processingEvidence.flatMap(ProcessingEvidence::sourceSummary)
                    .map(s -> s.completedAt().isAfter(finishedAt.orElseThrow())).orElse(false)) {
                throw new IllegalArgumentException("Observation chronology is invalid");
            }
            var faultSet = java.util.EnumSet.noneOf(ConversionFault.class);
            for (var fault : postWriteFaults) {
                if (fault == null || !faultSet.add(fault))
                    throw new IllegalArgumentException("Invalid conversion fault list");
            }
            postWriteFaults = List.copyOf(faultSet);
            if (postWriteFaults.size() > 32) throw new IllegalArgumentException("Too many conversion faults");
            if (postWriteVerification.isPresent() && !postWriteVerification.orElseThrow().stagedDigest().equals(outputDigest.orElse(null)))
                throw new IllegalArgumentException("Verification digest does not match stage");
            boolean coherent = switch (state) {
                case NOT_STARTED -> startedAt.isEmpty() && finishedAt.isEmpty() && outputDigest.isEmpty()
                        && matchedExpectedSourceCount == 0 && disposition.isEmpty() && processingEvidence.isEmpty();
                case IN_PROGRESS -> startedAt.isPresent() && finishedAt.isEmpty() && outputDigest.isEmpty()
                        && matchedExpectedSourceCount > 0 && disposition.isEmpty() && processingEvidence.isEmpty();
                case COMPLETED, IDENTICAL_EXISTING -> startedAt.isPresent() && finishedAt.isPresent() && outputDigest.isPresent()
                        && matchedExpectedSourceCount > 0 && disposition.isEmpty() && processingEvidence.isEmpty();
                case WRITTEN_UNVERIFIED -> startedAt.isPresent() && finishedAt.isPresent() && outputDigest.isPresent()
                        && matchedExpectedSourceCount > 0 && disposition.isEmpty()
                        && processingEvidence.map(e -> e.scope() == ProcessingEvidence.Scope.CONVERSION
                                && e.phaseSuccessful() && e.conversionFacts().isPresent()).orElse(false);
                case SKIPPED_POLICY -> finishedAt.isPresent() && outputDigest.isEmpty() && matchedExpectedSourceCount == 0
                        && disposition.equals(Optional.of(Disposition.NOT_REQUESTED_BY_POLICY)) && processingEvidence.isEmpty();
                case BLOCKED -> finishedAt.isPresent() && outputDigest.isEmpty() && matchedExpectedSourceCount == 0
                        && disposition.map(d -> d == Disposition.VALIDATION_REQUIRED || d == Disposition.UNSUPPORTED_INPUT
                                || d == Disposition.INPUT_UNAVAILABLE).orElse(false);
                case STAGED_VERIFIED, VERIFIED_PUBLISHED -> startedAt.isPresent() && finishedAt.isPresent()
                        && outputDigest.isPresent() && matchedExpectedSourceCount > 0 && disposition.isEmpty()
                        && postWriteVerification.isPresent() && postWriteFaults.isEmpty()
                        && processingEvidence.map(e -> e.scope() == ProcessingEvidence.Scope.CONVERSION
                                && e.phaseSuccessful() && e.conversionFacts().isPresent()).orElse(false);
                case FAILED -> disposition.isEmpty() && (finishedAt.isPresent() || startedAt.isEmpty())
                        && (!postWriteFaults.isEmpty() || matchedExpectedSourceCount == 0 || hasEvidenceErrors(processingEvidence));
                case RECOVERY_REQUIRED -> disposition.equals(Optional.of(Disposition.RECOVERY_RECONCILIATION_REQUIRED));
            };
            if (!coherent || (state != State.FAILED && state != State.RECOVERY_REQUIRED
                    && !postWriteFaults.isEmpty())
                    || (state != State.FAILED && state != State.RECOVERY_REQUIRED
                    && state != State.STAGED_VERIFIED && state != State.VERIFIED_PUBLISHED
                    && postWriteVerification.isPresent())) {
                throw new IllegalArgumentException("Observation facts do not match state");
            }
        }
        private static boolean hasEvidenceErrors(Optional<ProcessingEvidence> evidence) {
            return evidence.map(e -> !e.reconstructionErrors().isEmpty() || !e.conversionErrors().isEmpty()).orElse(false);
        }
    }

    private static boolean isHash(String value) {
        return value.length() == 64 && value.matches("[0-9a-f]{64}");
    }

    private static List<ManifestFailure> orderedFailures(List<ManifestFailure> failures) {
        if (failures.size() > 64) {
            throw new IllegalArgumentException("Checkpoint failure limit exceeded");
        }
        var seen = new HashSet<ManifestFailure>();
        var ordered = new ArrayList<ManifestFailure>(failures.size());
        for (var failure : failures) {
            if (failure == null || !seen.add(failure)) {
                throw new IllegalArgumentException("Checkpoint failures must be non-null and unique");
            }
            ordered.add(failure);
        }
        ordered.sort(Comparator.comparingInt((ManifestFailure f) -> f.phase().ordinal()).thenComparingInt(f -> f.code().ordinal()));
        return List.copyOf(ordered);
    }

    private static boolean hasProcessingErrors(Observation observed) {
        return observed.processingEvidence().map(e -> !e.reconstructionErrors().isEmpty() || !e.conversionErrors().isEmpty()).orElse(false);
    }

    private static boolean occursAfter(Observation observed, Instant time) {
        return observed.startedAt().map(t -> t.isAfter(time)).orElse(false)
                || observed.finishedAt().map(t -> t.isAfter(time)).orElse(false)
                || observed.processingEvidence().flatMap(ProcessingEvidence::sourceSummary).map(s -> s.completedAt().isAfter(time)).orElse(false);
    }
}
