package org.cbihi.mrinormalizer.domain.model;

public record GeometryValidationPolicy(
        double directionCosineTolerance,
        double positionToleranceMm,
        double pixelSpacingToleranceMm,
        double sliceSpacingToleranceMm,
        double duplicatePositionToleranceMm
) {

    public static GeometryValidationPolicy defaults() {
        // 1e-4 is dimensionless; remaining values are millimetres. These compare decimal metadata only.
        return new GeometryValidationPolicy(1e-4, 1e-3, 1e-3, 1e-3, 1e-3);
    }
}
