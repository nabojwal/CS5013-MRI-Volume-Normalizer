package org.cbihi.mrinormalizer.infrastructure.detection;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.cbihi.mrinormalizer.domain.model.DetectionDiagnostic;
import org.cbihi.mrinormalizer.domain.model.DetectionResult;
import org.cbihi.mrinormalizer.domain.model.InputSource;
import org.cbihi.mrinormalizer.domain.port.FormatProbe;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;

public final class DicomFormatProbe implements FormatProbe {

    private static final int DICOM_PREAMBLE_LENGTH = 132;
    private static final int MAX_DETECTION_BYTES = 1024 * 1024;

    @Override
    public DetectionResult probe(InputSource input) {
        Path path = Path.of(input.reference());
        try (var inputStream = new BufferedInputStream(Files.newInputStream(path))) {
            inputStream.mark(DICOM_PREAMBLE_LENGTH);
            byte[] prefix = inputStream.readNBytes(DICOM_PREAMBLE_LENGTH);
            inputStream.reset();
            boolean part10 = prefix.length >= DICOM_PREAMBLE_LENGTH
                    && prefix[128] == 'D' && prefix[129] == 'I'
                    && prefix[130] == 'C' && prefix[131] == 'M';
            try (DicomInputStream stream = DicomInputStream.createWithLimit(inputStream, MAX_DETECTION_BYTES)) {
                Attributes meta = stream.readFileMetaInformation();
                // Identification is not whole-object validation. Complete File Meta
                // Information can establish format without reading bulk Pixel Data.
                if (hasText(meta, Tag.MediaStorageSOPClassUID)
                        && hasText(meta, Tag.MediaStorageSOPInstanceUID)
                        && hasText(meta, Tag.TransferSyntaxUID)) {
                    return DetectionResult.identified(org.cbihi.mrinormalizer.domain.model.DetectionOutcome.DICOM);
                }
                Attributes dataset = stream.readDataset(Tag.SOPInstanceUID + 1);
                if (hasText(dataset, Tag.SOPClassUID) && hasText(dataset, Tag.SOPInstanceUID)) {
                    return DetectionResult.identified(org.cbihi.mrinormalizer.domain.model.DetectionOutcome.DICOM);
                }
                return part10 ? DetectionResult.corrupt(DetectionDiagnostic.INVALID_DICOM)
                        : DetectionResult.unknown(DetectionDiagnostic.UNSUPPORTED_FORMAT);
            } catch (IOException | RuntimeException exception) {
                if (exception instanceof EOFException && Files.size(path) > MAX_DETECTION_BYTES) {
                    return DetectionResult.unknown(DetectionDiagnostic.INPUT_TOO_LARGE);
                }
                return part10
                        ? DetectionResult.corrupt(DetectionDiagnostic.INVALID_DICOM)
                        : DetectionResult.unknown(DetectionDiagnostic.UNSUPPORTED_FORMAT);
            }
        } catch (IOException | RuntimeException exception) {
            return DetectionResult.unknown(DetectionDiagnostic.IO_ERROR);
        }
    }

    private boolean hasText(Attributes attributes, int tag) {
        String value = attributes == null ? null : attributes.getString(tag);
        return value != null && !value.isBlank();
    }

}
