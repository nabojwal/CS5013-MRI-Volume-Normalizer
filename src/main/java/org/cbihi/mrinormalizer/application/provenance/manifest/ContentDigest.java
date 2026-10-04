package org.cbihi.mrinormalizer.application.provenance.manifest;

/** Supplied evidence for original stored file bytes; this value performs no hashing. */
public record ContentDigest(long sizeBytes, String sha256) {

    public ContentDigest {
        if (sha256 == null) {
            throw new IllegalArgumentException("Content digest hash must be non-null");
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("Content digest size must be nonnegative");
        }
        if (sha256.length() != 64 || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Content digest hash must be lowercase SHA-256");
        }
    }
}
