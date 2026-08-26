package org.cbihi.mrinormalizer.domain.model;

public enum DetectionDiagnostic {
    NONE,
    INPUT_NOT_FOUND,
    INPUT_IS_DIRECTORY,
    INPUT_NOT_READABLE,
    EMPTY_INPUT,
    INVALID_DICOM,
    INVALID_NIFTI,
    INVALID_GZIP,
    UNSUPPORTED_FORMAT,
    IO_ERROR
}
