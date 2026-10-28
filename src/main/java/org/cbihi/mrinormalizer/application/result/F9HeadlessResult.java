package org.cbihi.mrinormalizer.application.result;

import java.util.List;
import java.util.Optional;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestReceipt;

/** Public-safe controlled outcome: no identifiers, exception text, or paths. */
public record F9HeadlessResult(Status status, List<Reason> reasons,
        Optional<ManifestReceipt> acknowledgedHead) {
    public enum Status { VERIFIED_PUBLISHED, FAILED, RECOVERY_REQUIRED }
    public enum Reason {
        INPUT_OR_SELECTION_INVALID, INVENTORY_INCOMPLETE, METADATA_OR_SCREENING_FAILED,
        SELECTED_MEMBERS_MISMATCH, SOURCE_EVIDENCE_UNAVAILABLE,
        PLAN_OR_INTENT_NOT_ACKNOWLEDGED, CONVERSION_OR_VERIFICATION_FAILED,
        PUBLICATION_UNCERTAIN, FINAL_JOB_ACK_UNCERTAIN,
        RESOURCE_LIMIT, INTERRUPTED, INTERNAL_ERROR
    }
    public F9HeadlessResult {
        if (status == null || reasons == null || acknowledgedHead == null ||
                reasons.stream().anyMatch(java.util.Objects::isNull) || new java.util.HashSet<>(reasons).size() != reasons.size() ||
                (status == Status.VERIFIED_PUBLISHED) != reasons.isEmpty()) {
            throw new IllegalArgumentException("Invalid F9 public result");
        }
        reasons = List.copyOf(reasons);
    }
    public static F9HeadlessResult success(ManifestReceipt head) {
        return new F9HeadlessResult(Status.VERIFIED_PUBLISHED, List.of(), Optional.of(head));
    }
    public static F9HeadlessResult failed(Reason reason) {
        return new F9HeadlessResult(Status.FAILED, List.of(reason), Optional.empty());
    }
    public static F9HeadlessResult uncertain(Reason reason, Optional<ManifestReceipt> head) {
        return new F9HeadlessResult(Status.RECOVERY_REQUIRED, List.of(reason), head);
    }
}
