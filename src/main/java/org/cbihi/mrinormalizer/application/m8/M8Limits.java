package org.cbihi.mrinormalizer.application.m8;

import org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadLimits;

/** Frozen upper bounds; users may lower, but not raise, this submission profile. */
public record M8Limits(NiftiReadLimits nifti, int maxDimension,
                       long maxWorkingBytes, long maxOutputBytes,
                       long maxCheckpointBytes, long maxJournalBytes) {
    public M8Limits {
        if (nifti == null || maxDimension < 1 || maxDimension > 4096
                || maxWorkingBytes <= 0 || maxWorkingBytes > (1L<<30)
                || maxOutputBytes <= 0 || maxOutputBytes > 128L*(1<<20)
                || maxCheckpointBytes <= 0 || maxCheckpointBytes > 8L*(1<<20)
                || maxJournalBytes <= 0 || maxJournalBytes > 64L*(1<<20)
                || maxCheckpointBytes > maxJournalBytes
                || nifti.maxEncodedBytes() > 512L*(1<<20)
                || nifti.maxDecodedBytes() > 512L*(1<<20)
                || nifti.maxVoxels() > 32_000_000L
                || nifti.maxPrefixBytes() > (1<<20)
                || nifti.maxGzipHeaderBytes() > (64<<10)
                || nifti.maxWorkingBytes() > 768L*(1<<20)) {
            throw new IllegalArgumentException("M8 limits exceed approved profile");
        }
    }
    public static M8Limits defaults() {
        return new M8Limits(new NiftiReadLimits(512L<<20, 512L<<20, 32_000_000L,
                1<<20, 768L<<20, 64<<10), 4096, 1L<<30, 128L<<20, 8L<<20, 64L<<20);
    }
}
