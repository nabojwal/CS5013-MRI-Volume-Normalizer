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

    private static double[] copyVector(double[] vector) {
        if (vector == null || vector.length != 3) {
            throw new IllegalArgumentException("geometry vectors must contain three values");
        }
        return Arrays.copyOf(vector, vector.length);
    }
}
