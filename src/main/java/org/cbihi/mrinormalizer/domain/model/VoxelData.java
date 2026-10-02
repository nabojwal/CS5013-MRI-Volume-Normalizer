package org.cbihi.mrinormalizer.domain.model;

public interface VoxelData {

    int width();

    int height();

    int depth();

    ScalarType scalarType();

    long rawValueAt(int x, int y, int z);
}
