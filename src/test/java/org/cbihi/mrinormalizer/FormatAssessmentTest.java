package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.cbihi.mrinormalizer.application.dataset.model.AssessmentReason;
import org.cbihi.mrinormalizer.application.dataset.model.ConversionReadiness;
import org.cbihi.mrinormalizer.application.dataset.model.FormatAssessment;
import org.cbihi.mrinormalizer.application.dataset.model.FormatVariant;
import org.cbihi.mrinormalizer.application.dataset.model.SupportStatus;
import org.cbihi.mrinormalizer.application.dataset.model.ValidityStatus;
import org.cbihi.mrinormalizer.domain.model.DetectionDiagnostic;
import org.cbihi.mrinormalizer.domain.model.DetectionOutcome;
import org.cbihi.mrinormalizer.domain.model.DetectionResult;
import org.cbihi.mrinormalizer.domain.model.ImagingFormat;
import org.junit.jupiter.api.Test;

/** S1 constructor contracts only; no factory or real validator is exercised. */
class FormatAssessmentTest {

    private static final List<UnknownCase> UNKNOWN_CASES = List.of(
            new UnknownCase(DetectionDiagnostic.INPUT_NOT_FOUND, ValidityStatus.NOT_ASSESSED,
                    AssessmentReason.DETECTION_NOT_COMPLETED),
            new UnknownCase(DetectionDiagnostic.INPUT_IS_DIRECTORY, ValidityStatus.NOT_ASSESSED,
                    AssessmentReason.DETECTION_NOT_COMPLETED),
            new UnknownCase(DetectionDiagnostic.INPUT_NOT_READABLE, ValidityStatus.NOT_ASSESSED,
                    AssessmentReason.DETECTION_NOT_COMPLETED),
            new UnknownCase(DetectionDiagnostic.INVALID_INPUT_REFERENCE, ValidityStatus.NOT_ASSESSED,
                    AssessmentReason.DETECTION_NOT_COMPLETED),
            new UnknownCase(DetectionDiagnostic.IO_ERROR, ValidityStatus.NOT_ASSESSED,
                    AssessmentReason.DETECTION_NOT_COMPLETED),
            new UnknownCase(DetectionDiagnostic.EMPTY_INPUT, ValidityStatus.NOT_ASSESSED,
                    AssessmentReason.DETECTION_NOT_COMPLETED),
            new UnknownCase(DetectionDiagnostic.INPUT_TOO_LARGE, ValidityStatus.INCONCLUSIVE,
                    AssessmentReason.DETECTION_INCONCLUSIVE),
            new UnknownCase(DetectionDiagnostic.UNSUPPORTED_FORMAT, ValidityStatus.NOT_ASSESSED,
                    AssessmentReason.FORMAT_NOT_RECOGNIZED));

    // U03-S1
    @Test
    void rejectsMissingComponentsAndRawEnums() {
        var raw = DetectionResult.identified(DetectionOutcome.NIFTI);
        var reasons = List.of(AssessmentReason.VALIDATION_NOT_PERFORMED);
        reject(null, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE,
                ValidityStatus.NOT_ASSESSED, SupportStatus.NOT_ASSESSED,
                ConversionReadiness.REQUIRES_VALIDATION, reasons);
        reject(raw, null, FormatVariant.NIFTI_1_SINGLE_FILE, ValidityStatus.NOT_ASSESSED,
                SupportStatus.NOT_ASSESSED, ConversionReadiness.REQUIRES_VALIDATION, reasons);
        reject(raw, ImagingFormat.NIFTI, null, ValidityStatus.NOT_ASSESSED,
                SupportStatus.NOT_ASSESSED, ConversionReadiness.REQUIRES_VALIDATION, reasons);
        reject(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE, null,
                SupportStatus.NOT_ASSESSED, ConversionReadiness.REQUIRES_VALIDATION, reasons);
        reject(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE,
                ValidityStatus.NOT_ASSESSED, null, ConversionReadiness.REQUIRES_VALIDATION, reasons);
        reject(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE,
                ValidityStatus.NOT_ASSESSED, SupportStatus.NOT_ASSESSED, null, reasons);
        reject(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE,
                ValidityStatus.NOT_ASSESSED, SupportStatus.NOT_ASSESSED,
                ConversionReadiness.REQUIRES_VALIDATION, null);
        reject(new DetectionResult(null, DetectionDiagnostic.NONE, false), ImagingFormat.NIFTI,
                FormatVariant.NIFTI_1_SINGLE_FILE, ValidityStatus.NOT_ASSESSED,
                SupportStatus.NOT_ASSESSED, ConversionReadiness.REQUIRES_VALIDATION, reasons);
        reject(new DetectionResult(DetectionOutcome.NIFTI, null, false), ImagingFormat.NIFTI,
                FormatVariant.NIFTI_1_SINGLE_FILE, ValidityStatus.NOT_ASSESSED,
                SupportStatus.NOT_ASSESSED, ConversionReadiness.REQUIRES_VALIDATION, reasons);
    }

