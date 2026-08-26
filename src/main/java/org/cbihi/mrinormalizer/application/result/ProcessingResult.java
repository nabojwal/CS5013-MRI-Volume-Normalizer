package org.cbihi.mrinormalizer.application.result;

import org.cbihi.mrinormalizer.application.provenance.ProvenanceRecord;

public record ProcessingResult(
        boolean successful,
        ProvenanceRecord provenance
) {
}
