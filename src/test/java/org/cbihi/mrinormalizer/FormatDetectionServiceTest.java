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
        byte[] header = new byte[headerSize];
        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).putInt(headerSize);
        byte[] magicBytes = magic.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        int offset = headerSize == NIFTI_1_HEADER_SIZE ? 344 : 4;
        System.arraycopy(magicBytes, 0, header, offset, 4);
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
}
