package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.*;
import static org.cbihi.mrinormalizer.OrganizationPlanModelTest.*;
import java.util.*;
import org.cbihi.mrinormalizer.application.dataset.dicom.*;
import org.cbihi.mrinormalizer.application.dataset.model.*;
import org.cbihi.mrinormalizer.application.dataset.organization.*;
import org.cbihi.mrinormalizer.application.dataset.organization.OrganizationPlan.Status;
import org.cbihi.mrinormalizer.application.dataset.organization.OrganizationSourceDecision.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.domain.model.*;
import org.junit.jupiter.api.Test;

class OrganizationPlanningServiceTest {
    // F5-T07
    @Test void t07RecognizedWrappersAreCopyEligible() {
        var inv = inventory(entry("d", DetectionOutcome.DICOM), entry("n", DetectionOutcome.NIFTI), entry("g", DetectionOutcome.NIFTI_GZ));
        var disc = discovery(success("d", "1.2", "1.3", "1.4")); var p = plan(inv, disc, digests(inv));
        assertEquals(Status.COMPLETE, p.status()); assertEquals(3, p.copies().size());
        for (var e : inv.entries()) { assertEquals(Disposition.COPY_PLANNED, decision(p, e.source().path()).disposition()); assertEquals(digest(), operation(p, e.source().path()).expectedDigest()); }
    }
    // F5-T08
    @Test void t08ExactDicomCoverage() {
        var inv = inventory(entry("a", DetectionOutcome.DICOM), entry("b", DetectionOutcome.NIFTI));
        invalid(() -> plan(inv, emptyDiscovery(), digests(inv)));
        invalid(() -> plan(inv, discovery(success("a", "1.2", "1.3", "1.4"), success("b", "1.2", "1.3", "1.5")), digests(inv)));
        invalid(() -> plan(inv, discovery(success("other", "1.2", "1.3", "1.4")), digests(inv)));
        assertEquals(Status.COMPLETE, plan(inv, discovery(success("a", "1.2", "1.3", "1.4")), digests(inv)).status());
    }
    // F5-T09
    @Test void t09EmptyDicomSetAndEmptyInventory() {
        assertEquals(Status.COMPLETE, plan(inventory(), emptyDiscovery(), Map.of()).status());
        var inv = inventory(entry("n", DetectionOutcome.NIFTI)); assertEquals(1, plan(inv, emptyDiscovery(), digests(inv)).copies().size());
        invalid(() -> plan(inv, discovery(failed("extra", DicomMetadataInspection.FailureCode.INCOMPLETE_METADATA)), Map.of()));
    }
    // F5-T10
    @Test void t10InventoryFailureTakesGlobalPrecedence() {
        var entries = List.of(entry("copy", DetectionOutcome.NIFTI), entry("skip", DetectionOutcome.UNKNOWN), entry("bad", DetectionOutcome.CORRUPT));
        for (var code : InventoryFailure.Code.values()) {
            var failure = new InventoryFailure(code, Optional.empty()); var inv = new DatasetInventory(entries, List.of(failure));
            var p = plan(inv, emptyDiscovery(), Map.of()); assertEquals(Status.BLOCKED, p.status()); assertTrue(p.copies().isEmpty());
            assertSame(inv, p.inventory()); assertSame(failure, p.inventory().failures().getFirst());
            for (var d : p.sourceDecisions()) assertEquals(Optional.of(Reason.INVENTORY_INCOMPLETE), d.reason());
        }
        var rootFailed = new DatasetInventory(List.of(), List.of(new InventoryFailure(InventoryFailure.Code.TRAVERSAL_FAILED, Optional.empty())));
        assertEquals(Status.BLOCKED, plan(rootFailed, emptyDiscovery(), Map.of()).status());
        assertTrue(plan(rootFailed, emptyDiscovery(), Map.of()).sourceDecisions().isEmpty());
    }
    // F5-T11
    @Test void t11OriginalInspectionFailuresRemainUnassigned() {
        for (var code : DicomMetadataInspection.FailureCode.values()) {
            var inv = inventory(entry("d", DetectionOutcome.DICOM)); var failure = failed("d", code); var disc = discovery(failure);
            var p = plan(inv, disc, digests(inv)); assertSame(disc, p.dicomDiscovery()); assertSame(failure, p.dicomDiscovery().unassigned().getFirst());
            assertEquals(Optional.of(Reason.DICOM_INSPECTION_UNAVAILABLE), decision(p, "d").reason()); assertTrue(p.copies().isEmpty());
        }
    }
    // F5-T12
    @Test void t12ScreeningFindingsDoNotProhibitLosslessCopy() {
        var inv = inventory(entry("a", DetectionOutcome.DICOM), entry("b", DetectionOutcome.DICOM));
        var base = discovery(success("a", "1.2", "1.3", "1.4"), success("b", "1.2", "1.3", "1.4"));
        var c = base.candidates().getFirst(); var findings = new ArrayList<DicomSeriesScreeningFinding>();
        for (var reason : DicomSeriesScreeningFinding.Reason.values()) findings.add(new DicomSeriesScreeningFinding(reason.ordinal() < 8 ? DicomSeriesScreeningFinding.Code.AMBIGUOUS_SERIES : DicomSeriesScreeningFinding.Code.MULTIDIMENSIONAL_SERIES, reason));
        var disc = new DicomSeriesDiscovery(List.of(new DicomSeriesCandidate(c.key(), c.members(), findings)), List.of());
        var p = plan(inv, disc, digests(inv)); assertEquals(2, p.copies().size()); assertSame(disc, p.dicomDiscovery());
        assertEquals(findings, p.dicomDiscovery().candidates().getFirst().findings());
        for (var e : p.inventory().entries()) assertEquals(ConversionReadiness.REQUIRES_VALIDATION, e.assessment().readiness());
    }
    // F5-T13
    @Test void t13ConclusiveNonMriIsPolicySkip() {
        var p = plan(inventory(entry("text.dcm", DetectionOutcome.UNKNOWN)), emptyDiscovery(), Map.of());
        var d = decision(p, "text.dcm"); assertEquals(Disposition.SKIPPED_POLICY, d.disposition()); assertEquals(Optional.of(Reason.NON_MRI_INPUT), d.reason()); assertTrue(d.operationId().isEmpty());
    }
    // F5-T14
    @Test void t14CorruptInconclusiveAndInvalidEvidenceBlock() {
        var p = plan(inventory(entry("bad", DetectionOutcome.CORRUPT)), emptyDiscovery(), Map.of());
        assertEquals(Optional.of(Reason.CORRUPT_INPUT), decision(p, "bad").reason());
        for (var diag : List.of(DetectionDiagnostic.INPUT_TOO_LARGE, DetectionDiagnostic.IO_ERROR, DetectionDiagnostic.EMPTY_INPUT)) {
            var e = new InventoryEntry(source("unknown"), FormatAssessment.fromDetection(DetectionResult.unknown(diag)));
            var blocked = plan(inventory(e), emptyDiscovery(), Map.of());
            // F2 complete() is false: frozen whole-inventory precedence applies first.
            assertEquals(Optional.of(Reason.INVENTORY_INCOMPLETE), decision(blocked, "unknown").reason()); assertTrue(blocked.copies().isEmpty());
        }
        var raw = DetectionResult.identified(DetectionOutcome.NIFTI);
        var assessed = new FormatAssessment(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE, ValidityStatus.INVALID, SupportStatus.NOT_ASSESSED, ConversionReadiness.BLOCKED, List.of(AssessmentReason.VALIDATION_FAILED));
        var invalid = plan(inventory(new InventoryEntry(source("invalid"), assessed)), emptyDiscovery(), Map.of());
        assertEquals(Optional.of(Reason.INVALID_FORMAT_EVIDENCE), decision(invalid, "invalid").reason()); assertSame(assessed, invalid.inventory().entries().getFirst().assessment());
    }
    // F5-T15
    @Test void t15MissingDigestBlocksWithoutHashFailureEvidence() {
        for (var outcome : List.of(DetectionOutcome.NIFTI, DetectionOutcome.NIFTI_GZ, DetectionOutcome.DICOM)) {
            var inv = inventory(entry("a", outcome)); var disc = outcome == DetectionOutcome.DICOM ? discovery(success("a", "1.2", "1.3", "1.4")) : emptyDiscovery();
            var p = plan(inv, disc, Map.of()); assertEquals(Optional.of(Reason.MISSING_CONTENT_DIGEST), decision(p, "a").reason());
            assertTrue(p.copies().isEmpty()); assertTrue(p.inventory().failures().isEmpty()); assertTrue(p.dicomDiscovery().unassigned().isEmpty());
        }
    }
    // F5-T16
    @Test void t16RejectMalformedDigestMapsBeforeInventoryGating() {
        var inv = inventory(entry("a", DetectionOutcome.NIFTI));
        invalid(() -> plan(inv, emptyDiscovery(), Map.of(source("extra"), digest())));
        invalid(() -> plan(inv, emptyDiscovery(), Map.of(output("a"), digest())));
        var map = new HashMap<RelativePath, ContentDigest>(); map.put(null, digest()); invalid(() -> plan(inv, emptyDiscovery(), map));
        map.clear(); map.put(source("a"), null); invalid(() -> plan(inv, emptyDiscovery(), map));
        var failed = new DatasetInventory(inv.entries(), List.of(new InventoryFailure(InventoryFailure.Code.TRAVERSAL_FAILED, Optional.empty())));
        invalid(() -> plan(failed, emptyDiscovery(), Map.of(source("extra"), digest())));
        var callerMap = new HashMap<>(digests(inv)); var p = plan(inv, emptyDiscovery(), callerMap); callerMap.clear(); assertEquals(digest(), p.copies().getFirst().expectedDigest());
    }
    // F5-T17
    @Test void t17WholeBatchKeepsSpecificBlockersAndSkips() {
        var inv = inventory(entry("copy", DetectionOutcome.NIFTI), entry("missing", DetectionOutcome.NIFTI_GZ), entry("bad", DetectionOutcome.CORRUPT), entry("skip", DetectionOutcome.UNKNOWN));
        var p = plan(inv, emptyDiscovery(), Map.of(source("copy"), digest())); assertEquals(Status.BLOCKED, p.status()); assertTrue(p.copies().isEmpty());
        assertEquals(Optional.of(Reason.BATCH_BLOCKED), decision(p, "copy").reason());
        assertEquals(Optional.of(Reason.MISSING_CONTENT_DIGEST), decision(p, "missing").reason());
        assertEquals(Optional.of(Reason.CORRUPT_INPUT), decision(p, "bad").reason()); assertEquals(Disposition.SKIPPED_POLICY, decision(p, "skip").disposition());
    }
    // F5-T18
    @Test void t18AllSkippedIsLogicalCompletionOnly() {
        var inv = inventory(entry("a", DetectionOutcome.UNKNOWN), entry("b", DetectionOutcome.UNKNOWN)); var p = plan(inv, emptyDiscovery(), Map.of());
        assertEquals(Status.COMPLETE, p.status()); assertTrue(p.copies().isEmpty()); assertEquals(2, p.sourceDecisions().size());
        for (var e : p.inventory().entries()) assertEquals(ConversionReadiness.BLOCKED, e.assessment().readiness());
    }
    // F5-T19
    @Test void t19SupportAndReadinessAreNeverUpgraded() {
        var raw = DetectionResult.identified(DetectionOutcome.NIFTI);
        var unsupported = new FormatAssessment(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_2_SINGLE_FILE, ValidityStatus.NOT_ASSESSED, SupportStatus.UNSUPPORTED, ConversionReadiness.BLOCKED, List.of(AssessmentReason.UNSUPPORTED_FORMAT_VARIANT));
        var ready = new FormatAssessment(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE, ValidityStatus.VALID, SupportStatus.SUPPORTED, ConversionReadiness.READY, List.of());
        var inv = inventory(new InventoryEntry(source("unsupported"), unsupported), new InventoryEntry(source("already-ready"), ready), entry("pending", DetectionOutcome.NIFTI));
        var p = plan(inv, emptyDiscovery(), digests(inv)); assertEquals(3, p.copies().size());
        for (var e : inv.entries()) assertSame(e.assessment(), p.inventory().entries().stream().filter(x -> x.source().equals(e.source())).findFirst().orElseThrow().assessment());
        assertEquals(ConversionReadiness.BLOCKED, unsupported.readiness()); assertEquals(ConversionReadiness.REQUIRES_VALIDATION, entry("pending", DetectionOutcome.NIFTI).assessment().readiness());
    }
}
