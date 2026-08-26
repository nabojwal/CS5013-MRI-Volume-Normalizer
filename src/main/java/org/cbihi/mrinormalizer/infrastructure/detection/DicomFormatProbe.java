package org.cbihi.mrinormalizer.infrastructure.detection;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.cbihi.mrinormalizer.domain.model.DetectionDiagnostic;
import org.cbihi.mrinormalizer.domain.model.DetectionResult;
import org.cbihi.mrinormalizer.domain.model.InputSource;
import org.cbihi.mrinormalizer.domain.port.FormatProbe;
import org.dcm4che3.io.DicomInputStream;

public final class DicomFormatProbe implements FormatProbe {

    private static final int DICOM_PREAMBLE_LENGTH = 132;
    private static final int MAX_DETECTION_BYTES = 1024 * 1024;

    @Override
    public DetectionResult probe(InputSource input) {
        Path path = Path.of(input.reference());
        try {
            byte[] prefix = readPrefix(path, DICOM_PREAMBLE_LENGTH);
            boolean part10 = prefix.length >= DICOM_PREAMBLE_LENGTH
                    && prefix[128] == 'D' && prefix[129] == 'I'
                    && prefix[130] == 'C' && prefix[131] == 'M';
            boolean datasetEvidence = hasDatasetEvidence(prefix);
            if (!part10 && !datasetEvidence) {
                return DetectionResult.unknown(DetectionDiagnostic.UNSUPPORTED_FORMAT);
            }
            try (DicomInputStream stream = DicomInputStream.createWithLimit(
                    new FileInputStream(path.toFile()), MAX_DETECTION_BYTES)) {
                stream.readFileMetaInformation();
                stream.readDataset();
                return DetectionResult.identified(org.cbihi.mrinormalizer.domain.model.DetectionOutcome.DICOM);
            } catch (IOException | RuntimeException exception) {
                return DetectionResult.corrupt(DetectionDiagnostic.INVALID_DICOM);
            }
        } catch (IOException | RuntimeException exception) {
            return DetectionResult.unknown(DetectionDiagnostic.IO_ERROR);
        }
    }

    private byte[] readPrefix(Path path, int length) throws IOException {
        try (var input = Files.newInputStream(path)) {
            return input.readNBytes(length);
        }
    }

    private boolean hasDatasetEvidence(byte[] prefix) {
        if (prefix.length < 8) {
            return false;
        }
        int group = unsignedShort(prefix[0], prefix[1]);
        int element = unsignedShort(prefix[2], prefix[3]);
        return group % 2 == 0 && element > 0 && element < 0x1000;
    }

    private int unsignedShort(byte high, byte low) {
        return ((high & 0xff) << 8) | (low & 0xff);
    }
}
