package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.*;
import static org.cbihi.mrinormalizer.OrganizationPlanModelTest.*;
import java.util.*;
import org.cbihi.mrinormalizer.application.dataset.dicom.*;
import org.cbihi.mrinormalizer.application.dataset.model.*;
import org.cbihi.mrinormalizer.application.dataset.organization.OrganizationCopyOperation;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.application.service.OrganizationPlanningService;
import org.cbihi.mrinormalizer.domain.model.DetectionOutcome;
import org.junit.jupiter.api.Test;

class OrganizationNamingTest {
    // Independently computed with Python hashlib + struct.pack('>I') from the frozen specification.
    private static final String STUDY = "8a3178316aa34de4a4568b76ee42f75920eec9a263c8b997248df9011bcfdda9";
    private static final String SERIES = "f4076cfa04031fc34572258e400586a93f0f05bd4f14e41dcfb704d72a0e7219";
    private static final String SOP = "296801d12dbf082fad64911c1def21bc32413146035dbf8f4151f75a345c314e";
    private static final String DICOM_DEST = "layout-v1/dicom/study-" + STUDY + "/series-" + SERIES + "/sop-" + SOP + "-sha256-" + "a".repeat(64) + ".dcm";
    // F5-T20
    @Test void t20ExactDicomLayout() { assertEquals(DICOM_DEST, dicom("a", "1.2", "1.3", "1.4", digest()).destination().path()); }
    // F5-T21
    @Test void t21WrappersComeFromRecognition() {
        for (var outcome : List.of(DetectionOutcome.NIFTI, DetectionOutcome.NIFTI_GZ)) {
            var inv = inventory(entry("misleading.dcm", outcome)); var p = plan(inv, emptyDiscovery(), digests(inv));
            assertEquals("layout-v1/nifti/sha256-" + "a".repeat(64) + (outcome == DetectionOutcome.NIFTI ? ".nii" : ".nii.gz"), p.copies().getFirst().destination().path());
            assertEquals(digest(), p.copies().getFirst().expectedDigest());
        }
    }
    // F5-T22
    @Test void t22IndependentGoldenVectorsAndDomains() {
        var op = dicom("a", "1.2", "1.3", "1.4", digest());
        assertEquals("a1365a142f8f8d985de4aa62edb43e4806ab88037858d3967fca8490a1cd7f17", op.operationId());
        assertEquals(DICOM_DEST, op.destination().path()); assertEquals(3, Set.of(STUDY, SERIES, SOP).size());
        // Identical UID spelling appears in all domains; none of their outputs may alias.
        var repeated = dicom("same", "1.2", "1.2", "1.2", digest()).destination().path().split("/");
        assertNotEquals(repeated[2].substring(6), repeated[3].substring(7));
        assertNotEquals(repeated[3].substring(7), repeated[4].substring(4, 68));
    }
    // F5-T23
    @Test void t23LengthFramingPreventsConcatenationAmbiguity() {
        assertEquals("1" + "23", "12" + "3");
        var left = dicom("a", "1", "23", "4", digest()).destination().path().split("/")[3];
        var right = dicom("a", "12", "3", "4", digest()).destination().path().split("/")[3]; assertNotEquals(left, right);
    }
    // F5-T24
    @Test void t24ExactUtf8AndCanonicalDecimalGoldenVectors() {
        var inv = inventory(entry("é/𐀀", DetectionOutcome.NIFTI));
        assertEquals("6558558d0deaf62e8c16035741113b4baaad67d69acadeb15f01810d2e5e8e93", plan(inv, emptyDiscovery(), Map.of(source("é/𐀀"), new ContentDigest(0, "a".repeat(64)))).copies().getFirst().operationId());
        assertEquals("87f665878260716f93cb69d43dc47b56cb38bfc6fc8e9fb0390af4f824be693a", plan(inv, emptyDiscovery(), Map.of(source("é/𐀀"), new ContentDigest(Long.MAX_VALUE, "a".repeat(64)))).copies().getFirst().operationId());
        var decomposed = inventory(entry("e\u0301/𐀀", DetectionOutcome.NIFTI));
        assertNotEquals(plan(inv, emptyDiscovery(), digests(inv)).copies().getFirst().operationId(), plan(decomposed, emptyDiscovery(), digests(decomposed)).copies().getFirst().operationId());
    }
    // F5-T25
    @Test void t25PermutationInvariantAndReusableStatelessService() {
        var entries = new ArrayList<>(List.of(entry("a", DetectionOutcome.DICOM), entry("b", DetectionOutcome.DICOM), entry("c", DetectionOutcome.NIFTI), entry("skip", DetectionOutcome.UNKNOWN)));
        var inspections = new ArrayList<>(List.of(success("a", "1.2", "1.3", "1.4"), success("b", "1.9", "1.3", "1.5")));
        var inv = new DatasetInventory(entries, List.of()); var disc = discovery(inspections.toArray(DicomMetadataInspection[]::new));
        var expected = plan(inv, disc, digests(inv)); var service = new OrganizationPlanningService();
        for (int seed = 0; seed < 12; seed++) {
            Collections.shuffle(entries, new Random(seed)); Collections.shuffle(inspections, new Random(seed + 3));
            var keys = new ArrayList<>(digests(inv).keySet()); Collections.shuffle(keys, new Random(seed + 7));
            var map = new LinkedHashMap<RelativePath, ContentDigest>(); for (var key : keys) map.put(key, digest());
            assertEquals(expected, service.plan(new DatasetInventory(entries, List.of()), discovery(inspections.toArray(DicomMetadataInspection[]::new)), map));
        }
    }
    // F5-T26
    @Test void t26RenamingSourceDoesNotRenameDestination() {
        var a = dicom("a", "1.2", "1.3", "1.4", digest()); var b = dicom("other/name.nii", "1.2", "1.3", "1.4", digest());
        assertEquals(a.destination(), b.destination()); assertNotEquals(a.operationId(), b.operationId()); assertEquals(source("other/name.nii"), b.source());
    }
    // F5-T27
    @Test void t27DigestAndEachTechnicalUidAffectDestination() {
        var base = dicom("a", "1.2", "1.3", "1.4", digest());
        for (var changed : List.of(dicom("a", "1.9", "1.3", "1.4", digest()), dicom("a", "1.2", "1.9", "1.4", digest()), dicom("a", "1.2", "1.3", "1.9", digest()), dicom("a", "1.2", "1.3", "1.4", new ContentDigest(7, "b".repeat(64))))) assertNotEquals(base.destination(), changed.destination());
    }
    // F5-T28
    @Test void t28IdenticalBytesRetainDistinctSourceOperations() {
        var inv = inventory(entry("a", DetectionOutcome.NIFTI), entry("b", DetectionOutcome.NIFTI)); var p = plan(inv, emptyDiscovery(), digests(inv));
        assertEquals(2, p.copies().size()); assertEquals(1, p.copies().stream().map(x -> x.destination()).distinct().count()); assertEquals(2, p.copies().stream().map(x -> x.operationId()).distinct().count());
        assertEquals(2, p.sourceDecisions().size());
    }
    // F5-T29
    @Test void t29NamingHasNoAmbientInputsOrExtensionDependency() {
        var a = dicom("input.nii.gz", "1.2", "1.3", "1.4", digest()); var b = dicom("opaque", "1.2", "1.3", "1.4", digest());
        assertEquals(a.destination(), b.destination()); assertEquals(a, dicom("input.nii.gz", "1.2", "1.3", "1.4", digest()));
        assertEquals(64, a.operationId().length()); assertTrue(a.destination().path().contains("a".repeat(64)));
    }
    // F5-T30
    @Test void t30PortableLowercaseFullWidthOutputNames() {
        var op = dicom("INPUT/é/𐀀", "1".repeat(64), "2".repeat(64), "3".repeat(64), digest()); var path = op.destination().path();
        assertTrue(path.matches("layout-v1/dicom/study-[0-9a-f]{64}/series-[0-9a-f]{64}/sop-[0-9a-f]{64}-sha256-[0-9a-f]{64}\\.dcm"));
        assertTrue(path.length() < 4096); assertEquals(output(path), op.destination());
        assertFalse(path.contains("1".repeat(64))); assertFalse(path.contains("INPUT")); assertEquals(source("INPUT/é/𐀀"), op.source());
    }
    private static OrganizationCopyOperation dicom(String path, String study, String series, String sop, ContentDigest digest) {
        return plan(inventory(entry(path, DetectionOutcome.DICOM)), discovery(success(path, study, series, sop)), Map.of(source(path), digest)).copies().getFirst();
    }
}
