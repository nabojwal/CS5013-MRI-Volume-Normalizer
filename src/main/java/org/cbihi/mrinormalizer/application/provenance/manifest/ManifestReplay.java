package org.cbihi.mrinormalizer.application.provenance.manifest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.cbihi.mrinormalizer.application.dataset.model.ConversionReadiness;
import org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.Observation;
import org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.State;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestState.JobState;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestState.OperationState;

/**
 * Folds supplied, consecutive journal facts in memory. Hash arguments are supplied
 * evidence, not calculated or authenticated here. No execution or recovery occurs.
 * Only the immutable plan and each operation's current observation are retained.
 */
public final class ManifestReplay {
    private static final long MAX_CHECKPOINTS = 300001;

    private final ProvenanceManifest plan;
    private final Map<String, Integer> operationIndexes;
    private final OperationState[] operations;
    private final boolean[] digestEligible;
    private final boolean[] blockingEvidence;
    private final ContentDigest[] copyDigests;
    private final boolean sourceFailures;
    private final boolean sourceUncertainty;

    private ManifestReceipt head;
    private JobState jobState = JobState.PLANNED;
    private Instant recordedAt;
    private List<ManifestFailure> jobFailures = List.of();
    private int activeCount;
    private int successfulCopies;
    private int completionEligibleCount;
    private int operationFailureCount;
    private int uncertaintyCount;

    public ManifestReplay(ProvenanceManifest plan, String planSha256) {
        require(plan != null && planSha256 != null, "Replay components must be non-null");
        require(isHash(planSha256), "Replay hash must be lowercase SHA-256");
        this.plan = plan;
        this.head = new ManifestReceipt(plan.jobId(), 0, planSha256, planSha256);
        this.recordedAt = plan.createdAt();
        int size = plan.operations().size();
        this.operationIndexes = new HashMap<>();
        this.operations = new OperationState[size];
        this.digestEligible = new boolean[size];
        this.blockingEvidence = new boolean[size];
        this.copyDigests = new ContentDigest[size];

        var sources = new HashMap<RelativePath, SourceFileRecord>();
        boolean anySourceFailure = false;
        boolean anySourceUncertainty = false;
        for (var source : plan.sources()) {
            sources.put(source.source(), source);
            anySourceFailure |= !source.failures().isEmpty()
                    || source.assessment().readiness() == ConversionReadiness.BLOCKED;
            anySourceUncertainty |= hasUncertainty(source.failures());
        }
        this.sourceFailures = anySourceFailure;
        this.sourceUncertainty = anySourceUncertainty;
        var initial = new Observation(State.NOT_STARTED, Optional.empty(), Optional.empty(),
                Optional.empty(), 0, Optional.empty(), Optional.empty());
        for (int i = 0; i < size; i++) {
            var operation = plan.operations().get(i);
            operationIndexes.put(operation.operationId(), i);
            operations[i] = new OperationState(operation.operationId(), initial, List.of());
            digestEligible[i] = true;
            for (var reference : operation.sources()) {
                var source = sources.get(reference);
                digestEligible[i] &= source.digest().isPresent() && source.failures().isEmpty();
                blockingEvidence[i] |= !source.failures().isEmpty()
                        || source.assessment().readiness() != ConversionReadiness.READY;
            }
            if (operation.kind() == ManifestOperation.Kind.COPY) {
                copyDigests[i] = sources.get(operation.sources().get(0)).digest().orElse(null);
            }
        }
    }

    /** Checks the next supplied fact without changing any reducer state. */
    public synchronized void validateNext(CheckpointRecord record) {
        require(record != null, "Replay record must be non-null");
        require(record.schemaVersion() == plan.schemaVersion() && record.jobId().equals(plan.jobId()),
                "Replay record does not match plan");
        long next;
        try {
            next = Math.addExact(head.sequence(), 1);
        } catch (ArithmeticException ignored) {
            throw new IllegalArgumentException("Replay sequence overflow");
        }
        require(next <= MAX_CHECKPOINTS, "Replay checkpoint limit exceeded");
        require(record.sequence() == next, "Replay sequence must be consecutive");
        require(record.previousRecordSha256().equals(head.headSha256()), "Replay hash link does not match head");
        require(!record.recordedAt().isBefore(recordedAt), "Replay chronology is invalid");
        if (record.kind() == CheckpointRecord.Kind.JOB_OBSERVED) {
            validateJob(record);
        } else {
            validateOperation(record);
        }
    }