    // U03-S1
    @Test
    void rejectsNullAndDuplicateReasonEntries() {
        var raw = DetectionResult.identified(DetectionOutcome.NIFTI);
        reject(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_UNSPECIFIED,
                ValidityStatus.NOT_ASSESSED, SupportStatus.NOT_ASSESSED,
                ConversionReadiness.REQUIRES_VALIDATION,
                Arrays.asList(AssessmentReason.VALIDATION_NOT_PERFORMED, null));
        var error = reject(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_UNSPECIFIED,
                ValidityStatus.NOT_ASSESSED, SupportStatus.NOT_ASSESSED,
                ConversionReadiness.REQUIRES_VALIDATION,
                List.of(AssessmentReason.VALIDATION_NOT_PERFORMED,
                        AssessmentReason.VALIDATION_NOT_PERFORMED));
        assertEquals("Reasons must be non-null and unique", error.getMessage());
    }

    // U03-S1: malformed evidence cannot be hidden by supplied successful refinement.
    @Test
    void rejectsMalformedUnknownEvidenceBeforeRefinement() {
        for (var diagnostic : List.of(DetectionDiagnostic.NONE, DetectionDiagnostic.INVALID_DICOM,
                DetectionDiagnostic.INVALID_NIFTI, DetectionDiagnostic.INVALID_GZIP)) {
            for (boolean mismatch : new boolean[] {false, true}) {
                var raw = new DetectionResult(DetectionOutcome.UNKNOWN, diagnostic, mismatch);
                var error = reject(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE,
                        ValidityStatus.VALID, SupportStatus.SUPPORTED,
                        ConversionReadiness.READY, List.of());
                assertEquals("Raw detection outcome and diagnostic are inconsistent", error.getMessage());
                reject(raw, ImagingFormat.UNKNOWN, FormatVariant.UNDETERMINED,
                        ValidityStatus.NOT_ASSESSED, SupportStatus.NOT_ASSESSED,
                        ConversionReadiness.BLOCKED,
                        List.of(AssessmentReason.DETECTION_NOT_COMPLETED));
            }
        }
    }

    // U03-S1
    @Test
    void positiveRawOutcomesRequireNoneDiagnostic() {
        for (var outcome : List.of(DetectionOutcome.DICOM, DetectionOutcome.NIFTI,
                DetectionOutcome.NIFTI_GZ)) {
            for (var diagnostic : DetectionDiagnostic.values()) {
                if (diagnostic != DetectionDiagnostic.NONE) {
                    var raw = new DetectionResult(outcome, diagnostic, false);
                    reject(raw, outcome == DetectionOutcome.DICOM ? ImagingFormat.DICOM : ImagingFormat.NIFTI,
                            outcome == DetectionOutcome.DICOM
                                    ? FormatVariant.DICOM_UNSPECIFIED : FormatVariant.NIFTI_UNSPECIFIED,
                            ValidityStatus.NOT_ASSESSED, SupportStatus.NOT_ASSESSED,
                            ConversionReadiness.REQUIRES_VALIDATION,
                            List.of(AssessmentReason.VALIDATION_NOT_PERFORMED));
                }
            }
        }
    }

