package org.cbihi.mrinormalizer.application.dataset.model;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import org.cbihi.mrinormalizer.domain.model.DetectionDiagnostic;
import org.cbihi.mrinormalizer.domain.model.DetectionOutcome;

/**
 * Immutable, deterministically ordered, restricted discovery snapshot. No clock,
 * absolute root, digest, metadata, series membership, destination or execution
 * evidence is recorded. A complete inventory is not a conversion-ready dataset.
 */
public record DatasetInventory(List<InventoryEntry> entries, List<InventoryFailure> failures) {
    public DatasetInventory {
        if (entries == null || failures == null || entries.stream().anyMatch(e -> e == null)
                || failures.stream().anyMatch(f -> f == null)) {
            throw new IllegalArgumentException("Inventory collections must contain non-null facts");
        }
        var sources = new HashSet<String>();
        for (var entry : entries) if (!sources.add(entry.source().path())) {
            throw new IllegalArgumentException("Inventory source identities must be unique");
        }
        if (new HashSet<>(failures).size() != failures.size()) {
            throw new IllegalArgumentException("Inventory failure facts must be unique");
        }
        var orderedEntries = new ArrayList<>(entries);
        orderedEntries.sort(Comparator.comparing(e -> e.source().path(), DatasetInventory::comparePaths));
        entries = List.copyOf(orderedEntries);
        var orderedFailures = new ArrayList<>(failures);
        orderedFailures.sort(Comparator.comparing((InventoryFailure f) -> f.location().map(InventoryLocation::spelling).orElse(""), DatasetInventory::comparePaths)
                .thenComparingInt(f -> f.code().ordinal()));
        failures = List.copyOf(orderedFailures);
    }

    /**
     * Complete traversal and conclusive M5 recognition attempts, including
     * recognized-corrupt and conclusive nonmatch. Unavailable, empty or budget-
     * inconclusive recognition remains visible in its original assessment and
     * prevents a complete claim. VALID/SUPPORTED/READY is never implied.
     */
    public boolean complete() {
        return failures.isEmpty() && entries.stream().allMatch(entry -> {
            var detection = entry.assessment().initialDetection();
            return detection.outcome() != DetectionOutcome.UNKNOWN
                    || detection.diagnostic() == DetectionDiagnostic.UNSUPPORTED_FORMAT;
        });
    }

    private static int comparePaths(String left, String right) {
        int comparison = Arrays.compareUnsigned(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
        // Compare exact spellings without Unicode normalization or case folding.
        return comparison != 0 ? comparison : left.compareTo(right);
    }
}
