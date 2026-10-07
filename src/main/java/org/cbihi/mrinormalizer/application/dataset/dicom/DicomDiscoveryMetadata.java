package org.cbihi.mrinormalizer.application.dataset.dicom;

import java.util.List;
import java.util.Optional;

/**
 * Closed technical observations, not a support, geometry or series-validity verdict.
 * Empty optional values/lists mean not observed in the inspected top-level prefix,
 * not whole-object absence. UIDs and implementer-defined codes are restricted
 * operational values: no automatic logging, serialization or naming is authorized.
 */
public record DicomDiscoveryMetadata(
        String studyInstanceUid, String seriesInstanceUid, String sopInstanceUid,
        String modality, String sopClassUid, String transferSyntaxUid,
        Optional<String> frameOfReferenceUid, Optional<Integer> rows, Optional<Integer> columns,
        Optional<Integer> numberOfFrames, Optional<Integer> acquisitionNumber,
        Optional<Integer> temporalPositionIdentifier, List<Integer> echoNumbers, List<String> imageType) {
    public DicomDiscoveryMetadata {
        uid(studyInstanceUid); uid(seriesInstanceUid); uid(sopInstanceUid); uid(sopClassUid); uid(transferSyntaxUid);
        cs(modality, false);
        require(frameOfReferenceUid != null && rows != null && columns != null && numberOfFrames != null
                && acquisitionNumber != null && temporalPositionIdentifier != null
                && echoNumbers != null && imageType != null);
        frameOfReferenceUid.ifPresent(DicomDiscoveryMetadata::uid);
        rows.ifPresent(n -> require(n >= 0 && n <= 65535));
        columns.ifPresent(n -> require(n >= 0 && n <= 65535));
        numberOfFrames.ifPresent(n -> require(n > 0));
        for (var n : echoNumbers) require(n != null);
        if (!imageType.isEmpty()) {
            require(imageType.size() >= 2);
            for (int i = 0; i < imageType.size(); i++) cs(imageType.get(i), i >= 2);
        }
        echoNumbers = List.copyOf(echoNumbers); imageType = List.copyOf(imageType);
    }

    private static void uid(String s) {
        require(s != null && !s.isEmpty() && s.length() <= 64);
        int start = 0;
        for (int i = 0; i <= s.length(); i++) {
            if (i == s.length() || s.charAt(i) == '.') {
                require(i > start && (i - start == 1 || s.charAt(start) != '0')); start = i + 1;
            } else require(s.charAt(i) >= '0' && s.charAt(i) <= '9');
        }
    }
    private static void cs(String s, boolean emptyAllowed) {
        require(s != null && s.length() <= 16 && (emptyAllowed || !s.isEmpty()));
        require(s.isEmpty() || (s.charAt(0) != ' ' && s.charAt(s.length() - 1) != ' '));
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i); require(c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == ' ' || c == '_');
        }
    }
    private static void require(boolean valid) {
        if (!valid) throw new IllegalArgumentException("Invalid technical metadata components");
    }
}
