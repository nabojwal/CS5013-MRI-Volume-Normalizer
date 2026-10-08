package org.cbihi.mrinormalizer.application.dataset.dicom;

/** Finite observed screening evidence, never a geometry, support or readiness verdict. */
public record DicomSeriesScreeningFinding(Code code, Reason reason) {
    public enum Code { AMBIGUOUS_SERIES, MULTIDIMENSIONAL_SERIES }

    public enum Reason {
        DUPLICATE_SOP_INSTANCE,
        SOP_INSTANCE_REUSED_ACROSS_SERIES,
        MIXED_MODALITY,
        MIXED_SOP_CLASS,
        MIXED_FRAME_OF_REFERENCE,
        MIXED_MATRIX,
        MULTIFRAME_MEMBER,
        MIXED_IMAGE_TYPE,
        MULTIPLE_ACQUISITION_NUMBERS,
        MULTIPLE_TEMPORAL_POSITIONS,
        MULTIPLE_ECHO_NUMBERS
    }

    public DicomSeriesScreeningFinding {
        if (code == null || reason == null) throw new IllegalArgumentException("Screening finding requires a mapped code and reason");
        Code required = switch (reason) {
            case DUPLICATE_SOP_INSTANCE, SOP_INSTANCE_REUSED_ACROSS_SERIES, MIXED_MODALITY,
                    MIXED_SOP_CLASS, MIXED_FRAME_OF_REFERENCE, MIXED_MATRIX, MULTIFRAME_MEMBER, MIXED_IMAGE_TYPE -> Code.AMBIGUOUS_SERIES;
            case MULTIPLE_ACQUISITION_NUMBERS, MULTIPLE_TEMPORAL_POSITIONS, MULTIPLE_ECHO_NUMBERS -> Code.MULTIDIMENSIONAL_SERIES;
        };
        if (code != required) throw new IllegalArgumentException("Screening finding requires a mapped code and reason");
    }
}