    /** Validates and prepares all replacement values before advancing the current view. */
    public synchronized ManifestReceipt accept(CheckpointRecord record, String recordSha256) {
        require(recordSha256 != null && isHash(recordSha256), "Replay hash must be lowercase SHA-256");
        validateNext(record);
        var nextHead = new ManifestReceipt(plan.jobId(), record.sequence(), head.planSha256(), recordSha256);
        int index = -1;
        OperationState replacement = null;
        int nextActive = activeCount;
        int nextSuccessful = successfulCopies;
        int nextEligible = completionEligibleCount;
        int nextFailures = operationFailureCount;
        int nextUncertain = uncertaintyCount;
        var nextJobState = jobState;
        var nextJobFailures = jobFailures;
        if (record.kind() == CheckpointRecord.Kind.OPERATION_OBSERVED) {
            index = operationIndexes.get(record.operationId().orElseThrow());
            var previous = operations[index];
            replacement = new OperationState(previous.operationId(), record.observation().orElseThrow(), record.failures());
            var before = previous.observation().state();
            var after = replacement.observation().state();
            nextActive += flag(after == State.IN_PROGRESS) - flag(before == State.IN_PROGRESS);
            nextSuccessful += flag(copySuccess(after)) - flag(copySuccess(before));
            nextEligible += flag(completionEligible(after)) - flag(completionEligible(before));
            nextFailures += flag(hasFailures(replacement)) - flag(hasFailures(previous));
            nextUncertain += flag(after == State.RECOVERY_REQUIRED) - flag(before == State.RECOVERY_REQUIRED);
        } else {
            nextJobState = record.jobState().orElseThrow();
            nextJobFailures = record.failures();
        }

        // No validation, allocation or caller code remains after this commit point.
        if (index >= 0) operations[index] = replacement;
        activeCount = nextActive;
        successfulCopies = nextSuccessful;
        completionEligibleCount = nextEligible;
        operationFailureCount = nextFailures;
        uncertaintyCount = nextUncertain;
        jobState = nextJobState;
        jobFailures = nextJobFailures;
        recordedAt = record.recordedAt();
        head = nextHead;
        return nextHead;
    }

    /** Constructs an immutable view on request; views are not retained as history. */
    public synchronized ManifestState current() {
        var view = new ArrayList<OperationState>(operations.length);
        for (var operation : operations) view.add(operation);
        return new ManifestState(plan, head, jobState, recordedAt, view, jobFailures);
    }

    private void validateOperation(CheckpointRecord record) {
        var index = operationIndexes.get(record.operationId().orElseThrow());
        require(index != null, "Replay operation is not in plan");
        var operation = plan.operations().get(index);
        var prior = operations[index];
        var before = prior.observation();
        var after = record.observation().orElseThrow();
        boolean recovering = after.state() == State.RECOVERY_REQUIRED;
        require(recovering || !hasUncertainty(record.failures()), "Replay uncertain facts require recovery state");
        require(jobState == JobState.RUNNING || jobState == JobState.RECOVERY_REQUIRED && recovering,
                "Replay operation requires an eligible running job");
        require(uncertaintyCount == 0 || recovering, "Replay uncertainty prevents normal operation observations");
        boolean transition = recovering ? before.state() != State.NOT_STARTED
                : before.state() == State.NOT_STARTED
                    ? after.state() == State.IN_PROGRESS || after.state() == State.BLOCKED || after.state() == State.SKIPPED_POLICY
                    : before.state() == State.IN_PROGRESS
                        && (after.state() == State.COMPLETED || after.state() == State.IDENTICAL_EXISTING
                            || after.state() == State.WRITTEN_UNVERIFIED || after.state() == State.FAILED);
        require(transition, "Replay operation transition is invalid");
        if (operation.kind() == ManifestOperation.Kind.COPY) {
            require(after.processingEvidence().isEmpty() && after.state() != State.WRITTEN_UNVERIFIED,
                    "Replay COPY cannot carry conversion evidence");
        } else {
            require(!copySuccess(after.state()), "Replay conversion cannot claim verified COPY completion");
        }
        if (after.state() == State.IN_PROGRESS || copySuccess(after.state()) || after.state() == State.WRITTEN_UNVERIFIED) {
            require(digestEligible[index] && after.matchedExpectedSourceCount() == operation.sources().size(),
                    "Replay requires all planned digest matches");
        }
        if (after.state() == State.IN_PROGRESS) {
            require(activeCount == 0, "Replay permits only one active operation");
        }
        if (copySuccess(after.state())) {
            require(after.outputDigest().orElseThrow().equals(copyDigests[index]), "Replay COPY output digest does not match plan");
        }
        if (after.state() == State.BLOCKED) {
            require(blockingEvidence[index] || !record.failures().isEmpty() || hasProcessingErrors(after),
                    "Replay blocked operation requires applicable evidence");
        }
        if (recovering) {
            validateRecovery(prior, after, record.failures(), operation.sources().size(), digestEligible[index]);
        } else if (before.startedAt().isPresent()) {
            require(before.startedAt().equals(after.startedAt()), "Replay cannot rewrite known operation start");
        }
    }

