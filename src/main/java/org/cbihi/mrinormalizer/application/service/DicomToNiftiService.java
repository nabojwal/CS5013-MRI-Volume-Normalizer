package org.cbihi.mrinormalizer.application.service;

import org.cbihi.mrinormalizer.application.request.DicomToNiftiRequest;
import org.cbihi.mrinormalizer.application.result.DicomToNiftiResult;

/** Application use case for converting one explicitly selected DICOM series to NIfTI-1. */
public interface DicomToNiftiService {

    DicomToNiftiResult convert(DicomToNiftiRequest request);
}
