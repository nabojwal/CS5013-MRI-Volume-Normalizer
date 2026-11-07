package org.cbihi.mrinormalizer.domain.model;

import java.util.Arrays;

public record VolumeGeometry(
        int width,
        int height,
        int depth,
        double rowSpacingMm,
        double columnSpacingMm,
        double sliceSpacing,
        double[] origin,
        double[] columnIndexDirection,
        double[] rowIndexDirection,
        double[] sliceDirection,
        CoordinateSystem coordinateSystem
) {

    public VolumeGeometry {
        if (width <= 0 || height <= 0 || depth <= 0
                || !Double.isFinite(rowSpacingMm) || !Double.isFinite(columnSpacingMm)
                || !Double.isFinite(sliceSpacing) || rowSpacingMm <= 0 || columnSpacingMm <= 0
                || sliceSpacing < 0) {
            throw new IllegalArgumentException("volume dimensions and spacing must be valid");
        }
        origin = copyVector(origin);
        columnIndexDirection = copyVector(columnIndexDirection);
        rowIndexDirection = copyVector(rowIndexDirection);
        sliceDirection = copyVector(sliceDirection);
        if (coordinateSystem == null) {
            throw new IllegalArgumentException("coordinate system is required");
        }
    }

    @Override
    public double[] origin() {
        return origin.clone();
    }

    @Override
    public double[] columnIndexDirection() {
        return columnIndexDirection.clone();
    }

    @Override
    public double[] rowIndexDirection() {
        return rowIndexDirection.clone();
    }

    @Override
    public double[] sliceDirection() {
        return sliceDirection.clone();
    }

    /**
     * Builds the affine mapping voxel indices (x=column, y=row, z=slice) to the
     * declared world coordinate system. The origin is the centre of voxel (0,0,0).
     */
    public AffineMatrix4 voxelToWorldAffine() {
        return new AffineMatrix4(new double[][] {
                {columnIndexDirection[0] * columnSpacingMm, rowIndexDirection[0] * rowSpacingMm,
                        sliceDirection[0] * sliceSpacing, origin[0]},
                {columnIndexDirection[1] * columnSpacingMm, rowIndexDirection[1] * rowSpacingMm,
                        sliceDirection[1] * sliceSpacing, origin[1]},
                {columnIndexDirection[2] * columnSpacingMm, rowIndexDirection[2] * rowSpacingMm,
                        sliceDirection[2] * sliceSpacing, origin[2]},
                {0.0d, 0.0d, 0.0d, 1.0d}
        });
    }

    private static double[] copyVector(double[] vector) {
        if (vector == null || vector.length != 3) {
            throw new IllegalArgumentException("geometry vectors must contain three values");
        }
        return Arrays.copyOf(vector, vector.length);
    }
}
