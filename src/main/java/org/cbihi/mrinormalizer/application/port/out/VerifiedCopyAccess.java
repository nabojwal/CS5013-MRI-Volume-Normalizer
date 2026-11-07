package org.cbihi.mrinormalizer.application.port.out;

import java.util.*;
import org.cbihi.mrinormalizer.application.dataset.copy.*;
import org.cbihi.mrinormalizer.application.dataset.dicom.DicomSeriesDiscovery;
import org.cbihi.mrinormalizer.application.dataset.model.DatasetInventory;
import org.cbihi.mrinormalizer.application.dataset.organization.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.application.service.CopyExecutionService;

/** Exclusive-session original-byte access. Preparation alone grants no publication authority. */
public interface VerifiedCopyAccess extends AutoCloseable {
    SourceDigestBatch acquire(DatasetInventory inventory, DicomSeriesDiscovery discovery);
    List<CopyFailure> revalidate(OrganizationPlan plan, SourceDigestBatch evidence);
    PreparedCopy prepare(OrganizationCopyOperation operation);
    CopyExecutionResult inspect(OrganizationPlan plan, ManifestState replayed);
    void close();
    interface PreparedCopy extends AutoCloseable {
        ContentDigest verifiedSourceDigest();
        CopyOutcome publish(CopyExecutionService.AcknowledgedCopyIntent intent);
        void close();
    }
    enum Publication { NOT_PUBLISHED, VERIFIED, UNVERIFIED, UNKNOWN }
    record CopyOutcome(CopyExecutionResult.OperationResult operation, Publication publication, List<CopyFailure> failures) {
        public CopyOutcome {
            if (operation == null || publication == null || failures == null || failures.size() > 100001) invalid();
            var seen = new HashSet<CopyFailure>(); var list = new ArrayList<CopyFailure>();
            for (Object value : failures) { if (!(value instanceof CopyFailure f) || !seen.add(f)) invalid(); list.add((CopyFailure)value); }
            for (var f : list) if (f.operationId().filter(id -> !id.equals(operation.operationId())).isPresent()) invalid();
            list.sort(Comparator.comparingInt((CopyFailure f) -> f.stage().ordinal()).thenComparingInt(f -> f.code().ordinal())
                    .thenComparing(f -> f.operationId().orElse(""))
                    .thenComparing(f -> f.source().map(RelativePath::path).orElse(""), (a, b) -> Arrays.compareUnsigned(a.getBytes(java.nio.charset.StandardCharsets.UTF_8), b.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            failures = List.copyOf(list);
            if (publication == Publication.UNKNOWN && operation.outcome() != CopyExecutionResult.Outcome.RECOVERY_REQUIRED
                    || operation.outcome() == CopyExecutionResult.Outcome.NOT_STARTED
                    || failures.stream().anyMatch(f -> f.code() == CopyFailure.Code.CLEANUP_FAILED || f.code() == CopyFailure.Code.RECOVERY_REQUIRED)
                            && operation.outcome() != CopyExecutionResult.Outcome.RECOVERY_REQUIRED) invalid();
            boolean success = operation.outcome() == CopyExecutionResult.Outcome.COMPLETED || operation.outcome() == CopyExecutionResult.Outcome.IDENTICAL_EXISTING;
            if (success && (publication != Publication.VERIFIED || !failures.isEmpty())
                    || publication == Publication.VERIFIED && operation.outputDigest().isEmpty()
                    || publication != Publication.VERIFIED && operation.outputDigest().isPresent()
                    || !success && failures.isEmpty()) invalid();
        }
        private static void invalid() { throw new IllegalArgumentException("Invalid COPY publication fact"); }
    }
    final class AccessFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final List<CopyFailure> failures;
        public AccessFailure(List<CopyFailure> failures) {
            super("Verified COPY access failed", null, false, false);
            if (failures == null || failures.isEmpty() || failures.size() > 100001
                    || ((List<?>)failures).stream().anyMatch(f -> !(f instanceof CopyFailure)) || new HashSet<>(failures).size() != failures.size())
                throw new IllegalArgumentException("Invalid COPY access failure facts");
            this.failures = List.copyOf(failures);
        }
        public List<CopyFailure> failures() { return failures; }
    }
}
