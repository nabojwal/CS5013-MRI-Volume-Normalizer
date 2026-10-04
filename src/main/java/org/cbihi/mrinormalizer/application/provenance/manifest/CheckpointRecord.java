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
        WRITTEN_UNVERIFIED, SKIPPED_POLICY, BLOCKED, FAILED, RECOVERY_REQUIRED
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
        if (schemaVersion != 1) {
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
            boolean coherent = switch (observed.state()) {
                case NOT_STARTED, IN_PROGRESS, COMPLETED, IDENTICAL_EXISTING, WRITTEN_UNVERIFIED, SKIPPED_POLICY -> failures.isEmpty();
                case BLOCKED -> true; // Applicable plan assessment is checked with the plan during replay.
                case FAILED -> !failures.isEmpty() || hasProcessingErrors(observed);
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
            Optional<Disposition> disposition, Optional<ProcessingEvidence> processingEvidence
    ) {
        public Observation {
            if (state == null || startedAt == null || finishedAt == null || outputDigest == null
                    || disposition == null || processingEvidence == null) {
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
                case FAILED -> finishedAt.isPresent() && outputDigest.isEmpty() && matchedExpectedSourceCount == 0 && disposition.isEmpty();
                case RECOVERY_REQUIRED -> disposition.equals(Optional.of(Disposition.RECOVERY_RECONCILIATION_REQUIRED));
            };
            if (!coherent) {
                throw new IllegalArgumentException("Observation facts do not match state");
            }
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
