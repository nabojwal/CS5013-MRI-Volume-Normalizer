package org.cbihi.mrinormalizer.application.provenance.manifest;

import java.util.UUID;

/** Supplied publication identity; does not establish chain membership or universal power-loss durability. */
public record ManifestReceipt(UUID jobId, long sequence, String planSha256, String headSha256) {
    public ManifestReceipt {
        if (jobId == null || planSha256 == null || headSha256 == null) {
            throw new IllegalArgumentException("Receipt components must be non-null");
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("Receipt sequence must be nonnegative");
        }
        if (planSha256.length() != 64 || headSha256.length() != 64
                || !planSha256.matches("[0-9a-f]{64}") || !headSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Receipt hashes must be lowercase SHA-256");
        }
        if (sequence == 0 && !planSha256.equals(headSha256)) {
            throw new IllegalArgumentException("Plan receipt head must equal plan hash");
        }
    }
}
