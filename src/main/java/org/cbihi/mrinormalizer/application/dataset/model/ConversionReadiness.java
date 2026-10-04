package org.cbihi.mrinormalizer.application.dataset.model;

public enum ConversionReadiness {
    REQUIRES_VALIDATION,
    BLOCKED,
    /** Later owning-validator/reader evidence; does not bypass M6/M7 processing. */
    READY
}
