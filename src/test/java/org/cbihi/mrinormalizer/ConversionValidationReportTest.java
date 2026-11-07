package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.cbihi.mrinormalizer.application.conversion.NiftiAffineMapper;
import org.cbihi.mrinormalizer.application.provenance.ProvenanceRecord;
import org.cbihi.mrinormalizer.application.request.DicomToNiftiRequest;
import org.cbihi.mrinormalizer.application.result.DicomProcessingResult;
import org.cbihi.mrinormalizer.application.result.DicomToNiftiResult;
import org.cbihi.mrinormalizer.application.service.DefaultDicomToNiftiService;
import org.cbihi.mrinormalizer.application.validation.ConversionValidationReport;
import org.cbihi.mrinormalizer.domain.error.DicomProcessingError;
import org.cbihi.mrinormalizer.domain.error.DicomToNiftiError;
import org.cbihi.mrinormalizer.domain.model.AffineMatrix4;
import org.cbihi.mrinormalizer.domain.model.CoordinateSystem;
import org.cbihi.mrinormalizer.domain.model.ImageVolume;
import org.cbihi.mrinormalizer.domain.model.ImmutableVoxelData;
import org.cbihi.mrinormalizer.domain.model.InputSource;
import org.cbihi.mrinormalizer.domain.model.IntensityTransform;
import org.cbihi.mrinormalizer.domain.model.OutputTarget;
import org.cbihi.mrinormalizer.domain.model.ScalarType;
import org.cbihi.mrinormalizer.domain.model.VolumeGeometry;
import org.junit.jupiter.api.Test;

class ConversionValidationReportTest {

    @Test
    void capturesLosslessConversionInvariants() {
        ImageVolume volume = volume();
        OutputTarget output = new OutputTarget("synthetic-output.nii");
        AffineMatrix4 rasAffine =
                NiftiAffineMapper.toNiftiRas(volume.geometry());

        ConversionValidationReport report =
                ConversionValidationReport.losslessIntegerPreserving(
                        output,
                        volume,
                        rasAffine);

        assertEquals(output, report.output());

        assertEquals(2, report.width());
        assertEquals(2, report.height());
        assertEquals(1, report.depth());
        assertEquals(4L, report.voxelCount());

        assertEquals(ScalarType.UINT16, report.scalarType());

        assertEquals(0.75d, report.rowSpacingMm(), 1.0e-12);
        assertEquals(0.5d, report.columnSpacingMm(), 1.0e-12);
        assertEquals(2.0d, report.sliceSpacingMm(), 1.0e-12);

        AffineMatrix4 expectedAffine = new AffineMatrix4(new double[][] {
                {-0.5d, 0.0d, 0.0d, -10.0d},
                {0.0d, -0.75d, 0.0d, -20.0d},
                {0.0d, 0.0d, 2.0d, 30.0d},
                {0.0d, 0.0d, 0.0d, 1.0d}
        });

        assertEquals(expectedAffine, report.niftiRasVoxelToWorld());

        assertTrue(report.intensityTransform().declared());
        assertEquals(2.5d, report.intensityTransform().slope(), 1.0e-12);
        assertEquals(-100.0d, report.intensityTransform().intercept(), 1.0e-12);

        assertTrue(report.storedVoxelValuesPreserved());
        assertFalse(report.resampled());
        assertFalse(report.interpolated());
        assertFalse(report.voxelOrderChanged());
    }

    @Test
    void successfulConversionReturnsValidationReport() {
        AtomicBoolean writerCompleted = new AtomicBoolean(false);

        var service = new DefaultDicomToNiftiService(
                request -> successfulReconstruction(),
                (volume, affine, target) -> writerCompleted.set(true));

        var result = service.convert(request("successful-output.nii"));

        assertTrue(writerCompleted.get());
        assertTrue(result.successful());
        assertNotNull(result.validationReport());

        assertEquals(
                result.output(),
                result.validationReport().output());

        assertTrue(
                result.validationReport().storedVoxelValuesPreserved());
        assertFalse(result.validationReport().resampled());
        assertFalse(result.validationReport().interpolated());
        assertFalse(result.validationReport().voxelOrderChanged());
    }

