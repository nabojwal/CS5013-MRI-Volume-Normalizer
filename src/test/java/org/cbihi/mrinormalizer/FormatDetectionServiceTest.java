package org.cbihi.mrinormalizer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.cbihi.mrinormalizer.application.service.FormatDetectionService;
import org.cbihi.mrinormalizer.domain.model.DetectionDiagnostic;
import org.cbihi.mrinormalizer.domain.model.DetectionOutcome;
import org.cbihi.mrinormalizer.domain.model.DetectionResult;
import org.cbihi.mrinormalizer.domain.model.InputSource;
import org.cbihi.mrinormalizer.infrastructure.detection.DicomFormatProbe;
import org.cbihi.mrinormalizer.infrastructure.detection.NiftiFormatProbe;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FormatDetectionServiceTest {

    private static final int NIFTI_1_HEADER_SIZE = 348;
    private static final int NIFTI_2_HEADER_SIZE = 540;

    @TempDir
    Path temporaryDirectory;

    @Test
    void detectsDicomWithExpectedExtension() throws IOException {
        DetectionResult result = detect("scan.dcm", dicomBytes());

        assertEquals(DetectionOutcome.DICOM, result.outcome());
        assertEquals(DetectionDiagnostic.NONE, result.diagnostic());
        assertFalse(result.extensionMismatch());
    }

    @Test
    void detectsDicomWithoutExtension() throws IOException {
        DetectionResult result = detect("scan", dicomBytes());

        assertEquals(DetectionOutcome.DICOM, result.outcome());
        assertTrue(result.extensionMismatch());
    }

    @Test
    void detectsDicomWithWrongExtension() throws IOException {
        DetectionResult result = detect("scan.txt", dicomBytes());

        assertEquals(DetectionOutcome.DICOM, result.outcome());
        assertTrue(result.extensionMismatch());
    }

    @Test
    void detectsNifti1AndNifti2() throws IOException {
        assertEquals(DetectionOutcome.NIFTI, detect("scan.nii", niftiBytes(NIFTI_1_HEADER_SIZE, "n+1\0")).outcome());
        assertEquals(DetectionOutcome.NIFTI, detect("scan.nii", niftiBytes(NIFTI_2_HEADER_SIZE, "n+2\0")).outcome());
    }

    @Test
    void detectsBigEndianNifti() throws IOException {
        assertEquals(DetectionOutcome.NIFTI,
                detect("big.nii", niftiBytes(NIFTI_1_HEADER_SIZE, "n+1\0", ByteOrder.BIG_ENDIAN)).outcome());
    }

    @Test
    void detectsNiftiWithoutExtensionAndIgnoresWrongExtension() throws IOException {
        DetectionResult missingExtension = detect("scan", niftiBytes(NIFTI_1_HEADER_SIZE, "n+1\0"));
        DetectionResult wrongExtension = detect("scan.dcm", niftiBytes(NIFTI_1_HEADER_SIZE, "n+1\0"));

        assertEquals(DetectionOutcome.NIFTI, missingExtension.outcome());
        assertEquals(DetectionOutcome.NIFTI, wrongExtension.outcome());
        assertTrue(missingExtension.extensionMismatch());
        assertTrue(wrongExtension.extensionMismatch());
    }

    @Test
    void detectsGzipWrappedNiftiOnlyWhenPayloadIsNifti() throws IOException {
        DetectionResult niftiGzip = detect("scan.nii.gz", gzip(niftiBytes(NIFTI_1_HEADER_SIZE, "n+1\0")));
        DetectionResult arbitraryGzip = detect("data.nii.gz", gzip("not NIfTI".getBytes()));

        assertEquals(DetectionOutcome.NIFTI_GZ, niftiGzip.outcome());
        assertEquals(DetectionOutcome.UNKNOWN, arbitraryGzip.outcome());
        assertEquals(DetectionDiagnostic.UNSUPPORTED_FORMAT, arbitraryGzip.diagnostic());
    }

    @Test
    void rejectsNiftiImagePairHeaders() throws IOException {
        assertEquals(DetectionOutcome.UNKNOWN,
                detect("pair.hdr", niftiBytes(NIFTI_1_HEADER_SIZE, "ni1\0")).outcome());
        assertEquals(DetectionOutcome.UNKNOWN,
                detect("pair2.hdr", niftiBytes(NIFTI_2_HEADER_SIZE, "ni2\0")).outcome());
    }

    @Test
    void detectsCompleteGzipHeaderWithoutRequiringTrailerValidation() throws IOException {
        byte[] valid = gzip(niftiBytes(NIFTI_1_HEADER_SIZE, "n+1\0"));
        byte[] truncated = java.util.Arrays.copyOf(valid, valid.length - 2);

        // Detection stops at the complete header; payload/trailer integrity is a later concern.
        assertEquals(DetectionOutcome.NIFTI_GZ, detect("truncated.nii.gz", truncated).outcome());
    }

    @Test
    void detectsCorruptRecognizableInputs() throws IOException {
        byte[] truncatedNifti = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(NIFTI_1_HEADER_SIZE).array();
        byte[] invalidNiftiMagic = niftiBytes(NIFTI_1_HEADER_SIZE, "bad!");
        byte[] malformedGzip = new byte[] {0x1f, (byte) 0x8b, 0x08, 0x00};
        byte[] corruptDicom = new byte[132];
        corruptDicom[128] = 'D';
        corruptDicom[129] = 'I';
        corruptDicom[130] = 'C';
        corruptDicom[131] = 'M';

        assertEquals(DetectionOutcome.CORRUPT, detect("short.nii", truncatedNifti).outcome());
        assertEquals(DetectionOutcome.CORRUPT, detect("bad.nii", invalidNiftiMagic).outcome());
        assertEquals(DetectionOutcome.CORRUPT, detect("bad.nii.gz", malformedGzip).outcome());
        assertEquals(DetectionOutcome.CORRUPT, detect("bad.dcm", corruptDicom).outcome());
    }

    @Test
    void weakDicomLikeBytesRemainUnknown() throws IOException {
        assertEquals(DetectionOutcome.UNKNOWN,
                detect("random.dcm", new byte[] {0, 2, 0, 1, 9, 9, 9, 9}).outcome());
    }

    @Test
    void oversizedDicomIsInspectedOnlyWithinTheDetectionBound() throws IOException {
        Path input = largeFileMetaInformation("budget.dcm", 2 * 1024 * 1024);
        DetectionResult result = service().detect(new InputSource(input.toString()));

        assertEquals(DetectionOutcome.UNKNOWN, result.outcome());
        assertEquals(DetectionDiagnostic.INPUT_TOO_LARGE, result.diagnostic());
    }

    @Test
    void detectsLargePart10DicomFromEarlyMetadata() throws IOException {
        Path input = temporaryDirectory.resolve("large.data");
        try (var output = Files.newOutputStream(input)) {
            output.write(dicomBytes());
            // Explicit-VR OW Pixel Data header, followed by a streamed 2 MiB value.
            output.write(ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort((short) 0x7fe0).putShort((short) 0x0010)
                    .put((byte) 'O').put((byte) 'W').putShort((short) 0).putInt(2 * 1024 * 1024).array());
            writeZeros(output, 2 * 1024 * 1024);
        }
        assertTrue(Files.size(input) > 1024 * 1024);
        DetectionResult result = service().detect(new InputSource(input.toString()));
        assertEquals(DetectionOutcome.DICOM, result.outcome());
        assertEquals(DetectionDiagnostic.NONE, result.diagnostic());
        assertTrue(result.extensionMismatch());
    }

    @Test
    void continuesToLaterProbeAfterInsufficientEvidence() throws IOException {
        Path input = Files.write(temporaryDirectory.resolve("later.nii"), niftiBytes(348, "n+1\0"));
        var detector = new FormatDetectionService(List.of(
                source -> DetectionResult.unknown(DetectionDiagnostic.INPUT_TOO_LARGE),
                new NiftiFormatProbe()));
        assertEquals(DetectionOutcome.NIFTI, detector.detect(new InputSource(input.toString())).outcome());
    }

    @Test
    void detectsLargeNiftiAfterDicomProbe() throws IOException {
        Path input = temporaryDirectory.resolve("large.nii");
        try (var output = Files.newOutputStream(input)) {
            output.write(niftiBytes(348, "n+1\0"));
            writeZeros(output, 2 * 1024 * 1024);
        }
        assertEquals(DetectionOutcome.NIFTI, service().detect(new InputSource(input.toString())).outcome());
    }

    @Test
    void detectsLargeGzipNiftiWithoutDrainingPayload() throws IOException {
        Path input = temporaryDirectory.resolve("large.gz");
        try (var output = new GZIPOutputStream(Files.newOutputStream(input))) {
            output.write(niftiBytes(348, "n+1\0"));
            // Exceeds the old 16 MiB decompressed cap, with only a small streaming buffer.
            writeZeros(output, 17 * 1024 * 1024);
        }
        DetectionResult result = service().detect(new InputSource(input.toString()));
        assertEquals(DetectionOutcome.NIFTI_GZ, result.outcome());
        assertEquals(DetectionDiagnostic.NONE, result.diagnostic());
    }

    @Test
    void rejectsTruncatedGzipNiftiHeader() throws IOException {
        byte[] partial = java.util.Arrays.copyOf(niftiBytes(348, "n+1\0"), 100);
        DetectionResult result = detect("short.gz", gzip(partial));
        assertEquals(DetectionOutcome.CORRUPT, result.outcome());
        assertEquals(DetectionDiagnostic.INVALID_NIFTI, result.diagnostic());
    }

    @Test
    void largeUnrelatedFileIsUnknownRatherThanSizeFailure() throws IOException {
        Path input = temporaryDirectory.resolve("random.dcm");
        byte[] block = new byte[8192];
        new java.util.Random(5013).nextBytes(block);
        try (var output = Files.newOutputStream(input)) {
            for (int i = 0; i < 256; i++) output.write(block);
        }
        DetectionResult result = service().detect(new InputSource(input.toString()));
        assertEquals(DetectionOutcome.UNKNOWN, result.outcome());
        assertEquals(DetectionDiagnostic.UNSUPPORTED_FORMAT, result.diagnostic());
    }

    @Test
    void distinguishesTruncatedDicomMetadataFromProbeExhaustion() throws IOException {
        Path input = largeFileMetaInformation("truncated.dcm", 32);
        DetectionResult result = service().detect(new InputSource(input.toString()));
        assertEquals(DetectionOutcome.CORRUPT, result.outcome());
        assertEquals(DetectionDiagnostic.INVALID_DICOM, result.diagnostic());
    }

    @Test
    void detectsBigEndianGzipNifti2Header() throws IOException {
        assertEquals(DetectionOutcome.NIFTI_GZ,
                detect("header.bin", gzip(niftiBytes(540, "n+2\0", ByteOrder.BIG_ENDIAN))).outcome());
    }

    private Path largeFileMetaInformation(String filename, int payloadBytes) throws IOException {
        Path input = temporaryDirectory.resolve(filename);
        try (var output = Files.newOutputStream(input)) {
            output.write(new byte[128]);
            output.write(new byte[] {'D', 'I', 'C', 'M'});
            // File Meta Information Version followed by a declared 2 MiB Private Information.
            // No complete identifying metadata is available within the probe budget.
            output.write(new byte[] {2, 0, 1, 0, 'O', 'B', 0, 0, 2, 0, 0, 0, 0, 1});
            output.write(ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort((short) 2).putShort((short) 0x0102)
                    .put((byte) 'O').put((byte) 'B').putShort((short) 0).putInt(2 * 1024 * 1024).array());
            writeZeros(output, payloadBytes);
        }
        return input;
    }

    private void writeZeros(java.io.OutputStream output, int count) throws IOException {
        byte[] block = new byte[8192];
        while (count > 0) {
            int length = Math.min(count, block.length);
            output.write(block, 0, length);
            count -= length;
        }
    }

    @Test
    void detectsPreambleLessDicomThroughDcm4che() throws IOException {
        assertEquals(DetectionOutcome.DICOM, detect("dataset", dicomDatasetBytes()).outcome());
    }

    @Test
    void invalidInputReferenceGetsStableDiagnostic() {
        DetectionResult result = service().detect(new InputSource("\0"));

        assertEquals(DetectionOutcome.UNKNOWN, result.outcome());
        assertEquals(DetectionDiagnostic.INVALID_INPUT_REFERENCE, result.diagnostic());
    }

    @Test
    void handlesEmptyAndUnsupportedInputs() throws IOException {
        DetectionResult empty = detect("empty", new byte[0]);
        DetectionResult random = detect("random.bin", new byte[] {1, 2, 3, 4, 5});

        assertEquals(DetectionOutcome.UNKNOWN, empty.outcome());
        assertEquals(DetectionDiagnostic.EMPTY_INPUT, empty.diagnostic());
        assertEquals(DetectionOutcome.UNKNOWN, random.outcome());
        assertEquals(DetectionDiagnostic.UNSUPPORTED_FORMAT, random.diagnostic());
    }

    @Test
    void handlesMissingPathAndDirectory() throws IOException {
        Path missing = temporaryDirectory.resolve("missing.nii");
        DetectionResult missingResult = service()
                .detect(new InputSource(missing.toString()));
        DetectionResult directoryResult = service()
                .detect(new InputSource(temporaryDirectory.toString()));

        assertEquals(DetectionOutcome.UNKNOWN, missingResult.outcome());
        assertEquals(DetectionDiagnostic.INPUT_NOT_FOUND, missingResult.diagnostic());
        assertEquals(DetectionOutcome.UNKNOWN, directoryResult.outcome());
        assertEquals(DetectionDiagnostic.INPUT_IS_DIRECTORY, directoryResult.diagnostic());
    }

    private DetectionResult detect(String filename, byte[] content) throws IOException {
        Path input = temporaryDirectory.resolve(filename);
        Files.write(input, content);
        return service().detect(new InputSource(input.toString()));
    }

    private FormatDetectionService service() {
        return new FormatDetectionService(List.of(new DicomFormatProbe(), new NiftiFormatProbe()));
    }

    private byte[] niftiBytes(int headerSize, String magic) {
        return niftiBytes(headerSize, magic, ByteOrder.LITTLE_ENDIAN);
    }

    private byte[] niftiBytes(int headerSize, String magic, ByteOrder order) {
        byte[] header = new byte[headerSize];
        ByteBuffer buffer = ByteBuffer.wrap(header).order(order);
        buffer.putInt(headerSize);
        byte[] magicBytes = magic.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        int offset = headerSize == NIFTI_1_HEADER_SIZE ? 344 : 4;
        System.arraycopy(magicBytes, 0, header, offset, 4);
        if (headerSize == NIFTI_1_HEADER_SIZE) {
            buffer.putShort(40, (short) 3);
            buffer.putShort(42, (short) 1);
            buffer.putShort(44, (short) 1);
            buffer.putShort(46, (short) 1);
            buffer.putShort(70, (short) 16);
            buffer.putShort(72, (short) 32);
        } else {
            buffer.putShort(12, (short) 16);
            buffer.putShort(14, (short) 32);
            buffer.putLong(16, 3);
            buffer.putLong(24, 1);
            buffer.putLong(32, 1);
            buffer.putLong(40, 1);
        }
        return header;
    }

    private byte[] gzip(byte[] content) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(content);
        }
        return output.toByteArray();
    }

    private byte[] dicomBytes() throws IOException {
        Attributes dataset = new Attributes();
        dataset.setString(Tag.SOPClassUID, VR.UI, UID.MRImageStorage);
        dataset.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
        dataset.setString(Tag.PatientName, VR.PN, "TEST^PATIENT");
        Attributes fileMetaInformation = dataset.createFileMetaInformation(UID.ExplicitVRLittleEndian);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DicomOutputStream dicom = new DicomOutputStream(output, UID.ExplicitVRLittleEndian)) {
            dicom.writeFileMetaInformation(fileMetaInformation);
            dicom.writeDataset(null, dataset);
        }
        return output.toByteArray();
    }

    private byte[] dicomDatasetBytes() throws IOException {
        Attributes dataset = new Attributes();
        dataset.setString(Tag.SOPClassUID, VR.UI, UID.MRImageStorage);
        dataset.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
        dataset.setString(Tag.PatientName, VR.PN, "TEST^PATIENT");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DicomOutputStream dicom = new DicomOutputStream(output, UID.ExplicitVRLittleEndian)) {
            dicom.writeDataset(null, dataset);
        }
        return output.toByteArray();
    }
}
