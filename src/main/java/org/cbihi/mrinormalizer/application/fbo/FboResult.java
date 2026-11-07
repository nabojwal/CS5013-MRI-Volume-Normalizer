package org.cbihi.mrinormalizer.application.fbo;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Sanitized, ordinal-aligned batch results: no DICOM identifiers or original paths. */
public record FboResult(UUID batchId, Status status, List<Entry> entries, Optional<Path> reportPath) {
    public enum Status { SUCCESS, PARTIAL_SUCCESS, FAILED, RECOVERY_REQUIRED, INVALID_REQUEST, INTERRUPTED }
    public enum EntryState { VERIFIED_PUBLISHED, FAILED, RECOVERY_REQUIRED, NOT_ATTEMPTED }
    public enum Reason {
        NONE, NOT_ATTEMPTED, PREFLIGHT_INVALID, SOURCE_CHANGED_BEFORE_DISPATCH,
        F9_FAILED, F9_RECOVERY_REQUIRED, F9_EXECUTION_UNCERTAIN, F9_CLOSE_UNCERTAIN,
        OUTPUT_UNCERTAIN, INTERRUPTED, INTERNAL_ERROR
    }
    public record Entry(int ordinal, UUID jobId, EntryState state, Reason reason,
                        String outputDirectory, boolean verifiedOutput) {
        public Entry {
            if (ordinal < 1 || state == null || reason == null)
                throw new IllegalArgumentException("Invalid entry");
            if (state == EntryState.NOT_ATTEMPTED) {
                if (jobId != null || outputDirectory != null || verifiedOutput
                        || reason != Reason.NOT_ATTEMPTED) throw new IllegalArgumentException("Invalid unattempted entry");
            } else {
                if (jobId == null || jobId.version() != 4) throw new IllegalArgumentException("Missing v4 job UUID");
            }
            if (state == EntryState.VERIFIED_PUBLISHED) {
                if (!verifiedOutput || reason != Reason.NONE || outputDirectory == null
                        || !outputDirectory.matches("job-[0-9]{3}-[0-9a-f-]{36}"))
                    throw new IllegalArgumentException("Unverified success prohibited");
            } else if (verifiedOutput) throw new IllegalArgumentException("Non-success cannot be verified");
            if (outputDirectory != null && !outputDirectory.matches("job-[0-9]{3}-[0-9a-f-]{36}"))
                throw new IllegalArgumentException("Non-opaque output name");
        }
        public static Entry unattempted(int ordinal) {
            return new Entry(ordinal, null, EntryState.NOT_ATTEMPTED, Reason.NOT_ATTEMPTED, null, false);
        }
        public static Entry failed(int ordinal, UUID jobId, Reason reason) {
            return new Entry(ordinal, jobId, EntryState.FAILED, reason, null, false);
        }
        public static Entry recovery(int ordinal, UUID jobId, Reason reason) {
            return new Entry(ordinal, jobId, EntryState.RECOVERY_REQUIRED, reason, null, false);
        }
        public static Entry verified(int ordinal, UUID jobId) {
            return new Entry(ordinal, jobId, EntryState.VERIFIED_PUBLISHED, Reason.NONE,
                    "job-%03d-%s".formatted(ordinal, jobId), true);
        }
    }
    public FboResult {
        Objects.requireNonNull(batchId); Objects.requireNonNull(status);
        Objects.requireNonNull(entries); Objects.requireNonNull(reportPath);
        entries = List.copyOf(entries);
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i) == null || entries.get(i).ordinal() != i + 1)
                throw new IllegalArgumentException("Non-aligned entries");
            var id = entries.get(i).jobId();
            if (id != null && (!ids.add(id) || id.equals(batchId)))
                throw new IllegalArgumentException("Duplicate job identity");
        }
        if (status == Status.SUCCESS && (entries.isEmpty() || verifiedCount(entries) != entries.size()
                || reportPath.isEmpty())) throw new IllegalArgumentException("Unreported success prohibited");
        if (status == Status.INVALID_REQUEST && reportPath.isPresent())
            throw new IllegalArgumentException("Invalid request cannot have report");
    }
    private static long verifiedCount(List<Entry> e) { return e.stream().filter(x -> x.state() == EntryState.VERIFIED_PUBLISHED).count(); }
    public int selectedCount() { return entries.size(); }
    public int verifiedCount() { return (int)verifiedCount(entries); }
    public int failedCount() { return (int)entries.stream().filter(e -> e.state() == EntryState.FAILED).count(); }
    public int recoveryRequiredCount() { return (int)entries.stream().filter(e -> e.state() == EntryState.RECOVERY_REQUIRED).count(); }
    public int notAttemptedCount() { return (int)entries.stream().filter(e -> e.state() == EntryState.NOT_ATTEMPTED).count(); }
    public int attemptedCount() { return selectedCount() - notAttemptedCount(); }
    public FboResult withReport(Path path) { return new FboResult(batchId, status, entries, Optional.of(path)); }
    public FboResult recoveryWithoutReport() { return new FboResult(batchId, Status.RECOVERY_REQUIRED, entries, Optional.empty()); }
}
