package org.cbihi.mrinormalizer.application.result;

import java.util.List;

import org.cbihi.mrinormalizer.domain.error.DicomProcessingError;
import org.cbihi.mrinormalizer.domain.model.NativeVolume;
import org.cbihi.mrinormalizer.application.provenance.ProvenanceRecord;

public record DicomProcessingResult(
        NativeVolume volume,
        List<DicomProcessingError> errors,
        ProvenanceRecord provenance
) {
    public DicomProcessingResult {
        errors = List.copyOf(errors);
    }

    public boolean successful() {
        return errors.isEmpty() && volume != null;
    }

    public static DicomProcessingResult success(NativeVolume volume, ProvenanceRecord provenance) {
        return new DicomProcessingResult(volume, List.of(), provenance);
    }

    public static DicomProcessingResult failure(DicomProcessingError error, ProvenanceRecord provenance) {
        return new DicomProcessingResult(null, List.of(error), provenance);
    }
}
