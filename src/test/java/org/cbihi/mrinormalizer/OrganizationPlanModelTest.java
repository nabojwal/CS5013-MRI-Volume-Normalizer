package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.cbihi.mrinormalizer.application.dataset.dicom.*;
import org.cbihi.mrinormalizer.application.dataset.model.*;
import org.cbihi.mrinormalizer.application.dataset.organization.*;
import org.cbihi.mrinormalizer.application.dataset.organization.OrganizationPlan.Status;
import org.cbihi.mrinormalizer.application.dataset.organization.OrganizationSourceDecision.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.application.service.*;
import org.cbihi.mrinormalizer.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

public class OrganizationPlanModelTest {
    // F5-T01
    @Test void t01FrozenApi() throws Exception {
        shape(OrganizationCopyOperation.class, "operationId", "source", "destination", "expectedDigest");
        shape(OrganizationSourceDecision.class, "source", "disposition", "reason", "operationId");
        shape(OrganizationPlan.class, "layoutVersion", "status", "inventory", "dicomDiscovery", "sourceDecisions", "copies");
        assertEquals(List.of(String.class, RelativePath.class, RelativePath.class, ContentDigest.class),
                Arrays.stream(OrganizationCopyOperation.class.getRecordComponents()).map(c -> c.getType()).toList());
        assertEquals(List.of(RelativePath.class.getName(), Disposition.class.getName(),
                "java.util.Optional<" + Reason.class.getName() + ">", "java.util.Optional<java.lang.String>"), generic(OrganizationSourceDecision.class));
        assertEquals(List.of("int", Status.class.getName(), DatasetInventory.class.getName(), DicomSeriesDiscovery.class.getName(),
                "java.util.List<" + OrganizationSourceDecision.class.getName() + ">", "java.util.List<" + OrganizationCopyOperation.class.getName() + ">"), generic(OrganizationPlan.class));
        assertEquals(List.of("COMPLETE", "BLOCKED"), names(Status.values()));
        assertEquals(List.of("COPY_PLANNED", "SKIPPED_POLICY", "BLOCKED"), names(Disposition.values()));
        assertEquals(List.of("NON_MRI_INPUT", "CORRUPT_INPUT", "INCONCLUSIVE_RECOGNITION", "INVALID_FORMAT_EVIDENCE", "DICOM_INSPECTION_UNAVAILABLE", "MISSING_CONTENT_DIGEST", "INVENTORY_INCOMPLETE", "BATCH_BLOCKED"), names(Reason.values()));
        var service = OrganizationPlanningService.class;
        assertTrue(Modifier.isFinal(service.getModifiers())); assertEquals(1, service.getConstructors().length);
        assertEquals(0, service.getConstructors()[0].getParameterCount());
        var method = service.getMethod("plan", DatasetInventory.class, DicomSeriesDiscovery.class, Map.class);
        assertEquals(OrganizationPlan.class, method.getReturnType());
        assertEquals("java.util.Map<" + RelativePath.class.getName() + ", " + ContentDigest.class.getName() + ">", method.getGenericParameterTypes()[2].getTypeName());
        assertEquals(List.of("plan"), Arrays.stream(service.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers())).map(m -> m.getName()).toList());
    }

    // F5-T02
    @Test void t02InvalidComponentsAndFixedErrors() {
        var s = source("restricted-sentinel"); var d = output("target"); var digest = digest();
        for (String id : Arrays.asList(null, "", "A".repeat(64), "a".repeat(63), "a".repeat(65), "restricted-sentinel")) {
            invalid(() -> new OrganizationCopyOperation(id, s, d, digest));
            invalid(() -> new OrganizationSourceDecision(s, Disposition.COPY_PLANNED, Optional.empty(), Optional.ofNullable(id)));
        }
        invalid(() -> new OrganizationCopyOperation("a".repeat(64), null, d, digest));
        invalid(() -> new OrganizationCopyOperation("a".repeat(64), s, null, digest));
        invalid(() -> new OrganizationCopyOperation("a".repeat(64), s, d, null));
        invalid(() -> new OrganizationSourceDecision(null, Disposition.BLOCKED, Optional.of(Reason.CORRUPT_INPUT), Optional.empty()));
        invalid(() -> new OrganizationSourceDecision(s, null, Optional.empty(), Optional.empty()));
        invalid(() -> new OrganizationSourceDecision(s, Disposition.BLOCKED, null, Optional.empty()));
        invalid(() -> new OrganizationSourceDecision(s, Disposition.BLOCKED, Optional.of(Reason.CORRUPT_INPUT), null));
        for (var disposition : Disposition.values()) for (var reason : Reason.values()) {
            if (disposition == Disposition.BLOCKED && reason != Reason.NON_MRI_INPUT) continue;
            if (disposition == Disposition.SKIPPED_POLICY && reason == Reason.NON_MRI_INPUT) continue;
            invalid(() -> new OrganizationSourceDecision(s, disposition, Optional.of(reason), Optional.empty()));
        }
        invalid(() -> new OrganizationSourceDecision(s, Disposition.BLOCKED, Optional.empty(), Optional.empty()));
        invalid(() -> new OrganizationSourceDecision(s, Disposition.SKIPPED_POLICY, Optional.of(Reason.NON_MRI_INPUT), Optional.of("a".repeat(64))));
        invalid(() -> new OrganizationSourceDecision(s, Disposition.BLOCKED, Optional.of(Reason.CORRUPT_INPUT), Optional.of("a".repeat(64))));
        var inv = inventory(entry("a", DetectionOutcome.NIFTI)); var disc = emptyDiscovery();
        invalid(() -> new OrganizationPlan(0, Status.COMPLETE, inv, disc, List.of(), List.of()));
        invalid(() -> new OrganizationPlan(2, Status.COMPLETE, inv, disc, List.of(), List.of()));
        invalid(() -> new OrganizationPlan(1, null, inv, disc, List.of(), List.of()));
        invalid(() -> new OrganizationPlan(1, Status.COMPLETE, null, disc, List.of(), List.of()));
        invalid(() -> new OrganizationPlan(1, Status.COMPLETE, inv, null, List.of(), List.of()));
        invalid(() -> new OrganizationPlan(1, Status.COMPLETE, inv, disc, null, List.of()));
        invalid(() -> new OrganizationPlan(1, Status.COMPLETE, inv, disc, List.of(), null));
        invalid(() -> new OrganizationPlan(1, Status.COMPLETE, inv, disc, Arrays.asList((OrganizationSourceDecision)null), List.of()));
        invalid(() -> new OrganizationPlan(1, Status.COMPLETE, inv, disc, List.of(), Arrays.asList((OrganizationCopyOperation)null)));
        var service = new OrganizationPlanningService();
        invalid(() -> service.plan(null, disc, Map.of())); invalid(() -> service.plan(inv, null, Map.of()));
        invalid(() -> service.plan(inv, disc, null));
    }

    // F5-T03
    @Test void t03ExactSourceCoverageAndRoles() {
        var entry = entry("a", DetectionOutcome.NIFTI); var inv = inventory(entry); var op = canonicalNiftiCopy(entry.source(), digest());
        var decision = linked(op); var disc = emptyDiscovery();
        invalid(() -> new OrganizationCopyOperation(op.operationId(), output("a"), op.destination(), digest()));
        invalid(() -> new OrganizationCopyOperation(op.operationId(), op.source(), source("a"), digest()));
        invalid(() -> new OrganizationSourceDecision(output("a"), Disposition.COPY_PLANNED, Optional.empty(), Optional.of(op.operationId())));
        invalid(() -> manual(inv, disc, Status.COMPLETE, List.of(), List.of(op)));
        invalid(() -> manual(inv, disc, Status.COMPLETE, List.of(decision, decision), List.of(op)));
        invalid(() -> manual(inv, disc, Status.COMPLETE, List.of(new OrganizationSourceDecision(source("other"), Disposition.COPY_PLANNED, Optional.empty(), Optional.of(op.operationId()))), List.of(op)));
        invalid(() -> manual(inv, disc, Status.COMPLETE, List.of(decision), List.of(copy(source("other"), "target", "a"))));
        invalid(() -> manual(inventory(entry("a", DetectionOutcome.DICOM)), disc, Status.BLOCKED,
                List.of(blocked(source("a"), Reason.DICOM_INSPECTION_UNAVAILABLE)), List.of()));
        assertEquals(1, manual(inv, disc, Status.COMPLETE, List.of(decision), List.of(op)).sourceDecisions().size());
    }

    // F5-T04
    @Test void t04StatusAndCopyLinkage() {
        var inv = inventory(entry("a", DetectionOutcome.NIFTI)); var op = canonicalNiftiCopy(source("a"), digest());
        invalid(() -> manual(inv, emptyDiscovery(), Status.BLOCKED, List.of(linked(op)), List.of(op)));
        invalid(() -> manual(inv, emptyDiscovery(), Status.COMPLETE, List.of(blocked(source("a"), Reason.MISSING_CONTENT_DIGEST)), List.of()));
        invalid(() -> manual(inv, emptyDiscovery(), Status.COMPLETE, List.of(linked(op)), List.of()));
        invalid(() -> manual(inv, emptyDiscovery(), Status.COMPLETE, List.of(linked(op)), List.of(copy(source("a"), "target", "b"))));
        invalid(() -> manual(inv, emptyDiscovery(), Status.BLOCKED, List.of(), List.of()));
        assertTrue(manual(inv, emptyDiscovery(), Status.BLOCKED, List.of(blocked(source("a"), Reason.MISSING_CONTENT_DIGEST)), List.of()).copies().isEmpty());
        assertEquals(1, manual(inv, emptyDiscovery(), Status.COMPLETE, List.of(linked(op)), List.of(op)).copies().size());
        var corrupt = inventory(entry("bad", DetectionOutcome.CORRUPT));
        invalid(() -> manual(corrupt, emptyDiscovery(), Status.COMPLETE, List.of(linked(copy(source("bad"), "x", "a"))), List.of(copy(source("bad"), "x", "a"))));
    }

    // F5-T05
    @Test void t05ImmutableCanonicalSnapshots() {
        var inv = inventory(entry("𐀀", DetectionOutcome.NIFTI), entry("\ue000", DetectionOutcome.NIFTI));
        var a = canonicalNiftiCopy(source("𐀀"), digest()); var b = canonicalNiftiCopy(source("\ue000"), digest());
        var decisions = new ArrayList<>(List.of(linked(a), linked(b))); var copies = new ArrayList<>(List.of(b, a));
        var plan = manual(inv, emptyDiscovery(), Status.COMPLETE, decisions, copies); decisions.clear(); copies.clear();
        assertEquals(List.of("\ue000", "𐀀"), plan.sourceDecisions().stream().map(x -> x.source().path()).toList());
        assertEquals(List.of(a, b).stream().sorted(Comparator.comparing(OrganizationCopyOperation::operationId)).toList(), plan.copies()); assertSame(inv, plan.inventory());
        assertThrows(UnsupportedOperationException.class, () -> plan.copies().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.sourceDecisions().clear());
    }

    // F5-T06
    @Test void t06ResourceBoundsRejectRatherThanTruncate() {
        var entries = new ArrayList<InventoryEntry>(); var copies = new ArrayList<OrganizationCopyOperation>(); var decisions = new ArrayList<OrganizationSourceDecision>();
        for (int i = 0; i < 100000; i++) {
            var e = entry("s" + i, DetectionOutcome.NIFTI); entries.add(e);
            var op = canonicalNiftiCopy(e.source(), digest());
            copies.add(op); decisions.add(linked(op));
        }
        var inv = new DatasetInventory(entries, List.of());
        var plan = manual(inv, emptyDiscovery(), Status.COMPLETE, decisions, copies);
        assertEquals(100000, plan.copies().size()); assertEquals(200000L, (long)plan.copies().size() + plan.sourceDecisions().size());
        var extra = entry("extra", DetectionOutcome.NIFTI); entries.add(extra);
        var tooLarge = new DatasetInventory(entries, List.of());
        invalid(() -> new OrganizationPlanningService().plan(tooLarge, emptyDiscovery(), Map.of()));
        invalid(() -> manual(tooLarge, emptyDiscovery(), Status.BLOCKED, List.of(), List.of()));
        copies.add(copies.getFirst()); invalid(() -> manual(inv, emptyDiscovery(), Status.COMPLETE, decisions, copies)); copies.removeLast();
        decisions.add(decisions.getFirst()); invalid(() -> manual(inv, emptyDiscovery(), Status.COMPLETE, decisions, copies));
    }

    // R02 / F5-C19,C22,C30: public construction cannot bypass layout or IDs.
    @Test void r02DirectNiftiConstructionEnforcesCanonicalMappings() {
        for (var outcome : List.of(DetectionOutcome.NIFTI, DetectionOutcome.NIFTI_GZ)) {
            var inv = inventory(entry("restricted-sentinel/é/𐀀", outcome));
            var canonical = plan(inv, emptyDiscovery(), digests(inv)); var op = canonical.copies().getFirst();
            assertEquals(canonical, manual(inv, emptyDiscovery(), Status.COMPLETE, List.of(linked(op)), List.of(op)));
            var forgedId = new OrganizationCopyOperation("0".repeat(64), op.source(), op.destination(), op.expectedDigest());
            invalid(() -> manual(inv, emptyDiscovery(), Status.COMPLETE, List.of(linked(forgedId)), List.of(forgedId)));
            for (var path : List.of("arbitrary", "layout-v2/nifti/sha256-" + "a".repeat(64) + ".nii",
                    "layout-v1/nifti/sha256-" + "b".repeat(64) + ".nii", "layout-v1/nifti/sha256-" + "a".repeat(64) + (outcome == DetectionOutcome.NIFTI ? ".nii.gz" : ".nii"))) {
                var target = output(path); var id = referenceIdentifier("copy-operation-v1", op.source().path(), path, "7", "a".repeat(64));
                var wrong = new OrganizationCopyOperation(id, op.source(), target, op.expectedDigest());
                invalid(() -> manual(inv, emptyDiscovery(), Status.COMPLETE, List.of(linked(wrong)), List.of(wrong)));
            }
            var changedSize = new OrganizationCopyOperation(op.operationId(), op.source(), op.destination(), new ContentDigest(8, "a".repeat(64)));
            invalid(() -> manual(inv, emptyDiscovery(), Status.COMPLETE, List.of(linked(changedSize)), List.of(changedSize)));
        }
    }

    // R02 / F5-C18,C21,C22,C30: correct grammar is insufficient; all UID hashes bind.
    @Test void r02DirectDicomConstructionEnforcesUidAndOperationBindings() {
        var inv = inventory(entry("restricted-sentinel", DetectionOutcome.DICOM));
        var disc = discovery(success("restricted-sentinel", "1.2", "1.3", "1.4"));
        var canonical = plan(inv, disc, digests(inv)); var op = canonical.copies().getFirst();
        assertEquals(canonical, manual(inv, disc, Status.COMPLETE, List.of(linked(op)), List.of(op)));
        var segments = op.destination().path().split("/");
        for (int index : List.of(2, 3, 4)) {
            var changed = segments.clone();
            changed[index] = changed[index].replaceFirst("[0-9a-f]{64}", "0".repeat(64));
            var path = String.join("/", changed);
            var wrong = new OrganizationCopyOperation(referenceIdentifier("copy-operation-v1", op.source().path(), path, "7", "a".repeat(64)), op.source(), output(path), digest());
            invalid(() -> manual(inv, disc, Status.COMPLETE, List.of(linked(wrong)), List.of(wrong)));
        }
        var forged = new OrganizationCopyOperation("0".repeat(64), op.source(), op.destination(), digest());
        invalid(() -> manual(inv, disc, Status.COMPLETE, List.of(linked(forged)), List.of(forged)));
        var changedMetadata = discovery(success("restricted-sentinel", "1.9", "1.3", "1.4"));
        invalid(() -> manual(inv, changedMetadata, Status.COMPLETE, List.of(linked(op)), List.of(op)));
    }

    private static OrganizationCopyOperation canonicalNiftiCopy(RelativePath source, ContentDigest digest) {
        var destination = output("layout-v1/nifti/sha256-" + digest.sha256() + ".nii");
        return new OrganizationCopyOperation(referenceIdentifier("copy-operation-v1", source.path(), destination.path(), Long.toString(digest.sizeBytes()), digest.sha256()), source, destination, digest);
    }
    // Independent test encoder using DataOutputStream framing, not production helpers.
    private static String referenceIdentifier(String domain, String... fields) {
        try {
            var bytes = new ByteArrayOutputStream(); var frame = new DataOutputStream(bytes);
            frame.write("MRI-NORMALIZER-F5-v1".getBytes(StandardCharsets.US_ASCII));
            var d = domain.getBytes(StandardCharsets.US_ASCII); frame.writeInt(d.length); frame.write(d); frame.writeInt(fields.length);
            for (var field : fields) { var utf8 = field.getBytes(StandardCharsets.UTF_8); frame.writeInt(utf8.length); frame.write(utf8); }
            frame.flush(); return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (Exception error) { throw new AssertionError("Reference encoding failed", error); }
    }

    private static void shape(Class<?> c, String... components) {
        assertTrue(c.isRecord()); assertTrue(Modifier.isPublic(c.getModifiers())); assertEquals(1, c.getConstructors().length);
        assertEquals(List.of(components), Arrays.stream(c.getRecordComponents()).map(x -> x.getName()).toList());
        assertEquals(Arrays.stream(c.getRecordComponents()).map(x -> x.getType()).toList(), List.of(c.getConstructors()[0].getParameterTypes()));
    }
    private static List<String> generic(Class<?> c) { return Arrays.stream(c.getRecordComponents()).map(x -> x.getGenericType().getTypeName()).toList(); }
    private static List<String> names(Enum<?>[] values) { return Arrays.stream(values).map(Enum::name).toList(); }
    public static void invalid(Executable action) {
        var error = assertThrows(IllegalArgumentException.class, action); assertNull(error.getCause());
        assertEquals("Invalid organization planning contract", error.getMessage());
    }
    public static RelativePath source(String path) { return new RelativePath(RelativePath.Root.SOURCE, path); }
    public static RelativePath output(String path) { return new RelativePath(RelativePath.Root.OUTPUT, path); }
    public static ContentDigest digest() { return new ContentDigest(7, "a".repeat(64)); }
    public static InventoryEntry entry(String path, DetectionOutcome outcome) {
        var detection = switch (outcome) {
            case UNKNOWN -> DetectionResult.unknown(DetectionDiagnostic.UNSUPPORTED_FORMAT);
            case CORRUPT -> DetectionResult.corrupt(DetectionDiagnostic.INVALID_NIFTI);
            default -> DetectionResult.identified(outcome);
        };
        return new InventoryEntry(source(path), FormatAssessment.fromDetection(detection));
    }
    public static DatasetInventory inventory(InventoryEntry... entries) { return new DatasetInventory(List.of(entries), List.of()); }
    public static DicomSeriesDiscovery emptyDiscovery() { return new DicomSeriesDiscovery(List.of(), List.of()); }
    public static OrganizationCopyOperation copy(RelativePath s, String target, String idCharacter) { return new OrganizationCopyOperation(idCharacter.repeat(64), s, output(target), digest()); }
    public static OrganizationSourceDecision linked(OrganizationCopyOperation op) { return new OrganizationSourceDecision(op.source(), Disposition.COPY_PLANNED, Optional.empty(), Optional.of(op.operationId())); }
    public static OrganizationSourceDecision blocked(RelativePath s, Reason reason) { return new OrganizationSourceDecision(s, Disposition.BLOCKED, Optional.of(reason), Optional.empty()); }
    public static OrganizationPlan manual(DatasetInventory i, DicomSeriesDiscovery d, Status status, List<OrganizationSourceDecision> decisions, List<OrganizationCopyOperation> copies) { return new OrganizationPlan(1, status, i, d, decisions, copies); }
    public static DicomDiscoveryMetadata metadata(String study, String series, String sop) {
        return new DicomDiscoveryMetadata(study, series, sop, "MR", "1.2.840.10008.5.1.4.1.1.4", "1.2.840.10008.1.2.1", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of());
    }
    public static DicomMetadataInspection success(String path, String study, String series, String sop) { return new DicomMetadataInspection(source(path), Optional.of(metadata(study, series, sop)), Optional.empty()); }
    public static DicomMetadataInspection failed(String path, DicomMetadataInspection.FailureCode code) { return new DicomMetadataInspection(source(path), Optional.empty(), Optional.of(code)); }
    public static DicomSeriesDiscovery discovery(DicomMetadataInspection... inspections) { return new DicomSeriesDiscoveryService().discover(new DicomMetadataCatalog(List.of(inspections))); }
    public static OrganizationPlan plan(DatasetInventory inv, DicomSeriesDiscovery disc, Map<RelativePath, ContentDigest> digests) { return new OrganizationPlanningService().plan(inv, disc, digests); }
    public static OrganizationSourceDecision decision(OrganizationPlan p, String path) { return p.sourceDecisions().stream().filter(x -> x.source().equals(source(path))).findFirst().orElseThrow(); }
    public static OrganizationCopyOperation operation(OrganizationPlan p, String path) { return p.copies().stream().filter(x -> x.source().equals(source(path))).findFirst().orElseThrow(); }
    public static Map<RelativePath, ContentDigest> digests(DatasetInventory inv) { var map = new LinkedHashMap<RelativePath, ContentDigest>(); for (var e : inv.entries()) map.put(e.source(), digest()); return map; }
}
