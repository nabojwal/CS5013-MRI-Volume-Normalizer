package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.cbihi.mrinormalizer.application.request.DicomToNiftiRequest;
import org.cbihi.mrinormalizer.application.service.DefaultDicomSeriesService;
import org.cbihi.mrinormalizer.application.service.DefaultDicomToNiftiService;
import org.cbihi.mrinormalizer.domain.error.DicomProcessingError;
import org.cbihi.mrinormalizer.domain.model.GeometryValidationPolicy;
import org.cbihi.mrinormalizer.domain.model.InputSource;
import org.cbihi.mrinormalizer.domain.model.OutputTarget;
import org.cbihi.mrinormalizer.infrastructure.dicom.Dcm4cheInstanceReader;
import org.cbihi.mrinormalizer.infrastructure.nifti.Nifti1VolumeWriter;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DicomToNiftiServiceTest {

    private static final String STUDY = "2.25.501";
    private static final String SERIES = "2.25.502";
    private static final String FRAME = "2.25.503";

    @TempDir
    Path temporaryDirectory;

    @Test
    void convertsDicomSeriesToNiftiPreservingPixelsGeometryAndScaling() throws IOException {
        Path first = dicom("first.dcm", spec("2.25.510", 30.0, new long[] {1, 2, 3, 4})
                .rescale(2.5, -100.0));
        Path second = dicom("second.dcm", spec("2.25.511", 32.0, new long[] {5, 6, 7, 8})
                .rescale(2.5, -100.0));
        Path output = temporaryDirectory.resolve("converted.nii");

        var result = service().convert(request(output, first, second));

        assertTrue(result.successful(), result.errors().toString());
        assertEquals(output.toString(), result.output().reference());
        assertEquals(2, result.provenance().acceptedSlices());
        assertTrue(result.provenance().inputFingerprint().matches("[0-9a-f]{64}"));

        byte[] bytes = Files.readAllBytes(output);
        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(348, header.getInt(0));
        assertEquals(3, header.getShort(40));
        assertEquals(2, header.getShort(42));
        assertEquals(2, header.getShort(44));
        assertEquals(2, header.getShort(46));
        assertEquals(512, Short.toUnsignedInt(header.getShort(70)));
        assertEquals(16, header.getShort(72));
        assertEquals(0.5f, header.getFloat(80), 1.0e-6f);
        assertEquals(0.75f, header.getFloat(84), 1.0e-6f);
        assertEquals(2.0f, header.getFloat(88), 1.0e-6f);
        assertEquals(2.5f, header.getFloat(112), 1.0e-6f);
        assertEquals(-100.0f, header.getFloat(116), 1.0e-6f);
        assertEquals(1, header.getShort(254));

        assertSrow(header, 280, new float[] {-0.5f, 0.0f, 0.0f, -10.0f});
        assertSrow(header, 296, new float[] {0.0f, -0.75f, 0.0f, -20.0f});
        assertSrow(header, 312, new float[] {0.0f, 0.0f, 2.0f, 30.0f});

        long[] expected = {1, 2, 3, 4, 5, 6, 7, 8};
        ByteBuffer voxels = ByteBuffer.wrap(bytes, 352, expected.length * Short.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (long value : expected) {
            assertEquals(value, Short.toUnsignedInt(voxels.getShort()));
        }
    }

    @Test
    void writesGzipWrappedNiftiThroughTheSameUseCase() throws IOException {
        Path input = dicom("gzip.dcm", spec("2.25.520", 30.0, new long[] {10, 20, 30, 40})
                .singleSliceSpacing("3.5"));
        Path output = temporaryDirectory.resolve("converted.nii.gz");

        var result = service().convert(request(output, input));

        assertTrue(result.successful(), result.errors().toString());
        byte[] bytes;
        try (var gzip = new GZIPInputStream(Files.newInputStream(output))) {
            bytes = gzip.readAllBytes();
        }
        assertEquals(352 + 4 * Short.BYTES, bytes.length);
        assertArrayEquals("n+1\0".getBytes(StandardCharsets.ISO_8859_1), Arrays.copyOfRange(bytes, 344, 348));
        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(3.5f, header.getFloat(88), 1.0e-6f);
    }

    @Test
    void outputIsInvariantToDicomInputOrder() throws IOException {
        Path first = dicom("order-first.dcm", spec("2.25.530", 30.0, new long[] {1, 1, 1, 1}));
        Path second = dicom("order-second.dcm", spec("2.25.531", 32.0, new long[] {2, 2, 2, 2}));
        Path forward = temporaryDirectory.resolve("forward.nii");
        Path reverse = temporaryDirectory.resolve("reverse.nii");

        assertTrue(service().convert(request(forward, first, second)).successful());
        assertTrue(service().convert(request(reverse, second, first)).successful());

        assertArrayEquals(Files.readAllBytes(forward), Files.readAllBytes(reverse));
    }

    @Test
    void propagatesReconstructionFailureWithoutCreatingOutput() throws IOException {
        Path input = dicom("wrong-series.dcm", spec("2.25.540", 30.0, new long[] {1, 2, 3, 4}));
        Path output = temporaryDirectory.resolve("should-not-exist.nii");
        var badRequest = new DicomToNiftiRequest(List.of(new InputSource(input.toString())),
                "2.25.999999", new OutputTarget(output.toString()));

        var result = service().convert(badRequest);

        assertFalse(result.successful());
        assertEquals(List.of(DicomProcessingError.SERIES_NOT_FOUND), result.errors());
        assertFalse(Files.exists(output));
    }

    @Test
    void preservesSignedStoredValuesWithoutNumericChange() throws IOException {
        Path input = dicom("signed.dcm", spec("2.25.550", 30.0,
                new long[] {-32768, -1, 0, 32767}).signed().singleSliceSpacing("2.0"));
        Path output = temporaryDirectory.resolve("signed.nii");

        var result = service().convert(request(output, input));

        assertTrue(result.successful(), result.errors().toString());
        byte[] bytes = Files.readAllBytes(output);
        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(4, Short.toUnsignedInt(header.getShort(70)));
        assertEquals(16, header.getShort(72));
        ByteBuffer voxels = ByteBuffer.wrap(bytes, 352, 8).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(Short.MIN_VALUE, voxels.getShort());
        assertEquals(-1, voxels.getShort());
        assertEquals(0, voxels.getShort());
        assertEquals(Short.MAX_VALUE, voxels.getShort());
    }

    private DefaultDicomToNiftiService service() {
        var reconstruction = new DefaultDicomSeriesService(
                new Dcm4cheInstanceReader(), GeometryValidationPolicy.defaults());
        return new DefaultDicomToNiftiService(reconstruction, new Nifti1VolumeWriter());
    }

    private DicomToNiftiRequest request(Path output, Path... inputs) {
        return new DicomToNiftiRequest(Arrays.stream(inputs)
                .map(path -> new InputSource(path.toString())).toList(), SERIES, new OutputTarget(output.toString()));
    }

    private void assertSrow(ByteBuffer header, int offset, float[] expected) {
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index], header.getFloat(offset + index * Float.BYTES), 1.0e-6f);
        }
    }

    private Path dicom(String name, Spec spec) throws IOException {
        Attributes dataset = new Attributes();
        dataset.setString(Tag.SOPClassUID, VR.UI, UID.MRImageStorage);
        dataset.setString(Tag.SOPInstanceUID, VR.UI, spec.sop);
        dataset.setString(Tag.StudyInstanceUID, VR.UI, STUDY);
        dataset.setString(Tag.SeriesInstanceUID, VR.UI, SERIES);
        dataset.setString(Tag.FrameOfReferenceUID, VR.UI, FRAME);
        dataset.setString(Tag.Modality, VR.CS, "MR");
        dataset.setInt(Tag.Rows, VR.US, 2);
        dataset.setInt(Tag.Columns, VR.US, 2);
        dataset.setString(Tag.PixelSpacing, VR.DS, "0.75", "0.5");
        dataset.setString(Tag.ImageOrientationPatient, VR.DS, "1", "0", "0", "0", "1", "0");
        dataset.setString(Tag.ImagePositionPatient, VR.DS, "10", "20", Double.toString(spec.z));
        if (spec.spacingBetweenSlices != null) {
            dataset.setString(Tag.SpacingBetweenSlices, VR.DS, spec.spacingBetweenSlices);
        }
        dataset.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
        dataset.setInt(Tag.SamplesPerPixel, VR.US, 1);
        dataset.setInt(Tag.BitsAllocated, VR.US, 16);
        dataset.setInt(Tag.BitsStored, VR.US, 16);
        dataset.setInt(Tag.HighBit, VR.US, 15);
        dataset.setInt(Tag.PixelRepresentation, VR.US, spec.signed ? 1 : 0);
        if (spec.rescale) {
            dataset.setDouble(Tag.RescaleSlope, VR.DS, spec.slope);
            dataset.setDouble(Tag.RescaleIntercept, VR.DS, spec.intercept);
        }
        dataset.setBytes(Tag.PixelData, VR.OW, pixelBytes(spec.values));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DicomOutputStream stream = new DicomOutputStream(output, UID.ExplicitVRLittleEndian)) {
            stream.writeFileMetaInformation(dataset.createFileMetaInformation(UID.ExplicitVRLittleEndian));
            stream.writeDataset(null, dataset);
        }
        Path path = temporaryDirectory.resolve(name);
        Files.write(path, output.toByteArray());
        return path;
    }

    private byte[] pixelBytes(long[] values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (long value : values) {
            buffer.putShort((short) (value & 0xffff));
        }
        return buffer.array();
    }

    private Spec spec(String sop, double z, long[] values) {
        return new Spec(sop, z, values);
    }

    private static final class Spec {
        private final String sop;
        private final double z;
        private final long[] values;
        private boolean signed;
        private boolean rescale;
        private double slope;
        private double intercept;
        private String spacingBetweenSlices = "2.0";

        private Spec(String sop, double z, long[] values) {
            this.sop = sop;
            this.z = z;
            this.values = values;
        }

        private Spec signed() {
            signed = true;
            return this;
        }

        private Spec rescale(double slope, double intercept) {
            rescale = true;
            this.slope = slope;
            this.intercept = intercept;
            return this;
        }

        private Spec singleSliceSpacing(String value) {
            spacingBetweenSlices = value;
            return this;
        }
    }
}