    private void validateJob(CheckpointRecord record) {
        var next = record.jobState().orElseThrow();
        require(next == JobState.RECOVERY_REQUIRED || !hasUncertainty(record.failures()),
                "Replay uncertain facts require recovery state");
        boolean transition = next == JobState.RECOVERY_REQUIRED
                || jobState == JobState.PLANNED && (next == JobState.RUNNING || next == JobState.FAILED)
                || jobState == JobState.RUNNING && (next == JobState.COMPLETED || next == JobState.FAILED);
        require(transition, "Replay job transition is invalid");
        require(record.failures().containsAll(jobFailures), "Replay cannot erase known job failures");
        switch (next) {
            case PLANNED -> throw new IllegalArgumentException("Replay initial job state cannot be checkpointed");
            case RUNNING -> require(operations.length > 0 && !sourceUncertainty,
                    "Replay requires nonempty certain plan before work");
            case COMPLETED -> require(operations.length > 0 && completionEligibleCount == operations.length
                    && successfulCopies > 0 && activeCount == 0 && uncertaintyCount == 0
                    && !sourceFailures && operationFailureCount == 0 && jobFailures.isEmpty() && record.failures().isEmpty(),
                    "Replay job completion requires successful failure-free COPY evidence");
            case FAILED -> require(activeCount == 0 && uncertaintyCount == 0 && !sourceUncertainty
                    && (sourceFailures || operationFailureCount > 0 || !jobFailures.isEmpty() || !record.failures().isEmpty()),
                    "Replay job failure requires certain failure evidence and no active operation");
            case RECOVERY_REQUIRED -> require(jobState != JobState.RECOVERY_REQUIRED || !record.failures().equals(jobFailures),
                    "Replay repeated uncertainty requires new facts");
        }
    }

    private static void validateRecovery(OperationState prior, Observation after, List<ManifestFailure> failures,
            int sourceCount, boolean eligible) {
        var before = prior.observation();
        require(retains(before.startedAt(), after.startedAt()) && retains(before.finishedAt(), after.finishedAt())
                && retains(before.outputDigest(), after.outputDigest()) && retains(before.processingEvidence(), after.processingEvidence())
                && before.matchedExpectedSourceCount() == after.matchedExpectedSourceCount()
                && failures.containsAll(prior.failures()), "Replay uncertainty must retain known facts and failures");
        require(after.matchedExpectedSourceCount() <= sourceCount
                && (after.matchedExpectedSourceCount() == 0 || eligible), "Replay uncertainty source facts do not match plan");
        require(before.state() != State.RECOVERY_REQUIRED || !before.equals(after) || !prior.failures().equals(failures),
                "Replay repeated uncertainty requires new facts");
    }

    private static boolean retains(Optional<?> before, Optional<?> after) {
        return before.isEmpty() || before.equals(after);
    }

    private static boolean hasProcessingErrors(Observation observed) {
        return observed.processingEvidence().map(e -> !e.reconstructionErrors().isEmpty() || !e.conversionErrors().isEmpty()).orElse(false);
    }

    private static boolean hasFailures(OperationState operation) {
        return !operation.failures().isEmpty() || hasProcessingErrors(operation.observation());
    }

    private static boolean hasUncertainty(List<ManifestFailure> failures) {
        return failures.stream().anyMatch(f -> f.code() == ManifestFailure.Code.RECOVERY_REQUIRED);
    }

    private static boolean copySuccess(State state) {
        return state == State.COMPLETED || state == State.IDENTICAL_EXISTING;
    }

    private static boolean completionEligible(State state) {
        return copySuccess(state) || state == State.SKIPPED_POLICY;
    }

    private static int flag(boolean value) { return value ? 1 : 0; }

    private static boolean isHash(String value) { return value.length() == 64 && value.matches("[0-9a-f]{64}"); }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
