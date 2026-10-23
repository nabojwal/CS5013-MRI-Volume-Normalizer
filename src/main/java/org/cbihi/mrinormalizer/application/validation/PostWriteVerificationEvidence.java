package org.cbihi.mrinormalizer.application.validation;
import java.util.UUID;
import org.cbihi.mrinormalizer.application.provenance.manifest.ContentDigest;
/** Factual comparison, never a publication credential. */
public record PostWriteVerificationEvidence(UUID sessionId, ContentDigest stagedDigest,
        long comparedVoxelCount, int comparedCornerCount, double maxWorldDeviationMm,
        boolean weakSourceIdentityObserved, String policyId) {
    public PostWriteVerificationEvidence {
        if (sessionId == null || stagedDigest == null || comparedVoxelCount <= 0
                || comparedCornerCount != 8 || !Double.isFinite(maxWorldDeviationMm)
                || maxWorldDeviationMm < 0.0d || !"F8-NIFTI1-F32-1".equals(policyId))
            throw new IllegalArgumentException("Invalid verification evidence");
    }
}
