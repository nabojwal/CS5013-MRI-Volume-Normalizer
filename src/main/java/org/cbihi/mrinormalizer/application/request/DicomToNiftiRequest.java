package org.cbihi.mrinormalizer.application.request;

import java.util.List;

import org.cbihi.mrinormalizer.domain.model.InputSource;
import org.cbihi.mrinormalizer.domain.model.OutputTarget;

/** Explicit DICOM-series to NIfTI conversion request. */
public record DicomToNiftiRequest(
        List<InputSource> inputs,
        String selectedSeriesInstanceUid,
        OutputTarget output
) {
    public DicomToNiftiRequest {
        inputs = inputs == null ? null : List.copyOf(inputs);
    }
}
