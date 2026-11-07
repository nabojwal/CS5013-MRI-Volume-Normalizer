package org.cbihi.mrinormalizer.domain.error;

/** Application-visible failures specific to DICOM-to-NIfTI output production. */
public enum DicomToNiftiError {
    INVALID_OUTPUT,
    OUTPUT_ALREADY_EXISTS,
    OUTPUT_WRITE_FAILED
}
