package org.cbihi.mrinormalizer.application.dataset.copy;

import java.util.Optional;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;

/** Restricted controlled fact; SOURCE and operation identity are never public diagnostic text. */
public record CopyFailure(Stage stage, Code code, Optional<RelativePath> source, Optional<String> operationId) {
    public enum Stage { DIGEST_ACQUISITION, REVALIDATION, COPY, OUTPUT_VERIFICATION, RECOVERY, CLEANUP, JOURNAL }
    public enum Code { INPUT_UNAVAILABLE, SOURCE_CHANGED, DISCOVERY_BINDING_UNPROVEN, CONTAINMENT_UNPROVEN,
        ACCESS_CONTROL_UNAVAILABLE, PROVIDER_UNQUALIFIED, RESOURCE_LIMIT, HASH_FAILED, READ_FAILED, WRITE_FAILED,
        OUTPUT_CONFLICT, VERIFICATION_FAILED, PUBLICATION_UNPROVEN, CLEANUP_FAILED, INTERRUPTED, JOURNAL_BLOCKED, RECOVERY_REQUIRED }
    public CopyFailure {
        if (stage == null || code == null || source == null || operationId == null
                || source.isPresent() && (!(((Optional<?>)source).get() instanceof RelativePath s) || s.root() != RelativePath.Root.SOURCE)
                || operationId.isPresent() && (!(((Optional<?>)operationId).get() instanceof String id) || !id.matches("[0-9a-f]{64}"))) invalid();
        boolean legal = switch (code) {
            case INPUT_UNAVAILABLE, SOURCE_CHANGED, HASH_FAILED -> stage == Stage.DIGEST_ACQUISITION || stage == Stage.REVALIDATION || stage == Stage.COPY || stage == Stage.RECOVERY;
            case DISCOVERY_BINDING_UNPROVEN -> stage == Stage.DIGEST_ACQUISITION || stage == Stage.REVALIDATION;
            case CONTAINMENT_UNPROVEN, ACCESS_CONTROL_UNAVAILABLE, PROVIDER_UNQUALIFIED, READ_FAILED -> stage != Stage.CLEANUP && stage != Stage.JOURNAL;
            case RESOURCE_LIMIT, INTERRUPTED, CLEANUP_FAILED -> stage != Stage.JOURNAL;
            case WRITE_FAILED, OUTPUT_CONFLICT, PUBLICATION_UNPROVEN -> stage == Stage.COPY;
            case VERIFICATION_FAILED -> stage == Stage.COPY || stage == Stage.OUTPUT_VERIFICATION || stage == Stage.RECOVERY;
            case JOURNAL_BLOCKED -> stage == Stage.JOURNAL;
            case RECOVERY_REQUIRED -> stage == Stage.COPY || stage == Stage.OUTPUT_VERIFICATION || stage == Stage.RECOVERY || stage == Stage.CLEANUP || stage == Stage.JOURNAL;
        };
        if (!legal) invalid();
    }
    private static void invalid() { throw new IllegalArgumentException("Invalid COPY failure fact"); }
}