    // U03-S1
    @Test
    void corruptRawOutcomeRequiresCorruptDiagnostic() {
        for (var diagnostic : DetectionDiagnostic.values()) {
            if (!List.of(DetectionDiagnostic.INVALID_DICOM, DetectionDiagnostic.INVALID_NIFTI,
                    DetectionDiagnostic.INVALID_GZIP).contains(diagnostic)) {
                reject(DetectionResult.corrupt(diagnostic), ImagingFormat.UNKNOWN,
                        FormatVariant.UNDETERMINED, ValidityStatus.INVALID, SupportStatus.NOT_ASSESSED,
                        ConversionReadiness.BLOCKED,
                        List.of(AssessmentReason.INPUT_RECOGNIZED_AS_CORRUPT));
            }
        }
    }

    // U04: exactly one pending readiness member, with explicit validity/support states.
    @Test
    void approvedEnumMemberSetsAreExact() {
        assertArrayEquals(new ConversionReadiness[] {ConversionReadiness.REQUIRES_VALIDATION,
                ConversionReadiness.BLOCKED, ConversionReadiness.READY}, ConversionReadiness.values());
        assertArrayEquals(new ValidityStatus[] {ValidityStatus.NOT_ASSESSED, ValidityStatus.INCONCLUSIVE,
                ValidityStatus.VALID, ValidityStatus.INVALID}, ValidityStatus.values());
        assertArrayEquals(new SupportStatus[] {SupportStatus.NOT_ASSESSED, SupportStatus.SUPPORTED,
                SupportStatus.UNSUPPORTED}, SupportStatus.values());
        assertArrayEquals(new FormatVariant[] {FormatVariant.UNDETERMINED, FormatVariant.DICOM_UNSPECIFIED,
                FormatVariant.NIFTI_UNSPECIFIED, FormatVariant.NIFTI_1_SINGLE_FILE,
                FormatVariant.NIFTI_2_SINGLE_FILE, FormatVariant.NIFTI_PAIR}, FormatVariant.values());
        assertArrayEquals(new AssessmentReason[] {AssessmentReason.FORMAT_NOT_RECOGNIZED,
                AssessmentReason.DETECTION_NOT_COMPLETED, AssessmentReason.DETECTION_INCONCLUSIVE,
                AssessmentReason.INPUT_RECOGNIZED_AS_CORRUPT, AssessmentReason.VALIDATION_NOT_PERFORMED,
                AssessmentReason.UNSUPPORTED_FORMAT_VARIANT, AssessmentReason.UNSUPPORTED_PROFILE,
                AssessmentReason.VALIDATION_FAILED}, AssessmentReason.values());
    }

    // U04
    @Test
    void rejectsIncompatibleFamiliesAndVariants() {
        var raw = DetectionResult.unknown(DetectionDiagnostic.UNSUPPORTED_FORMAT);
        for (var variant : FormatVariant.values()) {
            if (variant != FormatVariant.DICOM_UNSPECIFIED) {
                reject(raw, ImagingFormat.DICOM, variant, ValidityStatus.NOT_ASSESSED,
                        SupportStatus.NOT_ASSESSED, ConversionReadiness.REQUIRES_VALIDATION,
                        List.of(AssessmentReason.VALIDATION_NOT_PERFORMED));
            }
            if (variant == FormatVariant.UNDETERMINED || variant == FormatVariant.DICOM_UNSPECIFIED) {
                reject(raw, ImagingFormat.NIFTI, variant, ValidityStatus.NOT_ASSESSED,
                        SupportStatus.NOT_ASSESSED, ConversionReadiness.REQUIRES_VALIDATION,
                        List.of(AssessmentReason.VALIDATION_NOT_PERFORMED));
            }
            if (variant != FormatVariant.UNDETERMINED) {
                reject(raw, ImagingFormat.UNKNOWN, variant, ValidityStatus.NOT_ASSESSED,
                        SupportStatus.NOT_ASSESSED, ConversionReadiness.BLOCKED,
                        List.of(AssessmentReason.FORMAT_NOT_RECOGNIZED));
            }
        }
    }

