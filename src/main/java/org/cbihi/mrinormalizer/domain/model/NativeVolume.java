package org.cbihi.mrinormalizer.domain.model;

public record NativeVolume(VolumeGeometry geometry, VoxelData voxels,
                           RescaleTransform rescaleTransform) implements Volume {

    public NativeVolume {
        if (geometry == null || voxels == null || rescaleTransform == null) {
            throw new IllegalArgumentException("volume geometry and voxels are required");
        }
        if (geometry.width() != voxels.width()
                || geometry.height() != voxels.height()
                || geometry.depth() != voxels.depth()) {
            throw new IllegalArgumentException("volume geometry and voxels must have matching dimensions");
        }
    }
}
