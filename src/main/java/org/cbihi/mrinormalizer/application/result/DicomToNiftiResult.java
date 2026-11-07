package org.cbihi.mrinormalizer.application.result;

import java.util.List;
import java.util.Objects;

import org.cbihi.mrinormalizer.application.provenance.ProvenanceRecord;
import org.cbihi.mrinormalizer.application.validation.ConversionValidationReport;
import org.cbihi.mrinormalizer.domain.error.DicomProcessingError;
import org.cbihi.mrinormalizer.domain.error.DicomToNiftiError;
import org.cbihi.mrinormalizer.domain.model.OutputTarget;

/** Result of the DICOM-to-NIfTI application use case. */
public record DicomToNiftiResult(
        OutputTarget output,
        List<DicomProcessingError> errors,
        List<DicomToNiftiError> conversionErrors,
        ProvenanceRecord provenance,
        ConversionValidationReport validationReport
) {
    public DicomToNiftiResult {
        errors = List.copyOf(errors);
        conversionErrors = List.copyOf(conversionErrors);

        if (output != null) {
            if (!errors.isEmpty() || !conversionErrors.isEmpty()) {
                throw new IllegalArgumentException(
                        "successful conversion output cannot carry errors");
            }

            Objects.requireNonNull(
                    validationReport,
                    "successful conversion requires validationReport");

            if (!output.equals(validationReport.output())) {
                throw new IllegalArgumentException(
                        "conversion output and validation-report output must match");
            }
        } else if (validationReport != null) {
            throw new IllegalArgumentException(
                    "failed conversion cannot carry validationReport");
        }
    }

    public boolean successful() {
        return errors.isEmpty()
                && conversionErrors.isEmpty()
                && output != null
                && validationReport != null;
    }

    public static DicomToNiftiResult success(
            OutputTarget output,
            ProvenanceRecord provenance,
            ConversionValidationReport validationReport) {
        return new DicomToNiftiResult(
                Objects.requireNonNull(output, "output"),
                List.of(),
                List.of(),
                provenance,
                Objects.requireNonNull(validationReport, "validationReport"));
    }

    public static DicomToNiftiResult failure(
            List<DicomProcessingError> errors,
            ProvenanceRecord provenance) {
        return new DicomToNiftiResult(
                null,
                errors,
                List.of(),
                provenance,
                null);
    }

    public static DicomToNiftiResult outputFailure(
            DicomToNiftiError error,
            ProvenanceRecord provenance) {
        return new DicomToNiftiResult(
                null,
                List.of(),
                List.of(error),
                provenance,
                null);
    }
}
