package org.cbihi.mrinormalizer.domain.model;

public enum CoordinateSystem {
    /** Native DICOM Patient Coordinate System for the BIPED/LPS convention; no conversion is applied. */
    DICOM_PATIENT_LPS,
    /** NIfTI neurological/anatomical world convention: Right, Anterior, Superior. */
    NIFTI_RAS
}
