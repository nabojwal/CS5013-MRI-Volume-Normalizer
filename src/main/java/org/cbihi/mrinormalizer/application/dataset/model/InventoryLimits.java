package org.cbihi.mrinormalizer.application.dataset.model;

/**
 * Strictly positive caller-supplied finite scan budgets; no product defaults
 * are inferred.
 * maxEntries counts all discovered children, including directories and rejected
 * entries. maxDepth counts path segments below SOURCE. maxFailures bounds
 * ordinary F2 failure facts; one additional terminal RESOURCE_LIMIT fact is
 * reserved so exhaustion never silently discards an earlier fact.
 */
public record InventoryLimits(int maxEntries, int maxDepth, int maxFailures) {
    public InventoryLimits {
        if (maxEntries < 1 || maxDepth < 1 || maxFailures < 1) {
            throw new IllegalArgumentException("Inventory budgets must be positive");
        }
    }
}
