package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import org.cbihi.mrinormalizer.domain.model.AffineMatrix4;
import org.junit.jupiter.api.Test;

class AffineMatrix4Test {

    @Test
    void transformsPointsUsingColumnVectorConvention() {
        var affine = new AffineMatrix4(new double[][] {
                {2, 0, 0, 10},
                {0, 3, 0, -20},
                {0, 0, 4, 30},
                {0, 0, 0, 1}
        });

        assertArrayEquals(new double[] {12, -14, 42}, affine.transformPoint(1, 2, 3), 1.0e-12);
    }

    @Test
    void matrixMultiplicationComposesRightHandTransformFirst() {
        var scale = new AffineMatrix4(new double[][] {
                {2, 0, 0, 0}, {0, 3, 0, 0}, {0, 0, 4, 0}, {0, 0, 0, 1}
        });
        var translate = new AffineMatrix4(new double[][] {
                {1, 0, 0, 10}, {0, 1, 0, 20}, {0, 0, 1, 30}, {0, 0, 0, 1}
        });

        assertArrayEquals(new double[] {12, 26, 42},
                translate.multiply(scale).transformPoint(1, 2, 3), 1.0e-12);
    }

    @Test
    void constructorAndCopyRowsAreDefensive() {
        double[][] rows = {
                {1, 0, 0, 10}, {0, 1, 0, 20}, {0, 0, 1, 30}, {0, 0, 0, 1}
        };
        var affine = new AffineMatrix4(rows);
        rows[0][3] = 999;
        double[][] copy = affine.copyRows();
        copy[1][3] = 999;

        assertEquals(10, affine.get(0, 3));
        assertEquals(20, affine.get(1, 3));
    }

    @Test
    void rejectsMalformedNonFiniteAndNonAffineMatrices() {
        assertThrowsExactly(IllegalArgumentException.class, () -> new AffineMatrix4(new double[][] {{1}}));
        assertThrowsExactly(IllegalArgumentException.class, () -> new AffineMatrix4(new double[][] {
                {1, 0, 0, 0}, {0, Double.NaN, 0, 0}, {0, 0, 1, 0}, {0, 0, 0, 1}
        }));
        assertThrowsExactly(IllegalArgumentException.class, () -> new AffineMatrix4(new double[][] {
                {1, 0, 0, 0}, {0, 1, 0, 0}, {0, 0, 1, 0}, {0, 0, 1, 1}
        }));
    }

    @Test
    void rejectsOutOfBoundsIndicesAndNonFinitePoints() {
        var affine = new AffineMatrix4(new double[][] {
                {1, 0, 0, 0}, {0, 1, 0, 0}, {0, 0, 1, 0}, {0, 0, 0, 1}
        });
        assertThrowsExactly(IndexOutOfBoundsException.class, () -> affine.get(4, 0));
        assertThrowsExactly(IllegalArgumentException.class,
                () -> affine.transformPoint(Double.POSITIVE_INFINITY, 0, 0));
    }
}
