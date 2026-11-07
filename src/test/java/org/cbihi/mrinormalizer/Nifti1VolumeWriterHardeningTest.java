package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.cbihi.mrinormalizer.application.conversion.NiftiAffineMapper;
import org.cbihi.mrinormalizer.domain.model.CoordinateSystem;
import org.cbihi.mrinormalizer.domain.model.ImageVolume;
import org.cbihi.mrinormalizer.domain.model.ImmutableVoxelData;
import org.cbihi.mrinormalizer.domain.model.IntensityTransform;
import org.cbihi.mrinormalizer.domain.model.OutputTarget;
import org.cbihi.mrinormalizer.domain.model.ScalarType;
import org.cbihi.mrinormalizer.domain.model.VolumeGeometry;
import org.cbihi.mrinormalizer.infrastructure.nifti.Nifti1VolumeWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Nifti1VolumeWriterHardeningTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void existingOutputIsNeverOverwritten() throws Exception {
        Path output = temporaryDirectory.resolve("existing.nii");
        byte[] original = new byte[] {9, 8, 7, 6};
        Files.write(output, original);

        ImageVolume volume = volume(
                ScalarType.UINT8,
                new long[] {1, 2, 3, 4});

        UncheckedIOException exception = assertThrowsExactly(
                UncheckedIOException.class,
                () -> new Nifti1VolumeWriter().write(
                        volume,
                        NiftiAffineMapper.toNiftiRas(volume.geometry()),
                        new OutputTarget(output.toString())));

        assertTrue(exception.getCause() instanceof FileAlreadyExistsException);
        assertArrayEquals(original, Files.readAllBytes(output));
    }

    @Test
    void partialNewOutputIsRemovedWhenSerializationFails() {
        Path output = temporaryDirectory.resolve("partial.nii");

        ImageVolume volume = volume(
                ScalarType.UINT8,
                new long[] {1, 2, 3, 256});

        assertThrowsExactly(
                IllegalArgumentException.class,
                () -> new Nifti1VolumeWriter().write(
                        volume,
                        NiftiAffineMapper.toNiftiRas(volume.geometry()),
                        new OutputTarget(output.toString())));

        assertFalse(Files.exists(output));
    }

    private ImageVolume volume(ScalarType type, long[] values) {
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
                new ImmutableVoxelData(2, 2, 1, type, values),
                IntensityTransform.identityNotDeclared());
    }
}
