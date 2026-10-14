package org.cbihi.mrinormalizer.application.dataset.copy;

/** Caller-supplied original-byte and retained-failure bounds; no product defaults. */
public record CopyLimits(long maxFileBytes, long maxReadBytes, int bufferBytes, int maxFailures) {
    public CopyLimits {
        if (maxFileBytes <= 0 || maxReadBytes <= 0 || bufferBytes <= 0 || bufferBytes > 1048576
                || maxFailures <= 0 || maxFailures > 100000) throw new IllegalArgumentException("Invalid COPY limits");
    }
}
