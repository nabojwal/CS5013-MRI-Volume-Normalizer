package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.util.List;

import org.cbihi.mrinormalizer.application.request.DicomSeriesRequest;
import org.cbihi.mrinormalizer.application.service.DefaultDicomSeriesService;
import org.cbihi.mrinormalizer.domain.error.DicomProcessingError;
import org.cbihi.mrinormalizer.domain.model.DicomInstance;
import org.cbihi.mrinormalizer.domain.model.GeometryValidationPolicy;
import org.cbihi.mrinormalizer.domain.model.ImmutableVoxelData;
import org.cbihi.mrinormalizer.domain.model.InputSource;
import org.cbihi.mrinormalizer.domain.model.RescaleTransform;
import org.cbihi.mrinormalizer.domain.model.ScalarType;
import org.cbihi.mrinormalizer.domain.model.SliceGeometry;
import org.cbihi.mrinormalizer.domain.model.VoxelData;
import org.junit.jupiter.api.Test;

class ImmutableVoxelDataTest {

    private static final ScalarType SCALAR_TYPE = ScalarType.UINT16;

    @Test
    void rejectsCountThatWrapsToZero() {
        assertThrowsExactly(IllegalArgumentException.class,
                () -> new ImmutableVoxelData(65_536, 65_536, 1, SCALAR_TYPE, new long[0]));
    }

    @Test
    void rejectsCountThatWrapsToSmallPositiveLength() {
        // MAX_VALUE squared wraps to 1 in int arithmetic.
        assertThrowsExactly(IllegalArgumentException.class,
                () -> new ImmutableVoxelData(Integer.MAX_VALUE, Integer.MAX_VALUE, 1, SCALAR_TYPE, new long[1]));
    }

    @Test
    void rejectsCountBeyondEvenLongCapacity() {
        // 2^22 cubed is 2^66; neither unchecked int nor unchecked long is safe.
        int dimension = 1 << 22;
        assertThrowsExactly(IllegalArgumentException.class,
                () -> new ImmutableVoxelData(dimension, dimension, dimension, SCALAR_TYPE, new long[0]));
    }

    @Test
    void rejectsPositiveLongCountAboveArrayCapacity() {
        assertThrowsExactly(IllegalArgumentException.class,
                () -> new ImmutableVoxelData(50_000, 50_000, 1, SCALAR_TYPE, new long[0]));
    }

    @Test
    void rejectsZeroInEachDimension() {
        for (int[] shape : new int[][] {{0, 1, 1}, {1, 0, 1}, {1, 1, 0}}) {
            assertThrowsExactly(IllegalArgumentException.class,
                    () -> new ImmutableVoxelData(shape[0], shape[1], shape[2], SCALAR_TYPE, new long[0]));
        }
    }

    @Test
    void rejectsNegativeInEachDimension() {
        for (int[] shape : new int[][] {{-1, 1, 1}, {1, -1, 1}, {1, 1, -1}}) {
            assertThrowsExactly(IllegalArgumentException.class,
                    () -> new ImmutableVoxelData(shape[0], shape[1], shape[2], SCALAR_TYPE, new long[0]));
        }
    }

    @Test
    void preservesSmallVolumeCountAndIndexing() {
        var single = new ImmutableVoxelData(1, 1, 1, SCALAR_TYPE, new long[] {42});
        assertEquals(42, single.rawValueAt(0, 0, 0));
        assertEquals(1, single.copyValues().length);

        long[] values = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
        var volume = new ImmutableVoxelData(2, 3, 2, SCALAR_TYPE, values);
        assertEquals(2, volume.width());
        assertEquals(3, volume.height());
        assertEquals(2, volume.depth());
        assertArrayEquals(values, volume.copyValues());
        for (int z = 0; z < 2; z++) {
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 2; x++) {
                    assertEquals((z * 3 + y) * 2 + x, volume.rawValueAt(x, y, z));
                }
            }
        }
    }

    @Test
    void rejectsMismatchedArrayLength() {
        assertThrowsExactly(IllegalArgumentException.class,
                () -> new ImmutableVoxelData(2, 3, 2, SCALAR_TYPE, new long[11]));
    }

    @Test
    void rejectsOversizedReconstructionBeforeReadingVoxels() {
        VoxelData virtualPixels = new VoxelData() {
            public int width() { return 50_000; }
            public int height() { return 50_000; }
            public int depth() { return 1; }
            public ScalarType scalarType() { return SCALAR_TYPE; }
            public long rawValueAt(int x, int y, int z) {
                throw new AssertionError("Oversized volume must be rejected before reading voxels");
            }
        };
        var geometry = new SliceGeometry(new double[] {0, 0, 0}, new double[] {1, 0, 0},
                new double[] {0, 1, 0}, new double[] {0, 0, 1}, 0);
        var instance = new DicomInstance("2.25.1", "2.25.2", "2.25.3", "MR",
                "1.2.840.10008.5.1.4.1.1.4", "1.2.840.10008.1.2.1",
                50_000, 50_000, 1, 1, geometry,
                new org.cbihi.mrinormalizer.domain.model.PixelEncoding(16, 16, 15,
                        org.cbihi.mrinormalizer.domain.model.PixelValueType.UNSIGNED_INTEGER, 1, "MONOCHROME2"),
                RescaleTransform.identityNotDeclared(), virtualPixels, "2.25.4", null, 2.0, null);
        var service = new DefaultDicomSeriesService(input -> instance, GeometryValidationPolicy.defaults());

        var result = service.process(new DicomSeriesRequest(List.of(new InputSource("virtual")), "2.25.3"));

        assertFalse(result.successful());
        assertEquals(List.of(DicomProcessingError.DICOM_PARSE_FAILED), result.errors());
    }
}
