package org.cbihi.mrinormalizer.application.provenance.manifest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** Immutable supplied current view, not a reducer or proof of legal journal history. */
public record ManifestState(
        ProvenanceManifest plan, ManifestReceipt receipt, JobState state,
        Instant recordedAt, List<OperationState> operations, List<ManifestFailure> jobFailures
) {
    public enum JobState { PLANNED, RUNNING, COMPLETED, FAILED, RECOVERY_REQUIRED }

    public ManifestState {
        if (plan == null || receipt == null || state == null || recordedAt == null || operations == null || jobFailures == null) {
            throw new IllegalArgumentException("Manifest state components must be non-null");
        }
        if (!plan.jobId().equals(receipt.jobId())) {
            throw new IllegalArgumentException("Manifest state receipt belongs to another job");
        }
        if (recordedAt.isBefore(plan.createdAt())) {
            throw new IllegalArgumentException("Manifest state chronology is invalid");
        }
        if (operations.size() != plan.operations().size()) {
            throw new IllegalArgumentException("Manifest state operation count does not match plan");
        }
        var planned = new HashSet<String>();
        for (var operation : plan.operations()) planned.add(operation.operationId());
        var seen = new HashSet<String>();
        var ordered = new ArrayList<OperationState>(operations.size());
        for (var operation : operations) {
            if (operation == null || !planned.contains(operation.operationId()) || !seen.add(operation.operationId())) {
                throw new IllegalArgumentException("Manifest state operations must be non-null unique planned keys");
            }
            if (occursAfter(operation.observation(), recordedAt)) {
                throw new IllegalArgumentException("Manifest state chronology is invalid");
            }
            ordered.add(operation);
        }
        ordered.sort(Comparator.comparing(OperationState::operationId));
        operations = List.copyOf(ordered);
        jobFailures = orderedFailures(jobFailures);
        if (receipt.sequence() == 0 && (state != JobState.PLANNED || !recordedAt.equals(plan.createdAt())
                || !jobFailures.isEmpty() || operations.stream().anyMatch(o -> o.observation().state() != CheckpointRecord.State.NOT_STARTED))) {
            throw new IllegalArgumentException("Plan anchor state is invalid");
        }
    }

    public record OperationState(String operationId, CheckpointRecord.Observation observation, List<ManifestFailure> failures) {
        public OperationState {
            if (operationId == null || observation == null || failures == null) {
                throw new IllegalArgumentException("Operation state components must be non-null");
            }
            if (operationId.length() != 64 || !operationId.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Operation state ID must be lowercase SHA-256");
            }
            failures = orderedFailures(failures);
            boolean coherent = switch (observation.state()) {
                case NOT_STARTED, IN_PROGRESS, COMPLETED, IDENTICAL_EXISTING, WRITTEN_UNVERIFIED, SKIPPED_POLICY -> failures.isEmpty();
                case BLOCKED -> true;
                case FAILED -> !failures.isEmpty() || observation.processingEvidence()
                        .map(e -> !e.reconstructionErrors().isEmpty() || !e.conversionErrors().isEmpty()).orElse(false);
                case RECOVERY_REQUIRED -> failures.stream().anyMatch(f -> f.code() == ManifestFailure.Code.RECOVERY_REQUIRED);
            };
            if (!coherent) {
                throw new IllegalArgumentException("Operation state failures do not match observation");
            }
        }
    }

    private static List<ManifestFailure> orderedFailures(List<ManifestFailure> failures) {
        if (failures.size() > 64) {
            throw new IllegalArgumentException("State failure limit exceeded");
        }
        var seen = new HashSet<ManifestFailure>();
        var ordered = new ArrayList<ManifestFailure>(failures.size());
        for (var failure : failures) {
            if (failure == null || !seen.add(failure)) {
                throw new IllegalArgumentException("State failures must be non-null and unique");
            }
            ordered.add(failure);
        }
        ordered.sort(Comparator.comparingInt((ManifestFailure f) -> f.phase().ordinal()).thenComparingInt(f -> f.code().ordinal()));
        return List.copyOf(ordered);
    }

    private static boolean occursAfter(CheckpointRecord.Observation observed, Instant time) {
        return observed.startedAt().map(t -> t.isAfter(time)).orElse(false)
                || observed.finishedAt().map(t -> t.isAfter(time)).orElse(false)
                || observed.processingEvidence().flatMap(ProcessingEvidence::sourceSummary).map(s -> s.completedAt().isAfter(time)).orElse(false);
    }
}
