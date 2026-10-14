package org.cbihi.mrinormalizer.application.dataset.copy;

import java.util.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;

/** Restricted factual result; no READY, conversion, de-identification or present-byte guarantee. */
public record CopyExecutionResult(Status status, Optional<ManifestState> journal,
        List<OperationResult> operations, List<CopyFailure> failures) {
    public enum Status { NOTHING_TO_COPY, JOURNAL_COMPLETED, FAILED, RECOVERY_REQUIRED }
    public enum Outcome { NOT_STARTED, COMPLETED, IDENTICAL_EXISTING, FAILED, RECOVERY_REQUIRED }
    public record OperationResult(String operationId, Outcome outcome, Optional<ContentDigest> outputDigest) {
        public OperationResult {
            if (operationId == null || !operationId.matches("[0-9a-f]{64}") || outcome == null || outputDigest == null
                    || outputDigest.isPresent() && !(((Optional<?>)outputDigest).get() instanceof ContentDigest)
                    || (outcome == Outcome.COMPLETED || outcome == Outcome.IDENTICAL_EXISTING) && outputDigest.isEmpty()
                    || outcome == Outcome.NOT_STARTED && outputDigest.isPresent()) invalid();
        }
    }
    public CopyExecutionResult {
        if (status == null || journal == null || journal.isPresent() && !(((Optional<?>)journal).get() instanceof ManifestState) || operations == null || failures == null
                || operations.size() > 100000 || failures.size() > 100001) invalid();
        var ids = new HashSet<String>(); var ordered = new ArrayList<OperationResult>();
        for (Object value : operations) {
            if (!(value instanceof OperationResult o) || !ids.add(o.operationId())) invalid();
            ordered.add((OperationResult)value);
        }
        ordered.sort(Comparator.comparing(OperationResult::operationId)); operations = List.copyOf(ordered);
        var seen = new HashSet<CopyFailure>(); var fs = new ArrayList<CopyFailure>();
        for (Object value : failures) { if (!(value instanceof CopyFailure f) || !seen.add(f)) invalid(); fs.add((CopyFailure)value); }
        fs.sort(Comparator.comparingInt((CopyFailure f) -> f.stage().ordinal()).thenComparingInt(f -> f.code().ordinal())
                .thenComparing(f -> f.operationId().orElse(""))
                .thenComparing(f -> f.source().map(RelativePath::path).orElse(""), CopyExecutionResult::utf8)); failures = List.copyOf(fs);
        if (status == Status.NOTHING_TO_COPY && (!operations.isEmpty() || !failures.isEmpty()
                || journal.map(s -> !s.plan().operations().isEmpty() || s.state() != ManifestState.JobState.PLANNED).orElse(false))) invalid();
        if (status == Status.JOURNAL_COMPLETED) {
            if (operations.isEmpty() || !failures.isEmpty() || journal.isEmpty() || journal.get().state() != ManifestState.JobState.COMPLETED
                    || journal.get().operations().size() != operations.size()) invalid();
            var state = journal.get();
            for (var o : operations) {
                if (o.outcome() != Outcome.COMPLETED && o.outcome() != Outcome.IDENTICAL_EXISTING) invalid();
                var acknowledged = state.operations().stream().filter(a -> a.operationId().equals(o.operationId())).findFirst().orElseThrow();
                var observed = acknowledged.observation();
                if (!observed.outputDigest().equals(o.outputDigest()) || !acknowledged.failures().isEmpty()
                        || observed.state() != (o.outcome() == Outcome.COMPLETED ? CheckpointRecord.State.COMPLETED : CheckpointRecord.State.IDENTICAL_EXISTING)) invalid();
            }
        }
        if (status == Status.FAILED && (failures.isEmpty() || operations.stream().anyMatch(o -> o.outcome() == Outcome.RECOVERY_REQUIRED)
                || journal.filter(s -> s.state() == ManifestState.JobState.RECOVERY_REQUIRED).isPresent())) invalid();
    }
    private static int utf8(String a, String b) { return Arrays.compareUnsigned(a.getBytes(java.nio.charset.StandardCharsets.UTF_8), b.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    private static void invalid() { throw new IllegalArgumentException("Invalid COPY execution result"); }
}
