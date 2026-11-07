package org.cbihi.mrinormalizer.application.validation;
import java.util.*;
public record NiftiComparisonResult(Optional<PostWriteVerificationEvidence> verified,
        List<NiftiComparisonFailure> failures) {
    public NiftiComparisonResult {
        if (verified == null || failures == null) throw new IllegalArgumentException("Invalid comparison result");
        var ordered = EnumSet.noneOf(NiftiComparisonFailure.class);
        for (var f : failures) if (f == null || !ordered.add(f))
            throw new IllegalArgumentException("Invalid comparison failure");
        failures = List.copyOf(ordered);
        if (verified.isPresent() == !failures.isEmpty())
            throw new IllegalArgumentException("Comparison result must be exclusive");
    }
}
