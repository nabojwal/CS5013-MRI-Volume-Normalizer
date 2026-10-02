package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import org.cbihi.mrinormalizer.application.conversion.NiftiAffineMapper;
import org.cbihi.mrinormalizer.domain.model.CoordinateSystem;
import org.cbihi.mrinormalizer.domain.model.VolumeGeometry;
import org.junit.jupiter.api.Test;

class NiftiAffineMapperTest {

    @Test
    void buildsLpsVoxelToWorldAffineWithXColumnYRowZSlice() {
        var geometry = geometry(CoordinateSystem.DICOM_PATIENT_LPS,
                0.75, 0.5, 2.0,
                new double[] {10, 20, 30},
                new double[] {1, 0, 0},
                new double[] {0, 1, 0},
                new double[] {0, 0, 1});

        var lps = geometry.voxelToWorldAffine();

        assertArrayEquals(new double[] {11.5, 23.0, 34.0}, lps.transformPoint(3, 4, 2), 1.0e-12);
        assertEquals(0.5, lps.get(0, 0), 1.0e-12);
        assertEquals(0.75, lps.get(1, 1), 1.0e-12);
        assertEquals(2.0, lps.get(2, 2), 1.0e-12);
    }

    @Test
    void convertsCanonicalLpsWorldCoordinatesToRasWithoutVoxelReordering() {
        var geometry = geometry(CoordinateSystem.DICOM_PATIENT_LPS,
                0.75, 0.5, 2.0,
                new double[] {10, 20, 30},
                new double[] {1, 0, 0},
                new double[] {0, 1, 0},
                new double[] {0, 0, 1});

        var ras = NiftiAffineMapper.toNiftiRas(geometry);

        assertArrayEquals(new double[] {-10, -20, 30}, ras.transformPoint(0, 0, 0), 1.0e-12);
        assertArrayEquals(new double[] {-11.5, -23.0, 34.0}, ras.transformPoint(3, 4, 2), 1.0e-12);
    }

    @Test
    void preservesObliqueAxisMeaningAndUnequalSpacingAcrossLpsToRas() {
        var geometry = geometry(CoordinateSystem.DICOM_PATIENT_LPS,
                0.8, 0.6, 2.5,
                new double[] {100, 200, -50},
                new double[] {0, 1, 0},
                new double[] {-1, 0, 0},
                new double[] {0, 0, 1});

        var ras = NiftiAffineMapper.toNiftiRas(geometry);

        // LPS(3,4,2) = [96.8, 201.8, -45]; RAS flips only world x and y.
        assertArrayEquals(new double[] {-96.8, -201.8, -45.0},
                ras.transformPoint(3, 4, 2), 1.0e-12);
        assertEquals(0.0, ras.get(0, 0), 1.0e-12);
        assertEquals(-0.6, ras.get(1, 0), 1.0e-12);
        assertEquals(0.8, ras.get(0, 1), 1.0e-12);
        assertEquals(2.5, ras.get(2, 2), 1.0e-12);
    }

    @Test
    void mapsRepresentativeVolumeCornerExactly() {
        var geometry = geometry(CoordinateSystem.DICOM_PATIENT_LPS,
                1.2, 0.9, 3.0,
                new double[] {-40, 15, 5},
                new double[] {1, 0, 0},
                new double[] {0, 1, 0},
                new double[] {0, 0, 1});

        var ras = NiftiAffineMapper.toNiftiRas(geometry);

        assertArrayEquals(new double[] {37.3, -18.6, 11.0},
                ras.transformPoint(3, 3, 2), 1.0e-12);
    }

    @Test
    void rejectsGeometryAlreadyDeclaredInAnotherWorldConvention() {
        var geometry = geometry(CoordinateSystem.NIFTI_RAS,
                1, 1, 1, new double[] {0, 0, 0},
                new double[] {1, 0, 0}, new double[] {0, 1, 0}, new double[] {0, 0, 1});

        assertThrowsExactly(IllegalArgumentException.class, () -> NiftiAffineMapper.toNiftiRas(geometry));
    }

    private VolumeGeometry geometry(CoordinateSystem system, double rowSpacing, double columnSpacing,
                                    double sliceSpacing, double[] origin, double[] columnDirection,
                                    double[] rowDirection, double[] sliceDirection) {
        return new VolumeGeometry(4, 4, 3, rowSpacing, columnSpacing, sliceSpacing,
                origin, columnDirection, rowDirection, sliceDirection, system);
    }
}