    // U04
    @Test
    void rawPositiveFamilyCannotBeRewritten() {
        reject(DetectionResult.identified(DetectionOutcome.DICOM), ImagingFormat.NIFTI,
                FormatVariant.NIFTI_1_SINGLE_FILE, ValidityStatus.VALID, SupportStatus.SUPPORTED,
                ConversionReadiness.READY, List.of());
        reject(DetectionResult.identified(DetectionOutcome.DICOM), ImagingFormat.UNKNOWN,
                FormatVariant.UNDETERMINED, ValidityStatus.NOT_ASSESSED, SupportStatus.NOT_ASSESSED,
                ConversionReadiness.BLOCKED, List.of(AssessmentReason.FORMAT_NOT_RECOGNIZED));
        for (var outcome : List.of(DetectionOutcome.NIFTI, DetectionOutcome.NIFTI_GZ)) {
            reject(DetectionResult.identified(outcome), ImagingFormat.DICOM,
                    FormatVariant.DICOM_UNSPECIFIED, ValidityStatus.VALID, SupportStatus.SUPPORTED,
                    ConversionReadiness.READY, List.of());
            reject(DetectionResult.identified(outcome), ImagingFormat.UNKNOWN,
                    FormatVariant.UNDETERMINED, ValidityStatus.NOT_ASSESSED, SupportStatus.NOT_ASSESSED,
                    ConversionReadiness.BLOCKED, List.of(AssessmentReason.FORMAT_NOT_RECOGNIZED));
        }
    }

    // U04/U07: constructor table, not fromDetection().
    @Test
    void unresolvedUnknownEvidenceRequiresExactValidityAndReason() {
        for (var entry : UNKNOWN_CASES) {
            var raw = DetectionResult.unknown(entry.diagnostic());
            var value = assessment(raw, ImagingFormat.UNKNOWN, FormatVariant.UNDETERMINED,
                    entry.validity(), SupportStatus.NOT_ASSESSED, ConversionReadiness.BLOCKED,
                    entry.reason());
            assertSame(raw, value.initialDetection());
            assertEquals(entry.validity(), value.validity());
            for (var validity : ValidityStatus.values()) {
                if (validity != entry.validity()) {
                    reject(raw, ImagingFormat.UNKNOWN, FormatVariant.UNDETERMINED,
                            validity, SupportStatus.NOT_ASSESSED, ConversionReadiness.BLOCKED,
                            List.of(entry.reason()));
                }
            }
            for (var support : List.of(SupportStatus.SUPPORTED, SupportStatus.UNSUPPORTED)) {
                reject(raw, ImagingFormat.UNKNOWN, FormatVariant.UNDETERMINED, entry.validity(),
                        support, ConversionReadiness.BLOCKED, List.of(entry.reason()));
            }
        }
    }

    // U04/U10
    @Test
    void recognizedPendingStatesRequireValidationReason() {
        var raw = DetectionResult.identified(DetectionOutcome.NIFTI);
        for (var validity : List.of(ValidityStatus.NOT_ASSESSED, ValidityStatus.INCONCLUSIVE,
                ValidityStatus.VALID)) {
            for (var support : List.of(SupportStatus.NOT_ASSESSED, SupportStatus.SUPPORTED)) {
                var value = assessment(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE,
                        validity, support, ConversionReadiness.REQUIRES_VALIDATION,
                        AssessmentReason.VALIDATION_NOT_PERFORMED);
                assertEquals(ConversionReadiness.REQUIRES_VALIDATION, value.readiness());
                reject(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE,
                        validity, support, ConversionReadiness.REQUIRES_VALIDATION, List.of());
            }
        }
    }

    // U04
    @Test
    void invalidUnsupportedAndUnknownStatesMustBeBlocked() {
        var raw = DetectionResult.identified(DetectionOutcome.NIFTI);
        for (var readiness : List.of(ConversionReadiness.REQUIRES_VALIDATION, ConversionReadiness.READY)) {
            reject(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE, ValidityStatus.INVALID,
                    SupportStatus.SUPPORTED, readiness, List.of(AssessmentReason.VALIDATION_FAILED));
            reject(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE, ValidityStatus.VALID,
                    SupportStatus.UNSUPPORTED, readiness, List.of(AssessmentReason.UNSUPPORTED_PROFILE));
            reject(DetectionResult.unknown(DetectionDiagnostic.UNSUPPORTED_FORMAT), ImagingFormat.UNKNOWN,
                    FormatVariant.UNDETERMINED, ValidityStatus.NOT_ASSESSED, SupportStatus.NOT_ASSESSED,
                    readiness, List.of(AssessmentReason.FORMAT_NOT_RECOGNIZED));
        }
    }

