package org.cbihi.mrinormalizer.application.m8;

import java.util.Objects;
import java.util.UUID;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;

/** One input NIfTI and one new derived MR job. */
public record M8Request(UUID jobId, RelativePath source, M8Metadata metadata) {
    public M8Request {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(metadata, "metadata");
        if (jobId.version() != 4 || source.root() != RelativePath.Root.SOURCE) {
            throw new IllegalArgumentException("Invalid M8 request");
        }
    }
}
