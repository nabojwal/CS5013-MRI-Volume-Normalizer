package org.cbihi.mrinormalizer.domain.model;

public record DetectionResult(
        DetectionOutcome outcome,
        DetectionDiagnostic diagnostic,
        boolean extensionMismatch
) {

    public static DetectionResult identified(DetectionOutcome outcome) {
        return new DetectionResult(outcome, DetectionDiagnostic.NONE, false);
    }

    public static DetectionResult unknown(DetectionDiagnostic diagnostic) {
        return new DetectionResult(DetectionOutcome.UNKNOWN, diagnostic, false);
    }

    public static DetectionResult corrupt(DetectionDiagnostic diagnostic) {
        return new DetectionResult(DetectionOutcome.CORRUPT, diagnostic, false);
    }

    public DetectionResult withExtensionMismatch(boolean mismatch) {
        return new DetectionResult(outcome, diagnostic, mismatch);
    }
}
