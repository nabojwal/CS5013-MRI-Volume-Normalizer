package org.cbihi.mrinormalizer.application.validation;

import java.util.Objects;

import org.cbihi.mrinormalizer.domain.model.AffineMatrix4;
import org.cbihi.mrinormalizer.domain.model.ImageVolume;
import org.cbihi.mrinormalizer.domain.model.IntensityTransform;
import org.cbihi.mrinormalizer.domain.model.OutputTarget;
import org.cbihi.mrinormalizer.domain.model.ScalarType;
import org.cbihi.mrinormalizer.domain.model.VolumeGeometry;

/**
 * Application-level report of factual DICOM-to-NIfTI conversion invariants.
 *
 * <p>This report describes properties established from the validated source
 * volume, the explicit NIfTI-RAS affine supplied to the writer, and the
 * conversion path. It does not imply that the written NIfTI output was
 * reopened, reparsed, or independently validated.</p>
 */
public record ConversionValidationReport(
        OutputTarget output,
        int width,
        int height,
        int depth,
        ScalarType scalarType,
        long voxelCount,
        double rowSpacingMm,
        double columnSpacingMm,
        double sliceSpacingMm,
        AffineMatrix4 niftiRasVoxelToWorld,
        IntensityTransform intensityTransform,
        boolean storedVoxelValuesPreserved,
        boolean resampled,
        boolean interpolated,
        boolean voxelOrderChanged
) {

    public ConversionValidationReport {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(scalarType, "scalarType");
        Objects.requireNonNull(niftiRasVoxelToWorld, "niftiRasVoxelToWorld");
        Objects.requireNonNull(intensityTransform, "intensityTransform");

        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException(
                    "validation-report dimensions must be positive");
        }

        if (!Double.isFinite(rowSpacingMm)
                || !Double.isFinite(columnSpacingMm)
                || !Double.isFinite(sliceSpacingMm)
                || rowSpacingMm <= 0.0d
                || columnSpacingMm <= 0.0d
                || sliceSpacingMm < 0.0d) {
            throw new IllegalArgumentException(
                    "validation-report spacing must be finite and valid");
        }

        long expectedVoxelCount = checkedVoxelCount(width, height, depth);
        if (voxelCount != expectedVoxelCount) {
            throw new IllegalArgumentException(
                    "validation-report voxel count does not match dimensions");
        }
    }

    /**
     * Creates the report for the supported M7 integer-preserving conversion
     * path. The invariants describe the conversion operation itself and do not
     * claim post-write file validation.
     */
    public static ConversionValidationReport losslessIntegerPreserving(
            OutputTarget output,
            ImageVolume volume,
            AffineMatrix4 niftiRasVoxelToWorld) {

        Objects.requireNonNull(volume, "volume");

        VolumeGeometry geometry = volume.geometry();

        return new ConversionValidationReport(
                output,
                geometry.width(),
                geometry.height(),
                geometry.depth(),
                volume.voxels().scalarType(),
                checkedVoxelCount(
                        geometry.width(),
                        geometry.height(),
                        geometry.depth()),
                geometry.rowSpacingMm(),
                geometry.columnSpacingMm(),
                geometry.sliceSpacing(),
                niftiRasVoxelToWorld,
                volume.intensityTransform(),
                true,
                false,
                false,
                false);
    }

    private static long checkedVoxelCount(int width, int height, int depth) {
        try {
            return Math.multiplyExact(
                    Math.multiplyExact((long) width, height),
                    depth);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "volume dimensions overflow voxel count",
                    exception);
        }
    }
}
