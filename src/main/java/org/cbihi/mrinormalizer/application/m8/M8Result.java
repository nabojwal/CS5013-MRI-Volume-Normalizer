package org.cbihi.mrinormalizer.application.m8;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadFinding;
import org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadFailure;
import org.cbihi.mrinormalizer.application.port.out.M8Workspace;
import org.cbihi.mrinormalizer.application.provenance.manifest.ContentDigest;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;

public record M8Result(UUID jobId, Status status, Publication publication,
                       Optional<RelativePath> outputDirectory,
                       Optional<ContentDigest> sourceDigest,
                       Optional<M8Workspace.Receipt> receipt,
                       List<M8Workspace.SliceEvidence> evidence,
                       List<NiftiReadFinding> sourceFindings,
                       List<NiftiReadFailure> sourceFailures,
                       List<M8Fault> faults) {
    public enum Status { SUCCESS, REJECTED, FAILED, RECOVERY_REQUIRED }
    public enum Publication { NONE, PARTIAL_OR_UNKNOWN, COMPLETE_BYTES_VERIFIED }
    public M8Result {
        Objects.requireNonNull(jobId); Objects.requireNonNull(status); Objects.requireNonNull(publication);
        Objects.requireNonNull(outputDirectory); Objects.requireNonNull(sourceDigest); Objects.requireNonNull(receipt);
        evidence=List.copyOf(evidence); sourceFindings=List.copyOf(sourceFindings);
        sourceFailures=List.copyOf(sourceFailures); faults=List.copyOf(faults);
        if (status==Status.SUCCESS && (publication!=Publication.COMPLETE_BYTES_VERIFIED
                || outputDirectory.isEmpty() || receipt.isEmpty() || !faults.isEmpty() || !sourceFailures.isEmpty()))
            throw new IllegalArgumentException("Unverified success prohibited");
        if (publication==Publication.COMPLETE_BYTES_VERIFIED && status==Status.REJECTED)
            throw new IllegalArgumentException("Rejected job cannot publish");
    }
}
