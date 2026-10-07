package org.cbihi.mrinormalizer.application.dataset.model;

import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;

/**
 * Restricted operational SOURCE identity and unchanged conservative M5/F0 evidence.
 * F2 reuses accepted F1 RelativePath solely as the canonical restricted
 * SOURCE-relative operational identity. F1 plan, checkpoint, execution,
 * persistence and reporting types are not repurposed as F2 discovery state.
 */
public record InventoryEntry(RelativePath source, FormatAssessment assessment) {
    public InventoryEntry {
        if (source == null || source.root() != RelativePath.Root.SOURCE || assessment == null) {
            throw new IllegalArgumentException("Inventory entry requires source and assessment");
        }
    }
}
