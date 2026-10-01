package org.cbihi.mrinormalizer.application.service;

import org.cbihi.mrinormalizer.application.request.DicomSeriesRequest;
import org.cbihi.mrinormalizer.application.result.DicomProcessingResult;

public interface DicomSeriesService {

    DicomProcessingResult process(DicomSeriesRequest request);
}