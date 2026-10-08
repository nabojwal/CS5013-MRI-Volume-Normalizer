package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.*;
import static org.cbihi.mrinormalizer.OrganizationPlanModelTest.*;
import java.time.Instant;
import java.util.*;
import org.cbihi.mrinormalizer.application.dataset.model.*;
import org.cbihi.mrinormalizer.application.dataset.organization.*;
import org.cbihi.mrinormalizer.application.dataset.organization.OrganizationPlan.Status;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.domain.model.DetectionOutcome;
import org.junit.jupiter.api.Test;

class OrganizationPlanCompatibilityTest {
    // F5-T31
    @Test void t31OneCopyMapsToOneF1Operation() {
        var inv = inventory(entry("a", DetectionOutcome.NIFTI), entry("d", DetectionOutcome.DICOM));
        var p = plan(inv, discovery(success("d", "1.2", "1.3", "1.4")), digests(inv)); var manifest = compose(p);
        assertEquals(p.copies().size(), manifest.operations().size());
        for (var op : p.copies()) {
            var f1 = manifest.operations().stream().filter(x -> x.operationId().equals(op.operationId())).findFirst().orElseThrow();
            assertEquals(ManifestOperation.Kind.COPY, f1.kind()); assertEquals(List.of(op.source()), f1.sources()); assertEquals(op.destination(), f1.destination());
        }
    }
    // F5-T32
    @Test void t32F1CarriesOriginalAssessmentAndExpectedDigest() {
        var inv = inventory(entry("a", DetectionOutcome.NIFTI)); var p = plan(inv, emptyDiscovery(), digests(inv)); var record = compose(p).sources().getFirst();
        assertSame(inv.entries().getFirst().assessment(), record.assessment()); assertSame(p.copies().getFirst().expectedDigest(), record.digest().orElseThrow()); assertTrue(record.failures().isEmpty());
    }
    // F5-T33
    @Test void t33CompatibleDestinationSharingPreservesMappings() {
        var inv = inventory(entry("a", DetectionOutcome.DICOM), entry("b", DetectionOutcome.DICOM));
        var p = plan(inv, discovery(success("a", "1.2", "1.3", "1.4"), success("b", "1.2", "1.3", "1.4")), digests(inv));
        var f1 = compose(p); assertEquals(2, f1.sources().size()); assertEquals(2, f1.operations().size());
        assertEquals(1, f1.operations().stream().map(x -> x.destination()).distinct().count());
    }
    // F5-T34
    @Test void t34RejectIncompatibleAndStructuralCollisions() {
        var a = copy(source("a"), "shared", "a");
        var different = new OrganizationCopyOperation("b".repeat(64), source("b"), output("shared"), new ContentDigest(8, "a".repeat(64)));
        rejectPair(inventory(entry("a", DetectionOutcome.NIFTI), entry("b", DetectionOutcome.NIFTI)), emptyDiscovery(), a, different);
        different = new OrganizationCopyOperation("b".repeat(64), source("b"), output("shared"), new ContentDigest(7, "b".repeat(64)));
        rejectPair(inventory(entry("a", DetectionOutcome.NIFTI), entry("b", DetectionOutcome.NIFTI)), emptyDiscovery(), a, different);
        rejectPair(inventory(entry("a", DetectionOutcome.NIFTI), entry("b", DetectionOutcome.NIFTI_GZ)), emptyDiscovery(), a, copy(source("b"), "shared", "b"));
        var dicomInv = inventory(entry("a", DetectionOutcome.DICOM), entry("b", DetectionOutcome.DICOM));
        for (var disc : List.of(discovery(success("a", "1.2", "1.3", "1.4"), success("b", "1.9", "1.3", "1.4")),
                discovery(success("a", "1.2", "1.3", "1.4"), success("b", "1.2", "1.9", "1.4")),
                discovery(success("a", "1.2", "1.3", "1.4"), success("b", "1.2", "1.3", "1.9")))) rejectPair(dicomInv, disc, a, copy(source("b"), "shared", "b"));
        var inv = inventory(entry("a", DetectionOutcome.NIFTI), entry("b", DetectionOutcome.NIFTI));
        rejectPair(inv, emptyDiscovery(), a, copy(source("b"), "shared/child", "b"));
        rejectPair(inv, emptyDiscovery(), a, copy(source("b"), "SHARED", "b"));
        rejectPair(inv, emptyDiscovery(), a, copy(source("b"), "SHARED/child", "b"));
        rejectPair(inv, emptyDiscovery(), a, copy(source("b"), "other", "a"));
        var noDigest = List.of(new SourceFileRecord(source("a"), Optional.empty(), inv.entries().getFirst().assessment(), List.of()),
                new SourceFileRecord(source("b"), Optional.of(digest()), inv.entries().getLast().assessment(), List.of()));
        var operations = List.of(new ManifestOperation(a.operationId(), ManifestOperation.Kind.COPY, List.of(a.source()), a.destination()),
                new ManifestOperation("b".repeat(64), ManifestOperation.Kind.COPY, List.of(source("b")), a.destination()));
        assertThrows(IllegalArgumentException.class, () -> new ProvenanceManifest(1, new UUID(0, 1), Instant.EPOCH, noDigest, operations));
        invalid(() -> new OrganizationCopyOperation("a".repeat(64), source("a"), output("shared"), null));
    }
    // F5-T35
    @Test void t35NoExecutionOrPersistenceFactsAreManufactured() {
        var inv = inventory(entry("a", DetectionOutcome.CORRUPT)); var p = plan(inv, emptyDiscovery(), Map.of());
        assertTrue(p.copies().isEmpty()); assertSame(inv, p.inventory()); assertTrue(p.inventory().failures().isEmpty());
        var componentNames = Arrays.stream(OrganizationPlan.class.getRecordComponents()).map(x -> x.getName()).toList();
        for (String banned : List.of("receipt", "checkpoint", "success", "outputDigest", "validationReport", "manifest")) assertFalse(componentNames.contains(banned));
        assertEquals(Status.BLOCKED, p.status());
    }
    private static void rejectPair(DatasetInventory inv, org.cbihi.mrinormalizer.application.dataset.dicom.DicomSeriesDiscovery disc, OrganizationCopyOperation a, OrganizationCopyOperation b) {
        invalid(() -> manual(inv, disc, Status.COMPLETE, List.of(linked(a), linked(b)), List.of(a, b)));
    }
    // Test-only future-owner composition; no F1 composition API is introduced in F5.
    private static ProvenanceManifest compose(OrganizationPlan p) {
        assertEquals(Status.COMPLETE, p.status());
        var sources = p.copies().stream().map(op -> new SourceFileRecord(op.source(), Optional.of(op.expectedDigest()),
                p.inventory().entries().stream().filter(e -> e.source().equals(op.source())).findFirst().orElseThrow().assessment(), List.<ManifestFailure>of())).toList();
        var ops = p.copies().stream().map(op -> new ManifestOperation(op.operationId(), ManifestOperation.Kind.COPY, List.of(op.source()), op.destination())).toList();
        return new ProvenanceManifest(1, new UUID(0, 1), Instant.EPOCH, sources, ops);
    }
}
