package org.cbihi.mrinormalizer.domain.model;

public enum DetectionDiagnostic {
    NONE,
    INPUT_NOT_FOUND,
    INPUT_IS_DIRECTORY,
    INPUT_NOT_READABLE,
    EMPTY_INPUT,
    INVALID_DICOM,
    /** Inconclusive within the probe budget; not a file-size validity limit. */
    INPUT_TOO_LARGE,
    INVALID_INPUT_REFERENCE,
    INVALID_NIFTI,
    INVALID_GZIP,
    UNSUPPORTED_FORMAT,
    IO_ERROR
}
