package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Modifier;
import java.util.*;
import org.cbihi.mrinormalizer.application.dataset.copy.*;
import org.cbihi.mrinormalizer.application.dataset.dicom.*;
import org.cbihi.mrinormalizer.application.dataset.model.*;
import org.cbihi.mrinormalizer.application.port.out.VerifiedCopyAccess;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.application.service.CopyExecutionService;
import org.cbihi.mrinormalizer.domain.model.*;
import org.junit.jupiter.api.Test;

/** Tests authored before F6 production. Restricted fixtures contain no patient data. */
class VerifiedCopyModelTest {
    private static final RelativePath SOURCE = new RelativePath(RelativePath.Root.SOURCE, "synthetic.nii");
    private static final ContentDigest DIGEST = new ContentDigest(3, "a".repeat(64));
    private static final DicomSeriesDiscovery EMPTY = new DicomSeriesDiscovery(List.of(), List.of());
    private static DatasetInventory inventory() {
        return new DatasetInventory(List.of(new InventoryEntry(SOURCE,
                FormatAssessment.fromDetection(DetectionResult.identified(DetectionOutcome.NIFTI)))), List.of());
    }
    // T01/T08: inspect actual public declarations, not a parallel description.
    @Test void capabilityHasOnlyPrivateConstructorAndTwoAuthorityChecks() {
        var type = CopyExecutionService.AcknowledgedCopyIntent.class;
        assertEquals(0, type.getConstructors().length);
        assertTrue(Arrays.stream(type.getDeclaredConstructors()).allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        assertEquals(Set.of("claim", "checkLive"), new HashSet<>(Arrays.stream(type.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers())).map(m -> m.getName()).toList()));
        assertFalse(java.io.Serializable.class.isAssignableFrom(type));
        assertEquals(0, type.getFields().length);
        assertEquals(1, Arrays.stream(VerifiedCopyAccess.PreparedCopy.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("publish") && m.getParameterCount() == 1).count());
        assertFalse(Arrays.stream(VerifiedCopyAccess.PreparedCopy.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("publish") && m.getParameterCount() == 0));
    }
    // T02: all four caller bounds and their exact upper limits.
    @Test void finiteLimitsHaveNoDefaultAndRejectEveryInvalidDimension() {
        assertEquals(new CopyLimits(Long.MAX_VALUE, Long.MAX_VALUE, 1048576, 100000),
                new CopyLimits(Long.MAX_VALUE, Long.MAX_VALUE, 1048576, 100000));
        for (long bad : new long[] {0, -1, Long.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new CopyLimits(bad, 1, 1, 1));
            assertThrows(IllegalArgumentException.class, () -> new CopyLimits(1, bad, 1, 1));
        }
        for (int bad : new int[] {0, -1, 1048577, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> new CopyLimits(1, 1, bad, 1));
        for (int bad : new int[] {0, -1, 100001, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> new CopyLimits(1, 1, 1, bad));
        assertEquals(1, CopyLimits.class.getConstructors().length);
    }
    // T03/T07: complete exact handoff, defensive snapshots and no usable subset.
    @Test void completeDigestCoverageIsExactImmutableAndBlockedCannotHandoff() {
        var facts = new ArrayList<>(List.of(new SourceDigestBatch.DigestFact(SOURCE, DIGEST)));
        var batch = new SourceDigestBatch(SourceDigestBatch.Status.COMPLETE, inventory(), EMPTY, facts, List.of());
        facts.clear(); assertEquals(Map.of(SOURCE, DIGEST), batch.suppliedDigests());
        assertThrows(UnsupportedOperationException.class, () -> batch.suppliedDigests().clear());
        assertThrows(IllegalArgumentException.class, () -> new SourceDigestBatch(SourceDigestBatch.Status.COMPLETE,
                inventory(), EMPTY, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SourceDigestBatch(SourceDigestBatch.Status.COMPLETE,
                inventory(), EMPTY, List.of(batch.digests().getFirst(), batch.digests().getFirst()), List.of()));
        var blocked = new SourceDigestBatch(SourceDigestBatch.Status.BLOCKED, inventory(), EMPTY, batch.digests(),
                List.of(new CopyFailure(CopyFailure.Stage.DIGEST_ACQUISITION, CopyFailure.Code.READ_FAILED,
                        Optional.of(SOURCE), Optional.empty())));
        assertThrows(IllegalStateException.class, blocked::suppliedDigests);
    }
    // T04: predecessor blockers stay original facts, without synthetic hashing failures.
    @Test void predecessorFailureIsRetainedAndInconsistentCoverageRejected() {
        var original = new InventoryFailure(InventoryFailure.Code.TRAVERSAL_FAILED, Optional.empty());
        var inv = new DatasetInventory(inventory().entries(), List.of(original));
        var batch = new SourceDigestBatch(SourceDigestBatch.Status.BLOCKED, inv, EMPTY, List.of(), List.of());
        assertSame(inv, batch.inventory()); assertEquals(List.of(original), batch.inventory().failures());
        assertTrue(batch.failures().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new SourceDigestBatch(SourceDigestBatch.Status.COMPLETE,
                inv, EMPTY, List.of(new SourceDigestBatch.DigestFact(SOURCE, DIGEST)), List.of()));
        var dicom = new DatasetInventory(List.of(new InventoryEntry(SOURCE,
                FormatAssessment.fromDetection(DetectionResult.identified(DetectionOutcome.DICOM)))), List.of());
        assertThrows(IllegalArgumentException.class, () -> new SourceDigestBatch(SourceDigestBatch.Status.COMPLETE,
                dicom, EMPTY, List.of(new SourceDigestBatch.DigestFact(SOURCE, DIGEST)), List.of()));
    }
    // T05/T08: verified byte outcomes are not fictitious acknowledged job success.
    @Test void resultAndPublicationRejectUnmeasuredSuccess() {
        var id = "b".repeat(64);
        assertThrows(IllegalArgumentException.class, () -> new CopyExecutionResult.OperationResult(id,
                CopyExecutionResult.Outcome.COMPLETED, Optional.empty()));
        var operation = new CopyExecutionResult.OperationResult(id, CopyExecutionResult.Outcome.COMPLETED, Optional.of(DIGEST));
        assertThrows(IllegalArgumentException.class, () -> new CopyExecutionResult(CopyExecutionResult.Status.JOURNAL_COMPLETED,
                Optional.empty(), List.of(operation), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new CopyExecutionResult(CopyExecutionResult.Status.NOTHING_TO_COPY,
                Optional.empty(), List.of(operation), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new VerifiedCopyAccess.CopyOutcome(operation,
                VerifiedCopyAccess.Publication.UNKNOWN, List.of()));
    }
    // T06: exhaustive closed stage/code acceptance table.
    @Test void everyStageCodePairMatchesFrozenTable() {
        for (var code : CopyFailure.Code.values()) for (var stage : CopyFailure.Stage.values()) {
            String admitted = switch (code) {
                case INPUT_UNAVAILABLE, SOURCE_CHANGED -> "DIGEST_ACQUISITION REVALIDATION COPY RECOVERY";
                case DISCOVERY_BINDING_UNPROVEN -> "DIGEST_ACQUISITION REVALIDATION";
                case CONTAINMENT_UNPROVEN, ACCESS_CONTROL_UNAVAILABLE, PROVIDER_UNQUALIFIED,
                        READ_FAILED -> "DIGEST_ACQUISITION REVALIDATION COPY OUTPUT_VERIFICATION RECOVERY";
                case RESOURCE_LIMIT, INTERRUPTED, CLEANUP_FAILED -> "DIGEST_ACQUISITION REVALIDATION COPY OUTPUT_VERIFICATION RECOVERY CLEANUP";
                case HASH_FAILED -> "DIGEST_ACQUISITION REVALIDATION COPY RECOVERY";
                case WRITE_FAILED, OUTPUT_CONFLICT, PUBLICATION_UNPROVEN -> "COPY";
                case VERIFICATION_FAILED -> "COPY OUTPUT_VERIFICATION RECOVERY";
                case JOURNAL_BLOCKED -> "JOURNAL";
                case RECOVERY_REQUIRED -> "COPY OUTPUT_VERIFICATION RECOVERY CLEANUP JOURNAL";
            };
            if (Arrays.asList(admitted.split(" ")).contains(stage.name()))
                assertEquals(code, new CopyFailure(stage, code, Optional.of(SOURCE), Optional.of("c".repeat(64))).code());
            else assertThrows(IllegalArgumentException.class,
                    () -> new CopyFailure(stage, code, Optional.of(SOURCE), Optional.empty()));
        }
        assertThrows(IllegalArgumentException.class, () -> new CopyFailure(CopyFailure.Stage.COPY,
                CopyFailure.Code.READ_FAILED, Optional.of(new RelativePath(RelativePath.Root.OUTPUT, "output")), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new CopyFailure(CopyFailure.Stage.COPY,
                CopyFailure.Code.READ_FAILED, Optional.empty(), Optional.of("patient-sentinel")));
    }
    // T07: sorting must follow unsigned UTF-8, not UTF-16 surrogate order.
    @Test void digestFactsAreOrderedByUnsignedUtf8AndDefensivelyRetained() {
        var a = new RelativePath(RelativePath.Root.SOURCE, "\uE000.nii");
        var b = new RelativePath(RelativePath.Root.SOURCE, "\uD800\uDC00.nii");
        var assessment = inventory().entries().getFirst().assessment();
        var inv = new DatasetInventory(List.of(new InventoryEntry(b, assessment), new InventoryEntry(a, assessment)), List.of());
        var batch = new SourceDigestBatch(SourceDigestBatch.Status.COMPLETE, inv, EMPTY,
                List.of(new SourceDigestBatch.DigestFact(b, DIGEST), new SourceDigestBatch.DigestFact(a, DIGEST)), List.of());
        assertEquals(List.of(a, b), new ArrayList<>(batch.suppliedDigests().keySet()));
    }
    // T08/T61: no raw cause, suppression or stack payload on the closed I/O exception.
    @Test void accessFailureHasFixedNonIdentifyingClosedSurface() {
        var failure = new CopyFailure(CopyFailure.Stage.COPY, CopyFailure.Code.READ_FAILED, Optional.of(SOURCE), Optional.empty());
        var error = new VerifiedCopyAccess.AccessFailure(List.of(failure));
        assertNull(error.getCause()); assertEquals(0, error.getStackTrace().length);
        error.addSuppressed(new IllegalStateException("patient-sentinel")); assertEquals(0, error.getSuppressed().length);
        assertFalse(error.toString().contains(SOURCE.path())); assertFalse(error.toString().contains("patient-sentinel"));
        assertThrows(UnsupportedOperationException.class, () -> error.failures().clear());
    }

    @Test void malformedGenericPayloadsRejectWithoutProviderOrCastMessages() {
        assertThrows(IllegalArgumentException.class, () -> new CopyFailure(CopyFailure.Stage.COPY, CopyFailure.Code.READ_FAILED,
                (Optional)Optional.of("restricted-root-sentinel"), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new CopyExecutionResult.OperationResult("a".repeat(64),
                CopyExecutionResult.Outcome.FAILED, (Optional)Optional.of("digest-sentinel")));
        assertThrows(IllegalArgumentException.class, () -> new CopyExecutionResult(CopyExecutionResult.Status.RECOVERY_REQUIRED,
                (Optional)Optional.of("journal-sentinel"), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new VerifiedCopyAccess.AccessFailure((List)List.of("provider-sentinel")));
    }
    @Test void failureOrderingUsesUnsignedUtf8AndPublicationRejectsForeignIdentity() {
        var a = new RelativePath(RelativePath.Root.SOURCE, "\uE000.nii");
        var b = new RelativePath(RelativePath.Root.SOURCE, "\uD800\uDC00.nii");
        var first = new CopyFailure(CopyFailure.Stage.COPY, CopyFailure.Code.READ_FAILED, Optional.of(a), Optional.empty());
        var second = new CopyFailure(CopyFailure.Stage.COPY, CopyFailure.Code.READ_FAILED, Optional.of(b), Optional.empty());
        var r = new CopyExecutionResult(CopyExecutionResult.Status.FAILED, Optional.empty(), List.of(), List.of(second, first));
        assertEquals(List.of(first, second), r.failures());
        var op = new CopyExecutionResult.OperationResult("a".repeat(64), CopyExecutionResult.Outcome.FAILED, Optional.empty());
        var foreign = new CopyFailure(CopyFailure.Stage.COPY, CopyFailure.Code.READ_FAILED, Optional.empty(), Optional.of("b".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> new VerifiedCopyAccess.CopyOutcome(op, VerifiedCopyAccess.Publication.NOT_PUBLISHED, List.of(foreign)));
    }


    @Test void publicationCertaintyCannotContradictOperationOutcome() {
        var id = "d".repeat(64); var failure = new CopyFailure(CopyFailure.Stage.COPY, CopyFailure.Code.PUBLICATION_UNPROVEN, Optional.empty(), Optional.of(id));
        var knownFailed = new CopyExecutionResult.OperationResult(id, CopyExecutionResult.Outcome.FAILED, Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> new VerifiedCopyAccess.CopyOutcome(knownFailed, VerifiedCopyAccess.Publication.UNKNOWN, List.of(failure)));
        var cleanup = new CopyFailure(CopyFailure.Stage.CLEANUP, CopyFailure.Code.CLEANUP_FAILED, Optional.empty(), Optional.of(id));
        assertThrows(IllegalArgumentException.class, () -> new VerifiedCopyAccess.CopyOutcome(knownFailed, VerifiedCopyAccess.Publication.NOT_PUBLISHED, List.of(cleanup)));
        var uncertain = new CopyExecutionResult.OperationResult(id, CopyExecutionResult.Outcome.RECOVERY_REQUIRED, Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> new CopyExecutionResult(CopyExecutionResult.Status.FAILED, Optional.empty(), List.of(uncertain), List.of(failure)));
    }

}
