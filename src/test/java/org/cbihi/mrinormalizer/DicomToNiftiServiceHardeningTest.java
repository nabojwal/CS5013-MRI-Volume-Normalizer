package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.cbihi.mrinormalizer.application.provenance.ProvenanceRecord;
import org.cbihi.mrinormalizer.application.request.DicomToNiftiRequest;
import org.cbihi.mrinormalizer.application.result.DicomProcessingResult;
import org.cbihi.mrinormalizer.application.service.DefaultDicomToNiftiService;
import org.cbihi.mrinormalizer.domain.error.DicomToNiftiError;
import org.cbihi.mrinormalizer.domain.model.CoordinateSystem;
import org.cbihi.mrinormalizer.domain.model.ImageVolume;
import org.cbihi.mrinormalizer.domain.model.ImmutableVoxelData;
import org.cbihi.mrinormalizer.domain.model.InputSource;
import org.cbihi.mrinormalizer.domain.model.IntensityTransform;
import org.cbihi.mrinormalizer.domain.model.OutputTarget;
import org.cbihi.mrinormalizer.domain.model.ScalarType;
import org.cbihi.mrinormalizer.domain.model.VolumeGeometry;
import org.cbihi.mrinormalizer.infrastructure.nifti.Nifti1VolumeWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DicomToNiftiServiceHardeningTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void invalidOutputIsReturnedAsStructuredConversionFailure() {
        var service = new DefaultDicomToNiftiService(
                request -> successfulReconstruction(),
                new Nifti1VolumeWriter());

        var result = service.convert(
                request(temporaryDirectory.resolve("invalid.img")));

        assertFalse(result.successful());
        assertEquals(List.of(), result.errors());
        assertEquals(
                List.of(DicomToNiftiError.INVALID_OUTPUT),
                result.conversionErrors());
        assertFalse(result.provenance().successful());
    }

    @Test
    void existingOutputIsReturnedAsStructuredCollisionFailureAndRemainsUnchanged()
            throws Exception {

        Path output = temporaryDirectory.resolve("existing.nii");
        byte[] original = new byte[] {11, 22, 33};
        Files.write(output, original);

        var service = new DefaultDicomToNiftiService(
                request -> successfulReconstruction(),
                new Nifti1VolumeWriter());

        var result = service.convert(request(output));

        assertFalse(result.successful());
        assertEquals(List.of(), result.errors());
        assertEquals(
                List.of(DicomToNiftiError.OUTPUT_ALREADY_EXISTS),
                result.conversionErrors());
        assertFalse(result.provenance().successful());
        assertArrayEquals(original, Files.readAllBytes(output));
    }

    @Test
    void genericWriterIoFailureIsReturnedAsStructuredWriteFailure() {
        var service = new DefaultDicomToNiftiService(
                request -> successfulReconstruction(),
                (volume, affine, target) -> {
                    throw new UncheckedIOException(
                            "synthetic write failure",
                            new IOException("disk unavailable"));
                });

        var result = service.convert(
                request(temporaryDirectory.resolve("failed.nii")));

        assertFalse(result.successful());
        assertEquals(List.of(), result.errors());
        assertEquals(
                List.of(DicomToNiftiError.OUTPUT_WRITE_FAILED),
                result.conversionErrors());
        assertFalse(result.provenance().successful());
    }

    private DicomProcessingResult successfulReconstruction() {
        ImageVolume volume = volume();

        ProvenanceRecord provenance = new ProvenanceRecord(
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

        return DicomProcessingResult.success(volume, provenance);
    }

    private DicomToNiftiRequest request(Path output) {
        return new DicomToNiftiRequest(
                List.of(new InputSource("synthetic-input.dcm")),
                "2.25.700",
                new OutputTarget(output.toString()));
    }

    private ImageVolume volume() {
        var geometry = new VolumeGeometry(
                2,
                2,
                1,
                0.75,
                0.5,
                2.0,
                new double[] {10, 20, 30},
                new double[] {1, 0, 0},
                new double[] {0, 1, 0},
                new double[] {0, 0, 1},
                CoordinateSystem.DICOM_PATIENT_LPS);

        return new ImageVolume(
                geometry,
                new ImmutableVoxelData(
                        2,
                        2,
                        1,
                        ScalarType.UINT16,
                        new long[] {1, 2, 3, 4}),
                IntensityTransform.identityNotDeclared());
    }
}
