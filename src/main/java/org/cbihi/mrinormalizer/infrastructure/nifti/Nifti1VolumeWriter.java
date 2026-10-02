package org.cbihi.mrinormalizer.infrastructure.nifti;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

import org.cbihi.mrinormalizer.application.port.out.NiftiVolumeWriter;
import org.cbihi.mrinormalizer.domain.model.AffineMatrix4;
import org.cbihi.mrinormalizer.domain.model.ImageVolume;
import org.cbihi.mrinormalizer.domain.model.IntensityTransform;
import org.cbihi.mrinormalizer.domain.model.OutputTarget;
import org.cbihi.mrinormalizer.domain.model.ScalarType;
import org.cbihi.mrinormalizer.domain.model.VoxelData;

/**
 * Minimal NIfTI-1 single-file writer for the approved integer 3D profile.
 * Header and voxel bytes are emitted little-endian; x is the fastest voxel axis.
 */
public final class Nifti1VolumeWriter implements NiftiVolumeWriter {

    static final int HEADER_SIZE = 348;
    static final int VOX_OFFSET = 352;
    static final short XFORM_SCANNER_ANAT = 1;
    static final byte UNITS_MM = 2;

    @Override
    public void write(ImageVolume volume, AffineMatrix4 voxelToWorldRas, OutputTarget target) {
        Objects.requireNonNull(volume, "volume");
        if (voxelToWorldRas == null) {
            throw new IllegalArgumentException("NIfTI RAS voxel-to-world affine is required");
        }
        Path path = targetPath(target);
        boolean gzip = isGzip(path);
        byte[] header = buildHeader(volume, voxelToWorldRas);
        try (OutputStream file = new BufferedOutputStream(Files.newOutputStream(path));
             OutputStream output = gzip ? new GZIPOutputStream(file) : file) {
            output.write(header);
            output.write(new byte[VOX_OFFSET - HEADER_SIZE]);
            writeVoxels(volume.voxels(), output);
        } catch (IOException exception) {
            throw new UncheckedIOException("NIfTI output cannot be written", exception);
        }
    }

