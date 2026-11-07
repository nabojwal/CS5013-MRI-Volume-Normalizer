package org.cbihi.mrinormalizer.application.dataset.model;

import java.util.Optional;

/**
 * F2-owned discovery failure only, never a replacement for an M5 diagnostic.
 * Location is exact restricted source-relative spelling, including a rejected
 * nonportable name; it is not anonymous text or a usable F1 reference. Empty
 * location denotes a configured-root/whole-scan condition or a discovered
 * spelling outside InventoryLocation's restricted grammar. Such a spelling is
 * withheld, never repaired or replaced with free text. The failure is retained.
 * No provider text, exception message/cause, absolute root or metadata payload
 * is retained.
 */
public record InventoryFailure(Code code, Optional<InventoryLocation> location) {
    public enum Code {
        INPUT_ROOT_INVALID, OUTPUT_ROOT_INVALID, ROOTS_OVERLAP,
        PATH_OUTSIDE_ROOT, CONTAINMENT_UNPROVEN, SYMLINK_DISALLOWED,
        SPECIAL_FILE_UNSUPPORTED, UNREPRESENTABLE_REFERENCE,
        TRAVERSAL_FAILED, DETECTION_FAILED, SOURCE_CHANGED, RESOURCE_LIMIT
    }
    public InventoryFailure {
        if (code == null || location == null) {
            throw new IllegalArgumentException("Inventory failure requires controlled code and relative location");
        }
    }
}
