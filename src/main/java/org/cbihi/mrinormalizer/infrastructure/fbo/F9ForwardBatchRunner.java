package org.cbihi.mrinormalizer.infrastructure.fbo;

import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import org.cbihi.mrinormalizer.application.fbo.FboBatchController.PreparedSeries;
import org.cbihi.mrinormalizer.application.fbo.FboBatchController.JobRunner;
import org.cbihi.mrinormalizer.application.fbo.FboResult;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;
import org.cbihi.mrinormalizer.application.request.F9HeadlessRequest;
import org.cbihi.mrinormalizer.application.result.F9HeadlessResult;
import org.cbihi.mrinormalizer.infrastructure.headless.F9WindowsComposition;

/** Sole creator of job roots; F9's one-shot engine is never changed or copied. */
public final class F9ForwardBatchRunner implements JobRunner {
    @Override public FboResult.Entry runOne(PreparedSeries prepared, UUID jobId, Path existingBatchOutputRoot) {
        int ordinal = prepared.ordinal();
        Path root = existingBatchOutputRoot.resolve("job-%03d-%s".formatted(ordinal, jobId));
        try {
            if (!Files.isDirectory(existingBatchOutputRoot, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(existingBatchOutputRoot))
                return FboResult.Entry.recovery(ordinal, jobId, FboResult.Reason.OUTPUT_UNCERTAIN);
            Path parent = existingBatchOutputRoot.toRealPath();
            if (!parent.equals(existingBatchOutputRoot.toAbsolutePath().normalize()))
                return FboResult.Entry.recovery(ordinal, jobId, FboResult.Reason.OUTPUT_UNCERTAIN);
            // Parent preflight was qualified; guard provider and device again at mutation.
            FboDirectoryPreflight.outputQualified(parent); // recheck publication provider at mutation boundary
            FboDirectoryPreflight.qualifyF9StageDepth(root, jobId); // no job root or F9 open on deep paths
            Files.createDirectory(root); // exclusive and non-replacing; never reused
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                    || !root.toRealPath().equals(root))
                return FboResult.Entry.recovery(ordinal, jobId, FboResult.Reason.OUTPUT_UNCERTAIN);
        } catch (Exception e) {
            return FboResult.Entry.recovery(ordinal, jobId, FboResult.Reason.OUTPUT_UNCERTAIN);
        }
        List<RelativePath> selected = prepared.members().stream()
                .map(f -> new RelativePath(RelativePath.Root.SOURCE, f.relativeName())).toList();
        F9HeadlessResult actual = null;
        boolean opened = false, closeFailed = false;
        org.cbihi.mrinormalizer.application.service.F9HeadlessConversionService session = null;
        try {
            session = F9WindowsComposition.open(prepared.canonicalSourceDirectory(), root, jobId);
            opened = true;
            actual = session.executeOne(new F9HeadlessRequest(jobId, prepared.selectedSeriesInstanceUid(),
                    selected, new RelativePath(RelativePath.Root.OUTPUT, "selected.nii")));
        } catch (F9WindowsComposition.InitializationFailure initialization) {
            return initialization.recoveryRequired()
                    ? FboResult.Entry.recovery(ordinal, jobId, FboResult.Reason.F9_EXECUTION_UNCERTAIN)
                    : FboResult.Entry.failed(ordinal, jobId, FboResult.Reason.F9_FAILED);
        } catch (RuntimeException uncertain) {
            // Session construction can create private stage/journal; never assume no effect.
            return FboResult.Entry.recovery(ordinal, jobId, FboResult.Reason.F9_EXECUTION_UNCERTAIN);
        } finally {
            if (opened) {
                try { session.close(); } catch (RuntimeException close) { closeFailed = true; }
            }
        }
        if (closeFailed) return FboResult.Entry.recovery(ordinal, jobId, FboResult.Reason.F9_CLOSE_UNCERTAIN);
        if (actual == null || actual.status() == F9HeadlessResult.Status.RECOVERY_REQUIRED)
            return FboResult.Entry.recovery(ordinal, jobId, FboResult.Reason.F9_RECOVERY_REQUIRED);
        if (actual.status() == F9HeadlessResult.Status.VERIFIED_PUBLISHED) {
            if (actual.acknowledgedHead().isEmpty())
                return FboResult.Entry.recovery(ordinal, jobId, FboResult.Reason.F9_RECOVERY_REQUIRED);
            return FboResult.Entry.verified(ordinal, jobId);
        }
        // The existing F9 FAILED result is a controlled known-nonpublished terminal outcome.
        if (actual.reasons().contains(F9HeadlessResult.Reason.INTERRUPTED))
            return FboResult.Entry.recovery(ordinal, jobId, FboResult.Reason.INTERRUPTED);
        return FboResult.Entry.failed(ordinal, jobId, FboResult.Reason.F9_FAILED);
    }
}
