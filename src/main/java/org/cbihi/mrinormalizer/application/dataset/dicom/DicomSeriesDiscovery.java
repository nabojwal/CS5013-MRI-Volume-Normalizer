package org.cbihi.mrinormalizer.application.dataset.dicom;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;

/**
 * Immutable restricted candidate discovery plus unchanged failed F3 observations.
 * Global source uniqueness is structural; complete coverage of the supplied F3
 * catalog is checked by the service. No aggregate validity/readiness is implied.
 * Default record text must not be used as a public diagnostic/report channel.
 */
public record DicomSeriesDiscovery(List<DicomSeriesCandidate> candidates, List<DicomMetadataInspection> unassigned) {
    public DicomSeriesDiscovery {
        if (candidates == null || unassigned == null) invalid();
        var orderedCandidates = new ArrayList<>(candidates);
        var orderedUnassigned = new ArrayList<>(unassigned);
        var keys = new HashSet<DicomSeriesKey>(); var sources = new HashSet<RelativePath>();
        for (var candidate : orderedCandidates) {
            if (candidate == null || !keys.add(candidate.key())) invalid();
            for (var member : candidate.members()) if (!sources.add(member.source())) invalid();
        }
        for (var inspection : orderedUnassigned) {
            if (inspection == null || inspection.failure().isEmpty() || !sources.add(inspection.source())) invalid();
        }
        orderedCandidates.sort(Comparator.comparing((DicomSeriesCandidate candidate) -> candidate.key().studyInstanceUid())
                .thenComparing(candidate -> candidate.key().seriesInstanceUid()));
        orderedUnassigned.sort(Comparator.comparing(inspection -> inspection.source().path(), DicomSeriesDiscovery::comparePaths));
        candidates = List.copyOf(orderedCandidates); unassigned = List.copyOf(orderedUnassigned);
    }

    private static int comparePaths(String left, String right) {
        return Arrays.compareUnsigned(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
    private static void invalid() { throw new IllegalArgumentException("Discovery requires unique disjoint candidate and failure sources"); }
}