    // U04/U10
    @Test
    void blockedStateNeedsAnApplicableCause() {
        var raw = DetectionResult.identified(DetectionOutcome.NIFTI);
        reject(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE, ValidityStatus.VALID,
                SupportStatus.SUPPORTED, ConversionReadiness.BLOCKED, List.of());
        reject(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE, ValidityStatus.VALID,
                SupportStatus.SUPPORTED, ConversionReadiness.BLOCKED,
                List.of(AssessmentReason.VALIDATION_NOT_PERFORMED));
    }

    // U05
    @Test
    void unsupportedVariantsRemainBlockedEvenWhenValid() {
        var raw = DetectionResult.identified(DetectionOutcome.NIFTI);
        for (var variant : List.of(FormatVariant.NIFTI_2_SINGLE_FILE, FormatVariant.NIFTI_PAIR)) {
            var value = assessment(raw, ImagingFormat.NIFTI, variant, ValidityStatus.VALID,
                    SupportStatus.UNSUPPORTED, ConversionReadiness.BLOCKED,
                    AssessmentReason.UNSUPPORTED_FORMAT_VARIANT);
            assertEquals(ValidityStatus.VALID, value.validity());
            assertEquals(SupportStatus.UNSUPPORTED, value.support());
            for (var readiness : List.of(ConversionReadiness.READY, ConversionReadiness.REQUIRES_VALIDATION)) {
                reject(raw, ImagingFormat.NIFTI, variant, ValidityStatus.VALID,
                        SupportStatus.UNSUPPORTED, readiness,
                        List.of(AssessmentReason.UNSUPPORTED_FORMAT_VARIANT));
            }
            reject(raw, ImagingFormat.NIFTI, variant, ValidityStatus.VALID, SupportStatus.SUPPORTED,
                    ConversionReadiness.READY, List.of());
            reject(raw, ImagingFormat.NIFTI, variant, ValidityStatus.VALID, SupportStatus.UNSUPPORTED,
                    ConversionReadiness.BLOCKED, List.of(AssessmentReason.UNSUPPORTED_PROFILE));
        }
    }

    // U05
    @Test
    void unspecifiedNiftiCannotBeSupportedOrReady() {
        var raw = DetectionResult.identified(DetectionOutcome.NIFTI_GZ);
        assessment(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_UNSPECIFIED,
                ValidityStatus.NOT_ASSESSED, SupportStatus.NOT_ASSESSED,
                ConversionReadiness.REQUIRES_VALIDATION, AssessmentReason.VALIDATION_NOT_PERFORMED);
        reject(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_UNSPECIFIED, ValidityStatus.VALID,
                SupportStatus.SUPPORTED, ConversionReadiness.REQUIRES_VALIDATION,
                List.of(AssessmentReason.VALIDATION_NOT_PERFORMED));
        reject(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_UNSPECIFIED, ValidityStatus.VALID,
                SupportStatus.SUPPORTED, ConversionReadiness.READY, List.of());
    }

    // U06
    @Test
    void defensivelyCopiesReasonsAndOrdersThemLexically() {
        var raw = DetectionResult.corrupt(DetectionDiagnostic.INVALID_NIFTI);
        var input = new ArrayList<>(List.of(AssessmentReason.VALIDATION_FAILED,
                AssessmentReason.INPUT_RECOGNIZED_AS_CORRUPT));
        var value = new FormatAssessment(raw, ImagingFormat.UNKNOWN, FormatVariant.UNDETERMINED,
                ValidityStatus.INVALID, SupportStatus.NOT_ASSESSED, ConversionReadiness.BLOCKED, input);
        input.clear();
        assertEquals(List.of(AssessmentReason.INPUT_RECOGNIZED_AS_CORRUPT,
                AssessmentReason.VALIDATION_FAILED), value.reasons());
        assertThrowsExactly(UnsupportedOperationException.class,
                () -> value.reasons().add(AssessmentReason.UNSUPPORTED_PROFILE));
        assertThrowsExactly(UnsupportedOperationException.class,
                () -> value.reasons().set(0, AssessmentReason.VALIDATION_FAILED));
        var permuted = assessment(raw, ImagingFormat.UNKNOWN, FormatVariant.UNDETERMINED,
                ValidityStatus.INVALID, SupportStatus.NOT_ASSESSED, ConversionReadiness.BLOCKED,
                AssessmentReason.INPUT_RECOGNIZED_AS_CORRUPT, AssessmentReason.VALIDATION_FAILED);
        assertEquals(value, permuted);
        assertEquals(value.hashCode(), permuted.hashCode());
    }

