package org.cbihi.mrinormalizer.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.cbihi.mrinormalizer.application.dataset.dicom.*;
import org.cbihi.mrinormalizer.application.dataset.model.*;
import org.cbihi.mrinormalizer.application.dataset.organization.*;
import org.cbihi.mrinormalizer.application.dataset.organization.OrganizationPlan.Status;
import org.cbihi.mrinormalizer.application.dataset.organization.OrganizationSourceDecision.Disposition;
import org.cbihi.mrinormalizer.application.dataset.organization.OrganizationSourceDecision.Reason;
import org.cbihi.mrinormalizer.application.provenance.manifest.ContentDigest;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;
import org.cbihi.mrinormalizer.domain.model.DetectionDiagnostic;
import org.cbihi.mrinormalizer.domain.model.DetectionOutcome;

/**
 * Pure stateless layout-v1 planning from immutable F2/F4 and supplied digests.
 * SHA-256 here hashes only framed in-memory technical identifiers; F6 alone
 * acquires and revalidates original-file bytes, containment and no-replace COPY.
 * No F1 persistence, execution facts or conversion/readiness claims are produced.
 */
public final class OrganizationPlanningService {
    public OrganizationPlanningService() {}

    public OrganizationPlan plan(DatasetInventory inventory, DicomSeriesDiscovery discovery,
            Map<RelativePath, ContentDigest> suppliedDigests) {
        if (inventory == null || discovery == null || suppliedDigests == null
                || inventory.entries().size() > 100000 || suppliedDigests.size() > inventory.entries().size()) invalid();
        var sources = new HashSet<RelativePath>(); var expectedDicom = new HashSet<RelativePath>();
        for (var entry : inventory.entries()) {
            if (entry == null || entry.source().root() != RelativePath.Root.SOURCE || !sources.add(entry.source())) invalid();
            if (entry.assessment().initialDetection().outcome() == DetectionOutcome.DICOM) expectedDicom.add(entry.source());
        }
        // Validate and snapshot every supplied map entry before any batch gate.
        var digests = new HashMap<RelativePath, ContentDigest>();
        for (Map.Entry<?, ?> entry : suppliedDigests.entrySet()) {
            if (entry == null || !(entry.getKey() instanceof RelativePath key)
                    || key.root() != RelativePath.Root.SOURCE || !sources.contains(key)
                    || !(entry.getValue() instanceof ContentDigest value)) invalid();
            // Explicit casts follow validation so malformed generic callers also
            // get the same fixed contract error instead of provider/type text.
            var key = (RelativePath)entry.getKey(); var value = (ContentDigest)entry.getValue();
            if (digests.putIfAbsent(key, value) != null) invalid();
        }
        var members = new HashMap<RelativePath, DicomSeriesMember>(); var observedDicom = new HashSet<RelativePath>();
        for (var candidate : discovery.candidates()) for (var member : candidate.members()) {
            if (!observedDicom.add(member.source()) || observedDicom.size() > 100000) invalid();
            members.put(member.source(), member);
        }
        for (var inspection : discovery.unassigned()) if (!observedDicom.add(inspection.source()) || observedDicom.size() > 100000) invalid();
        if (!expectedDicom.equals(observedDicom)) invalid();
        var decisions = new ArrayList<OrganizationSourceDecision>();
        if (!inventory.complete() || !inventory.failures().isEmpty()) {
            for (var entry : inventory.entries()) decisions.add(blocked(entry.source(), Reason.INVENTORY_INCOMPLETE));
            return new OrganizationPlan(1, Status.BLOCKED, inventory, discovery, decisions, List.of());
        }
        // Classify first. No partially executable operation list is exposed.
        var reasons = new HashMap<RelativePath, Reason>(); boolean batchBlocked = false;
        for (var entry : inventory.entries()) {
            Reason reason = reason(entry, members, digests);
            if (reason != null) { reasons.put(entry.source(), reason); if (reason != Reason.NON_MRI_INPUT) batchBlocked = true; }
        }
        var copies = new ArrayList<OrganizationCopyOperation>();
        for (var entry : inventory.entries()) {
            var source = entry.source(); var reason = reasons.get(source);
            if (reason == Reason.NON_MRI_INPUT) decisions.add(new OrganizationSourceDecision(source, Disposition.SKIPPED_POLICY, Optional.of(reason), Optional.empty()));
            else if (reason != null) decisions.add(blocked(source, reason));
            else if (batchBlocked) decisions.add(blocked(source, Reason.BATCH_BLOCKED));
            else {
                var digest = digests.get(source); var destination = destination(entry, members.get(source), digest);
                var id = identifier("copy-operation-v1", source.path(), destination.path(), Long.toString(digest.sizeBytes()), digest.sha256());
                copies.add(new OrganizationCopyOperation(id, source, destination, digest));
                decisions.add(new OrganizationSourceDecision(source, Disposition.COPY_PLANNED, Optional.empty(), Optional.of(id)));
            }
        }
        return new OrganizationPlan(1, batchBlocked ? Status.BLOCKED : Status.COMPLETE, inventory, discovery, decisions, copies);
    }

