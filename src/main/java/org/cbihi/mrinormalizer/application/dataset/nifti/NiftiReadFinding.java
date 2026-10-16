package org.cbihi.mrinormalizer.application.dataset.nifti;

/** Observations retained even if a later activity invalidates the volume. */
public enum NiftiReadFinding {
    SFORM_SELECTED, QFORM_SELECTED, INVALID_SFORM_IGNORED, INVALID_QFORM_IGNORED,
    QFAC_ZERO_AS_POSITIVE, QUATERNION_ROUNDING_NORMALIZED, EXTENSIONS_NOT_INTERPRETED,
    WEAK_SOURCE_IDENTITY
}
