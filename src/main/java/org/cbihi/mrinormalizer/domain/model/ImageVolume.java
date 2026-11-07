package org.cbihi.mrinormalizer.domain.model;

/** Format-neutral immutable 3D image aggregate used by conversion workflows. */
public record ImageVolume(VolumeGeometry geometry, VoxelData voxels,
                          IntensityTransform intensityTransform) implements Volume {

    public ImageVolume {
        if (geometry == null || voxels == null || intensityTransform == null) {
            throw new IllegalArgumentException("volume geometry, voxels and intensity transform are required");
        }
        if (geometry.width() != voxels.width()
                || geometry.height() != voxels.height()
                || geometry.depth() != voxels.depth()) {
            throw new IllegalArgumentException("volume geometry and voxels must have matching dimensions");
        }
    }
}
