package org.cbihi.mrinormalizer.domain.model;

/** Linear interpretation metadata for stored voxel values. */
public record IntensityTransform(boolean declared, double slope, double intercept) {
    public IntensityTransform {
        if (!Double.isFinite(slope) || !Double.isFinite(intercept) || slope == 0.0d) {
            throw new IllegalArgumentException("intensity transform must be finite with non-zero slope");
        }
    }

    public static IntensityTransform identityNotDeclared() {
        return new IntensityTransform(false, 1.0d, 0.0d);
    }
}
