package org.cbihi.mrinormalizer.application.dataset.nifti;

/** Explicit accounting bounds, independent of JVM heap availability. No default profile. */
public record NiftiReadLimits(long maxEncodedBytes, long maxDecodedBytes, long maxVoxels,
        long maxPrefixBytes, long maxWorkingBytes, long maxGzipHeaderBytes) {
    public NiftiReadLimits {
        if (maxEncodedBytes <= 0 || maxDecodedBytes <= 0 || maxVoxels <= 0
                || maxPrefixBytes < 352 || maxWorkingBytes <= 0 || maxGzipHeaderBytes < 10
                || maxVoxels > Integer.MAX_VALUE - 8L) {
            throw new IllegalArgumentException("Invalid reading limits");
        }
    }
}
