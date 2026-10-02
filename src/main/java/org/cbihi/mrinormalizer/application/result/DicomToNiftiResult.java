package org.cbihi.mrinormalizer.application.result;

import java.util.List;

import org.cbihi.mrinormalizer.application.provenance.ProvenanceRecord;
import org.cbihi.mrinormalizer.domain.error.DicomProcessingError;
import org.cbihi.mrinormalizer.domain.error.DicomToNiftiError;
import org.cbihi.mrinormalizer.domain.model.OutputTarget;

/** Result of the DICOM-to-NIfTI application use case. */
public record DicomToNiftiResult(
        OutputTarget output,
        List<DicomProcessingError> errors,
        List<DicomToNiftiError> conversionErrors,
        ProvenanceRecord provenance
) {
    public DicomToNiftiResult {
        errors = List.copyOf(errors);
        conversionErrors = List.copyOf(conversionErrors);
    }

    public boolean successful() {
        return errors.isEmpty() && conversionErrors.isEmpty() && output != null;
    }

    public static DicomToNiftiResult success(OutputTarget output, ProvenanceRecord provenance) {
        return new DicomToNiftiResult(output, List.of(), List.of(), provenance);
    }

    public static DicomToNiftiResult failure(
            List<DicomProcessingError> errors,
            ProvenanceRecord provenance) {
        return new DicomToNiftiResult(null, errors, List.of(), provenance);
    }

    public static DicomToNiftiResult outputFailure(
            DicomToNiftiError error,
            ProvenanceRecord provenance) {
        return new DicomToNiftiResult(null, List.of(), List.of(error), provenance);
    }
}
