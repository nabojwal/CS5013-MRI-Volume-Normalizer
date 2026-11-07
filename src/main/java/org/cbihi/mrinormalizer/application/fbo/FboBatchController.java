package org.cbihi.mrinormalizer.application.fbo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.cbihi.mrinormalizer.application.fbo.FboResult.Entry;
import org.cbihi.mrinormalizer.application.fbo.FboResult.EntryState;
import org.cbihi.mrinormalizer.application.fbo.FboResult.Reason;
import org.cbihi.mrinormalizer.application.fbo.FboResult.Status;

/** One-use, strictly sequential coordination. Only this class creates the batch root. */
public final class FboBatchController {
    public record DirectoryObservation(Object fileKeyOrNull, FileTime creationTime, FileTime lastModifiedTime) {
        public DirectoryObservation { Objects.requireNonNull(creationTime); Objects.requireNonNull(lastModifiedTime); }
    }
    public record PreparedFile(String relativeName, long byteLength, FileTime creationTime,
                               FileTime lastModifiedTime, Object fileKeyOrNull, String sha256) {
        public PreparedFile {
            if (relativeName == null || relativeName.isBlank() || byteLength < 0
                    || creationTime == null || lastModifiedTime == null
                    || sha256 == null || !sha256.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Invalid file observation");
        }
    }
    public record PreparedSeries(int ordinal, Path canonicalSourceDirectory,
                                 String selectedSeriesInstanceUid, DirectoryObservation directory,
                                 List<PreparedFile> members) {
        public PreparedSeries {
            if (ordinal < 1 || canonicalSourceDirectory == null || selectedSeriesInstanceUid == null
                    || directory == null || members == null || members.isEmpty())
                throw new IllegalArgumentException("Invalid prepared series");
            members = List.copyOf(members);
        }
    }
    public record PreparedBatch(Path canonicalInputParent, Path canonicalOutputParent,
                                List<PreparedSeries> series) {
        public PreparedBatch {
            if (canonicalInputParent == null || canonicalOutputParent == null
                    || series == null || series.isEmpty() || series.size() > 16)
                throw new IllegalArgumentException("Invalid prepared batch");
            series = List.copyOf(series);
            for (int i = 0; i < series.size(); i++)
                if (series.get(i).ordinal() != i + 1) throw new IllegalArgumentException("Prepared order mismatch");
        }
    }
    public interface Preflight {
        PreparedBatch inspectAll(FboRequest request) throws IOException;
        void assertUnchanged(PreparedSeries prepared) throws IOException;
    }
    @FunctionalInterface public interface JobRunner {
        Entry runOne(PreparedSeries prepared, UUID jobId, Path existingBatchOutputRoot);
    }
    @FunctionalInterface public interface ReportSink {
        Path publish(FboResult finalResult, Path existingBatchOutputRoot) throws IOException;
    }
    private final Preflight preflight;
    private final JobRunner runner;
    private final ReportSink reportSink;
    private boolean used;
    public FboBatchController(Preflight preflight, JobRunner runner, ReportSink reportSink) {
        this.preflight = Objects.requireNonNull(preflight);
        this.runner = Objects.requireNonNull(runner);
        this.reportSink = Objects.requireNonNull(reportSink);
    }
    public synchronized FboResult execute(FboRequest request) {
        if (used) throw new IllegalStateException("FBO controller is single-use");
        used = true;
        UUID batchId = UUID.randomUUID();
        PreparedBatch batch;
        try { batch = Objects.requireNonNull(preflight.inspectAll(request)); }
        catch (IOException | RuntimeException invalid) {
            return new FboResult(batchId, Status.INVALID_REQUEST, unattempted(request), Optional.empty());
        }
        // All selected directories have passed inspection before this first output mutation.
        Path batchRoot = batch.canonicalOutputParent().resolve("fbo-" + batchId);
        try {
            Files.createDirectory(batchRoot); // exclusive, no-replace
            if (Files.isSymbolicLink(batchRoot) || !Files.isDirectory(batchRoot, LinkOption.NOFOLLOW_LINKS)
                    || !batchRoot.toRealPath().startsWith(batch.canonicalOutputParent()))
                throw new IOException("Uncertain batch-root identity");
        } catch (IOException | RuntimeException uncertainty) {
            return new FboResult(batchId, Status.RECOVERY_REQUIRED, unattempted(batch.series().size()), Optional.empty());
        }
        List<Entry> entries = new ArrayList<>();
        boolean halt = false, uncertain = false, interrupted = false;
        for (PreparedSeries selected : batch.series()) {
            int ordinal = selected.ordinal();
            if (halt) { entries.add(Entry.unattempted(ordinal)); continue; }
            if (Thread.currentThread().isInterrupted()) {
                interrupted = true; halt = true;
                entries.add(Entry.unattempted(ordinal)); continue;
            }
            UUID jobId = UUID.randomUUID();
            try {
                // The original snapshot is passed unchanged; this call may compare but never replace members.
                preflight.assertUnchanged(selected);
            } catch (IOException | RuntimeException changed) {
                entries.add(Entry.failed(ordinal, jobId, Reason.SOURCE_CHANGED_BEFORE_DISPATCH));
                halt = true; continue;
            }
            try {
                Entry outcome = Objects.requireNonNull(runner.runOne(selected, jobId, batchRoot));
                if (outcome.ordinal() != ordinal || !jobId.equals(outcome.jobId())
                        || outcome.state() == EntryState.NOT_ATTEMPTED)
                    throw new IllegalStateException("Runner returned inconsistent identity/status");
                entries.add(outcome);
                if (outcome.state() == EntryState.RECOVERY_REQUIRED) { uncertain = true; halt = true; }
            } catch (RuntimeException escaped) {
                entries.add(Entry.recovery(ordinal, jobId, Reason.F9_EXECUTION_UNCERTAIN));
                uncertain = true; halt = true;
            }
            if (Thread.currentThread().isInterrupted()) { interrupted = true; halt = true; }
        }
        int verified = (int)entries.stream().filter(e -> e.state() == EntryState.VERIFIED_PUBLISHED).count();
        Status status = uncertain ? Status.RECOVERY_REQUIRED : interrupted ? Status.INTERRUPTED :
                verified == batch.series().size() ? Status.SUCCESS :
                verified > 0 ? Status.PARTIAL_SUCCESS : Status.FAILED;
        // FboResult prohibits SUCCESS before report publication; use a provisional non-success state.
        FboResult provisional = new FboResult(batchId, status == Status.SUCCESS ? Status.PARTIAL_SUCCESS : status,
                entries, Optional.empty());
        boolean restore = Thread.interrupted();
        try {
            Path path = Objects.requireNonNull(reportSink.publish(provisional, batchRoot));
            if (!path.toAbsolutePath().normalize().getParent().equals(batchRoot)
                    || Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Report outside batch root");
            return new FboResult(batchId, status, entries, Optional.of(path));
        } catch (IOException | RuntimeException reportFailure) {
            return provisional.recoveryWithoutReport();
        } finally { if (restore) Thread.currentThread().interrupt(); }
    }
    private static List<Entry> unattempted(FboRequest request) {
        return unattempted(request == null ? 0 : request.selections().size());
    }
    private static List<Entry> unattempted(int n) {
        List<Entry> result = new ArrayList<>();
        for (int i = 1; i <= n; i++) result.add(Entry.unattempted(i));
        return result;
    }
}
