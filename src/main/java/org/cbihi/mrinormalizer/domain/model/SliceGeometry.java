package org.cbihi.mrinormalizer.domain.model;

public record SliceGeometry(
        double[] position,
        double[] columnIndexDirection,
        double[] rowIndexDirection,
        double[] sliceDirection,
        double projectedPosition
) {

    public SliceGeometry {
        position = copy(position);
        columnIndexDirection = copy(columnIndexDirection);
        rowIndexDirection = copy(rowIndexDirection);
        sliceDirection = copy(sliceDirection);
    }

    @Override
    public double[] position() {
        return position.clone();
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

    private static double[] copy(double[] values) {
        if (values == null || values.length != 3) {
            throw new IllegalArgumentException("geometry vector must contain three values");
        }
        return values.clone();
    }
}
