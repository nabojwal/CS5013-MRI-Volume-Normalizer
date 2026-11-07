package org.cbihi.mrinormalizer.application.m8;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadFinding;
import org.cbihi.mrinormalizer.application.provenance.manifest.ContentDigest;
import org.cbihi.mrinormalizer.domain.model.AffineMatrix4;
import org.cbihi.mrinormalizer.domain.model.IntensityTransform;
import org.cbihi.mrinormalizer.domain.model.ScalarType;

/** Immutable, source-bound complete plan: no late UID or geometry decisions. */
public record M8Plan(UUID jobId, ContentDigest sourceDigest,
                     ScalarType sourceScalar, int width, int height, int depth,
                     AffineMatrix4 sourceRasAffine, IntensityTransform sourceIntensity,
                     List<NiftiReadFinding> sourceFindings,
                     M8Metadata metadata, Instant generatedAt,
                     String studyUid, String seriesUid, String frameUid,
                     List<String> orientationDs, List<String> pixelSpacingDs,
                     Optional<String> sliceSpacingDs,
                     String slopeDs, String interceptDs,
                     int sliceStepSign, List<Slice> slices) {
    public record Slice(int sourceZ, int instanceNumber,
                        String filename, String sopUid, List<String> positionDs) {
        public Slice { Objects.requireNonNull(filename); Objects.requireNonNull(sopUid);
            positionDs = List.copyOf(positionDs); if (positionDs.size() != 3) throw new IllegalArgumentException("IPP VM"); }
    }
    public M8Plan {
        Objects.requireNonNull(jobId); Objects.requireNonNull(sourceDigest);
        Objects.requireNonNull(sourceScalar); Objects.requireNonNull(sourceRasAffine);
        Objects.requireNonNull(sourceIntensity); Objects.requireNonNull(metadata);
        Objects.requireNonNull(generatedAt); Objects.requireNonNull(studyUid);
        Objects.requireNonNull(seriesUid); Objects.requireNonNull(frameUid);
        Objects.requireNonNull(sliceSpacingDs); Objects.requireNonNull(slopeDs); Objects.requireNonNull(interceptDs);
        sourceFindings = List.copyOf(sourceFindings);
        orientationDs = List.copyOf(orientationDs); pixelSpacingDs = List.copyOf(pixelSpacingDs);
        slices = List.copyOf(slices);
        if (width < 1 || height < 1 || depth < 1 || slices.size() != depth
                || orientationDs.size() != 6 || pixelSpacingDs.size() != 2
                || (sliceStepSign != -1 && sliceStepSign != 1)) throw new IllegalArgumentException("Invalid plan");
        for (int k=0; k<depth;k++) {
            if (slices.get(k).sourceZ()!=k || slices.get(k).instanceNumber()!=k+1)
                throw new IllegalArgumentException("Noncontiguous source index mapping");
        }
    }
}
