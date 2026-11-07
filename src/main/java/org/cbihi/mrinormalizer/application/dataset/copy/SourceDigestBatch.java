package org.cbihi.mrinormalizer.application.dataset.copy;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.cbihi.mrinormalizer.application.dataset.dicom.DicomSeriesDiscovery;
import org.cbihi.mrinormalizer.application.dataset.model.DatasetInventory;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.application.dataset.model.ValidityStatus;
import org.cbihi.mrinormalizer.domain.model.DetectionOutcome;
import org.cbihi.mrinormalizer.domain.model.DetectionDiagnostic;

/** Immutable original-byte facts. This DTO is not an adapter binding or execution capability. */
public record SourceDigestBatch(Status status, DatasetInventory inventory, DicomSeriesDiscovery discovery,
        List<DigestFact> digests, List<CopyFailure> failures) {
    public enum Status { COMPLETE, BLOCKED }
    public record DigestFact(RelativePath source, ContentDigest digest) {
        public DigestFact {
            if (source == null || source.root() != RelativePath.Root.SOURCE || digest == null) invalid();
        }
    }
    public SourceDigestBatch {
        if (status == null || inventory == null || discovery == null || digests == null || failures == null
                || inventory.entries().size() > 100000 || digests.size() > 100000 || failures.size() > 100001
                || discovery.candidates().size() > 100000 || discovery.unassigned().size() > 100000) invalid();
        var all = new HashSet<RelativePath>(); for (var e : inventory.entries()) all.add(e.source());
        var ordered = new ArrayList<DigestFact>(); var map = new LinkedHashMap<RelativePath, ContentDigest>();
        for (Object value : digests) {
            if (!(value instanceof DigestFact d) || !all.contains(d.source()) || map.putIfAbsent(d.source(), d.digest()) != null) invalid();
            ordered.add((DigestFact)value);
        }
        ordered.sort((a, b) -> Arrays.compareUnsigned(a.source().path().getBytes(StandardCharsets.UTF_8), b.source().path().getBytes(StandardCharsets.UTF_8)));
        digests = List.copyOf(ordered);
        var unique = new HashSet<CopyFailure>(); var fs = new ArrayList<CopyFailure>();
        for (Object value : failures) {
            if (!(value instanceof CopyFailure f) || !unique.add(f)) invalid();
            fs.add((CopyFailure)value);
        }
        fs.sort(Comparator.comparingInt((CopyFailure f) -> f.stage().ordinal()).thenComparingInt(f -> f.code().ordinal())
                .thenComparing(f -> f.source().map(RelativePath::path).orElse(""),
                        (a, b) -> Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)))
                .thenComparing(f -> f.operationId().orElse("")));
        failures = List.copyOf(fs);
        // Coverage/eligibility only: no placeholder digest, layout construction or pre-acquisition F5 invocation.
        var expectedDicom = new HashSet<RelativePath>(); var observedDicom = new HashSet<RelativePath>();
        var eligible = new HashSet<RelativePath>(); boolean blocked = !inventory.complete() || !discovery.unassigned().isEmpty();
        for (var c : discovery.candidates()) for (var member : c.members()) {
            observedDicom.add(member.source()); if (observedDicom.size() > 100000) invalid();
        }
        var availableDicom = Set.copyOf(observedDicom);
        for (var i : discovery.unassigned()) observedDicom.add(i.source());
        for (var entry : inventory.entries()) {
            var raw = entry.assessment().initialDetection();
            if (raw.outcome() == DetectionOutcome.DICOM) expectedDicom.add(entry.source());
            boolean recognized = raw.outcome() == DetectionOutcome.NIFTI || raw.outcome() == DetectionOutcome.NIFTI_GZ
                    || raw.outcome() == DetectionOutcome.DICOM && availableDicom.contains(entry.source());
            if (entry.assessment().validity() == ValidityStatus.INVALID) blocked = true;
            if (recognized && entry.assessment().validity() != ValidityStatus.INVALID) eligible.add(entry.source());
            else if (raw.outcome() != DetectionOutcome.UNKNOWN || raw.diagnostic() != DetectionDiagnostic.UNSUPPORTED_FORMAT) blocked = true;
        }
        if (!observedDicom.equals(expectedDicom) || !eligible.containsAll(map.keySet())) invalid();
        if (status == Status.COMPLETE && (blocked || !failures.isEmpty() || !eligible.equals(map.keySet()))) invalid();
    }
    public Map<RelativePath, ContentDigest> suppliedDigests() {
        if (status != Status.COMPLETE) throw new IllegalStateException("COPY digest handoff blocked");
        var map = new LinkedHashMap<RelativePath, ContentDigest>(); for (var d : digests) map.put(d.source(), d.digest());
        return Collections.unmodifiableMap(map);
    }
    private static void invalid() { throw new IllegalArgumentException("Invalid COPY digest batch"); }
}
