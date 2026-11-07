package org.cbihi.mrinormalizer.application.dataset.dicom;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;

/**
 * Lossless UID-defined discovery candidate, including flagged candidates.
 * Empty findings do not establish validity, support, geometry, READY or conversion
 * eligibility. Member order is deterministic discovery order, not slice order.
 * Default record text includes restricted identifiers and is not a public report.
 * Constructors enforce structure; the discovery service owns finding derivation.
 */
public record DicomSeriesCandidate(DicomSeriesKey key, List<DicomSeriesMember> members,
        List<DicomSeriesScreeningFinding> findings) {
    public DicomSeriesCandidate {
        if (key == null || members == null || findings == null) invalid();
        var orderedMembers = new ArrayList<>(members);
        var orderedFindings = new ArrayList<>(findings);
        if (orderedMembers.isEmpty()) invalid();
        var sources = new HashSet<RelativePath>();
        for (var member : orderedMembers) {
            if (member == null || !sources.add(member.source())
                    || !key.studyInstanceUid().equals(member.metadata().studyInstanceUid())
                    || !key.seriesInstanceUid().equals(member.metadata().seriesInstanceUid())) invalid();
        }
        var uniqueFindings = new HashSet<DicomSeriesScreeningFinding>();
        for (var finding : orderedFindings) if (finding == null || !uniqueFindings.add(finding)) invalid();
        orderedMembers.sort(Comparator.comparing((DicomSeriesMember member) -> member.metadata().sopInstanceUid())
                .thenComparing(member -> member.source().path(), DicomSeriesCandidate::comparePaths));
        orderedFindings.sort(Comparator.comparingInt((DicomSeriesScreeningFinding finding) -> finding.code().ordinal())
                .thenComparingInt(finding -> finding.reason().ordinal()));
        members = List.copyOf(orderedMembers); findings = List.copyOf(orderedFindings);
    }

    private static int comparePaths(String left, String right) {
        return Arrays.compareUnsigned(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
    private static void invalid() { throw new IllegalArgumentException("Candidate requires unique matching members and findings"); }
}
