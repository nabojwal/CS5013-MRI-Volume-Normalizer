package org.cbihi.mrinormalizer.domain.model;

/** Linear interpretation metadata; it is never applied to M6 raw voxel values. */
public record RescaleTransform(boolean declared, double slope, double intercept) {
    public RescaleTransform {
        if (!Double.isFinite(slope) || !Double.isFinite(intercept) || slope == 0.0d) {
            throw new IllegalArgumentException("rescale transform must be finite with non-zero slope");
        }
    }

    public static RescaleTransform identityNotDeclared() {
        return new RescaleTransform(false, 1.0d, 0.0d);
    }
}
