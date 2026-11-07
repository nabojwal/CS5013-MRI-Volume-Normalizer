package org.cbihi.mrinormalizer.application.dataset.organization;

import java.util.Optional;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;

/**
 * F5 planning disposition only; original F0/F2/F3/F4 facts stay authoritative.
 * Block/skip decisions carry no executable identity. Default record text remains
 * restricted: SOURCE spellings must not enter logs, reports or presentation.
 */
public record OrganizationSourceDecision(RelativePath source, Disposition disposition,
        Optional<Reason> reason, Optional<String> operationId) {
    public enum Disposition { COPY_PLANNED, SKIPPED_POLICY, BLOCKED }
    public enum Reason {
        NON_MRI_INPUT, CORRUPT_INPUT, INCONCLUSIVE_RECOGNITION,
        INVALID_FORMAT_EVIDENCE, DICOM_INSPECTION_UNAVAILABLE,
        MISSING_CONTENT_DIGEST, INVENTORY_INCOMPLETE, BATCH_BLOCKED
    }

    public OrganizationSourceDecision {
        if (source == null || source.root() != RelativePath.Root.SOURCE
                || disposition == null || reason == null || operationId == null) invalid();
        boolean valid = switch (disposition) {
            case COPY_PLANNED -> reason.isEmpty() && operationId.isPresent()
                    && operationId.orElseThrow().matches("[0-9a-f]{64}");
            case SKIPPED_POLICY -> operationId.isEmpty() && reason.equals(Optional.of(Reason.NON_MRI_INPUT));
            case BLOCKED -> operationId.isEmpty() && reason.isPresent() && reason.orElseThrow() != Reason.NON_MRI_INPUT;
        };
        if (!valid) invalid();
    }

    private static void invalid() { throw new IllegalArgumentException("Invalid organization planning contract"); }
}