    byte[] buildHeader(ImageVolume volume, AffineMatrix4 ras) {
        var geometry = volume.geometry();
        requireNiftiDimension(geometry.width());
        requireNiftiDimension(geometry.height());
        requireNiftiDimension(geometry.depth());

        NiftiQuaternion quaternion = NiftiQuaternion.fromAffine(ras);

        ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(0, HEADER_SIZE);

        header.putShort(40, (short) 3);
        header.putShort(42, (short) geometry.width());
        header.putShort(44, (short) geometry.height());
        header.putShort(46, (short) geometry.depth());
        for (int offset = 48; offset <= 54; offset += 2) {
            header.putShort(offset, (short) 1);
        }

        ScalarType scalarType = volume.voxels().scalarType();
        header.putShort(70, datatype(scalarType));
        header.putShort(72, bitpix(scalarType));

        header.putFloat(76, (float) quaternion.qfac());
        header.putFloat(80, checkedPositiveFloat(quaternion.dx(), "x voxel spacing"));
        header.putFloat(84, checkedPositiveFloat(quaternion.dy(), "y voxel spacing"));
        header.putFloat(88, checkedPositiveFloat(quaternion.dz(), "z voxel spacing"));

        header.putFloat(108, VOX_OFFSET);
        writeScaling(header, volume.intensityTransform());

        header.put(123, UNITS_MM);
        putAscii(header, 148, 80, "CS5013 MRI Volume Normalizer");

        if (quaternion.representable()) {
            header.putShort(252, XFORM_SCANNER_ANAT);
            header.putFloat(256, (float) quaternion.b());
            header.putFloat(260, (float) quaternion.c());
            header.putFloat(264, (float) quaternion.d());
            header.putFloat(268, checkedFiniteFloat(quaternion.qx(), "qoffset_x"));
            header.putFloat(272, checkedFiniteFloat(quaternion.qy(), "qoffset_y"));
            header.putFloat(276, checkedFiniteFloat(quaternion.qz(), "qoffset_z"));
        } else {
            header.putShort(252, (short) 0);
        }

        header.putShort(254, XFORM_SCANNER_ANAT);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 4; column++) {
                int offset = 280 + row * 16 + column * 4;
                header.putFloat(offset, checkedFiniteFloat(ras.get(row, column), "sform value"));
            }
        }

        header.put(344, (byte) 'n');
        header.put(345, (byte) '+');
        header.put(346, (byte) '1');
        header.put(347, (byte) 0);
        return header.array();
    }

    private void writeScaling(ByteBuffer header, IntensityTransform transform) {
        if (!transform.declared()) {
            // NIfTI specifies scl_slope == 0 as "no scaling".
            header.putFloat(112, 0.0f);
            header.putFloat(116, 0.0f);
            return;
        }
        float slope = checkedFiniteFloat(transform.slope(), "scl_slope");
        if (slope == 0.0f) {
            throw new IllegalArgumentException("declared intensity slope cannot underflow to zero in NIfTI-1");
        }
        header.putFloat(112, slope);
        header.putFloat(116, checkedFiniteFloat(transform.intercept(), "scl_inter"));
    }

    private void writeVoxels(VoxelData voxels, OutputStream output) throws IOException {
        ByteBuffer twoBytes = ByteBuffer.allocate(Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (int z = 0; z < voxels.depth(); z++) {
            for (int y = 0; y < voxels.height(); y++) {
                for (int x = 0; x < voxels.width(); x++) {
                    long value = voxels.rawValueAt(x, y, z);
                    switch (voxels.scalarType()) {
                        case UINT8 -> output.write((int) requireRange(value, 0, 255));
                        case INT8 -> output.write((byte) requireRange(value, Byte.MIN_VALUE, Byte.MAX_VALUE));
                        case UINT16 -> writeShort(output, twoBytes, requireRange(value, 0, 65_535));
                        case INT16 -> writeShort(output, twoBytes,
                                requireRange(value, Short.MIN_VALUE, Short.MAX_VALUE));
                    }
                }
            }
        }
    }

    private void writeShort(OutputStream output, ByteBuffer buffer, long value) throws IOException {
        buffer.clear();
        buffer.putShort((short) value);
        output.write(buffer.array());
    }

    private long requireRange(long value, long minimum, long maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("voxel value is outside declared scalar type range");
        }
        return value;
    }

    private short datatype(ScalarType type) {
        return switch (type) {
            case UINT8 -> 2;
            case INT16 -> 4;
            case INT8 -> 256;
            case UINT16 -> 512;
        };
    }

    private short bitpix(ScalarType type) {
        return switch (type) {
            case UINT8, INT8 -> 8;
            case UINT16, INT16 -> 16;
        };
    }

    private int requireNiftiDimension(int value) {
        if (value < 1 || value > Short.MAX_VALUE) {
            throw new IllegalArgumentException("NIfTI-1 dimension exceeds signed 16-bit header capacity");
        }
        return value;
    }

    private float checkedPositiveFloat(double value, String field) {
        float converted = checkedFiniteFloat(value, field);
        if (converted <= 0.0f) {
            throw new IllegalArgumentException(field + " must remain positive in NIfTI-1");
        }
        return converted;
    }

    private float checkedFiniteFloat(double value, String field) {
        float converted = (float) value;
        if (!Float.isFinite(converted)) {
            throw new IllegalArgumentException(field + " cannot be represented as a finite NIfTI-1 float");
        }
        return converted;
    }

    private void putAscii(ByteBuffer buffer, int offset, int length, String text) {
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        int count = Math.min(bytes.length, length - 1);
        for (int index = 0; index < count; index++) {
            buffer.put(offset + index, bytes[index]);
        }
    }

    private Path targetPath(OutputTarget target) {
        if (target == null || target.reference() == null || target.reference().isBlank()) {
            throw new IllegalArgumentException("NIfTI output target is required");
        }
        Path path = Path.of(target.reference());
        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".nii") && !lower.endsWith(".nii.gz")) {
            throw new IllegalArgumentException("NIfTI output must end with .nii or .nii.gz");
        }
        return path;
    }

    private boolean isGzip(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".nii.gz");
    }

    private record NiftiQuaternion(
            boolean representable,
            double b,
            double c,
            double d,
            double qx,
            double qy,
            double qz,
            double dx,
            double dy,
            double dz,
            double qfac
    ) {
        private static final double TOLERANCE = 1.0e-5;

        static NiftiQuaternion fromAffine(AffineMatrix4 affine) {
            double dx = columnLength(affine, 0);
            double dy = columnLength(affine, 1);
            double dz = columnLength(affine, 2);
            if (!(dx > 0.0d && dy > 0.0d && dz > 0.0d)) {
                return unavailable(affine, dx, dy, dz);
            }

            double[][] rotation = new double[3][3];
            for (int row = 0; row < 3; row++) {
                rotation[row][0] = affine.get(row, 0) / dx;
                rotation[row][1] = affine.get(row, 1) / dy;
                rotation[row][2] = affine.get(row, 2) / dz;
            }
            if (!orthonormal(rotation)) {
                return unavailable(affine, dx, dy, dz);
            }

            double determinant = determinant(rotation);
            double qfac = 1.0d;
            if (determinant < 0.0d) {
                qfac = -1.0d;
                for (int row = 0; row < 3; row++) {
                    rotation[row][2] = -rotation[row][2];
                }
                determinant = -determinant;
            }
            if (Math.abs(determinant - 1.0d) > TOLERANCE) {
                return unavailable(affine, dx, dy, dz);
            }

            double[] quaternion = rotationToQuaternion(rotation);
            return new NiftiQuaternion(true, quaternion[1], quaternion[2], quaternion[3],
                    affine.get(0, 3), affine.get(1, 3), affine.get(2, 3), dx, dy, dz, qfac);
        }

        private static NiftiQuaternion unavailable(AffineMatrix4 affine, double dx, double dy, double dz) {
            return new NiftiQuaternion(false, 0, 0, 0,
                    affine.get(0, 3), affine.get(1, 3), affine.get(2, 3), dx, dy, dz, 1.0d);
        }

        private static double columnLength(AffineMatrix4 affine, int column) {
            return Math.sqrt(affine.get(0, column) * affine.get(0, column)
                    + affine.get(1, column) * affine.get(1, column)
                    + affine.get(2, column) * affine.get(2, column));
        }

        private static boolean orthonormal(double[][] matrix) {
            for (int column = 0; column < 3; column++) {
                double length = 0.0d;
                for (int row = 0; row < 3; row++) {
                    length += matrix[row][column] * matrix[row][column];
                }
                if (Math.abs(length - 1.0d) > TOLERANCE) {
                    return false;
                }
            }
            for (int first = 0; first < 3; first++) {
                for (int second = first + 1; second < 3; second++) {
                    double dot = 0.0d;
                    for (int row = 0; row < 3; row++) {
                        dot += matrix[row][first] * matrix[row][second];
                    }
                    if (Math.abs(dot) > TOLERANCE) {
                        return false;
                    }
                }
            }
            return true;
        }

        private static double determinant(double[][] matrix) {
            return matrix[0][0] * (matrix[1][1] * matrix[2][2] - matrix[1][2] * matrix[2][1])
                    - matrix[0][1] * (matrix[1][0] * matrix[2][2] - matrix[1][2] * matrix[2][0])
                    + matrix[0][2] * (matrix[1][0] * matrix[2][1] - matrix[1][1] * matrix[2][0]);
        }

        private static double[] rotationToQuaternion(double[][] r) {
            double a;
            double b;
            double c;
            double d;
            double trace = r[0][0] + r[1][1] + r[2][2];
            if (trace > 0.0d) {
                double s = Math.sqrt(trace + 1.0d) * 2.0d;
                a = 0.25d * s;
                b = (r[2][1] - r[1][2]) / s;
                c = (r[0][2] - r[2][0]) / s;
                d = (r[1][0] - r[0][1]) / s;
            } else if (r[0][0] > r[1][1] && r[0][0] > r[2][2]) {
                double s = Math.sqrt(1.0d + r[0][0] - r[1][1] - r[2][2]) * 2.0d;
                a = (r[2][1] - r[1][2]) / s;
                b = 0.25d * s;
                c = (r[0][1] + r[1][0]) / s;
                d = (r[0][2] + r[2][0]) / s;
            } else if (r[1][1] > r[2][2]) {
                double s = Math.sqrt(1.0d + r[1][1] - r[0][0] - r[2][2]) * 2.0d;
                a = (r[0][2] - r[2][0]) / s;
                b = (r[0][1] + r[1][0]) / s;
                c = 0.25d * s;
                d = (r[1][2] + r[2][1]) / s;
            } else {
                double s = Math.sqrt(1.0d + r[2][2] - r[0][0] - r[1][1]) * 2.0d;
                a = (r[1][0] - r[0][1]) / s;
                b = (r[0][2] + r[2][0]) / s;
                c = (r[1][2] + r[2][1]) / s;
                d = 0.25d * s;
            }

            double norm = Math.sqrt(a * a + b * b + c * c + d * d);
            a /= norm;
            b /= norm;
            c /= norm;
            d /= norm;
            if (a < 0.0d) {
                a = -a;
                b = -b;
                c = -c;
                d = -d;
            }
            return new double[] {a, b, c, d};
        }
    }
}