    @Test
    void reconstructionFailureDoesNotProduceValidationReportAndSkipsWriter() {
        AtomicBoolean writerCalled = new AtomicBoolean(false);

        var service = new DefaultDicomToNiftiService(
                request -> DicomProcessingResult.failure(
                        DicomProcessingError.SERIES_NOT_FOUND,
                        null),
                (volume, affine, target) -> writerCalled.set(true));

        var result = service.convert(
                request("reconstruction-failure.nii"));

        assertFalse(result.successful());
        assertFalse(writerCalled.get());

        assertEquals(
                List.of(DicomProcessingError.SERIES_NOT_FOUND),
                result.errors());

        assertNull(result.output());
        assertNull(result.validationReport());
    }

    @Test
    void writerFailureDoesNotProduceValidationReport() {
        var service = new DefaultDicomToNiftiService(
                request -> successfulReconstruction(),
                (volume, affine, target) -> {
                    throw new UncheckedIOException(
                            "synthetic write failure",
                            new IOException("disk unavailable"));
                });

        var result = service.convert(
                request("writer-failure.nii"));

        assertFalse(result.successful());

        assertEquals(
                List.of(DicomToNiftiError.OUTPUT_WRITE_FAILED),
                result.conversionErrors());

        assertNull(result.output());
        assertNull(result.validationReport());

        assertNotNull(result.provenance());
        assertFalse(result.provenance().successful());
    }

    @Test
    void resultContractRejectsMissingOrContradictoryValidationReport() {
        ImageVolume volume = volume();
        OutputTarget output = new OutputTarget("contract-output.nii");
        AffineMatrix4 rasAffine =
                NiftiAffineMapper.toNiftiRas(volume.geometry());

        ConversionValidationReport report =
                ConversionValidationReport.losslessIntegerPreserving(
                        output,
                        volume,
                        rasAffine);

        assertThrowsExactly(
                NullPointerException.class,
                () -> DicomToNiftiResult.success(
                        output,
                        successfulProvenance(),
                        null));

        assertThrowsExactly(
                IllegalArgumentException.class,
                () -> new DicomToNiftiResult(
                        null,
                        List.of(DicomProcessingError.SERIES_NOT_FOUND),
                        List.of(),
                        null,
                        report));

        ConversionValidationReport differentOutputReport =
                ConversionValidationReport.losslessIntegerPreserving(
                        new OutputTarget("different-output.nii"),
                        volume,
                        rasAffine);

        assertThrowsExactly(
                IllegalArgumentException.class,
                () -> DicomToNiftiResult.success(
                        output,
                        successfulProvenance(),
                        differentOutputReport));
    }

    @Test
    void rejectsVoxelCountThatContradictsDimensions() {
        ImageVolume volume = volume();
        AffineMatrix4 rasAffine =
                NiftiAffineMapper.toNiftiRas(volume.geometry());

        assertThrowsExactly(
                IllegalArgumentException.class,
                () -> new ConversionValidationReport(
                        new OutputTarget("bad-count.nii"),
                        2,
                        2,
                        1,
                        ScalarType.UINT16,
                        5L,
                        0.75d,
                        0.5d,
                        2.0d,
                        rasAffine,
                        volume.intensityTransform(),
                        true,
                        false,
                        false,
                        false));
    }

    private DicomProcessingResult successfulReconstruction() {
        return DicomProcessingResult.success(
                volume(),
                successfulProvenance());
    }

    private ProvenanceRecord successfulProvenance() {
        return new ProvenanceRecord(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "0.1.0-SNAPSHOT",
                "5.33.0",
                Instant.parse("2026-10-02T00:00:00Z"),
                true,
                1,
                1,
                "dimensions=2x2x1;coordinate=DICOM_PATIENT_LPS",
                "scalarType=UINT16",
                List.of());
    }

    private DicomToNiftiRequest request(String output) {
        return new DicomToNiftiRequest(
                List.of(new InputSource("synthetic-input.dcm")),
                "2.25.700",
                new OutputTarget(output));
    }

    private ImageVolume volume() {
        VolumeGeometry geometry = new VolumeGeometry(
                2,
                2,
                1,
                0.75d,
                0.5d,
                2.0d,
                new double[] {10.0d, 20.0d, 30.0d},
                new double[] {1.0d, 0.0d, 0.0d},
                new double[] {0.0d, 1.0d, 0.0d},
                new double[] {0.0d, 0.0d, 1.0d},
                CoordinateSystem.DICOM_PATIENT_LPS);

        return new ImageVolume(
                geometry,
                new ImmutableVoxelData(
                        2,
                        2,
                        1,
                        ScalarType.UINT16,
                        new long[] {1, 2, 3, 4}),
                new IntensityTransform(
                        true,
                        2.5d,
                        -100.0d));
    }
}
