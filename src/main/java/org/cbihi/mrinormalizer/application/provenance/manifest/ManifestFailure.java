package org.cbihi.mrinormalizer.application.provenance.manifest;

/** Workflow-only failure; original M5/M6/M7 diagnostics retain their owning evidence location. */
public record ManifestFailure(Phase phase, Code code) {

    public enum Phase { HASHING, EXECUTION, POST_WRITE_VALIDATION, PERSISTENCE }

    public enum Code {
        INVALID_MANIFEST, INVALID_REFERENCE, CONTAINMENT_UNPROVEN, INPUT_UNAVAILABLE,
        SOURCE_CHANGED, HASH_FAILED, CHECKPOINT_CONFLICT, WRITE_FAILED,
        PUBLICATION_UNAVAILABLE, READ_FAILED, UNSUPPORTED_SCHEMA, CORRUPT_CHECKPOINT,
        ACCESS_CONTROL_UNAVAILABLE, RESOURCE_LIMIT, CLEANUP_FAILED, RECOVERY_REQUIRED,
        OUTPUT_CONFLICT, VERIFICATION_FAILED
    }

    public ManifestFailure {
        if (phase == null || code == null) {
            throw new IllegalArgumentException("Failure components must be non-null");
        }
        boolean applicable = switch (code) {
            case INVALID_MANIFEST, CHECKPOINT_CONFLICT, WRITE_FAILED, PUBLICATION_UNAVAILABLE,
                    READ_FAILED, UNSUPPORTED_SCHEMA, CORRUPT_CHECKPOINT, ACCESS_CONTROL_UNAVAILABLE ->
                phase == Phase.PERSISTENCE;
            case INVALID_REFERENCE, INPUT_UNAVAILABLE, CONTAINMENT_UNPROVEN ->
                phase == Phase.HASHING || phase == Phase.EXECUTION || phase == Phase.PERSISTENCE;
            case SOURCE_CHANGED, HASH_FAILED -> phase == Phase.HASHING;
            case RESOURCE_LIMIT -> phase == Phase.HASHING || phase == Phase.PERSISTENCE;
            case CLEANUP_FAILED, RECOVERY_REQUIRED, OUTPUT_CONFLICT ->
                phase == Phase.EXECUTION || phase == Phase.PERSISTENCE;
            case VERIFICATION_FAILED -> phase == Phase.EXECUTION || phase == Phase.POST_WRITE_VALIDATION;
        };
        if (!applicable) {
            throw new IllegalArgumentException("Failure code does not apply to phase");
        }
    }
}
