package org.cbihi.mrinormalizer.domain.model;

import java.util.Arrays;
import java.util.Objects;

/** Immutable affine 4x4 matrix using row-major storage and column-vector multiplication. */
public final class AffineMatrix4 {

    private static final int SIZE = 4;
    private final double[] values;

    public AffineMatrix4(double[][] rows) {
        if (rows == null || rows.length != SIZE) {
            throw new IllegalArgumentException("affine matrix must contain four rows");
        }
        values = new double[SIZE * SIZE];
        for (int row = 0; row < SIZE; row++) {
            if (rows[row] == null || rows[row].length != SIZE) {
                throw new IllegalArgumentException("affine matrix rows must contain four values");
            }
            for (int column = 0; column < SIZE; column++) {
                double value = rows[row][column];
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("affine matrix values must be finite");
                }
                values[row * SIZE + column] = value;
            }
        }
        if (get(3, 0) != 0.0d || get(3, 1) != 0.0d || get(3, 2) != 0.0d || get(3, 3) != 1.0d) {
            throw new IllegalArgumentException("matrix must be affine with final row [0,0,0,1]");
        }
    }

    public double get(int row, int column) {
        if (row < 0 || row >= SIZE || column < 0 || column >= SIZE) {
            throw new IndexOutOfBoundsException("affine matrix index outside 4x4 bounds");
        }
        return values[row * SIZE + column];
    }

    public double[] transformPoint(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("point coordinates must be finite");
        }
        return new double[] {
                get(0, 0) * x + get(0, 1) * y + get(0, 2) * z + get(0, 3),
                get(1, 0) * x + get(1, 1) * y + get(1, 2) * z + get(1, 3),
                get(2, 0) * x + get(2, 1) * y + get(2, 2) * z + get(2, 3)
        };
    }

    public AffineMatrix4 multiply(AffineMatrix4 right) {
        Objects.requireNonNull(right, "right");
        double[][] product = new double[SIZE][SIZE];
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                double value = 0.0d;
                for (int inner = 0; inner < SIZE; inner++) {
                    value += get(row, inner) * right.get(inner, column);
                }
                product[row][column] = value;
            }
        }
        return new AffineMatrix4(product);
    }

    public double[][] copyRows() {
        double[][] copy = new double[SIZE][SIZE];
        for (int row = 0; row < SIZE; row++) {
            System.arraycopy(values, row * SIZE, copy[row], 0, SIZE);
        }
        return copy;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AffineMatrix4 matrix && Arrays.equals(values, matrix.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return Arrays.deepToString(copyRows());
    }
}
