package org.cbihi.mrinormalizer.domain.model;

import java.util.Arrays;

public final class ImmutableVoxelData implements VoxelData {

    private final int width;
    private final int height;
    private final int depth;
    private final ScalarType scalarType;
    private final long[] values;

    public ImmutableVoxelData(int width, int height, int depth,
                              ScalarType scalarType, long[] values) {
        int voxelCount = checkedVoxelCount(width, height, depth);
        if (values.length != voxelCount) {
            throw new IllegalArgumentException("voxel data length does not match dimensions");
        }
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.scalarType = java.util.Objects.requireNonNull(scalarType, "scalarType");
        this.values = values.clone();
    }

    private static int checkedVoxelCount(int width, int height, int depth) {
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException("voxel dimensions must be positive");
        }
        try {
            // Positive dimensions make any intermediate overflow exceed array capacity.
            return Math.multiplyExact(Math.multiplyExact(width, height), depth);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("voxel count exceeds Java array capacity", exception);
        }
    }

    @Override
    public int width() { return width; }

    @Override
    public int height() { return height; }

    @Override
    public int depth() { return depth; }

    @Override
    public ScalarType scalarType() { return scalarType; }

    @Override
    public long rawValueAt(int x, int y, int z) {
        if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= depth) {
            throw new IndexOutOfBoundsException("voxel coordinate outside volume");
        }
        return values[(z * height + y) * width + x];
    }

    public long[] copyValues() { return Arrays.copyOf(values, values.length); }
}
