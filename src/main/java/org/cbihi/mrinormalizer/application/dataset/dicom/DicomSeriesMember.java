package org.cbihi.mrinormalizer.application.dataset.dicom;

import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;

/**
 * One preserved successful F3 SOURCE observation. Neither its source nor metadata
 * proves current containment, unchanged bytes or reconstruction compatibility.
 * The default record representation remains restricted internal state.
 */
public record DicomSeriesMember(RelativePath source, DicomDiscoveryMetadata metadata) {
    public DicomSeriesMember {
        if (source == null || source.root() != RelativePath.Root.SOURCE || metadata == null) {
            throw new IllegalArgumentException("Candidate member requires a SOURCE metadata observation");
        }
    }
}
