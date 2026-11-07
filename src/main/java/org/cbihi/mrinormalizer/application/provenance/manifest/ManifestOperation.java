package org.cbihi.mrinormalizer.application.provenance.manifest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Accepted logical operation identity and layout only; no execution observation. */
public record ManifestOperation(
        String operationId, Kind kind, List<RelativePath> sources,
        RelativePath destination
) {
    public enum Kind { COPY, CONVERT_DICOM_TO_NIFTI }

    public ManifestOperation {
        if (operationId == null || kind == null || sources == null || destination == null) {
            throw new IllegalArgumentException("Operation components must be non-null");
        }
        if (operationId.length() != 64 || !operationId.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Operation identifier must be lowercase hex");
        }
        if (destination.root() != RelativePath.Root.OUTPUT) {
            throw new IllegalArgumentException("Operation destination requires OUTPUT root");
        }
        if (sources.isEmpty() || sources.size() > 100000 || (kind == Kind.COPY && sources.size() != 1)) {
            throw new IllegalArgumentException("Operation source cardinality is invalid");
        }
        var seen = new HashSet<RelativePath>();
        var ordered = new ArrayList<RelativePath>(sources.size());
        for (var source : sources) {
            if (source == null || source.root() != RelativePath.Root.SOURCE || !seen.add(source)) {
                throw new IllegalArgumentException("Operation sources must be unique SOURCE references");
            }
            ordered.add(source);
        }
        ordered.sort((left, right) -> comparePathStrings(left.path(), right.path()));
        sources = List.copyOf(ordered);
    }

    // UTF-8 preserves scalar order; UTF-16 String.compareTo does not for supplementary text.
    private static int comparePathStrings(String left, String right) {
        int a = 0;
        int b = 0;
        while (a < left.length() && b < right.length()) {
            int x = left.codePointAt(a);
            int y = right.codePointAt(b);
            if (x != y) {
                return Integer.compare(x, y);
            }
            a += Character.charCount(x);
            b += Character.charCount(y);
        }
        return Integer.compare(left.length() - a, right.length() - b);
    }
}
