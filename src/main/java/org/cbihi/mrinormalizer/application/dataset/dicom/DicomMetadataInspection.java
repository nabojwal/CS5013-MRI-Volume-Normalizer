package org.cbihi.mrinormalizer.application.dataset.dicom;

import java.util.Optional;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;

/** Exactly one sanitized metadata observation or controlled failure for a SOURCE. */
public record DicomMetadataInspection(RelativePath source, Optional<DicomDiscoveryMetadata> metadata,
        Optional<FailureCode> failure) {
    public enum FailureCode { RESOURCE_LIMIT_EXCEEDED, METADATA_READ_FAILED, INCOMPLETE_METADATA }
    public DicomMetadataInspection {
        if (source == null || source.root() != RelativePath.Root.SOURCE || metadata == null || failure == null
                || metadata.isPresent() == failure.isPresent()) {
            throw new IllegalArgumentException("Inspection requires one SOURCE outcome");
        }
    }
}
