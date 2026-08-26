package org.cbihi.mrinormalizer.application.provenance;

import java.time.Instant;

public record ProvenanceRecord(
        String inputFingerprint,
        String softwareVersion,
        Instant completedAt,
        boolean successful
) {
}
