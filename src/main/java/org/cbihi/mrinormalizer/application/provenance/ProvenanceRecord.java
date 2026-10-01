package org.cbihi.mrinormalizer.application.provenance;

import java.time.Instant;
import java.util.List;

import org.cbihi.mrinormalizer.domain.error.DicomProcessingError;

public record ProvenanceRecord(
        String inputFingerprint,
        String softwareVersion,
        String dcm4cheVersion,
        Instant completedAt,
        boolean successful,
        int inputCount,
        int acceptedSlices,
        String geometry,
        String pixels,
        List<DicomProcessingError> errors
) {
    public ProvenanceRecord {
        errors = List.copyOf(errors);
    }
}
