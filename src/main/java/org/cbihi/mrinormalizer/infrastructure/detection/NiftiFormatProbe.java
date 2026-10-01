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

    @Override
    public DetectionResult probe(InputSource input) {
        Path path = Path.of(input.reference());
        try (InputStream raw = Files.newInputStream(path)) {
            if (hasGzipSignature(raw)) {
                try (InputStream compressed = Files.newInputStream(path);
                     InputStream gzip = new GZIPInputStream(compressed)) {
                    return detectHeader(readHeaderPrefix(gzip), true);
                } catch (IOException exception) {
                    return DetectionResult.corrupt(DetectionDiagnostic.INVALID_GZIP);
                }
            }
        } catch (IOException exception) {
            return DetectionResult.unknown(DetectionDiagnostic.IO_ERROR);
        }

        try (InputStream stream = Files.newInputStream(path)) {
            return detectHeader(readHeaderPrefix(stream), false);
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
            if (hasMagic(bytes, 344, "ni1\0")) {
                return DetectionResult.unknown(DetectionDiagnostic.UNSUPPORTED_FORMAT);
            }
            if (!hasMagic(bytes, 344, "n+1\0")) {
                return DetectionResult.corrupt(DetectionDiagnostic.INVALID_NIFTI);
            }
            return DetectionResult.identified(gzip ? DetectionOutcome.NIFTI_GZ : DetectionOutcome.NIFTI);
        }
        if (hasMagic(bytes, 4, "ni2\0")) {
            return DetectionResult.unknown(DetectionDiagnostic.UNSUPPORTED_FORMAT);
        }
        if (!hasMagic(bytes, 4, "n+2\0")) {
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

    private boolean validNifti1Fields(byte[] bytes) {
        ByteOrder order = byteOrder(bytes, NIFTI_1_HEADER_SIZE);
        int dimensions = Short.toUnsignedInt(ByteBuffer.wrap(bytes, 40, 2).order(order).getShort());
        if (dimensions < 1 || dimensions > 7) {
            return false;
        }
        for (int offset = 42; offset < 42 + dimensions * 2; offset += 2) {
            if (ByteBuffer.wrap(bytes, offset, 2).order(order).getShort() <= 0) {
                return false;
            }
        }
        int datatype = Short.toUnsignedInt(ByteBuffer.wrap(bytes, 70, 2).order(order).getShort());
        int bitpix = Short.toUnsignedInt(ByteBuffer.wrap(bytes, 72, 2).order(order).getShort());
        return validTypeAndBitpix(datatype, bitpix);
    }

    private boolean validNifti2Fields(byte[] bytes) {
        ByteOrder order = byteOrder(bytes, NIFTI_2_HEADER_SIZE);
        long dimensions = ByteBuffer.wrap(bytes, 16, 8).order(order).getLong();
        if (dimensions < 1 || dimensions > 7) {
            return false;
        }
        for (int index = 0; index < dimensions; index++) {
            if (ByteBuffer.wrap(bytes, 24 + index * 8, 8).order(order).getLong() <= 0) {
                return false;
            }
        }
        int datatype = Short.toUnsignedInt(ByteBuffer.wrap(bytes, 12, 2).order(order).getShort());
        int bitpix = Short.toUnsignedInt(ByteBuffer.wrap(bytes, 14, 2).order(order).getShort());
        return validTypeAndBitpix(datatype, bitpix);
    }

    private boolean validTypeAndBitpix(int datatype, int bitpix) {
        return switch (datatype) {
            case 1, 2 -> bitpix == 1 || bitpix == 8;
            case 4, 8, 256 -> bitpix == 16 || bitpix == 32 || bitpix == 8;
            case 16, 32, 64, 128, 512, 768 -> bitpix == 32 || bitpix == 64 || bitpix == 128;
            default -> false;
        };
    }

    private ByteOrder byteOrder(byte[] bytes, int headerSize) {
        int little = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        return little == headerSize ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
    }

    private byte[] readHeaderPrefix(InputStream input) throws IOException {
        byte[] sizeBytes = input.readNBytes(Integer.BYTES);
        if (sizeBytes.length < Integer.BYTES) return sizeBytes;
        Integer size = headerSize(sizeBytes);
        if (size == null) return sizeBytes;
        // Stop at the header boundary. Payload/CRC/trailer validation belongs to
        // full-file loading, not bounded format recognition.
        byte[] remainder = input.readNBytes(size - Integer.BYTES);
        byte[] header = java.util.Arrays.copyOf(sizeBytes, Integer.BYTES + remainder.length);
        System.arraycopy(remainder, 0, header, Integer.BYTES, remainder.length);
        return header;
    }
}
