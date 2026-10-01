package org.cbihi.mrinormalizer.application.request;

import java.util.List;

import org.cbihi.mrinormalizer.domain.model.InputSource;

public record DicomSeriesRequest(
        List<InputSource> inputs,
    String selectedSeriesInstanceUid
) {
    public DicomSeriesRequest {
        inputs = inputs == null ? null : List.copyOf(inputs);
    }
}
