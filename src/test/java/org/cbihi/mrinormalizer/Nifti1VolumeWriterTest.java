package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

import org.cbihi.mrinormalizer.application.conversion.NiftiAffineMapper;
import org.cbihi.mrinormalizer.domain.model.CoordinateSystem;
import org.cbihi.mrinormalizer.domain.model.ImageVolume;
import org.cbihi.mrinormalizer.domain.model.ImmutableVoxelData;
import org.cbihi.mrinormalizer.domain.model.IntensityTransform;
import org.cbihi.mrinormalizer.domain.model.OutputTarget;
import org.cbihi.mrinormalizer.domain.model.ScalarType;
import org.cbihi.mrinormalizer.domain.model.VolumeGeometry;
import org.cbihi.mrinormalizer.infrastructure.nifti.Nifti1VolumeWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Nifti1VolumeWriterTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesCanonicalNifti1SingleFileHeaderAndUnsigned16Payload() throws IOException {
        Path output = temporaryDirectory.resolve("volume.nii");
        var volume = volume(ScalarType.UINT16, new long[] {0, 1, 65_535, 42},
                new IntensityTransform(true, 2.0, -100.0));

        new Nifti1VolumeWriter().write(volume, ras(volume), new OutputTarget(output.toString()));
        byte[] bytes = Files.readAllBytes(output);
        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(352 + 8, bytes.length);
        assertEquals(348, header.getInt(0));
        assertEquals(3, header.getShort(40));
        assertEquals(2, header.getShort(42));
        assertEquals(2, header.getShort(44));
        assertEquals(1, header.getShort(46));
        assertEquals(512, Short.toUnsignedInt(header.getShort(70)));
        assertEquals(16, header.getShort(72));
        assertEquals(352.0f, header.getFloat(108));
        assertEquals(2.0f, header.getFloat(112));
        assertEquals(-100.0f, header.getFloat(116));
        assertEquals(2, Byte.toUnsignedInt(header.get(123)));
        assertEquals(1, header.getShort(252));
        assertEquals(1, header.getShort(254));
        assertArrayEquals(new byte[] {'n', '+', '1', 0},
                java.util.Arrays.copyOfRange(bytes, 344, 348));
        assertArrayEquals(new byte[] {0, 0, 0, 0},
                java.util.Arrays.copyOfRange(bytes, 348, 352));
        assertArrayEquals(new byte[] {0, 0, 1, 0, (byte) 0xff, (byte) 0xff, 42, 0},
                java.util.Arrays.copyOfRange(bytes, 352, bytes.length));
    }

    @Test
    void writesAllApprovedScalarDatatypeMappingsAndRanges() throws IOException {
        assertDatatypeAndPayload(ScalarType.UINT8, 2, 8, new long[] {0, 255},
                new byte[] {0, (byte) 0xff});
        assertDatatypeAndPayload(ScalarType.INT8, 256, 8, new long[] {-128, 127},
                new byte[] {(byte) 0x80, 0x7f});
        assertDatatypeAndPayload(ScalarType.UINT16, 512, 16, new long[] {0, 65_535},
                new byte[] {0, 0, (byte) 0xff, (byte) 0xff});
        assertDatatypeAndPayload(ScalarType.INT16, 4, 16, new long[] {-32_768, 32_767},
                new byte[] {0, (byte) 0x80, (byte) 0xff, 0x7f});
    }

    @Test
    void preservesXFastestVoxelOrderWithoutFlipsOrReordering() throws IOException {
        Path output = temporaryDirectory.resolve("order.nii");
        long[] values = {1, 2, 3, 4, 5, 6, 7, 8};
        var geometry = new VolumeGeometry(2, 2, 2, 1.0, 1.0, 2.0,
                new double[] {10, 20, 30},
                new double[] {1, 0, 0}, new double[] {0, 1, 0}, new double[] {0, 0, 1},
                CoordinateSystem.DICOM_PATIENT_LPS);
        var voxels = new ImmutableVoxelData(2, 2, 2, ScalarType.UINT8, values);
        var volume = new ImageVolume(geometry, voxels, IntensityTransform.identityNotDeclared());

        new Nifti1VolumeWriter().write(volume, ras(volume), new OutputTarget(output.toString()));

        byte[] bytes = Files.readAllBytes(output);
        assertArrayEquals(new byte[] {1, 2, 3, 4, 5, 6, 7, 8},
                java.util.Arrays.copyOfRange(bytes, 352, bytes.length));
    }

    @Test
    void writesSformAsExactLpsToRasAffineWithinFloatPrecision() throws IOException {
        Path output = temporaryDirectory.resolve("affine.nii");
        var geometry = new VolumeGeometry(2, 2, 1, 0.75, 0.5, 2.0,
                new double[] {10, 20, 30},
                new double[] {1, 0, 0}, new double[] {0, 1, 0}, new double[] {0, 0, 1},
                CoordinateSystem.DICOM_PATIENT_LPS);
        var volume = new ImageVolume(geometry,
                new ImmutableVoxelData(2, 2, 1, ScalarType.UINT8, new long[] {1, 2, 3, 4}),
                IntensityTransform.identityNotDeclared());

        new Nifti1VolumeWriter().write(volume, ras(volume), new OutputTarget(output.toString()));

        ByteBuffer header = ByteBuffer.wrap(Files.readAllBytes(output)).order(ByteOrder.LITTLE_ENDIAN);
        assertArrayEquals(new float[] {-0.5f, 0, 0, -10},
                floats(header, 280, 4));
        assertArrayEquals(new float[] {0, -0.75f, 0, -20},
                floats(header, 296, 4));
        assertArrayEquals(new float[] {0, 0, 2.0f, 30},
                floats(header, 312, 4));
    }

    @Test
    void qformAndSformRepresentSameScannerGeometry() throws IOException {
        Path output = temporaryDirectory.resolve("qform.nii");
        var geometry = new VolumeGeometry(2, 2, 2, 0.8, 0.6, 2.5,
                new double[] {100, 200, -50},
                new double[] {0, 1, 0}, new double[] {-1, 0, 0}, new double[] {0, 0, 1},
                CoordinateSystem.DICOM_PATIENT_LPS);
        var volume = new ImageVolume(geometry,
                new ImmutableVoxelData(2, 2, 2, ScalarType.INT16,
                        new long[] {-1, 0, 1, 2, 3, 4, 5, 6}),
                IntensityTransform.identityNotDeclared());

        new Nifti1VolumeWriter().write(volume, ras(volume), new OutputTarget(output.toString()));

        ByteBuffer header = ByteBuffer.wrap(Files.readAllBytes(output)).order(ByteOrder.LITTLE_ENDIAN);
        double[][] qform = qformAffine(header);
        double[][] sform = sformAffine(header);
        for (int row = 0; row < 3; row++) {
            assertArrayEquals(sform[row], qform[row], 1.0e-5);
        }
    }

    @Test
    void writesExactSformAndDisablesQformForNonQuaternionAffine() throws IOException {
        Path output = temporaryDirectory.resolve("sform-only.nii");
        var volume = volume(ScalarType.UINT8, new long[] {1, 2, 3, 4},
                IntensityTransform.identityNotDeclared());
        var shearedRas = new org.cbihi.mrinormalizer.domain.model.AffineMatrix4(new double[][] {
                {0.5, 0.1, 0.0, -10.0},
                {0.0, 0.75, 0.0, -20.0},
                {0.0, 0.0, 2.0, 30.0},
                {0.0, 0.0, 0.0, 1.0}
        });

        new Nifti1VolumeWriter().write(volume, shearedRas, new OutputTarget(output.toString()));

        ByteBuffer header = ByteBuffer.wrap(Files.readAllBytes(output)).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0, header.getShort(252));
        assertEquals(1, header.getShort(254));
        assertArrayEquals(new float[] {0.5f, 0.1f, 0.0f, -10.0f}, floats(header, 280, 4));
        assertArrayEquals(new float[] {0.0f, 0.75f, 0.0f, -20.0f}, floats(header, 296, 4));
        assertArrayEquals(new float[] {0.0f, 0.0f, 2.0f, 30.0f}, floats(header, 312, 4));
    }

    @Test
    void writesUndeclaredIntensityTransformUsingNiftiNoScalingConvention() throws IOException {
        Path output = temporaryDirectory.resolve("noscale.nii");
        var volume = volume(ScalarType.UINT8, new long[] {1, 2, 3, 4},
                IntensityTransform.identityNotDeclared());
        new Nifti1VolumeWriter().write(volume, ras(volume), new OutputTarget(output.toString()));

        ByteBuffer header = ByteBuffer.wrap(Files.readAllBytes(output)).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0.0f, header.getFloat(112));
        assertEquals(0.0f, header.getFloat(116));
    }

    @Test
    void writesGzipWrappedNiftiWithIdenticalUncompressedBytes() throws IOException {
        Path plain = temporaryDirectory.resolve("plain.nii");
        Path gzip = temporaryDirectory.resolve("compressed.nii.gz");
        var volume = volume(ScalarType.INT16, new long[] {-2, -1, 0, 1},
                IntensityTransform.identityNotDeclared());
        var writer = new Nifti1VolumeWriter();

        writer.write(volume, ras(volume), new OutputTarget(plain.toString()));
        writer.write(volume, ras(volume), new OutputTarget(gzip.toString()));

        byte[] compressed = Files.readAllBytes(gzip);
        assertEquals(0x1f, Byte.toUnsignedInt(compressed[0]));
        assertEquals(0x8b, Byte.toUnsignedInt(compressed[1]));
        try (var input = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            assertArrayEquals(Files.readAllBytes(plain), input.readAllBytes());
        }
    }

    @Test
    void rejectsWrongExtensionAndMissingExplicitRasAffine() {
        var volume = volume(ScalarType.UINT8, new long[] {1, 2, 3, 4},
                IntensityTransform.identityNotDeclared());
        assertThrowsExactly(IllegalArgumentException.class,
                () -> new Nifti1VolumeWriter().write(volume, ras(volume),
                        new OutputTarget(temporaryDirectory.resolve("wrong.img").toString())));
        assertThrowsExactly(IllegalArgumentException.class,
                () -> new Nifti1VolumeWriter().write(volume, null,
                        new OutputTarget(temporaryDirectory.resolve("missing-affine.nii").toString())));
    }

    @Test
    void rejectsVoxelValuesOutsideDeclaredScalarRange() {
        var volume = volume(ScalarType.UINT8, new long[] {0, 1, 2, 256},
                IntensityTransform.identityNotDeclared());
        assertThrowsExactly(IllegalArgumentException.class,
                () -> new Nifti1VolumeWriter().write(volume, ras(volume),
                        new OutputTarget(temporaryDirectory.resolve("range.nii").toString())));
    }

    @Test
    void writtenOutputIsRecognizedByExistingNiftiDetector() throws IOException {
        Path output = temporaryDirectory.resolve("detected.nii");
        var volume = volume(ScalarType.UINT16, new long[] {1, 2, 3, 4},
                IntensityTransform.identityNotDeclared());
        new Nifti1VolumeWriter().write(volume, ras(volume), new OutputTarget(output.toString()));

        var result = new org.cbihi.mrinormalizer.infrastructure.detection.NiftiFormatProbe()
                .probe(new org.cbihi.mrinormalizer.domain.model.InputSource(output.toString()));
        assertEquals(org.cbihi.mrinormalizer.domain.model.DetectionOutcome.NIFTI, result.outcome());
    }

    private org.cbihi.mrinormalizer.domain.model.AffineMatrix4 ras(ImageVolume volume) {
        return NiftiAffineMapper.toNiftiRas(volume.geometry());
    }

    private ImageVolume volume(ScalarType type, long[] values, IntensityTransform transform) {
        var geometry = new VolumeGeometry(2, 2, 1, 0.75, 0.5, 2.0,
                new double[] {10, 20, 30},
                new double[] {1, 0, 0}, new double[] {0, 1, 0}, new double[] {0, 0, 1},
                CoordinateSystem.DICOM_PATIENT_LPS);
        return new ImageVolume(geometry, new ImmutableVoxelData(2, 2, 1, type, values), transform);
    }

    private void assertDatatypeAndPayload(ScalarType type, int datatype, int bitpix,
                                          long[] values, byte[] payload) throws IOException {
        Path output = temporaryDirectory.resolve(type.name().toLowerCase() + ".nii");
        var geometry = new VolumeGeometry(2, 1, 1, 1, 1, 1,
                new double[] {0, 0, 0},
                new double[] {1, 0, 0}, new double[] {0, 1, 0}, new double[] {0, 0, 1},
                CoordinateSystem.DICOM_PATIENT_LPS);
        var volume = new ImageVolume(geometry,
                new ImmutableVoxelData(2, 1, 1, type, values),
                IntensityTransform.identityNotDeclared());

        new Nifti1VolumeWriter().write(volume, ras(volume), new OutputTarget(output.toString()));

        byte[] bytes = Files.readAllBytes(output);
        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(datatype, Short.toUnsignedInt(header.getShort(70)));
        assertEquals(bitpix, header.getShort(72));
        assertArrayEquals(payload, java.util.Arrays.copyOfRange(bytes, 352, bytes.length));
    }

    private float[] floats(ByteBuffer buffer, int offset, int count) {
        float[] values = new float[count];
        for (int index = 0; index < count; index++) {
            values[index] = buffer.getFloat(offset + index * Float.BYTES);
        }
        return values;
    }

    private double[][] sformAffine(ByteBuffer header) {
        double[][] affine = new double[3][4];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 4; column++) {
                affine[row][column] = header.getFloat(280 + row * 16 + column * 4);
            }
        }
        return affine;
    }

    private double[][] qformAffine(ByteBuffer header) {
        double b = header.getFloat(256);
        double c = header.getFloat(260);
        double d = header.getFloat(264);
        double a = Math.sqrt(Math.max(0.0, 1.0 - b * b - c * c - d * d));
        double[][] rotation = {
                {a * a + b * b - c * c - d * d, 2 * b * c - 2 * a * d, 2 * b * d + 2 * a * c},
                {2 * b * c + 2 * a * d, a * a + c * c - b * b - d * d, 2 * c * d - 2 * a * b},
                {2 * b * d - 2 * a * c, 2 * c * d + 2 * a * b, a * a + d * d - c * c - b * b}
        };
        double qfac = header.getFloat(76) < 0 ? -1.0 : 1.0;
        double dx = header.getFloat(80);
        double dy = header.getFloat(84);
        double dz = header.getFloat(88);
        double[][] affine = new double[3][4];
        for (int row = 0; row < 3; row++) {
            affine[row][0] = rotation[row][0] * dx;
            affine[row][1] = rotation[row][1] * dy;
            affine[row][2] = rotation[row][2] * qfac * dz;
        }
        affine[0][3] = header.getFloat(268);
        affine[1][3] = header.getFloat(272);
        affine[2][3] = header.getFloat(276);
        return affine;
    }
}
