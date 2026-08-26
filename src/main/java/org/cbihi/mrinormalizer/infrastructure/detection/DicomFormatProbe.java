package org.cbihi.mrinormalizer.infrastructure.detection;

import java.io.ByteArrayInputStream;
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
            byte[] prefix = readPrefix(path, MAX_DETECTION_BYTES);
            boolean part10 = prefix.length >= DICOM_PREAMBLE_LENGTH
                    && prefix[128] == 'D' && prefix[129] == 'I'
                    && prefix[130] == 'C' && prefix[131] == 'M';
            if (Files.size(path) > MAX_DETECTION_BYTES) {
                return DetectionResult.unknown(DetectionDiagnostic.INPUT_TOO_LARGE);
            }
            try (var boundedInput = new ByteArrayInputStream(prefix);
                 DicomInputStream stream = new DicomInputStream(boundedInput)) {
                stream.setSkipAllDicomInputHandler();
                stream.readFileMetaInformation();
                stream.readDataset();
                return DetectionResult.identified(org.cbihi.mrinormalizer.domain.model.DetectionOutcome.DICOM);
            } catch (IOException | RuntimeException exception) {
                return part10
                        ? DetectionResult.corrupt(DetectionDiagnostic.INVALID_DICOM)
                        : DetectionResult.unknown(DetectionDiagnostic.UNSUPPORTED_FORMAT);
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

}
