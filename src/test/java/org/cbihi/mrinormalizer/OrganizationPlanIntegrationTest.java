package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.*;
import static org.cbihi.mrinormalizer.OrganizationPlanModelTest.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.cbihi.mrinormalizer.application.dataset.dicom.*;
import org.cbihi.mrinormalizer.application.dataset.model.*;
import org.cbihi.mrinormalizer.application.dataset.organization.*;
import org.cbihi.mrinormalizer.application.dataset.organization.OrganizationPlan.Status;
import org.cbihi.mrinormalizer.application.dataset.organization.OrganizationSourceDecision.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.application.service.*;
import org.cbihi.mrinormalizer.domain.model.DetectionOutcome;
import org.junit.jupiter.api.Test;

class OrganizationPlanIntegrationTest {
    // F5-T36
    @Test void t36RealApplicationPipelinePreservesCoverageUnderPermutation() {
        var entries = new ArrayList<>(List.of(entry("a", DetectionOutcome.DICOM), entry("b", DetectionOutcome.DICOM), entry("c", DetectionOutcome.DICOM), entry("d", DetectionOutcome.DICOM), entry("n", DetectionOutcome.NIFTI)));
        var observations = Map.of(source("a"), success("a", "1.2", "1.3", "1.4"), source("b"), success("b", "1.2", "1.3", "1.5"), source("c"), success("c", "1.2", "1.9", "1.6"), source("d"), success("d", "1.9", "1.3", "1.7"));
        OrganizationPlan expected = null;
        for (int seed = 0; seed < 10; seed++) {
            Collections.shuffle(entries, new Random(seed)); var inv = new DatasetInventory(entries, List.of());
            var catalog = new DicomMetadataInspectionService(observations::get).inspect(inv);
            var disc = new DicomSeriesDiscoveryService().discover(catalog); var p = plan(inv, disc, digests(inv));
            assertEquals(3, disc.candidates().size()); assertEquals(5, p.copies().size()); assertSame(inv, p.inventory()); assertSame(disc, p.dicomDiscovery());
            assertEquals(new HashSet<>(inv.entries().stream().map(e -> e.source()).toList()), new HashSet<>(p.sourceDecisions().stream().map(x -> x.source()).toList()));
            if (expected == null) expected = p; else assertEquals(expected, p);
        }
    }
    // F5-T37
    @Test void t37MixedCohortPreservesFlagsAndBlocksWholeBatch() {
        var inv = inventory(entry("d1", DetectionOutcome.DICOM), entry("d2", DetectionOutcome.DICOM), entry("n", DetectionOutcome.NIFTI), entry("g", DetectionOutcome.NIFTI_GZ), entry("text", DetectionOutcome.UNKNOWN), entry("bad", DetectionOutcome.CORRUPT));
        var catalog = new DicomMetadataInspectionService(s -> success(s.path(), "1.2", "1.3", "1.4")).inspect(inv);
        var disc = new DicomSeriesDiscoveryService().discover(catalog); assertFalse(disc.candidates().getFirst().findings().isEmpty());
        var p = plan(inv, disc, digests(inv)); assertEquals(Status.BLOCKED, p.status()); assertTrue(p.copies().isEmpty());
        assertEquals(Optional.of(Reason.CORRUPT_INPUT), decision(p, "bad").reason()); assertEquals(Disposition.SKIPPED_POLICY, decision(p, "text").disposition());
        for (var path : List.of("d1", "d2", "n", "g")) assertEquals(Optional.of(Reason.BATCH_BLOCKED), decision(p, path).reason());
        assertSame(disc.candidates().getFirst().findings(), p.dicomDiscovery().candidates().getFirst().findings());
    }
    // F5-T38
    @Test void t38ByteIdenticalDistinctSourcesStayDistinct() {
        var inv = inventory(entry("first", DetectionOutcome.DICOM), entry("second", DetectionOutcome.DICOM));
        var catalog = new DicomMetadataInspectionService(s -> success(s.path(), "1.2", "1.3", "1.4")).inspect(inv);
        var p = plan(inv, new DicomSeriesDiscoveryService().discover(catalog), digests(inv));
        assertEquals(2, p.copies().size()); assertEquals(operation(p, "first").destination(), operation(p, "second").destination());
        assertNotEquals(operation(p, "first").operationId(), operation(p, "second").operationId());
        assertEquals(Set.of(source("first"), source("second")), new HashSet<>(p.copies().stream().map(x -> x.source()).toList()));
    }
    // F5-T39
    @Test void t39OmissionsAndIncompleteInventoryCannotProducePartialPlan() {
        var inv = inventory(entry("a", DetectionOutcome.DICOM), entry("b", DetectionOutcome.DICOM), entry("n", DetectionOutcome.NIFTI));
        var omitted = discovery(success("a", "1.2", "1.3", "1.4")); invalid(() -> plan(inv, omitted, digests(inv)));
        var failedInspection = discovery(success("a", "1.2", "1.3", "1.4"), failed("b", DicomMetadataInspection.FailureCode.INCOMPLETE_METADATA));
        assertTrue(plan(inv, failedInspection, digests(inv)).copies().isEmpty());
        var incomplete = new DatasetInventory(inv.entries(), List.of(new InventoryFailure(InventoryFailure.Code.TRAVERSAL_FAILED, Optional.empty())));
        invalid(() -> plan(incomplete, omitted, digests(inv)));
        var p = plan(incomplete, failedInspection, digests(inv)); assertTrue(p.copies().isEmpty()); assertEquals(3, p.sourceDecisions().size());
        for (var d : p.sourceDecisions()) assertEquals(Optional.of(Reason.INVENTORY_INCOMPLETE), d.reason());
    }
    // F5-T40
    @Test void t40DigestOnlyLogicalReferencesNeedNoFilesystem() {
        var inv = inventory(entry("never-created/imaging-file", DetectionOutcome.DICOM)); var calls = new AtomicInteger();
        var catalog = new DicomMetadataInspectionService(s -> { calls.incrementAndGet(); return success(s.path(), "1.2", "1.3", "1.4"); }).inspect(inv);
        var disc = new DicomSeriesDiscoveryService().discover(catalog); var supplied = new ContentDigest(Long.MAX_VALUE, "f".repeat(64));
        for (int i = 0; i < 3; i++) { var p = plan(inv, disc, Map.of(inv.entries().getFirst().source(), supplied)); assertSame(supplied, p.copies().getFirst().expectedDigest()); assertEquals(Status.COMPLETE, p.status()); }
        assertEquals(1, calls.get()); assertSame(catalog.inspections().getFirst().metadata().orElseThrow(), disc.candidates().getFirst().members().getFirst().metadata());
    }
}