    // U07: successful fixtures are supplied evidence, not proof of a real validator.
    @Test
    void laterRefinementPreservesRawUnknownEvidenceAndExtensionHint() {
        for (var entry : UNKNOWN_CASES) {
            for (boolean mismatch : new boolean[] {false, true}) {
                var raw = new DetectionResult(DetectionOutcome.UNKNOWN, entry.diagnostic(), mismatch);
                for (var format : List.of(ImagingFormat.DICOM, ImagingFormat.NIFTI)) {
                    var value = assessment(raw, format, format == ImagingFormat.DICOM
                                    ? FormatVariant.DICOM_UNSPECIFIED : FormatVariant.NIFTI_1_SINGLE_FILE,
                            ValidityStatus.VALID, SupportStatus.SUPPORTED, ConversionReadiness.READY);
                    assertSame(raw, value.initialDetection());
                    assertEquals(entry.diagnostic(), value.initialDetection().diagnostic());
                    assertEquals(mismatch, value.initialDetection().extensionMismatch());
                }
            }
        }
    }

    // U07/U10
    @Test
    void refinedKnownFamilyMustDropUnresolvedDetectionReasons() {
        for (var entry : UNKNOWN_CASES) {
            reject(DetectionResult.unknown(entry.diagnostic()), ImagingFormat.NIFTI,
                    FormatVariant.NIFTI_1_SINGLE_FILE, ValidityStatus.NOT_ASSESSED,
                    SupportStatus.NOT_ASSESSED, ConversionReadiness.REQUIRES_VALIDATION,
                    List.of(AssessmentReason.VALIDATION_NOT_PERFORMED, entry.reason()));
        }
    }

    // U07
    @Test
    void corruptRawEvidenceCannotBeRehabilitated() {
        for (var diagnostic : List.of(DetectionDiagnostic.INVALID_DICOM, DetectionDiagnostic.INVALID_NIFTI,
                DetectionDiagnostic.INVALID_GZIP)) {
            var raw = DetectionResult.corrupt(diagnostic);
            assessment(raw, ImagingFormat.UNKNOWN, FormatVariant.UNDETERMINED,
                    ValidityStatus.INVALID, SupportStatus.NOT_ASSESSED, ConversionReadiness.BLOCKED,
                    AssessmentReason.INPUT_RECOGNIZED_AS_CORRUPT);
            reject(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE, ValidityStatus.VALID,
                    SupportStatus.SUPPORTED, ConversionReadiness.READY, List.of());
            reject(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE, ValidityStatus.NOT_ASSESSED,
                    SupportStatus.NOT_ASSESSED, ConversionReadiness.REQUIRES_VALIDATION,
                    List.of(AssessmentReason.VALIDATION_NOT_PERFORMED));
            reject(raw, ImagingFormat.UNKNOWN, FormatVariant.UNDETERMINED, ValidityStatus.INVALID,
                    SupportStatus.NOT_ASSESSED, ConversionReadiness.BLOCKED,
                    List.of(AssessmentReason.VALIDATION_FAILED));
        }
    }

