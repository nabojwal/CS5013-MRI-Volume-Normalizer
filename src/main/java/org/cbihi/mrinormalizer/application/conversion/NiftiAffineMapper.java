package org.cbihi.mrinormalizer.application.conversion;

import java.util.Objects;

import org.cbihi.mrinormalizer.domain.model.AffineMatrix4;
import org.cbihi.mrinormalizer.domain.model.CoordinateSystem;
import org.cbihi.mrinormalizer.domain.model.VolumeGeometry;

/** Pure spatial conversion from the supported DICOM LPS geometry to a NIfTI RAS affine. */
public final class NiftiAffineMapper {

    private static final AffineMatrix4 LPS_TO_RAS = new AffineMatrix4(new double[][] {
            {-1.0d, 0.0d, 0.0d, 0.0d},
            {0.0d, -1.0d, 0.0d, 0.0d},
            {0.0d, 0.0d, 1.0d, 0.0d},
            {0.0d, 0.0d, 0.0d, 1.0d}
    });

    private NiftiAffineMapper() {
    }

    public static AffineMatrix4 toNiftiRas(VolumeGeometry geometry) {
        Objects.requireNonNull(geometry, "geometry");
        if (geometry.coordinateSystem() != CoordinateSystem.DICOM_PATIENT_LPS) {
            throw new IllegalArgumentException("DICOM-to-NIfTI affine mapping requires DICOM patient LPS geometry");
        }
        return LPS_TO_RAS.multiply(geometry.voxelToWorldAffine());
    }
}
