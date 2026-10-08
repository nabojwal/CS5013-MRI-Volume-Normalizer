package org.cbihi.mrinormalizer.application.dataset.organization;

import org.cbihi.mrinormalizer.application.provenance.manifest.ContentDigest;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;

/**
 * Immutable original-byte COPY mapping, not execution or authenticated byte
 * evidence. Later F6/F9 must preserve this identity and revalidate its source.
 * Default record text is restricted internal data, never a public diagnostic.
 */
public record OrganizationCopyOperation(String operationId, RelativePath source,
        RelativePath destination, ContentDigest expectedDigest) {
    public OrganizationCopyOperation {
        if (operationId == null || !operationId.matches("[0-9a-f]{64}")
                || source == null || source.root() != RelativePath.Root.SOURCE
                || destination == null || destination.root() != RelativePath.Root.OUTPUT
                || expectedDigest == null) {
            throw new IllegalArgumentException("Invalid organization planning contract");
        }
    }
}
