package org.cbihi.mrinormalizer.infrastructure.detection;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

import org.cbihi.mrinormalizer.domain.model.DetectionDiagnostic;
import org.cbihi.mrinormalizer.domain.model.DetectionOutcome;
import org.cbihi.mrinormalizer.domain.model.DetectionResult;
import org.cbihi.mrinormalizer.domain.model.InputSource;
import org.cbihi.mrinormalizer.domain.port.FormatProbe;

public final class NiftiFormatProbe implements FormatProbe {

    private static final int NIFTI_1_HEADER_SIZE = 348;
    private static final int NIFTI_2_HEADER_SIZE = 540;
    private static final int MAX_HEADER_BYTES = 4096;

    @Override
    public DetectionResult probe(InputSource input) {
        Path path = Path.of(input.reference());
        try (InputStream raw = Files.newInputStream(path)) {
            if (hasGzipSignature(raw)) {
                try (InputStream gzip = new GZIPInputStream(Files.newInputStream(path))) {
                    byte[] header = gzip.readNBytes(MAX_HEADER_BYTES);
                    return detectHeader(header, true);
                } catch (IOException exception) {
                    return DetectionResult.corrupt(DetectionDiagnostic.INVALID_GZIP);
                }
            }
        } catch (IOException exception) {
            return DetectionResult.unknown(DetectionDiagnostic.IO_ERROR);
        }

        try (InputStream stream = Files.newInputStream(path)) {
            return detectHeader(stream.readNBytes(MAX_HEADER_BYTES), false);
        } catch (IOException exception) {
            return DetectionResult.unknown(DetectionDiagnostic.IO_ERROR);
        }
    }

    private boolean hasGzipSignature(InputStream input) throws IOException {
        return input.read() == 0x1f && input.read() == 0x8b;
    }

    private DetectionResult detectHeader(byte[] bytes, boolean gzip) {
        if (bytes.length == 0) {
            return DetectionResult.unknown(DetectionDiagnostic.EMPTY_INPUT);
        }
        if (bytes.length < 4) {
            return DetectionResult.unknown(DetectionDiagnostic.UNSUPPORTED_FORMAT);
        }
        Integer headerSize = headerSize(bytes);
        if (headerSize == null) {
            return DetectionResult.unknown(DetectionDiagnostic.UNSUPPORTED_FORMAT);
        }
        if (bytes.length < headerSize) {
            return DetectionResult.corrupt(DetectionDiagnostic.INVALID_NIFTI);
        }
        if (headerSize == NIFTI_1_HEADER_SIZE) {
            if (!hasMagic(bytes, 344, "n+1\0", "ni1\0")) {
                return DetectionResult.corrupt(DetectionDiagnostic.INVALID_NIFTI);
            }
            return DetectionResult.identified(gzip ? DetectionOutcome.NIFTI_GZ : DetectionOutcome.NIFTI);
        }
        if (!hasMagic(bytes, 4, "n+2\0", "ni2\0")) {
            return DetectionResult.corrupt(DetectionDiagnostic.INVALID_NIFTI);
        }
        return DetectionResult.identified(gzip ? DetectionOutcome.NIFTI_GZ : DetectionOutcome.NIFTI);
    }

    private Integer headerSize(byte[] bytes) {
        int little = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        int big = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        if (little == NIFTI_1_HEADER_SIZE || big == NIFTI_1_HEADER_SIZE) {
            return NIFTI_1_HEADER_SIZE;
        }
        if (little == NIFTI_2_HEADER_SIZE || big == NIFTI_2_HEADER_SIZE) {
            return NIFTI_2_HEADER_SIZE;
        }
        return null;
    }

    private boolean hasMagic(byte[] bytes, int offset, String... options) {
        if (bytes.length < offset + 4) {
            return false;
        }
        String magic = new String(bytes, offset, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
        return java.util.Arrays.asList(options).contains(magic);
    }
}