    // U06/U08: empty reasons remain immutable; no processing-success claim.
    @Test
    void readyKnownSupportedInputsAcceptImmutableEmptyReasons() {
        for (var outcome : List.of(DetectionOutcome.DICOM, DetectionOutcome.NIFTI, DetectionOutcome.NIFTI_GZ)) {
            for (boolean mismatch : new boolean[] {false, true}) {
                var raw = new DetectionResult(outcome, DetectionDiagnostic.NONE, mismatch);
                var value = assessment(raw,
                        outcome == DetectionOutcome.DICOM ? ImagingFormat.DICOM : ImagingFormat.NIFTI,
                        outcome == DetectionOutcome.DICOM
                                ? FormatVariant.DICOM_UNSPECIFIED : FormatVariant.NIFTI_1_SINGLE_FILE,
                        ValidityStatus.VALID, SupportStatus.SUPPORTED, ConversionReadiness.READY);
                assertSame(raw, value.initialDetection());
                assertEquals(List.of(), value.reasons());
                assertThrowsExactly(UnsupportedOperationException.class,
                        () -> value.reasons().add(AssessmentReason.VALIDATION_FAILED));
            }
        }
    }

    // U09
    @Test
    void readyRejectsUnsuccessfulStatusesAndEveryPendingOrBlockingReason() {
        var raw = DetectionResult.identified(DetectionOutcome.NIFTI);
        for (var validity : List.of(ValidityStatus.NOT_ASSESSED, ValidityStatus.INCONCLUSIVE,
                ValidityStatus.INVALID)) {
            reject(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE,
                    validity, SupportStatus.SUPPORTED, ConversionReadiness.READY, List.of());
        }
        for (var support : List.of(SupportStatus.NOT_ASSESSED, SupportStatus.UNSUPPORTED)) {
            reject(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE,
                    ValidityStatus.VALID, support, ConversionReadiness.READY, List.of());
        }
        for (var reason : AssessmentReason.values()) {
            reject(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE,
                    ValidityStatus.VALID, SupportStatus.SUPPORTED, ConversionReadiness.READY,
                    List.of(reason));
        }
    }

    // U10
    @Test
    void emptyReasonsRejectedForEveryUnsuccessfulState() {
        var raw = DetectionResult.identified(DetectionOutcome.NIFTI);
        reject(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE, ValidityStatus.INVALID,
                SupportStatus.SUPPORTED, ConversionReadiness.BLOCKED, List.of());
        reject(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE, ValidityStatus.VALID,
                SupportStatus.UNSUPPORTED, ConversionReadiness.BLOCKED, List.of());
        for (var entry : UNKNOWN_CASES) {
            reject(DetectionResult.unknown(entry.diagnostic()), ImagingFormat.UNKNOWN,
                    FormatVariant.UNDETERMINED, entry.validity(), SupportStatus.NOT_ASSESSED,
                    ConversionReadiness.BLOCKED, List.of());
        }
        for (var diagnostic : List.of(DetectionDiagnostic.INVALID_DICOM, DetectionDiagnostic.INVALID_NIFTI,
                DetectionDiagnostic.INVALID_GZIP)) {
            reject(DetectionResult.corrupt(diagnostic), ImagingFormat.UNKNOWN, FormatVariant.UNDETERMINED,
                    ValidityStatus.INVALID, SupportStatus.NOT_ASSESSED, ConversionReadiness.BLOCKED, List.of());
        }
    }

    // U10: nonempty but incorrect reasons must fail too.
    @Test
    void eachReasonMustApplyToTheSuppliedState() {
        for (var entry : UNKNOWN_CASES) {
            for (var reason : AssessmentReason.values()) {
                if (reason != entry.reason()) {
                    reject(DetectionResult.unknown(entry.diagnostic()), ImagingFormat.UNKNOWN,
                            FormatVariant.UNDETERMINED, entry.validity(), SupportStatus.NOT_ASSESSED,
                            ConversionReadiness.BLOCKED, List.of(entry.reason(), reason));
                }
            }
        }
        for (var reason : AssessmentReason.values()) {
            if (reason != AssessmentReason.VALIDATION_NOT_PERFORMED) {
                reject(DetectionResult.identified(DetectionOutcome.NIFTI), ImagingFormat.NIFTI,
                        FormatVariant.NIFTI_1_SINGLE_FILE, ValidityStatus.NOT_ASSESSED,
                        SupportStatus.NOT_ASSESSED, ConversionReadiness.REQUIRES_VALIDATION,
                        List.of(AssessmentReason.VALIDATION_NOT_PERFORMED, reason));
            }
        }
    }

