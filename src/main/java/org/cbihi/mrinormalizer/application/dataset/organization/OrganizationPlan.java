package org.cbihi.mrinormalizer.application.dataset.organization;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.cbihi.mrinormalizer.application.dataset.dicom.*;
import org.cbihi.mrinormalizer.application.dataset.model.*;
import org.cbihi.mrinormalizer.application.dataset.organization.OrganizationSourceDecision.Disposition;
import org.cbihi.mrinormalizer.application.dataset.organization.OrganizationSourceDecision.Reason;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;
import org.cbihi.mrinormalizer.domain.model.DetectionDiagnostic;
import org.cbihi.mrinormalizer.domain.model.DetectionOutcome;

/**
 * Restricted immutable logical layout and unchanged discovery evidence.
 * COMPLETE is logical coherence only: it proves no byte integrity, containment,
 * source continuity, copying, persistence, M6 compatibility or conversion READY.
 * BLOCKED cannot be executed, including by selecting a subset of this batch.
 * Record text must never serve as a public diagnostic or report channel.
 * Every construction path enforces canonical layout-v1 destinations and framed
 * operation identities; manually constructed plans have no weaker trust boundary.
 */
public record OrganizationPlan(int layoutVersion, Status status, DatasetInventory inventory,
        DicomSeriesDiscovery dicomDiscovery, List<OrganizationSourceDecision> sourceDecisions,
        List<OrganizationCopyOperation> copies) {
    public enum Status { COMPLETE, BLOCKED }

    public OrganizationPlan {
        if (layoutVersion != 1 || status == null || inventory == null || dicomDiscovery == null
                || sourceDecisions == null || copies == null) invalid();
        // Bound before allocating additional snapshots; widen before arithmetic.
        if (inventory.entries().size() > 100000 || sourceDecisions.size() > 100000 || copies.size() > 100000
                || (long)sourceDecisions.size() + copies.size() > 200000) invalid();
        var orderedDecisions = new ArrayList<>(sourceDecisions);
        var orderedCopies = new ArrayList<>(copies);
        var entries = new HashMap<RelativePath, InventoryEntry>();
        var expectedDicom = new HashSet<RelativePath>();
        for (var entry : inventory.entries()) {
            if (entry == null || entry.source().root() != RelativePath.Root.SOURCE
                    || entries.putIfAbsent(entry.source(), entry) != null) invalid();
            if (entry.assessment().initialDetection().outcome() == DetectionOutcome.DICOM) expectedDicom.add(entry.source());
        }
        var members = new HashMap<RelativePath, DicomSeriesMember>();
        var observedDicom = new HashSet<RelativePath>();
        for (var candidate : dicomDiscovery.candidates()) for (var member : candidate.members()) {
            if (!observedDicom.add(member.source())) invalid();
            members.put(member.source(), member);
        }
        for (var failure : dicomDiscovery.unassigned()) if (!observedDicom.add(failure.source())) invalid();
        if (!expectedDicom.equals(observedDicom)) invalid();

        boolean incomplete = !inventory.complete() || !inventory.failures().isEmpty();
        if (incomplete && status != Status.BLOCKED) invalid();
        var decisions = new HashMap<RelativePath, OrganizationSourceDecision>();
        boolean hardBlock = incomplete;
        for (var decision : orderedDecisions) {
            if (decision == null || decisions.putIfAbsent(decision.source(), decision) != null
                    || !entries.containsKey(decision.source())) invalid();
            if (incomplete) {
                if (decision.disposition() != Disposition.BLOCKED || !decision.reason().equals(Optional.of(Reason.INVENTORY_INCOMPLETE))) invalid();
                continue;
            }
            var entry = entries.get(decision.source());
            Reason intrinsic = intrinsicReason(entry, members);
            if (intrinsic == Reason.NON_MRI_INPUT) {
                if (decision.disposition() != Disposition.SKIPPED_POLICY) invalid();
            } else if (intrinsic != null) {
                if (decision.disposition() != Disposition.BLOCKED || !decision.reason().equals(Optional.of(intrinsic))) invalid();
                hardBlock = true;
            } else if (decision.disposition() == Disposition.BLOCKED) {
                var reason = decision.reason().orElseThrow();
                if (reason != Reason.MISSING_CONTENT_DIGEST && reason != Reason.BATCH_BLOCKED) invalid();
                if (reason == Reason.MISSING_CONTENT_DIGEST) hardBlock = true;
            } else if (decision.disposition() != Disposition.COPY_PLANNED) invalid();
            if (status == Status.COMPLETE && decision.disposition() == Disposition.BLOCKED) invalid();
            if (status == Status.BLOCKED && decision.disposition() == Disposition.COPY_PLANNED) invalid();
        }
        if (!decisions.keySet().equals(entries.keySet()) || (status == Status.BLOCKED && !hardBlock)) invalid();
        if (status == Status.BLOCKED && !orderedCopies.isEmpty()) invalid();

        var ids = new HashSet<String>(); var copySources = new HashSet<RelativePath>();
        var byDestination = new HashMap<RelativePath, OrganizationCopyOperation>();
        var foldedDestinations = new HashMap<String, String>(); var destinationPaths = new TreeSet<String>();
        for (var copy : orderedCopies) {
            if (copy == null || !ids.add(copy.operationId()) || !copySources.add(copy.source())) invalid();
            var decision = decisions.get(copy.source());
            if (decision == null || decision.disposition() != Disposition.COPY_PLANNED
                    || !decision.operationId().equals(Optional.of(copy.operationId()))) invalid();
            String path = copy.destination().path(); String folded = asciiFold(path);
            var alias = foldedDestinations.putIfAbsent(folded, path);
            if (alias != null && !alias.equals(path)) invalid();
            var previous = byDestination.putIfAbsent(copy.destination(), copy);
            if (previous != null && (!previous.expectedDigest().equals(copy.expectedDigest())
                    || !sameIdentity(entries.get(previous.source()), entries.get(copy.source()), members))) invalid();
            destinationPaths.add(folded);
        }
        for (var path : destinationPaths) {
            var prefix = path + "/"; var next = destinationPaths.ceiling(prefix);
            if (next != null && next.startsWith(prefix)) invalid();
        }
        for (var decision : orderedDecisions) if ((decision.disposition() == Disposition.COPY_PLANNED) != copySources.contains(decision.source())) invalid();
        // Keep structural collision checks independently enforced, then bind
        // every surviving mapping to the canonical naming and identity contract.
        for (var copy : orderedCopies) {
            var expectedDestination = canonicalDestination(entries.get(copy.source()), members.get(copy.source()), copy);
            if (!copy.destination().equals(expectedDestination)
                    || !copy.operationId().equals(identifier("copy-operation-v1", copy.source().path(),
                            expectedDestination.path(), Long.toString(copy.expectedDigest().sizeBytes()), copy.expectedDigest().sha256()))) invalid();
        }
        orderedDecisions.sort((a, b) -> Arrays.compareUnsigned(a.source().path().getBytes(StandardCharsets.UTF_8), b.source().path().getBytes(StandardCharsets.UTF_8)));
        orderedCopies.sort(Comparator.comparing(OrganizationCopyOperation::operationId));
        sourceDecisions = List.copyOf(orderedDecisions); copies = List.copyOf(orderedCopies);
    }

    private static Reason intrinsicReason(InventoryEntry entry, Map<RelativePath, DicomSeriesMember> members) {
        var raw = entry.assessment().initialDetection();
        if (raw.outcome() == DetectionOutcome.CORRUPT) return Reason.CORRUPT_INPUT;
        if (entry.assessment().validity() == ValidityStatus.INVALID) return Reason.INVALID_FORMAT_EVIDENCE;
        if (raw.outcome() == DetectionOutcome.UNKNOWN) return raw.diagnostic() == DetectionDiagnostic.UNSUPPORTED_FORMAT ? Reason.NON_MRI_INPUT : Reason.INCONCLUSIVE_RECOGNITION;
        if (raw.outcome() == DetectionOutcome.DICOM && !members.containsKey(entry.source())) return Reason.DICOM_INSPECTION_UNAVAILABLE;
        return null;
    }

    private static boolean sameIdentity(InventoryEntry a, InventoryEntry b, Map<RelativePath, DicomSeriesMember> members) {
        var outcome = a.assessment().initialDetection().outcome();
        if (outcome != b.assessment().initialDetection().outcome()) return false;
        if (outcome == DetectionOutcome.NIFTI || outcome == DetectionOutcome.NIFTI_GZ) return true;
        if (outcome != DetectionOutcome.DICOM) return false;
        var left = members.get(a.source()).metadata(); var right = members.get(b.source()).metadata();
        return left.studyInstanceUid().equals(right.studyInstanceUid()) && left.seriesInstanceUid().equals(right.seriesInstanceUid()) && left.sopInstanceUid().equals(right.sopInstanceUid());
    }

    private static String asciiFold(String value) {
        var out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) { char c = value.charAt(i); out.append(c >= 'A' && c <= 'Z' ? (char)(c + ('a' - 'A')) : c); }
        return out.toString();
    }

    private static RelativePath canonicalDestination(InventoryEntry entry, DicomSeriesMember member, OrganizationCopyOperation copy) {
        String path; var outcome = entry.assessment().initialDetection().outcome(); var digest = copy.expectedDigest();
        if (outcome == DetectionOutcome.DICOM) {
            var m = member.metadata();
            var study = identifier("dicom-study-v1", m.studyInstanceUid());
            var series = identifier("dicom-series-v1", m.studyInstanceUid(), m.seriesInstanceUid());
            var sop = identifier("dicom-sop-v1", m.studyInstanceUid(), m.seriesInstanceUid(), m.sopInstanceUid());
            path = "layout-v1/dicom/study-" + study + "/series-" + series + "/sop-" + sop + "-sha256-" + digest.sha256() + ".dcm";
        } else path = "layout-v1/nifti/sha256-" + digest.sha256() + (outcome == DetectionOutcome.NIFTI ? ".nii" : ".nii.gz");
        return new RelativePath(RelativePath.Root.OUTPUT, path);
    }

    // Private validation encoder: the frozen public API admits no shared public
    // naming utility. Golden vectors exercise agreement with the service encoder.
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
