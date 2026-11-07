package org.cbihi.mrinormalizer.application.provenance.manifest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.cbihi.mrinormalizer.application.dataset.model.FormatAssessment;

/** Immutable supplied source facts; assessment and optional digest are preserved without inference. */
public record SourceFileRecord(
        RelativePath source,
        Optional<ContentDigest> digest,
        FormatAssessment assessment,
        List<ManifestFailure> failures
) {

    public SourceFileRecord {
        if (source == null || digest == null || assessment == null || failures == null) {
            throw new IllegalArgumentException("Source record components must be non-null");
        }
        if (source.root() != RelativePath.Root.SOURCE) {
            throw new IllegalArgumentException("Source record requires SOURCE root");
        }
        if (failures.size() > 64) {
            throw new IllegalArgumentException("Source record failure limit exceeded");
        }
        var seen = new HashSet<ManifestFailure>();
        var ordered = new ArrayList<ManifestFailure>(failures.size());
        for (var failure : failures) {
            if (failure == null || !seen.add(failure)) {
                throw new IllegalArgumentException("Failures must be non-null and unique");
            }
            ordered.add(failure);
        }
        ordered.sort(Comparator.comparingInt((ManifestFailure failure) -> failure.phase().ordinal())
                .thenComparingInt(failure -> failure.code().ordinal()));
        failures = List.copyOf(ordered);
    }
}
