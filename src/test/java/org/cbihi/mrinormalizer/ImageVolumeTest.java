package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import org.cbihi.mrinormalizer.domain.model.CoordinateSystem;
import org.cbihi.mrinormalizer.domain.model.ImageVolume;
import org.cbihi.mrinormalizer.domain.model.ImmutableVoxelData;
import org.cbihi.mrinormalizer.domain.model.IntensityTransform;
import org.cbihi.mrinormalizer.domain.model.ScalarType;
import org.cbihi.mrinormalizer.domain.model.VolumeGeometry;
import org.junit.jupiter.api.Test;

class ImageVolumeTest {

    @Test
    void preservesFormatNeutralGeometryVoxelsAndIntensityTransform() {
        var geometry = geometry(2, 2, 1);
        var voxels = new ImmutableVoxelData(2, 2, 1, ScalarType.UINT16, new long[] {1, 2, 3, 4});
        var transform = new IntensityTransform(true, 2.0, -100.0);

        var volume = new ImageVolume(geometry, voxels, transform);

        assertSame(geometry, volume.geometry());
        assertSame(voxels, volume.voxels());
        assertSame(transform, volume.intensityTransform());
        assertEquals(ScalarType.UINT16, volume.voxels().scalarType());
        assertEquals(4L, volume.voxels().rawValueAt(1, 1, 0));
    }

    @Test
    void rejectsMismatchedGeometryAndVoxelDimensions() {
        var geometry = geometry(2, 2, 1);
        var voxels = new ImmutableVoxelData(1, 1, 1, ScalarType.INT16, new long[] {-1});
        assertThrowsExactly(IllegalArgumentException.class,
                () -> new ImageVolume(geometry, voxels, IntensityTransform.identityNotDeclared()));
    }

    @Test
    void rejectsMissingComponents() {
        var geometry = geometry(1, 1, 1);
        var voxels = new ImmutableVoxelData(1, 1, 1, ScalarType.UINT8, new long[] {1});
        var transform = IntensityTransform.identityNotDeclared();
        assertThrowsExactly(IllegalArgumentException.class, () -> new ImageVolume(null, voxels, transform));
        assertThrowsExactly(IllegalArgumentException.class, () -> new ImageVolume(geometry, null, transform));
        assertThrowsExactly(IllegalArgumentException.class, () -> new ImageVolume(geometry, voxels, null));
    }

    private VolumeGeometry geometry(int width, int height, int depth) {
        return new VolumeGeometry(width, height, depth, 1.0, 1.0, 2.0,
                new double[] {0, 0, 0}, new double[] {1, 0, 0}, new double[] {0, 1, 0},
                new double[] {0, 0, 1}, CoordinateSystem.DICOM_PATIENT_LPS);
    }
}
