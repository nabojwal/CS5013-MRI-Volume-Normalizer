package org.cbihi.mrinormalizer.domain.model;

import java.util.Arrays;

public final class ImmutableVoxelData implements VoxelData {

    private final int width;
    private final int height;
    private final int depth;
    private final PixelEncoding encoding;
    private final long[] values;

    public ImmutableVoxelData(int width, int height, int depth,
                              PixelEncoding encoding, long[] values) {
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException("voxel dimensions must be positive");
        }
        if (values.length != width * height * depth) {
            throw new IllegalArgumentException("voxel data length does not match dimensions");
        }
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.encoding = java.util.Objects.requireNonNull(encoding, "encoding");
        this.values = values.clone();
    }

    @Override
    public int width() { return width; }

    @Override
    public int height() { return height; }

    @Override
    public int depth() { return depth; }

    @Override
    public PixelEncoding encoding() { return encoding; }

    @Override
    public long rawValueAt(int x, int y, int z) {
        if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= depth) {
            throw new IndexOutOfBoundsException("voxel coordinate outside volume");
        }
        return values[(z * height + y) * width + x];
    }

    public long[] copyValues() { return Arrays.copyOf(values, values.length); }
}