    private static Reason reason(InventoryEntry entry, Map<RelativePath, DicomSeriesMember> members, Map<RelativePath, ContentDigest> digests) {
        var raw = entry.assessment().initialDetection();
        if (raw.outcome() == DetectionOutcome.CORRUPT) return Reason.CORRUPT_INPUT;
        if (entry.assessment().validity() == ValidityStatus.INVALID) return Reason.INVALID_FORMAT_EVIDENCE;
        if (raw.outcome() == DetectionOutcome.UNKNOWN) return raw.diagnostic() == DetectionDiagnostic.UNSUPPORTED_FORMAT ? Reason.NON_MRI_INPUT : Reason.INCONCLUSIVE_RECOGNITION;
        if (raw.outcome() == DetectionOutcome.DICOM && !members.containsKey(entry.source())) return Reason.DICOM_INSPECTION_UNAVAILABLE;
        return digests.containsKey(entry.source()) ? null : Reason.MISSING_CONTENT_DIGEST;
    }

    private static RelativePath destination(InventoryEntry entry, DicomSeriesMember member, ContentDigest digest) {
        var outcome = entry.assessment().initialDetection().outcome(); String path;
        if (outcome == DetectionOutcome.DICOM) {
            var m = member.metadata();
            var study = identifier("dicom-study-v1", m.studyInstanceUid());
            var series = identifier("dicom-series-v1", m.studyInstanceUid(), m.seriesInstanceUid());
            var sop = identifier("dicom-sop-v1", m.studyInstanceUid(), m.seriesInstanceUid(), m.sopInstanceUid());
            path = "layout-v1/dicom/study-" + study + "/series-" + series + "/sop-" + sop + "-sha256-" + digest.sha256() + ".dcm";
        } else path = "layout-v1/nifti/sha256-" + digest.sha256() + (outcome == DetectionOutcome.NIFTI ? ".nii" : ".nii.gz");
        return new RelativePath(RelativePath.Root.OUTPUT, path);
    }

    private static OrganizationSourceDecision blocked(RelativePath source, Reason reason) {
        return new OrganizationSourceDecision(source, Disposition.BLOCKED, Optional.of(reason), Optional.empty());
    }

    private static String identifier(String domain, String... fields) {
        final MessageDigest hash;
        try { hash = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalArgumentException("Invalid organization planning contract"); }
        hash.update("MRI-NORMALIZER-F5-v1".getBytes(StandardCharsets.US_ASCII));
        var domainBytes = domain.getBytes(StandardCharsets.US_ASCII); length(hash, domainBytes.length); hash.update(domainBytes);
        length(hash, fields.length);
        for (var field : fields) { var bytes = field.getBytes(StandardCharsets.UTF_8); length(hash, bytes.length); hash.update(bytes); }
        return HexFormat.of().formatHex(hash.digest());
    }
    private static void length(MessageDigest hash, int length) {
        hash.update((byte)(length >>> 24)); hash.update((byte)(length >>> 16)); hash.update((byte)(length >>> 8)); hash.update((byte)length);
    }
    private static void invalid() { throw new IllegalArgumentException("Invalid organization planning contract"); }
}
