package org.cbihi.mrinormalizer.infrastructure.dicom;

import static org.junit.jupiter.api.Assertions.*;
import static org.cbihi.mrinormalizer.DicomMetadataModelTest.source;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.cbihi.mrinormalizer.application.dataset.dicom.*;
import org.cbihi.mrinormalizer.application.dataset.dicom.DicomMetadataInspection.FailureCode;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Dcm4cheMetadataReaderTest {
    @TempDir Path root;
    private static final DicomMetadataLimits ROOM = new DicomMetadataLimits(1_000_000, 32);

    // T08
    @Test void part10MetadataExtractsOnlyAllowlistedFacts() throws Exception {
        var f = new Fixture(UID.ExplicitVRLittleEndian);
        f.optional(); f.text(Tag.PatientName, "PN", "PATIENT_SENTINEL");
        f.text(Tag.PatientID, "LO", "IDENTITY_SENTINEL"); f.text(0x00091010, "LO", "PRIVATE_SENTINEL");
        var result = inspect(f.part10(), ROOM);
        assertTrue(result.failure().isEmpty(), result.toString());
        var m = result.metadata().orElseThrow();
        assertEquals("1.2.3", m.studyInstanceUid()); assertEquals("1.2.4", m.seriesInstanceUid());
        assertEquals("1.2.5", m.sopInstanceUid()); assertEquals("MR", m.modality());
        assertEquals(UID.MRImageStorage, m.sopClassUid()); assertEquals(f.syntax, m.transferSyntaxUid());
        assertEquals(Optional.of("1.2.6"), m.frameOfReferenceUid());
        assertEquals(Optional.of(7), m.rows()); assertEquals(Optional.of(9), m.columns());
        assertEquals(Optional.of(120), m.numberOfFrames()); assertEquals(Optional.of(-2), m.acquisitionNumber());
        assertEquals(Optional.of(3), m.temporalPositionIdentifier()); assertEquals(List.of(2, 1, 2), m.echoNumbers());
        assertEquals(List.of("ORIGINAL", "PRIMARY", "OTHER"), m.imageType());
        assertFalse(result.toString().contains("SENTINEL"));
    }

    // T09
    @Test void nativeDatasetUsesActualParserSyntaxInference() throws Exception {
        for (var ts : List.of(UID.ImplicitVRLittleEndian, UID.ExplicitVRLittleEndian, UID.ExplicitVRBigEndian)) {
            var f = new Fixture(ts); var m = inspect(f.dataset(), ROOM).metadata().orElseThrow();
            assertEquals(ts, m.transferSyntaxUid());
        }
    }

    // T10
    @Test void eachMissingRequiredAttributeIsIncomplete() throws Exception {
        for (int tag : new int[] {Tag.StudyInstanceUID, Tag.SeriesInstanceUID, Tag.SOPInstanceUID, Tag.Modality, Tag.SOPClassUID}) {
            var f = new Fixture(UID.ExplicitVRLittleEndian); f.elements.remove(tag);
            failure(inspect(f.part10(), ROOM), FailureCode.INCOMPLETE_METADATA);
            f.text(tag, tag == Tag.Modality ? "CS" : "UI", "");
            failure(inspect(f.part10(), ROOM), FailureCode.INCOMPLETE_METADATA);
        }
    }

    // T11
    @Test void missingPart10SyntaxDoesNotUseLibraryFallback() throws Exception {
        var f = new Fixture(UID.ExplicitVRLittleEndian);
        failure(inspect(f.part10(null, "1.2.5", UID.MRImageStorage), ROOM), FailureCode.INCOMPLETE_METADATA);
        failure(inspect(f.part10("", "1.2.5", UID.MRImageStorage), ROOM), FailureCode.INCOMPLETE_METADATA);
        failure(inspect(f.part10("INVALID_SENTINEL", "1.2.5", UID.MRImageStorage), ROOM), FailureCode.METADATA_READ_FAILED);
    }

    // T12
    @Test void fileMetaIdentityConflictsFailClosed() throws Exception {
        var f = new Fixture(UID.ExplicitVRLittleEndian);
        assertTrue(inspect(f.part10(f.syntax, null, null), ROOM).metadata().isPresent());
        for (var meta : List.of(new String[] {f.syntax, "1.9", UID.MRImageStorage},
                new String[] {f.syntax, "1.2.5", UID.CTImageStorage},
                new String[] {f.syntax, "BAD", UID.MRImageStorage},
                new String[] {"1.2.3.999999", "1.2.5", UID.MRImageStorage},
                new String[] {"1.2.840.10008.1.2.6.1", "1.2.5", UID.MRImageStorage})) {
            failure(inspect(f.part10(meta[0], meta[1], meta[2]), ROOM), FailureCode.METADATA_READ_FAILED);
        }
        f.elements.remove(Tag.StudyInstanceUID);
        failure(inspect(f.part10(f.syntax, "1.9", UID.MRImageStorage), ROOM), FailureCode.METADATA_READ_FAILED);
    }

    // T13
    @Test void malformedPresentValuesAreNotTreatedAsAbsent() throws Exception {
        Object[][] bad = {{Tag.FrameOfReferenceUID, "UI", "1..2"}, {Tag.Modality, "CS", "lowercase"},
                {Tag.Modality, "CS", "MR\\CT"}, {Tag.StudyInstanceUID, "UI", "1.02"},
                {Tag.NumberOfFrames, "IS", "2147483648"}, {Tag.AcquisitionNumber, "IS", "x"},
                {Tag.EchoNumbers, "IS", "1\\oops"}, {Tag.ImageType, "CS", "ORIGINAL\\bad"},
                {Tag.ImageType, "CS", "ORIGINAL\\PRIMARY\\" + "X".repeat(17)},
                {Tag.StudyInstanceUID, "LO", "1.2.3"}, {Tag.Rows, "US", "abcd"}};
        for (var b : bad) {
            var f = new Fixture(UID.ExplicitVRLittleEndian); f.text((Integer)b[0], (String)b[1], (String)b[2]);
            failure(inspect(f.part10(), ROOM), FailureCode.METADATA_READ_FAILED);
        }
    }

    // T14
    @Test void optionalAbsenceDoesNotApplyM6Requirements() throws Exception {
        var m = inspect(new Fixture(UID.ExplicitVRLittleEndian).part10(), ROOM).metadata().orElseThrow();
        assertTrue(m.frameOfReferenceUid().isEmpty()); assertTrue(m.rows().isEmpty()); assertTrue(m.columns().isEmpty());
        assertTrue(m.numberOfFrames().isEmpty()); assertTrue(m.acquisitionNumber().isEmpty());
        assertTrue(m.temporalPositionIdentifier().isEmpty()); assertTrue(m.echoNumbers().isEmpty()); assertTrue(m.imageType().isEmpty());
    }

    // T15
    @Test void largePixelPayloadDoesNotDetermineMetadataBudget() throws Exception {
        var f = new Fixture(UID.ExplicitVRLittleEndian); f.pixelLength = 20_000_000; f.pixelBody = new byte[2_000_000];
        var bytes = f.part10(); int prefix = bytes.length - f.pixelBody.length;
        assertTrue(inspect(bytes, new DicomMetadataLimits(prefix, 2)).metadata().isPresent());
    }

    // T16
    @Test void byteBudgetBoundaryIsExactAndDistinguishesTruncation() throws Exception {
        var f = new Fixture(UID.ExplicitVRLittleEndian); byte[] bytes = f.part10();
        assertTrue(inspect(bytes, new DicomMetadataLimits(bytes.length, 2)).metadata().isPresent());
        failure(inspect(bytes, new DicomMetadataLimits(bytes.length - 1, 2)), FailureCode.RESOURCE_LIMIT_EXCEEDED);
        byte[] truncated = Arrays.copyOf(bytes, bytes.length - 1);
        failure(inspect(truncated, ROOM), FailureCode.METADATA_READ_FAILED);
        f.pixel = 0; byte[] noPixel = f.part10();
        assertTrue(inspect(noPixel, new DicomMetadataLimits(noPixel.length + 1, 2)).metadata().isPresent());
        failure(inspect(noPixel, new DicomMetadataLimits(noPixel.length, 2)), FailureCode.RESOURCE_LIMIT_EXCEEDED);
    }

    // T17
    @Test void declaredLengthsAreCheckedBeforeAllocation() throws Exception {
        var f = new Fixture(UID.ExplicitVRLittleEndian);
        f.elements.put(Tag.StudyInstanceUID, new Element("UI", new byte[0], 65534));
        failure(inspect(f.part10(), new DicomMetadataLimits(512, 2)), FailureCode.RESOURCE_LIMIT_EXCEEDED);
        f.elements.put(0x00091010, new Element("OB", new byte[0], 0xfffffffeL));
        failure(inspect(f.part10(), new DicomMetadataLimits(512, 2)), FailureCode.RESOURCE_LIMIT_EXCEEDED);
    }

    // T18
    @Test void deflatedMetadataCannotEscapeExpandedBudget() throws Exception {
        var f = new Fixture(UID.DeflatedExplicitVRLittleEndian);
        f.text(Tag.SeriesDescription, "LO", "X".repeat(20_000));
        var bytes = f.part10(); assertTrue(bytes.length < 1000);
        failure(inspect(bytes, new DicomMetadataLimits(1000, 8)), FailureCode.RESOURCE_LIMIT_EXCEEDED);
        var raw = new Fixture(UID.DeflatedExplicitVRLittleEndian); raw.compression = Deflater.NO_COMPRESSION;
        bytes = raw.part10(); int logical = raw.meta(raw.syntax, "1.2.5", UID.MRImageStorage).length + raw.dataset().length;
        assertTrue(bytes.length > logical);
        var counted = new Counted(bytes);
        failure(Dcm4cheMetadataReader.inspectStream(source("a"), counted,
                new DicomMetadataLimits(logical, 8), t -> {}), FailureCode.RESOURCE_LIMIT_EXCEEDED);
        assertEquals(logical, counted.obtained);
        assertTrue(inspect(raw.part10(), ROOM).metadata().isPresent());
    }

    // T19
    @Test void nestedMetadataCannotOverflowParserStack() throws Exception {
        for (boolean undefined : List.of(false, true)) {
            var f = new Fixture(UID.ExplicitVRLittleEndian); f.sequence(4, undefined, false, 0);
            assertTrue(inspect(f.part10(), new DicomMetadataLimits(100_000, 4)).metadata().isPresent());
            failure(inspect(f.part10(), new DicomMetadataLimits(100_000, 3)), FailureCode.RESOURCE_LIMIT_EXCEEDED);
        }
        var deep = new Fixture(UID.ExplicitVRLittleEndian); deep.sequence(2000, true, false, 0);
        assertTrue(inspect(deep.part10(), new DicomMetadataLimits(200_000, Integer.MAX_VALUE)).metadata().isPresent());
        var un = new Fixture(UID.ExplicitVRBigEndian); un.sequence(2, true, true, 0);
        assertTrue(inspect(un.part10(), ROOM).metadata().isPresent());
        un.sequence(2000, true, true, 0);
        assertTrue(inspect(un.part10(), new DicomMetadataLimits(200_000, Integer.MAX_VALUE)).metadata().isPresent());
    }

    // T20
    @Test void allPixelDataVariantsStopBeforeValueProcessing() throws Exception {
        for (int pixel : pixels()) {
            var f = new Fixture(UID.ExplicitVRLittleEndian); f.pixel = pixel;
            f.pixelLength = 0xfffffffeL; f.pixelBody = "PIXEL_SENTINEL".getBytes(StandardCharsets.US_ASCII);
            var semantic = new ArrayList<Integer>();
            var result = Dcm4cheMetadataReader.inspectStream(source("a"), new ByteArrayInputStream(f.part10()), ROOM, semantic::add);
            assertTrue(result.metadata().isPresent()); assertFalse(semantic.contains(pixel)); assertFalse(result.toString().contains("PIXEL_SENTINEL"));
            assertTrue(semantic.contains(Tag.StudyInstanceUID)); // the hook observes real scalar processing
        }
    }

    // T21
    @Test void nestedPixelValuesAreNeverProcessed() throws Exception {
        for (int pixel : pixels()) for (boolean undefined : List.of(false, true)) {
            var f = new Fixture(UID.ExplicitVRLittleEndian); f.sequence(1, undefined, false, pixel);
            var semantic = new ArrayList<Integer>();
            failure(Dcm4cheMetadataReader.inspectStream(source("a"), new ByteArrayInputStream(f.part10()), ROOM, semantic::add), FailureCode.METADATA_READ_FAILED);
            assertFalse(semantic.contains(pixel));
        }
    }

    // T22
    @Test void absentOrMalformedPixelPayloadDoesNotRequireDecoding() throws Exception {
        var f = new Fixture(UID.ExplicitVRLittleEndian); f.pixel = 0;
        assertTrue(inspect(f.part10(), ROOM).metadata().isPresent());
        f.pixel = Tag.PixelData; f.pixelLength = -1; f.pixelBody = new byte[] {1, 2, 3};
        assertTrue(inspect(f.part10(), ROOM).metadata().isPresent());
    }

    // T23
    @Test void physicalReadAheadMatchesApprovedContract() throws Exception {
        for (String ts : List.of(UID.ExplicitVRLittleEndian, UID.DeflatedExplicitVRLittleEndian)) {
            var f = new Fixture(ts); f.pixelBody = new byte[10_000];
            for (int budget : new int[] {1, 100, 300, 700}) {
                var counted = new Counted(f.part10());
                var result = Dcm4cheMetadataReader.inspectStream(source("a"), counted,
                        new DicomMetadataLimits(budget, 4), t -> {});
                assertTrue(counted.obtained <= budget); assertTrue(counted.closed);
                assertTrue(result.metadata().isPresent() || result.failure().orElseThrow() == FailureCode.RESOURCE_LIMIT_EXCEEDED);
            }
        }
    }

    // T24
    @Test void metadataInspectionIsNeutralToM6Profile() throws Exception {
        for (String ts : List.of(UID.ExplicitVRLittleEndian, UID.JPEG2000Lossless, UID.DeflatedExplicitVRLittleEndian)) {
            var f = new Fixture(ts); f.sop = UID.EnhancedCTImageStorage;
            f.text(Tag.SOPClassUID, "UI", f.sop); f.text(Tag.Modality, "CS", "CT"); f.optional();
            f.text(Tag.BitsAllocated, "US", "bad pixel semantics");
            var result = inspect(f.part10(), ROOM); assertTrue(result.metadata().isPresent(), result.toString());
            assertEquals(Optional.of(120), result.metadata().orElseThrow().numberOfFrames());
        }
    }

    // T25: real links require actual provider privileges; no assumption/skip fallback.
    @Test void containmentSymlinksAndObservedChangesFailClosed() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new Dcm4cheMetadataReader(null, ROOM));
        assertThrows(IllegalArgumentException.class, () -> new Dcm4cheMetadataReader(root, null));
        var badRoot = assertThrows(IllegalArgumentException.class, () -> new Dcm4cheMetadataReader(root.resolve("missing-root"), ROOM));
        assertNull(badRoot.getCause()); assertFalse(badRoot.getMessage().contains(root.toString()));
        byte[] bytes = new Fixture(UID.ExplicitVRLittleEndian).part10();
        var file = Files.write(root.resolve("a"), bytes);
        var reader = new Dcm4cheMetadataReader(root, ROOM);
        assertTrue(reader.inspect(source("a")).metadata().isPresent());
        failure(reader.inspect(source("missing")), FailureCode.METADATA_READ_FAILED);
        Files.createDirectory(root.resolve("directory")); failure(reader.inspect(source("directory")), FailureCode.METADATA_READ_FAILED);
        var outside = Files.createTempDirectory(root.getParent(), "f3-outside-");
        try {
            Files.write(outside.resolve("a"), bytes);
            Files.createSymbolicLink(root.resolve("final-link"), outside.resolve("a"));
            Files.createSymbolicLink(root.resolve("ancestor"), outside);
            failure(reader.inspect(source("final-link")), FailureCode.METADATA_READ_FAILED);
            failure(reader.inspect(source("ancestor/a")), FailureCode.METADATA_READ_FAILED);
            Files.createSymbolicLink(root.resolve("root-alias"), root);
            assertTrue(new Dcm4cheMetadataReader(root.resolve("root-alias"), ROOM).inspect(source("a")).metadata().isPresent());
        } finally { Files.deleteIfExists(outside.resolve("a")); Files.deleteIfExists(outside); }
        var changed = new Dcm4cheMetadataReader(root, ROOM, new Dcm4cheMetadataReader.Observation() {
            @Override void afterRead(Path p) throws IOException { Files.write(p, new byte[] {0}); }
        });
        failure(changed.inspect(source("a")), FailureCode.METADATA_READ_FAILED);
        Files.write(file, bytes);
        var changedRoot = new Dcm4cheMetadataReader(root, ROOM, new Dcm4cheMetadataReader.Observation() {
            @Override void afterRead(Path p) throws IOException { Files.setLastModifiedTime(root, FileTime.fromMillis(1)); }
        });
        failure(changedRoot.inspect(source("a")), FailureCode.METADATA_READ_FAILED);
        assertThrows(IllegalArgumentException.class, () -> reader.inspect(null));
        assertThrows(IllegalArgumentException.class, () -> reader.inspect(new org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath(
                org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath.Root.OUTPUT, "a")));
    }

    // T26: mechanical null-key branch coverage, not fabricated NTFS qualification.
    @Test void nullFileKeysDoNotFabricateIdentity() throws Exception {
        Files.write(root.resolve("a"), new Fixture(UID.ExplicitVRLittleEndian).part10());
        var observation = new Dcm4cheMetadataReader.Observation() {
            @Override BasicFileAttributes attributes(Path p) throws IOException {
                var a = super.attributes(p);
                return new BasicFileAttributes() {
                    public FileTime lastModifiedTime() { return a.lastModifiedTime(); }
                    public FileTime lastAccessTime() { return a.lastAccessTime(); }
                    public FileTime creationTime() { return a.creationTime(); }
                    public boolean isRegularFile() { return a.isRegularFile(); }
                    public boolean isDirectory() { return a.isDirectory(); }
                    public boolean isSymbolicLink() { return a.isSymbolicLink(); }
                    public boolean isOther() { return a.isOther(); }
                    public long size() { return a.size(); }
                    public Object fileKey() { return null; }
                };
            }
        };
        assertTrue(new Dcm4cheMetadataReader(root, ROOM, observation).inspect(source("a")).metadata().isPresent());
        assertTrue(new Dcm4cheMetadataReader(root, ROOM).inspect(source("a")).metadata().isPresent());
    }

    // T27
    @Test void patientAndProviderPayloadsNeverEscape() throws Exception {
        Files.write(root.resolve("a"), new Fixture(UID.ExplicitVRLittleEndian).part10());
        var reader = new Dcm4cheMetadataReader(root, ROOM, new Dcm4cheMetadataReader.Observation() {
            @Override InputStream open(Path p) throws IOException { throw new IOException("PROVIDER_PATIENT_SENTINEL"); }
        });
        var result = reader.inspect(source("a")); failure(result, FailureCode.METADATA_READ_FAILED);
        assertFalse(result.toString().contains("SENTINEL"));
        var f = new Fixture(UID.ExplicitVRLittleEndian); f.text(Tag.StudyInstanceUID, "UI", "PHI_SENTINEL");
        var invalid = inspect(f.part10(), ROOM); failure(invalid, FailureCode.METADATA_READ_FAILED);
        assertFalse(invalid.toString().contains("SENTINEL"));
    }

    // T28
    @Test void inspectionCreatesNoFilesystemArtifacts() throws Exception {
        byte[] bytes = new Fixture(UID.DeflatedExplicitVRLittleEndian).part10();
        Files.write(root.resolve("a"), bytes);
        assertTrue(new Dcm4cheMetadataReader(root, ROOM).inspect(source("a")).metadata().isPresent());
        try (var listing = Files.list(root)) { assertEquals(List.of("a"), listing.map(p -> p.getFileName().toString()).toList()); }
        assertArrayEquals(bytes, Files.readAllBytes(root.resolve("a")));
    }

    static DicomMetadataInspection inspect(byte[] bytes, DicomMetadataLimits limits) {
        return Dcm4cheMetadataReader.inspectStream(source("a"), new ByteArrayInputStream(bytes), limits, t -> {});
    }
    static void failure(DicomMetadataInspection i, FailureCode code) {
        assertEquals(Optional.of(code), i.failure(), i.toString()); assertTrue(i.metadata().isEmpty());
    }
    static int[] pixels() { return new int[] {Tag.PixelData, Tag.FloatPixelData, Tag.DoubleFloatPixelData}; }
    static final class Counted extends InputStream {
        private final ByteArrayInputStream delegate; int obtained; boolean closed;
        Counted(byte[] bytes) { delegate = new ByteArrayInputStream(bytes); }
        @Override public int read() { int n = delegate.read(); if (n >= 0) obtained++; return n; }
        @Override public int read(byte[] b, int o, int l) { int n = delegate.read(b, o, l); if (n > 0) obtained += n; return n; }
        @Override public void close() { closed = true; }
    }
    record Element(String vr, byte[] value, long length) {}
    static final class Fixture {
        final String syntax; String sop = UID.MRImageStorage;
        final SortedMap<Integer, Element> elements = new TreeMap<>();
        int pixel = Tag.PixelData; long pixelLength = 0; byte[] pixelBody = new byte[0]; int compression = Deflater.DEFAULT_COMPRESSION;
        Fixture(String syntax) {
            this.syntax = syntax;
            text(Tag.SOPClassUID, "UI", sop); text(Tag.SOPInstanceUID, "UI", "1.2.5"); text(Tag.Modality, "CS", "MR");
            text(Tag.StudyInstanceUID, "UI", "1.2.3"); text(Tag.SeriesInstanceUID, "UI", "1.2.4");
        }
        void text(int tag, String vr, String value) {
            byte[] b = value.getBytes(StandardCharsets.US_ASCII);
            if ((b.length & 1) != 0) { b = Arrays.copyOf(b, b.length + 1); b[b.length - 1] = vr.equals("UI") ? 0 : (byte)' '; }
            elements.put(tag, new Element(vr, b, b.length));
        }
        void optional() {
            text(Tag.FrameOfReferenceUID, "UI", "1.2.6");
            for (int tag : new int[] {Tag.Rows, Tag.Columns}) {
                byte[] b = ByteBuffer.allocate(2).order(order(syntax)).putShort((short)(tag == Tag.Rows ? 7 : 9)).array();
                elements.put(tag, new Element("US", b, 2));
            }
            text(Tag.NumberOfFrames, "IS", "120"); text(Tag.AcquisitionNumber, "IS", "-2");
            text(Tag.TemporalPositionIdentifier, "IS", "3"); text(Tag.EchoNumbers, "IS", "2\\1\\2");
            text(Tag.ImageType, "CS", "ORIGINAL\\PRIMARY\\OTHER");
        }
        byte[] dataset() throws IOException {
            var out = new ByteArrayOutputStream();
            for (var e : elements.entrySet()) out.write(element(e.getKey(), e.getValue().vr(), e.getValue().value(), e.getValue().length(), syntax));
            if (pixel != 0) out.write(element(pixel, pixel == Tag.FloatPixelData ? "OF" : pixel == Tag.DoubleFloatPixelData ? "OD" : "OB", pixelBody, pixelLength, syntax));
            return out.toByteArray();
        }
        byte[] part10() throws IOException { return part10(syntax, "1.2.5", sop); }
        byte[] part10(String supplied, String instance, String clazz) throws IOException {
            var out = new ByteArrayOutputStream(); out.write(meta(supplied, instance, clazz));
            byte[] body = dataset();
            if (syntax.equals(UID.DeflatedExplicitVRLittleEndian)) {
                var deflater = new Deflater(compression, true);
                try { try (var d = new DeflaterOutputStream(out, deflater)) { d.write(body); } }
                finally { deflater.end(); }
            } else out.write(body);
            return out.toByteArray();
        }
        byte[] meta(String supplied, String instance, String clazz) throws IOException {
            var body = new ByteArrayOutputStream();
            for (var e : new Object[][] {{Tag.MediaStorageSOPClassUID, clazz}, {Tag.MediaStorageSOPInstanceUID, instance}, {Tag.TransferSyntaxUID, supplied}}) {
                if (e[1] != null) {
                    byte[] b = ((String)e[1]).getBytes(StandardCharsets.US_ASCII); if ((b.length & 1) != 0) b = Arrays.copyOf(b, b.length + 1);
                    body.write(element((Integer)e[0], "UI", b, b.length, UID.ExplicitVRLittleEndian));
                }
            }
            var out = new ByteArrayOutputStream(); out.write(new byte[128]); out.write("DICM".getBytes(StandardCharsets.US_ASCII));
            out.write(element(Tag.FileMetaInformationGroupLength, "UL", ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(body.size()).array(), 4, UID.ExplicitVRLittleEndian));
            out.write(body.toByteArray()); return out.toByteArray();
        }
        void sequence(int depth, boolean undefined, boolean unknown, int nestedPixel) throws IOException {
            String nestedSyntax = unknown ? UID.ImplicitVRLittleEndian : syntax;
            byte[] value = nestedPixel == 0 ? element(Tag.PatientName, "PN", new byte[0], 0, nestedSyntax)
                    : element(nestedPixel, "OB", new byte[0], 0xfffffffeL, nestedSyntax);
            for (int i = 0; i < depth; i++) {
                byte[] item = element(Tag.Item, null, value, undefined ? -1 : value.length, nestedSyntax);
                if (undefined) item = join(item, element(Tag.ItemDelimitationItem, null, new byte[0], 0, nestedSyntax));
                value = element(Tag.ReferencedSeriesSequence, "SQ", item, undefined ? -1 : item.length, nestedSyntax);
                if (undefined) value = join(value, element(Tag.SequenceDelimitationItem, null, new byte[0], 0, nestedSyntax));
            }
            // The outer sequence is represented as a dataset element, not a raw concatenation.
            int header = nestedSyntax.equals(UID.ImplicitVRLittleEndian) ? 8 : 12;
            byte[] body = Arrays.copyOfRange(value, header, value.length - (undefined ? 8 : 0));
            elements.put(Tag.ReferencedSeriesSequence, new Element(unknown ? "UN" : "SQ", body, undefined ? -1 : body.length));
            if (undefined) {
                // A sequence delimiter belongs to the value body for our raw fixture writer.
                var e = elements.get(Tag.ReferencedSeriesSequence);
                elements.put(Tag.ReferencedSeriesSequence, new Element(e.vr(), join(e.value(),
                        element(Tag.SequenceDelimitationItem, null, new byte[0], 0, nestedSyntax)), -1));
            }
        }
        static byte[] element(int tag, String vr, byte[] value, long length, String syntax) throws IOException {
            var out = new ByteArrayOutputStream(); var order = order(syntax);
            out.write(ByteBuffer.allocate(4).order(order).putShort((short)(tag >>> 16)).putShort((short)tag).array());
            if (vr == null || syntax.equals(UID.ImplicitVRLittleEndian)) out.write(ByteBuffer.allocate(4).order(order).putInt((int)length).array());
            else {
                out.write(vr.getBytes(StandardCharsets.US_ASCII));
                if (Set.of("OB", "OD", "OF", "OL", "OV", "OW", "SQ", "UC", "UR", "UT", "UN").contains(vr)) {
                    out.write(new byte[2]); out.write(ByteBuffer.allocate(4).order(order).putInt((int)length).array());
                } else out.write(ByteBuffer.allocate(2).order(order).putShort((short)length).array());
            }
            out.write(value); return out.toByteArray();
        }
        static ByteOrder order(String syntax) { return syntax.equals(UID.ExplicitVRBigEndian) ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN; }
        static byte[] join(byte[] a, byte[] b) throws IOException { var out = new ByteArrayOutputStream(); out.write(a); out.write(b); return out.toByteArray(); }
    }
}
