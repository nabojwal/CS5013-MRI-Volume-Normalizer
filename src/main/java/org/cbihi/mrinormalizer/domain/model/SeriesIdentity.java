package org.cbihi.mrinormalizer.domain.model;

public record SeriesIdentity(String seriesInstanceUid) {
    public SeriesIdentity {
        if (seriesInstanceUid == null || seriesInstanceUid.isBlank()) {
            throw new IllegalArgumentException("Series Instance UID is required");
        }
    }
}
