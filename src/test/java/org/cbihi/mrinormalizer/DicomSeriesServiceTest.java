package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.cbihi.mrinormalizer.application.request.DicomSeriesRequest;
import org.cbihi.mrinormalizer.application.service.DefaultDicomSeriesService;
import org.cbihi.mrinormalizer.domain.error.DicomProcessingError;
import org.cbihi.mrinormalizer.domain.model.GeometryValidationPolicy;
import org.cbihi.mrinormalizer.domain.model.InputSource;
import org.cbihi.mrinormalizer.infrastructure.dicom.Dcm4cheInstanceReader;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class DicomSeriesServiceTest {

    private static final String STUDY = "2.25.101";
    private static final String SERIES = "2.25.102";
    private static final String FRAME = "2.25.104";

    @TempDir
    Path temporaryDirectory;

    @Test
    void reconstructsSingleSliceWithRawUnsignedValuesAndRescaleMetadata() throws IOException {
        Path input = dicom("one.dcm", spec(0, new long[] {0, 65535, 17, 23}).allocated(16).stored(16)
                .rescale(2.5, -100.0));

        var result = service().process(request(SERIES, input));

        assertTrue(result.successful());
        assertEquals(2, result.volume().geometry().width());
        assertEquals(2, result.volume().geometry().height());
        assertEquals(1, result.volume().geometry().depth());
        assertEquals(65535L, result.volume().voxels().rawValueAt(1, 0, 0));
        assertTrue(result.volume().rescaleTransform().declared());
        assertEquals(2.5, result.volume().rescaleTransform().slope());
        assertFalse(result.provenance().inputFingerprint().contains(input.toString()));
    }

    @Test
    void ordersShuffledSlicesByProjectedPatientPositionAndPlacesVoxels() throws IOException {
        Path high = dicom("high.dcm", spec(10, new long[] {30, 31, 32, 33}));
        Path low = dicom("low.dcm", spec(0, new long[] {10, 11, 12, 13}));
        Path middle = dicom("middle.dcm", spec(5, new long[] {20, 21, 22, 23}));

        var result = service().process(request(SERIES, high, low, middle));

        assertTrue(result.successful());
        assertEquals(10L, result.volume().voxels().rawValueAt(0, 0, 0));
        assertEquals(20L, result.volume().voxels().rawValueAt(0, 0, 1));
        assertEquals(30L, result.volume().voxels().rawValueAt(0, 0, 2));
        assertEquals(5.0, result.volume().geometry().sliceSpacing());
    }

    @Test
    void preservesSignedStoredValuesAndBitsStoredSemantics() throws IOException {
        Path input = dicom("signed.dcm", spec(0, new long[] {-2048, -1, 0, 2047}).signed().allocated(16).stored(12));

        var result = service().process(request(SERIES, input));

        assertTrue(result.successful());
        assertEquals(-2048L, result.volume().voxels().rawValueAt(0, 0, 0));
        assertEquals(-1L, result.volume().voxels().rawValueAt(1, 0, 0));
        assertEquals(12, result.volume().voxels().encoding().bitsStored());
        assertEquals(11, result.volume().voxels().encoding().highBit());
    }

    @Test
    void requiresExplicitSeriesSelectionAndReportsUnknownSeries() throws IOException {
        Path input = dicom("one.dcm", spec(0, new long[] {1, 2, 3, 4}));

        assertError(service().process(new DicomSeriesRequest(List.of(new InputSource(input.toString())), " ")),
                DicomProcessingError.SERIES_NOT_SELECTED);
        assertError(service().process(request("2.25.999", input)), DicomProcessingError.SERIES_NOT_FOUND);
    }

    @Test
    void acceptsMultipleSuppliedSeriesOnlyWhenRequestedSeriesIsExplicit() throws IOException {
        Path selected = dicom("selected.dcm", spec(0, new long[] {1, 2, 3, 4}).anatomy("BIPED"));
        Path other = dicom("other.dcm", spec(0, new long[] {9, 9, 9, 9}).series("2.25.103")
                .frame("2.25.999").anatomy("QUADRUPED"));
        Path missingFrame = dicom("unselected-missing-frame.dcm", spec(5, new long[] {9, 9, 9, 9})
                .series("2.25.103").frame(null));

        var result = service().process(request(SERIES, other, selected, missingFrame));

        assertTrue(result.successful());
        assertEquals(1L, result.volume().voxels().rawValueAt(0, 0, 0));
    }

    @Test
    void rejectsMixedStudyDuplicateSopAndIncompatibleDimensions() throws IOException {
        Path first = dicom("first.dcm", spec(0, new long[] {1, 2, 3, 4}).sop("2.25.201"));
        Path mixedStudy = dicom("study.dcm", spec(5, new long[] {5, 6, 7, 8}).study("2.25.999"));
        assertError(service().process(request(SERIES, first, mixedStudy)), DicomProcessingError.MIXED_STUDY);

        Path duplicateSop = dicom("duplicate.dcm", spec(5, new long[] {5, 6, 7, 8}).sop("2.25.201"));
        assertError(service().process(request(SERIES, first, duplicateSop)), DicomProcessingError.DUPLICATE_SOP_INSTANCE);

        Path dimensions = dicom("dimensions.dcm", spec(5, new long[] {1, 2, 3}).dimensions(1, 3));
        assertError(service().process(request(SERIES, first, dimensions)), DicomProcessingError.INCOMPATIBLE_INSTANCE);
    }

    @Test
    void rejectsIncompatibleSpacingOrientationDuplicateAndIrregularGeometry() throws IOException {
        Path first = dicom("first.dcm", spec(0, new long[] {1, 2, 3, 4}));
        Path spacing = dicom("spacing.dcm", spec(5, new long[] {5, 6, 7, 8}).spacing(1.1, 1.0));
        assertError(service().process(request(SERIES, first, spacing)), DicomProcessingError.INCOMPATIBLE_INSTANCE);

        Path orientation = dicom("orientation.dcm", spec(5, new long[] {5, 6, 7, 8})
                .orientation(new double[] {0, 1, 0, -1, 0, 0}));
        assertError(service().process(request(SERIES, first, orientation)), DicomProcessingError.INCOMPATIBLE_INSTANCE);

        Path duplicate = dicom("duplicate.dcm", spec(0, new long[] {5, 6, 7, 8}));
        assertError(service().process(request(SERIES, first, duplicate)), DicomProcessingError.DUPLICATE_SLICE);

        Path second = dicom("second.dcm", spec(5, new long[] {5, 6, 7, 8}));
        Path irregular = dicom("irregular.dcm", spec(11, new long[] {9, 10, 11, 12}));
        assertError(service().process(request(SERIES, first, second, irregular)), DicomProcessingError.IRREGULAR_SPACING);
    }

    @Test
    void rejectsMissingSpatialMetadataUnsupportedObjectsAndUnsupportedPixels() throws IOException {
        assertError(service().process(request(SERIES, dicom("orientation.dcm", spec(0, new long[] {1, 2, 3, 4}).without(Tag.ImageOrientationPatient)))),
                DicomProcessingError.MISSING_REQUIRED_METADATA);
        assertError(service().process(request(SERIES, dicom("position.dcm", spec(0, new long[] {1, 2, 3, 4}).without(Tag.ImagePositionPatient)))),
                DicomProcessingError.MISSING_REQUIRED_METADATA);
        assertError(service().process(request(SERIES, dicom("ct.dcm", spec(0, new long[] {1, 2, 3, 4}).sopClass(UID.CTImageStorage).modality("CT")))),
                DicomProcessingError.UNSUPPORTED_OBJECT_TYPE);
        assertError(service().process(request(SERIES, dicom("color.dcm", spec(0, new long[] {1, 2, 3, 4}).photometric("RGB").samples(3)))),
                DicomProcessingError.UNSUPPORTED_PIXEL_REPRESENTATION);
    }

    @Test
    void mapsEmptyMissingNonDicomAndCorruptInputsToStableErrors() throws IOException {
        assertError(service().process(new DicomSeriesRequest(List.of(), SERIES)), DicomProcessingError.EMPTY_INPUT);
        Path empty = temporaryDirectory.resolve("empty.dcm");
        Files.write(empty, new byte[0]);
        assertError(service().process(request(SERIES, empty)), DicomProcessingError.EMPTY_INPUT);
        assertError(service().process(request(SERIES, temporaryDirectory.resolve("missing.dcm"))), DicomProcessingError.INPUT_NOT_FOUND);
        Path random = temporaryDirectory.resolve("random.bin");
        Files.write(random, new byte[] {1, 2, 3, 4});
        assertError(service().process(request(SERIES, random)), DicomProcessingError.NOT_DICOM);
        Path corrupt = temporaryDirectory.resolve("corrupt.dcm");
        byte[] bytes = new byte[132]; bytes[128] = 'D'; bytes[129] = 'I'; bytes[130] = 'C'; bytes[131] = 'M';
        Files.write(corrupt, bytes);
        assertError(service().process(request(SERIES, corrupt)), DicomProcessingError.DICOM_PARSE_FAILED);
    }

    @Test
    void supportsAllApprovedUncompressedTransferSyntaxes() throws IOException {
        for (String syntax : List.of(UID.ImplicitVRLittleEndian, UID.ExplicitVRLittleEndian, UID.ExplicitVRBigEndian)) {
            Path input = dicom("syntax-" + syntax.hashCode() + ".dcm", spec(0, new long[] {1, 2, 3, 4}).syntax(syntax));
            var result = service().process(request(SERIES, input));
            assertTrue(result.successful(), syntax);
            assertEquals(4L, result.volume().voxels().rawValueAt(1, 1, 0));
        }
    }

    @Test
    void rejectsUnsupportedDeflatedTransferSyntax() throws IOException {
        Path input = dicom("deflated.dcm", spec(0, new long[] {1, 2, 3, 4}).syntax(UID.DeflatedExplicitVRLittleEndian));

        assertError(service().process(request(SERIES, input)), DicomProcessingError.UNSUPPORTED_TRANSFER_SYNTAX);
    }

    @Test
    void ordersSmallOrientationVariationsUsingOneCommonNormal() throws IOException {
        Path[] slices = slightlyTiltedStack(0.005);

        var result = service().process(request(SERIES, slices[1], slices[2], slices[0]));

        assertTrue(result.successful(), result.errors().toString());
        assertSliceValues(result.volume(), 10, 20, 30);
        assertArrayEquals(new double[] {200, 0, 0}, result.volume().geometry().origin(), 1e-12);
        assertArrayEquals(new double[] {0, 0, 1}, result.volume().geometry().sliceDirection(), 1e-12);
        assertEquals(0.005, result.volume().geometry().sliceSpacing(), 1e-12);
    }

    @Test
    void geometryAndVoxelsAreInvariantUnderInputPermutation() throws IOException {
        Path[] slices = slightlyTiltedStack(20.0);
        var baseline = service().process(request(SERIES, slices[1], slices[0], slices[2]));
        assertTrue(baseline.successful(), baseline.errors().toString());

        int[][] permutations = {{0, 1, 2}, {0, 2, 1}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
        for (int[] order : permutations) {
            var result = service().process(request(SERIES, slices[order[0]], slices[order[1]], slices[order[2]]));
            assertTrue(result.successful(), java.util.Arrays.toString(order) + ": " + result.errors());
            assertSliceValues(result.volume(), 10, 20, 30);
            assertArrayEquals(baseline.volume().geometry().origin(), result.volume().geometry().origin(), 0.0);
            assertArrayEquals(baseline.volume().geometry().sliceDirection(), result.volume().geometry().sliceDirection(), 0.0);
            assertArrayEquals(baseline.volume().geometry().columnIndexDirection(), result.volume().geometry().columnIndexDirection(), 0.0);
            assertArrayEquals(baseline.volume().geometry().rowIndexDirection(), result.volume().geometry().rowIndexDirection(), 0.0);
            assertEquals(baseline.volume().geometry().sliceSpacing(), result.volume().geometry().sliceSpacing());
        }
        assertSliceValues(baseline.volume(), 10, 20, 30);
        assertArrayEquals(new double[] {200, 0, 0}, baseline.volume().geometry().origin(), 1e-12);
        assertArrayEquals(new double[] {0, 0, 1}, baseline.volume().geometry().sliceDirection(), 1e-12);
        assertEquals(20.0, baseline.volume().geometry().sliceSpacing(), 1e-12);
    }

    @Test
    void rejectsCumulativeDriftDespiteLocallyRegularGaps() throws IOException {
        double[] positions = {0.0, 1.0, 2.0009, 3.0018};
        double tolerance = GeometryValidationPolicy.defaults().sliceSpacingToleranceMm();
        Path[] slices = new Path[positions.length];
        for (int i = 0; i < positions.length; i++) {
            if (i > 0) {
                assertTrue(Math.abs(positions[i] - positions[i - 1] - 1.0) <= tolerance);
            }
            slices[i] = dicom("drift-" + i + ".dcm", spec(positions[i], new long[] {i, i, i, i}));
        }

        assertError(service().process(request(SERIES, slices)), DicomProcessingError.IRREGULAR_SPACING);
    }

    @Test
    void acceptsRegularGridWithPositionNoiseWithinTolerance() throws IOException {
        double[] positions = {0.0, 1.0, 2.0004, 2.9996};
        Path[] slices = new Path[positions.length];
        for (int i = 0; i < positions.length; i++) {
            slices[i] = dicom("noise-" + i + ".dcm", spec(positions[i], new long[] {i, i, i, i}));
        }

        var result = service().process(request(SERIES, slices[3], slices[1], slices[0], slices[2]));

        assertTrue(result.successful(), result.errors().toString());
        assertSliceValues(result.volume(), 0, 1, 2, 3);
        assertEquals(1.0, result.volume().geometry().sliceSpacing(), 1e-12);
        assertArrayEquals(new double[] {0, 0, 0}, result.volume().geometry().origin(), 1e-12);
        for (int i = 0; i < positions.length; i++) {
            double expected = result.volume().geometry().origin()[2] + i * result.volume().geometry().sliceSpacing();
            assertTrue(Math.abs(positions[i] - expected) <= GeometryValidationPolicy.defaults().positionToleranceMm());
        }
    }

    @Test
    void acceptsConsistentFrameOfReferenceUid() throws IOException {
        assertTrue(profileResult(new String[] {FRAME, FRAME, FRAME}, new String[] {null, null, null}).successful());
    }

    @Test
    void rejectsMissingFrameOfReferenceUid() throws IOException {
        assertError(profileResult(new String[] {FRAME, null, FRAME}, new String[] {null, null, null}),
                DicomProcessingError.MISSING_REQUIRED_METADATA);
    }

    @Test
    void rejectsBlankFrameOfReferenceUid() throws IOException {
        for (String blank : new String[] {"", "   "}) {
            assertError(profileResult(new String[] {FRAME, blank, FRAME}, new String[] {null, null, null}),
                    DicomProcessingError.MISSING_REQUIRED_METADATA);
        }
    }

    @Test
    void rejectsMixedFrameOfReferenceUids() throws IOException {
        assertError(profileResult(new String[] {FRAME, FRAME, "2.25.999"}, new String[] {null, null, null}),
                DicomProcessingError.INCOMPATIBLE_INSTANCE);
    }

    @Test
    void acceptsExplicitBipedOrientation() throws IOException {
        assertTrue(profileResult(new String[] {FRAME, FRAME, FRAME},
                new String[] {"BIPED", "BIPED", "BIPED"}).successful());
    }

    @Test
    void acceptsMissingAnatomicalOrientationTypeAsBiped() throws IOException {
        assertTrue(profileResult(new String[] {FRAME, FRAME, FRAME},
                new String[] {"BIPED", null, "BIPED"}).successful());
    }

    @Test
    void acceptsBlankAnatomicalOrientationTypeAsBiped() throws IOException {
        assertTrue(profileResult(new String[] {FRAME, FRAME, FRAME},
                new String[] {"", "   ", "BIPED"}).successful());
    }

    @Test
    void rejectsQuadrupedOrientation() throws IOException {
        assertError(profileResult(new String[] {FRAME, FRAME, FRAME},
                new String[] {"QUADRUPED", "QUADRUPED", "QUADRUPED"}), DicomProcessingError.UNSUPPORTED_OBJECT_TYPE);
    }

    @Test
    void rejectsMixedBipedAndQuadrupedOrientations() throws IOException {
        assertError(profileResult(new String[] {FRAME, FRAME, FRAME},
                new String[] {"BIPED", "QUADRUPED", null}), DicomProcessingError.UNSUPPORTED_OBJECT_TYPE);
    }

    @Test
    void rejectsUnknownAnatomicalOrientationType() throws IOException {
        for (String unsupported : new String[] {"UNKNOWN", "biped"}) {
            assertError(profileResult(new String[] {FRAME, FRAME, FRAME},
                    new String[] {"BIPED", unsupported, null}), DicomProcessingError.UNSUPPORTED_OBJECT_TYPE);
        }
    }

    private org.cbihi.mrinormalizer.application.result.DicomProcessingResult profileResult(
            String[] frames, String[] anatomies) throws IOException {
        Path[] paths = new Path[3];
        for (int i = 0; i < paths.length; i++) {
            paths[i] = dicom("profile-" + i + ".dcm", spec(i * 5, new long[] {i, i, i, i})
                    .sop("2.25.80" + i).frame(frames[i]).anatomy(anatomies[i]));
            var decoded = new Dcm4cheInstanceReader().read(new InputSource(paths[i].toString()));
            // DICOM padding/empty values may be normalized by dcm4che; absent stays null.
            if (frames[i] == null || !frames[i].isBlank()) {
                assertEquals(frames[i], decoded.frameOfReferenceUid());
            }
            if (anatomies[i] == null || !anatomies[i].isBlank()) {
                assertEquals(anatomies[i], decoded.anatomicalOrientationType());
            }
        }
        return service().process(request(SERIES, paths));
    }

    private Path[] slightlyTiltedStack(double spacing) throws IOException {
        // All directions differ by less than 1e-4. At x=200, their own normals shift
        // projections by about +/-0.008 mm, reversing the 0.005 mm stack's order.
        double tilt = 0.00004;
        Path low = dicom("tilted-low.dcm", spec(0, new long[] {10, 10, 10, 10})
                .sop("2.25.302").position(200, 0, 0)
                .orientation(new double[] {Math.cos(tilt), 0, -Math.sin(tilt), 0, 1, 0}));
        // The middle slice has the lexically smallest SOP UID and defines the reference axes.
        Path middle = dicom("tilted-middle.dcm", spec(spacing, new long[] {20, 20, 20, 20})
                .sop("2.25.301").position(200, 0, spacing));
        Path high = dicom("tilted-high.dcm", spec(2 * spacing, new long[] {30, 30, 30, 30})
                .sop("2.25.303").position(200, 0, 2 * spacing)
                .orientation(new double[] {Math.cos(tilt), 0, Math.sin(tilt), 0, 1, 0}));
        return new Path[] {low, middle, high};
    }

    private void assertSliceValues(org.cbihi.mrinormalizer.domain.model.NativeVolume volume, long... expected) {
        assertEquals(expected.length, volume.voxels().depth());
        for (int z = 0; z < expected.length; z++) {
            for (int y = 0; y < volume.voxels().height(); y++) {
                for (int x = 0; x < volume.voxels().width(); x++) {
                    assertEquals(expected[z], volume.voxels().rawValueAt(x, y, z));
                }
            }
        }
    }

    private DefaultDicomSeriesService service() {
        return new DefaultDicomSeriesService(new Dcm4cheInstanceReader(), GeometryValidationPolicy.defaults());
    }

    private DicomSeriesRequest request(String selectedSeries, Path... paths) {
        return new DicomSeriesRequest(java.util.Arrays.stream(paths).map(path -> new InputSource(path.toString())).toList(), selectedSeries);
    }

    private void assertError(org.cbihi.mrinormalizer.application.result.DicomProcessingResult result,
                             DicomProcessingError expected) {
        assertFalse(result.successful());
        assertEquals(List.of(expected), result.errors());
    }

    private Path dicom(String name, Spec spec) throws IOException {
        Attributes dataset = new Attributes();
        dataset.setString(Tag.SOPClassUID, VR.UI, spec.sopClass);
        dataset.setString(Tag.SOPInstanceUID, VR.UI, spec.sop);
        dataset.setString(Tag.StudyInstanceUID, VR.UI, spec.study);
        dataset.setString(Tag.SeriesInstanceUID, VR.UI, spec.series);
        if (spec.frame != null) dataset.setString(Tag.FrameOfReferenceUID, VR.UI, spec.frame);
        if (spec.anatomy != null) dataset.setString(Tag.AnatomicalOrientationType, VR.CS, spec.anatomy);
        dataset.setString(Tag.Modality, VR.CS, spec.modality);
        dataset.setInt(Tag.Rows, VR.US, spec.rows);
        dataset.setInt(Tag.Columns, VR.US, spec.columns);
        dataset.setString(Tag.PixelSpacing, VR.DS, decimalStrings(spec.spacing));
        if (!spec.removed.contains(Tag.ImageOrientationPatient)) dataset.setString(Tag.ImageOrientationPatient, VR.DS, decimalStrings(spec.orientation));
        if (!spec.removed.contains(Tag.ImagePositionPatient)) dataset.setString(Tag.ImagePositionPatient, VR.DS, decimalStrings(spec.position));
        dataset.setString(Tag.PhotometricInterpretation, VR.CS, spec.photometric);
        dataset.setInt(Tag.SamplesPerPixel, VR.US, spec.samples);
        dataset.setInt(Tag.BitsAllocated, VR.US, spec.allocated);
        dataset.setInt(Tag.BitsStored, VR.US, spec.stored);
        dataset.setInt(Tag.HighBit, VR.US, spec.stored - 1);
        dataset.setInt(Tag.PixelRepresentation, VR.US, spec.signed ? 1 : 0);
        if (spec.rescale) {
            dataset.setDouble(Tag.RescaleSlope, VR.DS, spec.slope);
            dataset.setDouble(Tag.RescaleIntercept, VR.DS, spec.intercept);
        }
        dataset.setBytes(Tag.PixelData, spec.allocated == 8 ? VR.OB : VR.OW, pixelBytes(spec));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DicomOutputStream stream = new DicomOutputStream(output, UID.ExplicitVRLittleEndian)) {
            stream.writeFileMetaInformation(dataset.createFileMetaInformation(spec.syntax));
            stream.switchTransferSyntax(spec.syntax);
            stream.writeDataset(null, dataset);
        }
        Path path = temporaryDirectory.resolve(name);
        Files.write(path, output.toByteArray());
        return path;
    }

    private byte[] pixelBytes(Spec spec) {
        int bytes = spec.allocated / 8;
        // dcm4che's DicomOutputStream performs OW byte-order conversion for the selected syntax.
        ByteBuffer buffer = ByteBuffer.allocate(spec.values.length * bytes + ((spec.values.length * bytes) % 2))
                .order(ByteOrder.LITTLE_ENDIAN);
        long mask = (1L << spec.stored) - 1;
        for (long value : spec.values) {
            long stored = value & mask;
            if (spec.allocated == 8) buffer.put((byte) stored); else buffer.putShort((short) stored);
        }
        return buffer.array();
    }

    private String[] decimalStrings(double[] values) {
        return java.util.Arrays.stream(values).mapToObj(Double::toString).toArray(String[]::new);
    }

    private Spec spec(double z, long[] values) { return new Spec(z, values); }

    private static final class Spec {
        private double[] position; private long[] values; private String study = STUDY; private String series = SERIES;
        private String frame = FRAME; private String anatomy;
        private String sop = "2.25." + System.nanoTime(); private String sopClass = UID.MRImageStorage; private String modality = "MR";
        private int rows = 2; private int columns = 2; private double[] spacing = {1, 1};
        private double[] orientation = {1, 0, 0, 0, 1, 0}; private int allocated = 16; private int stored = 16;
        private boolean signed; private int samples = 1; private String photometric = "MONOCHROME2";
        private boolean rescale; private double slope; private double intercept; private String syntax = UID.ExplicitVRLittleEndian;
        private final java.util.Set<Integer> removed = new java.util.HashSet<>();
        private Spec(double z, long[] values) { this.position = new double[] {0, 0, z}; this.values = values; }
        Spec position(double x, double y, double z) { position = new double[] {x, y, z}; return this; }
        Spec study(String value) { study = value; return this; } Spec series(String value) { series = value; return this; }
        Spec frame(String value) { frame = value; return this; } Spec anatomy(String value) { anatomy = value; return this; }
        Spec sop(String value) { sop = value; return this; } Spec dimensions(int r, int c) { rows = r; columns = c; return this; }
        Spec spacing(double r, double c) { spacing = new double[] {r, c}; return this; }
        Spec orientation(double[] value) { orientation = value; return this; } Spec allocated(int value) { allocated = value; return this; }
        Spec stored(int value) { stored = value; return this; } Spec signed() { signed = true; return this; }
        Spec rescale(double s, double i) { rescale = true; slope = s; intercept = i; return this; }
        Spec without(int tag) { removed.add(tag); return this; } Spec modality(String value) { modality = value; return this; }
        Spec sopClass(String value) { sopClass = value; return this; } Spec photometric(String value) { photometric = value; return this; }
        Spec samples(int value) { samples = value; return this; } Spec syntax(String value) { syntax = value; return this; }
    }
}
