package org.cbihi.mrinormalizer.domain.model;

public interface VoxelData {

    int width();

    int height();

    int depth();

    PixelEncoding encoding();

    long rawValueAt(int x, int y, int z);
}