    // U04/U10: independent invalid and unsupported facts both need their reasons.
    @Test
    void invalidAndUnsupportedFactsCannotEraseEachOther() {
        var raw = DetectionResult.identified(DetectionOutcome.NIFTI);
        assessment(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE,
                ValidityStatus.INVALID, SupportStatus.UNSUPPORTED, ConversionReadiness.BLOCKED,
                AssessmentReason.UNSUPPORTED_PROFILE, AssessmentReason.VALIDATION_FAILED);
        reject(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE,
                ValidityStatus.INVALID, SupportStatus.UNSUPPORTED, ConversionReadiness.BLOCKED,
                List.of(AssessmentReason.UNSUPPORTED_PROFILE));
        reject(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE,
                ValidityStatus.INVALID, SupportStatus.UNSUPPORTED, ConversionReadiness.BLOCKED,
                List.of(AssessmentReason.VALIDATION_FAILED));
    }

    // U04/U10
    @Test
    void failedValidationBlocksRecognizedInputWithItsOwnReason() {
        var raw = DetectionResult.identified(DetectionOutcome.NIFTI);
        var value = assessment(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE,
                ValidityStatus.INVALID, SupportStatus.SUPPORTED, ConversionReadiness.BLOCKED,
                AssessmentReason.VALIDATION_FAILED);
        assertEquals(ValidityStatus.INVALID, value.validity());
        assertEquals(SupportStatus.SUPPORTED, value.support());
        assertEquals(ConversionReadiness.BLOCKED, value.readiness());
    }

    // U04/U10
    @Test
    void unsupportedProfileCanBeValidButRemainsBlocked() {
        var raw = DetectionResult.identified(DetectionOutcome.DICOM);
        var value = assessment(raw, ImagingFormat.DICOM, FormatVariant.DICOM_UNSPECIFIED,
                ValidityStatus.VALID, SupportStatus.UNSUPPORTED, ConversionReadiness.BLOCKED,
                AssessmentReason.UNSUPPORTED_PROFILE);
        assertEquals(ValidityStatus.VALID, value.validity());
        assertEquals(SupportStatus.UNSUPPORTED, value.support());
        assertEquals(ConversionReadiness.BLOCKED, value.readiness());
    }

    // U10: every supplied reason is checked even when required causes are present.
    @Test
    void requiredCausesDoNotMakeOtherReasonsApplicable() {
        var raw = DetectionResult.identified(DetectionOutcome.NIFTI);
        for (var reason : List.of(AssessmentReason.FORMAT_NOT_RECOGNIZED,
                AssessmentReason.DETECTION_NOT_COMPLETED, AssessmentReason.DETECTION_INCONCLUSIVE,
                AssessmentReason.INPUT_RECOGNIZED_AS_CORRUPT, AssessmentReason.VALIDATION_NOT_PERFORMED,
                AssessmentReason.UNSUPPORTED_FORMAT_VARIANT)) {
            reject(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE,
                    ValidityStatus.INVALID, SupportStatus.UNSUPPORTED, ConversionReadiness.BLOCKED,
                    List.of(AssessmentReason.VALIDATION_FAILED, AssessmentReason.UNSUPPORTED_PROFILE, reason));
        }
        reject(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_2_SINGLE_FILE,
                ValidityStatus.VALID, SupportStatus.UNSUPPORTED, ConversionReadiness.BLOCKED,
                List.of(AssessmentReason.UNSUPPORTED_FORMAT_VARIANT, AssessmentReason.VALIDATION_FAILED));
    }

    private static FormatAssessment assessment(DetectionResult raw, ImagingFormat format, FormatVariant variant,
            ValidityStatus validity, SupportStatus support, ConversionReadiness readiness,
            AssessmentReason... reasons) {
        return new FormatAssessment(raw, format, variant, validity, support, readiness, List.of(reasons));
    }

    private static IllegalArgumentException reject(DetectionResult raw, ImagingFormat format, FormatVariant variant,
            ValidityStatus validity, SupportStatus support, ConversionReadiness readiness,
            List<AssessmentReason> reasons) {
        return assertThrowsExactly(IllegalArgumentException.class,
                () -> new FormatAssessment(raw, format, variant, validity, support, readiness, reasons));
    }

    private record UnknownCase(DetectionDiagnostic diagnostic, ValidityStatus validity, AssessmentReason reason) { }
}
