package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.cbihi.mrinormalizer.application.dataset.model.AssessmentReason;
import org.cbihi.mrinormalizer.application.dataset.model.ConversionReadiness;
import org.cbihi.mrinormalizer.application.dataset.model.FormatAssessment;
import org.cbihi.mrinormalizer.application.dataset.model.FormatVariant;
import org.cbihi.mrinormalizer.application.dataset.model.SupportStatus;
import org.cbihi.mrinormalizer.application.dataset.model.ValidityStatus;
import org.cbihi.mrinormalizer.application.service.FormatDetectionService;
import org.cbihi.mrinormalizer.domain.model.DetectionDiagnostic;
import org.cbihi.mrinormalizer.domain.model.DetectionOutcome;
import org.cbihi.mrinormalizer.domain.model.DetectionResult;
import org.cbihi.mrinormalizer.domain.model.ImagingFormat;
import org.cbihi.mrinormalizer.domain.model.InputSource;
import org.cbihi.mrinormalizer.infrastructure.detection.DicomFormatProbe;
import org.cbihi.mrinormalizer.infrastructure.detection.NiftiFormatProbe;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real M5 recognition plus the pure assessment factory; not full imaging validation. */
class FormatAssessmentIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    // I01: only identifying header evidence is present; no supported payload is asserted.
    @Test
    void nifti1RecognitionRemainsUnspecifiedAndUnvalidated() throws IOException {
        var raw = detect("nifti1.nii", niftiHeader(348));
        assertEquals(DetectionOutcome.NIFTI, raw.outcome());
        assertFalse(raw.extensionMismatch());
        assertPendingRecognition(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_UNSPECIFIED);
    }

    // I01
    @Test
    void nifti2RecognitionDoesNotInferVersionSupportOrReadiness() throws IOException {
        var raw = detect("nifti2.nii", niftiHeader(540));
        assertEquals(DetectionOutcome.NIFTI, raw.outcome());
        assertPendingRecognition(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_UNSPECIFIED);
    }

    // I02: the existing bounded probe deliberately stops before trailer validation.
    @Test
    void truncatedGzipTrailerRecognitionDoesNotBecomeValid() throws IOException {
        byte[] complete = gzip(niftiHeader(348));
        byte[] truncated = Arrays.copyOf(complete, complete.length - 2);
        var raw = detect("truncated.nii.gz", truncated);
        assertEquals(DetectionOutcome.NIFTI_GZ, raw.outcome());
        assertPendingRecognition(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_UNSPECIFIED);
    }

    // I03: synthetic DICOM identifying metadata contains no Pixel Data.
    @Test
    void wrongAndExtensionlessDicomRequireValidation() throws IOException {
        byte[] metadata = dicomMetadata();
        for (var filename : List.of("dicom.txt", "dicom")) {
            var raw = detect(filename, metadata);
            assertEquals(DetectionOutcome.DICOM, raw.outcome());
            assertTrue(raw.extensionMismatch());
            assertPendingRecognition(raw, ImagingFormat.DICOM, FormatVariant.DICOM_UNSPECIFIED);
        }
    }

    // I04
    @Test
    void missingDirectoryAndInvalidReferenceKeepDistinctDiagnostics() {
        var detector = detector();
        assertBlockedDetection(detector.detect(new InputSource(temporaryDirectory.resolve("missing.nii").toString())),
                DetectionDiagnostic.INPUT_NOT_FOUND, ValidityStatus.NOT_ASSESSED,
                AssessmentReason.DETECTION_NOT_COMPLETED);
        assertBlockedDetection(detector.detect(new InputSource(temporaryDirectory.toString())),
                DetectionDiagnostic.INPUT_IS_DIRECTORY, ValidityStatus.NOT_ASSESSED,
                AssessmentReason.DETECTION_NOT_COMPLETED);
        assertBlockedDetection(detector.detect(new InputSource("\0")),
                DetectionDiagnostic.INVALID_INPUT_REFERENCE, ValidityStatus.NOT_ASSESSED,
                AssessmentReason.DETECTION_NOT_COMPLETED);
    }

    // I04
    @Test
    void emptyAndRandomNonmatchKeepDistinctReasons() throws IOException {
        assertBlockedDetection(detect("empty", new byte[0]), DetectionDiagnostic.EMPTY_INPUT,
                ValidityStatus.NOT_ASSESSED, AssessmentReason.DETECTION_NOT_COMPLETED);
        assertBlockedDetection(detect("random.bin", new byte[] {1, 2, 3, 4, 5}),
                DetectionDiagnostic.UNSUPPORTED_FORMAT, ValidityStatus.NOT_ASSESSED,
                AssessmentReason.FORMAT_NOT_RECOGNIZED);
    }

    // I05: real service boundary with a controlled probe; no permission-dependent fixture.
    @Test
    void controlledIoAndBudgetDiagnosticsRemainBlockedAndDistinct() throws IOException {
        var input = Files.write(temporaryDirectory.resolve("controlled.bin"), new byte[] {1, 2, 3, 4, 5});
        for (var diagnostic : List.of(DetectionDiagnostic.IO_ERROR, DetectionDiagnostic.INPUT_TOO_LARGE)) {
            var detector = new FormatDetectionService(List.of(
                    source -> DetectionResult.unknown(diagnostic), new NiftiFormatProbe()));
            var raw = detector.detect(new InputSource(input.toString()));
            assertBlockedDetection(raw, diagnostic,
                    diagnostic == DetectionDiagnostic.INPUT_TOO_LARGE
                            ? ValidityStatus.INCONCLUSIVE : ValidityStatus.NOT_ASSESSED,
                    diagnostic == DetectionDiagnostic.INPUT_TOO_LARGE
                            ? AssessmentReason.DETECTION_INCONCLUSIVE : AssessmentReason.DETECTION_NOT_COMPLETED);
        }
    }

    private static void assertPendingRecognition(DetectionResult raw, ImagingFormat format, FormatVariant variant) {
        assertEquals(DetectionDiagnostic.NONE, raw.diagnostic());
        var value = FormatAssessment.fromDetection(raw);
        assertSame(raw, value.initialDetection());
        assertEquals(raw.extensionMismatch(), value.initialDetection().extensionMismatch());
        assertEquals(format, value.format());
        assertEquals(variant, value.variant());
        assertEquals(ValidityStatus.NOT_ASSESSED, value.validity());
        assertEquals(SupportStatus.NOT_ASSESSED, value.support());
        assertEquals(ConversionReadiness.REQUIRES_VALIDATION, value.readiness());
        assertEquals(List.of(AssessmentReason.VALIDATION_NOT_PERFORMED), value.reasons());
    }

    private static void assertBlockedDetection(DetectionResult raw, DetectionDiagnostic diagnostic,
            ValidityStatus validity, AssessmentReason reason) {
        assertEquals(DetectionOutcome.UNKNOWN, raw.outcome());
        assertEquals(diagnostic, raw.diagnostic());
        var value = FormatAssessment.fromDetection(raw);
        assertSame(raw, value.initialDetection());
        assertEquals(raw.extensionMismatch(), value.initialDetection().extensionMismatch());
        assertEquals(ImagingFormat.UNKNOWN, value.format());
        assertEquals(FormatVariant.UNDETERMINED, value.variant());
        assertEquals(validity, value.validity());
        assertEquals(SupportStatus.NOT_ASSESSED, value.support());
        assertEquals(ConversionReadiness.BLOCKED, value.readiness());
        assertEquals(List.of(reason), value.reasons());
    }

    private DetectionResult detect(String filename, byte[] bytes) throws IOException {
        var path = Files.write(temporaryDirectory.resolve(filename), bytes);
        return detector().detect(new InputSource(path.toString()));
    }

    private static FormatDetectionService detector() {
        return new FormatDetectionService(List.of(new DicomFormatProbe(), new NiftiFormatProbe()));
    }

    // Recognition fixtures follow the inspected M5 tests; they do not establish file validity.
    private static byte[] niftiHeader(int size) {
        byte[] header = new byte[size];
        var buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(0, size);
        if (size == 348) {
            System.arraycopy("n+1\0".getBytes(StandardCharsets.ISO_8859_1), 0, header, 344, 4);
            buffer.putShort(40, (short) 3);
            buffer.putShort(42, (short) 1);
            buffer.putShort(44, (short) 1);
            buffer.putShort(46, (short) 1);
            buffer.putShort(70, (short) 16);
            buffer.putShort(72, (short) 32);
        } else {
            System.arraycopy(new byte[] {'n', '+', '2', 0, 13, 10, 26, 10}, 0, header, 4, 8);
            buffer.putShort(12, (short) 16);
            buffer.putShort(14, (short) 32);
            buffer.putLong(16, 3);
            buffer.putLong(24, 1);
            buffer.putLong(32, 1);
            buffer.putLong(40, 1);
        }
        return header;
    }

    private static byte[] gzip(byte[] bytes) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(output)) {
            gzip.write(bytes);
        }
        return output.toByteArray();
    }

    private static byte[] dicomMetadata() throws IOException {
        var dataset = new Attributes();
        dataset.setString(Tag.SOPClassUID, VR.UI, UID.MRImageStorage);
        dataset.setString(Tag.SOPInstanceUID, VR.UI, "1.2.826.0.1.3680043.10.5013.2.1");
        var metadata = dataset.createFileMetaInformation(UID.ExplicitVRLittleEndian);
        var output = new ByteArrayOutputStream();
        try (var dicom = new DicomOutputStream(output, UID.ExplicitVRLittleEndian)) {
            dicom.writeFileMetaInformation(metadata);
            dicom.writeDataset(null, dataset);
        }
        return output.toByteArray();
    }
}
