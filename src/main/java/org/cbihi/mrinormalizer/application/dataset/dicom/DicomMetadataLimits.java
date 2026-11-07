package org.cbihi.mrinormalizer.application.dataset.dicom;

/** Explicit per-source byte and DICOM nesting budgets; no product defaults. */
public record DicomMetadataLimits(int maxMetadataBytes, int maxNestingDepth) {
    public DicomMetadataLimits {
        if (maxMetadataBytes <= 0 || maxNestingDepth <= 0) {
            throw new IllegalArgumentException("Metadata limits must be positive");
        }
    }
}
