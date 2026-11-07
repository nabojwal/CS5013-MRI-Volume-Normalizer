package org.cbihi.mrinormalizer.application.provenance.manifest;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.cbihi.mrinormalizer.domain.error.DicomProcessingError;
import org.cbihi.mrinormalizer.domain.error.DicomToNiftiError;
import org.cbihi.mrinormalizer.domain.model.AffineMatrix4;
import org.cbihi.mrinormalizer.domain.model.IntensityTransform;
import org.cbihi.mrinormalizer.domain.model.ScalarType;

/** Supplied phase facts only; this value performs no reconstruction or conversion. */
public record ProcessingEvidence(
        Scope scope, boolean phaseSuccessful, Optional<SourceSummary> sourceSummary,
        List<DicomProcessingError> reconstructionErrors,
        List<DicomToNiftiError> conversionErrors,
        Optional<ConversionFacts> conversionFacts
) {
    public enum Scope { RECONSTRUCTION, CONVERSION }

    public ProcessingEvidence {
        if (scope == null || sourceSummary == null || reconstructionErrors == null
                || conversionErrors == null || conversionFacts == null) {
            throw new IllegalArgumentException("Processing evidence components must be non-null");
        }
        reconstructionErrors = orderedErrors(reconstructionErrors, DicomProcessingError.class);
        conversionErrors = orderedErrors(conversionErrors, DicomToNiftiError.class);
        boolean coherent = switch (scope) {
            case RECONSTRUCTION -> conversionErrors.isEmpty() && conversionFacts.isEmpty()
                    && phaseSuccessful == reconstructionErrors.isEmpty();
            case CONVERSION -> phaseSuccessful
                    ? reconstructionErrors.isEmpty() && conversionErrors.isEmpty() && conversionFacts.isPresent()
                    : (!reconstructionErrors.isEmpty() || !conversionErrors.isEmpty()) && conversionFacts.isEmpty();
        };
        if (!coherent) {
            throw new IllegalArgumentException("Processing evidence does not match phase");
        }
    }

    /** Bounded technical summary; original errors belong only to the outer evidence lists. */
    public record SourceSummary(
            Optional<String> selectedSourceFingerprint, String softwareVersion,
            String dcm4cheVersion, Instant completedAt, boolean provenanceSuccessful,
            int inputCount, int acceptedSlices
    ) {
        public SourceSummary {
            if (selectedSourceFingerprint == null || softwareVersion == null
                    || dcm4cheVersion == null || completedAt == null) {
                throw new IllegalArgumentException("Source summary components must be non-null");
            }
            if (selectedSourceFingerprint.isPresent()) {
                var fingerprint = selectedSourceFingerprint.orElseThrow();
                if (fingerprint.length() != 64 || !fingerprint.matches("[0-9a-f]{64}")) {
                    throw new IllegalArgumentException("Source fingerprint must be lowercase SHA-256");
                }
            }
            if (softwareVersion.length() > 64 || dcm4cheVersion.length() > 64
                    || !softwareVersion.matches("[A-Za-z0-9._+-]{1,64}")
                    || !dcm4cheVersion.matches("[A-Za-z0-9._+-]{1,64}")) {
                throw new IllegalArgumentException("Source versions must be bounded safe tokens");
            }
            if (inputCount < 0 || acceptedSlices < 0 || acceptedSlices > inputCount) {
                throw new IllegalArgumentException("Source summary counts are invalid");
            }
        }
    }

    /** Typed M7 runtime invariants, with no runtime target or reopened-output claim. */
    public record ConversionFacts(
            int width, int height, int depth, ScalarType scalarType, long voxelCount,
            double rowSpacingMm, double columnSpacingMm, double sliceSpacingMm,
            AffineMatrix4 niftiRasVoxelToWorld, IntensityTransform intensityTransform,
            boolean storedVoxelValuesPreserved, boolean resampled,
            boolean interpolated, boolean voxelOrderChanged
    ) {
        public ConversionFacts {
            if (scalarType == null || niftiRasVoxelToWorld == null || intensityTransform == null) {
                throw new IllegalArgumentException("Conversion fact components must be non-null");
            }
            if (width <= 0 || height <= 0 || depth <= 0) {
                throw new IllegalArgumentException("Conversion fact dimensions must be positive");
            }
            if (!Double.isFinite(rowSpacingMm) || !Double.isFinite(columnSpacingMm)
                    || !Double.isFinite(sliceSpacingMm) || rowSpacingMm <= 0
                    || columnSpacingMm <= 0 || sliceSpacingMm < 0) {
                throw new IllegalArgumentException("Conversion fact spacing must be finite and valid");
            }
            long expectedCount;
            try {
                expectedCount = Math.multiplyExact(Math.multiplyExact((long) width, height), depth);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Conversion fact dimensions overflow voxel count");
            }
            if (voxelCount != expectedCount) {
                throw new IllegalArgumentException("Conversion fact voxel count does not match dimensions");
            }
        }
    }

    private static <E extends Enum<E>> List<E> orderedErrors(List<E> errors, Class<E> type) {
        var ordered = EnumSet.noneOf(type);
        for (var error : errors) {
            if (error == null || !ordered.add(error)) {
                throw new IllegalArgumentException("Processing errors must be non-null and unique");
            }
        }
        return List.copyOf(ordered);
    }
}
