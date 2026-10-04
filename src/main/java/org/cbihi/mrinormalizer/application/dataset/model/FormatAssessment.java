package org.cbihi.mrinormalizer.application.dataset.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import org.cbihi.mrinormalizer.domain.model.DetectionDiagnostic;
import org.cbihi.mrinormalizer.domain.model.DetectionOutcome;
import org.cbihi.mrinormalizer.domain.model.DetectionResult;
import org.cbihi.mrinormalizer.domain.model.ImagingFormat;

/**
 * Immutable assessment of supplied evidence, not an imaging validator.
 * A later owning validator/reader may supply READY after successful evidence;
 * this value checks coherence, not evidence authenticity or M6/M7 completion.
 */
public record FormatAssessment(
        DetectionResult initialDetection,
        ImagingFormat format,
        FormatVariant variant,
        ValidityStatus validity,
        SupportStatus support,
        ConversionReadiness readiness,
        List<AssessmentReason> reasons
) {

    public FormatAssessment {
        require(initialDetection != null && format != null && variant != null && validity != null
                && support != null && readiness != null && reasons != null,
                "Assessment components must be non-null");
        require(initialDetection.outcome() != null && initialDetection.diagnostic() != null,
                "Raw detection components must be non-null");
        validateRawDetection(initialDetection);

        var seen = EnumSet.noneOf(AssessmentReason.class);
        var ordered = new ArrayList<AssessmentReason>(reasons.size());
        for (var reason : reasons) {
            require(reason != null && seen.add(reason), "Reasons must be non-null and unique");
            ordered.add(reason);
        }
        ordered.sort(Comparator.comparing(AssessmentReason::name));
        reasons = List.copyOf(ordered);

        validateFamily(initialDetection.outcome(), format, variant);
        if (format == ImagingFormat.UNKNOWN) {
            require(support == SupportStatus.NOT_ASSESSED && validity != ValidityStatus.VALID,
                    "Unknown family cannot be valid or have assessed support");
        }
        if (initialDetection.outcome() == DetectionOutcome.CORRUPT) {
            require(validity == ValidityStatus.INVALID && readiness == ConversionReadiness.BLOCKED
                    && reasons.contains(AssessmentReason.INPUT_RECOGNIZED_AS_CORRUPT),
                    "Corrupt recognition requires invalid blocked assessment and its reason");
        }
        if (initialDetection.outcome() == DetectionOutcome.UNKNOWN && format == ImagingFormat.UNKNOWN) {
            var expectedValidity = initialDetection.diagnostic() == DetectionDiagnostic.INPUT_TOO_LARGE
                    ? ValidityStatus.INCONCLUSIVE : ValidityStatus.NOT_ASSESSED;
            var expectedReason = switch (initialDetection.diagnostic()) {
                case INPUT_TOO_LARGE -> AssessmentReason.DETECTION_INCONCLUSIVE;
                case UNSUPPORTED_FORMAT -> AssessmentReason.FORMAT_NOT_RECOGNIZED;
                default -> AssessmentReason.DETECTION_NOT_COMPLETED;
            };
            require(validity == expectedValidity && readiness == ConversionReadiness.BLOCKED
                    && reasons.contains(expectedReason),
                    "Unresolved recognition requires its diagnostic state and reason");
        }

        if (isUnsupportedVariant(variant)) {
            require(support == SupportStatus.UNSUPPORTED && readiness == ConversionReadiness.BLOCKED
                    && reasons.contains(AssessmentReason.UNSUPPORTED_FORMAT_VARIANT),
                    "Unsupported variant requires unsupported blocked assessment and its reason");
        }
        if (variant == FormatVariant.NIFTI_UNSPECIFIED) {
            require(support != SupportStatus.SUPPORTED, "Unspecified NIfTI cannot be supported");
        }
        if (validity == ValidityStatus.INVALID) {
            require(readiness == ConversionReadiness.BLOCKED
                    && (reasons.contains(AssessmentReason.INPUT_RECOGNIZED_AS_CORRUPT)
                            || reasons.contains(AssessmentReason.VALIDATION_FAILED)),
                    "Invalid assessment requires blocked readiness and its reason");
        }
        if (support == SupportStatus.UNSUPPORTED) {
            require(readiness == ConversionReadiness.BLOCKED
                    && (reasons.contains(AssessmentReason.UNSUPPORTED_FORMAT_VARIANT)
                            || reasons.contains(AssessmentReason.UNSUPPORTED_PROFILE)),
                    "Unsupported assessment requires blocked readiness and its reason");
        }

        switch (readiness) {
            case REQUIRES_VALIDATION -> require(format != ImagingFormat.UNKNOWN
                    && validity != ValidityStatus.INVALID && support != SupportStatus.UNSUPPORTED
                    && reasons.contains(AssessmentReason.VALIDATION_NOT_PERFORMED),
                    "Pending assessment requires recognized eligible state and validation reason");
            case BLOCKED -> require((format == ImagingFormat.UNKNOWN || validity == ValidityStatus.INVALID
                    || support == SupportStatus.UNSUPPORTED) && !reasons.isEmpty(),
                    "Blocked assessment requires an applicable cause");
            case READY -> require(format != ImagingFormat.UNKNOWN && validity == ValidityStatus.VALID
                    && support == SupportStatus.SUPPORTED && reasons.isEmpty(),
                    "Ready assessment requires valid supported evidence and empty reasons");
        }
        for (var reason : reasons) {
            require(reasonApplies(reason, initialDetection, format, variant, validity, support, readiness),
                    "Reason does not apply to assessment state");
        }
    }

    private static void validateRawDetection(DetectionResult detection) {
        boolean coherent = switch (detection.outcome()) {
            case DICOM, NIFTI, NIFTI_GZ -> detection.diagnostic() == DetectionDiagnostic.NONE;
            case UNKNOWN -> isDetectionUnavailable(detection.diagnostic())
                    || detection.diagnostic() == DetectionDiagnostic.INPUT_TOO_LARGE
                    || detection.diagnostic() == DetectionDiagnostic.UNSUPPORTED_FORMAT;
            case CORRUPT -> switch (detection.diagnostic()) {
                case INVALID_DICOM, INVALID_NIFTI, INVALID_GZIP -> true;
                default -> false;
            };
        };
        require(coherent, "Raw detection outcome and diagnostic are inconsistent");
    }

    private static void validateFamily(DetectionOutcome outcome, ImagingFormat format, FormatVariant variant) {
        boolean compatibleVariant = switch (format) {
            case DICOM -> variant == FormatVariant.DICOM_UNSPECIFIED;
            case NIFTI -> switch (variant) {
                case NIFTI_UNSPECIFIED, NIFTI_1_SINGLE_FILE, NIFTI_2_SINGLE_FILE, NIFTI_PAIR -> true;
                default -> false;
            };
            case UNKNOWN -> variant == FormatVariant.UNDETERMINED;
        };
        require(compatibleVariant, "Family and variant are inconsistent");
        boolean compatibleRecognition = switch (outcome) {
            case DICOM -> format == ImagingFormat.DICOM;
            case NIFTI, NIFTI_GZ -> format == ImagingFormat.NIFTI;
            case UNKNOWN, CORRUPT -> true;
        };
        require(compatibleRecognition, "Recognized family cannot be rewritten");
    }

    private static boolean reasonApplies(AssessmentReason reason, DetectionResult detection, ImagingFormat format,
            FormatVariant variant, ValidityStatus validity, SupportStatus support, ConversionReadiness readiness) {
        return switch (reason) {
            case FORMAT_NOT_RECOGNIZED -> format == ImagingFormat.UNKNOWN
                    && detection.outcome() == DetectionOutcome.UNKNOWN
                    && detection.diagnostic() == DetectionDiagnostic.UNSUPPORTED_FORMAT
                    && validity == ValidityStatus.NOT_ASSESSED && readiness == ConversionReadiness.BLOCKED;
            case DETECTION_NOT_COMPLETED -> format == ImagingFormat.UNKNOWN
                    && detection.outcome() == DetectionOutcome.UNKNOWN
                    && isDetectionUnavailable(detection.diagnostic())
                    && validity == ValidityStatus.NOT_ASSESSED && readiness == ConversionReadiness.BLOCKED;
            case DETECTION_INCONCLUSIVE -> format == ImagingFormat.UNKNOWN
                    && detection.outcome() == DetectionOutcome.UNKNOWN
                    && detection.diagnostic() == DetectionDiagnostic.INPUT_TOO_LARGE
                    && validity == ValidityStatus.INCONCLUSIVE && readiness == ConversionReadiness.BLOCKED;
            case INPUT_RECOGNIZED_AS_CORRUPT -> detection.outcome() == DetectionOutcome.CORRUPT
                    && validity == ValidityStatus.INVALID && readiness == ConversionReadiness.BLOCKED;
            case VALIDATION_NOT_PERFORMED -> format != ImagingFormat.UNKNOWN
                    && readiness == ConversionReadiness.REQUIRES_VALIDATION
                    && validity != ValidityStatus.INVALID && support != SupportStatus.UNSUPPORTED;
            case UNSUPPORTED_FORMAT_VARIANT -> isUnsupportedVariant(variant)
                    && support == SupportStatus.UNSUPPORTED && readiness == ConversionReadiness.BLOCKED;
            case UNSUPPORTED_PROFILE -> support == SupportStatus.UNSUPPORTED
                    && readiness == ConversionReadiness.BLOCKED;
            case VALIDATION_FAILED -> validity == ValidityStatus.INVALID
                    && readiness == ConversionReadiness.BLOCKED;
        };
    }

    private static boolean isDetectionUnavailable(DetectionDiagnostic diagnostic) {
        return switch (diagnostic) {
            case INPUT_NOT_FOUND, INPUT_IS_DIRECTORY, INPUT_NOT_READABLE,
                    INVALID_INPUT_REFERENCE, IO_ERROR, EMPTY_INPUT -> true;
            default -> false;
        };
    }

    private static boolean isUnsupportedVariant(FormatVariant variant) {
        return variant == FormatVariant.NIFTI_2_SINGLE_FILE || variant == FormatVariant.NIFTI_PAIR;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
