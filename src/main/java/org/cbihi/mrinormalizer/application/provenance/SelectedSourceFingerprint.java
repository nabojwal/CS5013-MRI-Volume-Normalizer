package org.cbihi.mrinormalizer.application.provenance;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import org.cbihi.mrinormalizer.domain.error.DicomProcessingError;
import org.cbihi.mrinormalizer.domain.error.DicomProcessingException;
import org.cbihi.mrinormalizer.domain.model.InputSource;

/** Canonical identity of selected source bytes, independent of their locations. */
public final class SelectedSourceFingerprint {

    private SelectedSourceFingerprint() { }

    public record Source(String sopInstanceUid, InputSource input) { }

    public static String sha256(List<Source> selected) {
        if (selected == null || selected.isEmpty()) {
            throw new DicomProcessingException(DicomProcessingError.EMPTY_INPUT, "No selected sources to fingerprint");
        }
        for (Source source : selected) {
            if (source == null || source.sopInstanceUid() == null || source.sopInstanceUid().isBlank()) {
                throw new DicomProcessingException(DicomProcessingError.MISSING_REQUIRED_METADATA,
                        "Source SOPInstanceUID is required for fingerprinting");
            }
            if (source.input() == null || source.input().reference() == null || source.input().reference().isBlank()) {
                throw new DicomProcessingException(DicomProcessingError.INPUT_NOT_READABLE,
                        "Selected source location is required for fingerprinting");
            }
        }
        List<Source> ordered = selected.stream().sorted(Comparator.comparing(Source::sopInstanceUid)).toList();
        for (int i = 1; i < ordered.size(); i++) {
            if (ordered.get(i - 1).sopInstanceUid().equals(ordered.get(i).sopInstanceUid())) {
                throw new DicomProcessingException(DicomProcessingError.DUPLICATE_SOP_INSTANCE,
                        "Duplicate source SOPInstanceUID cannot be fingerprinted");
            }
        }
        MessageDigest aggregate = newSha256();
        MessageDigest content = newSha256();
        byte[] buffer = new byte[8192];
        ByteBuffer length = ByteBuffer.allocate(Integer.BYTES); // Big-endian canonical UID byte length.
        for (Source source : ordered) {
            try (var stream = Files.newInputStream(Path.of(source.input().reference()))) {
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    content.update(buffer, 0, count);
                }
            } catch (IOException | InvalidPathException | SecurityException exception) {
                throw new DicomProcessingException(DicomProcessingError.INPUT_NOT_READABLE,
                        "Selected source cannot be read for fingerprinting", exception);
            }
            byte[] uid = source.sopInstanceUid().getBytes(StandardCharsets.UTF_8);
            length.clear();
            length.putInt(uid.length);
            aggregate.update(length.array());
            aggregate.update(uid);
            aggregate.update(content.digest()); // digest() resets the per-source state.
        }
        return HexFormat.of().formatHex(aggregate.digest());
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
