package org.cbihi.mrinormalizer.application.dataset.dicom;

/**
 * Exact restricted Study+Series operational identity. Not an M6 selection,
 * content identity or authorization for naming, logging or public reporting.
 */
public record DicomSeriesKey(String studyInstanceUid, String seriesInstanceUid) {
    public DicomSeriesKey {
        uid(studyInstanceUid); uid(seriesInstanceUid);
    }

    private static void uid(String value) {
        if (value == null || value.isEmpty() || value.length() > 64) invalid();
        int start = 0;
        for (int i = 0; i <= value.length(); i++) {
            if (i == value.length() || value.charAt(i) == '.') {
                if (i == start || i - start > 1 && value.charAt(start) == '0') invalid();
                start = i + 1;
            } else if (value.charAt(i) < '0' || value.charAt(i) > '9') invalid();
        }
    }

    private static void invalid() { throw new IllegalArgumentException("Candidate identity requires canonical UIDs"); }
}
