package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.cbihi.mrinormalizer.application.provenance.manifest.ProcessingEvidence;
import org.cbihi.mrinormalizer.application.provenance.manifest.ProcessingEvidence.ConversionFacts;
import org.cbihi.mrinormalizer.application.provenance.manifest.ProcessingEvidence.Scope;
import org.cbihi.mrinormalizer.application.provenance.manifest.ProcessingEvidence.SourceSummary;
import org.cbihi.mrinormalizer.domain.error.DicomProcessingError;
import org.cbihi.mrinormalizer.domain.error.DicomToNiftiError;
import org.cbihi.mrinormalizer.domain.model.AffineMatrix4;
import org.cbihi.mrinormalizer.domain.model.IntensityTransform;
import org.cbihi.mrinormalizer.domain.model.ScalarType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** M04-M05 typed supplied phase facts; no projection, writer or reader. */
class ProcessingEvidenceTest {
    private static final Instant TIME = Instant.parse("2026-10-04T00:00:00Z");
    private static final AffineMatrix4 AFFINE = new AffineMatrix4(new double[][] {
            {1, 0, 0, 2}, {0, 1, 0, 3}, {0, 0, 1, 4}, {0, 0, 0, 1}});
    private static final IntensityTransform INTENSITY = new IntensityTransform(true, -2, 7);

    // M04
    @Test
    void originalErrorsHaveOneAuthoritativeHome() {
        assertArrayEquals(new String[] {"scope", "phaseSuccessful", "sourceSummary", "reconstructionErrors",
                "conversionErrors", "conversionFacts"}, Arrays.stream(ProcessingEvidence.class.getRecordComponents())
                        .map(c -> c.getName()).toArray(String[]::new));
        assertArrayEquals(new String[] {"selectedSourceFingerprint", "softwareVersion", "dcm4cheVersion", "completedAt",
                "provenanceSuccessful", "inputCount", "acceptedSlices"}, Arrays.stream(SourceSummary.class.getRecordComponents())
                        .map(c -> c.getName()).toArray(String[]::new));
        assertEquals("java.util.List<" + DicomProcessingError.class.getName() + ">",
                ProcessingEvidence.class.getRecordComponents()[3].getGenericType().getTypeName());
        assertEquals("java.util.List<" + DicomToNiftiError.class.getName() + ">",
                ProcessingEvidence.class.getRecordComponents()[4].getGenericType().getTypeName());
        assertArrayEquals(new String[] {"RECONSTRUCTION", "CONVERSION"},
                Arrays.stream(Scope.values()).map(Enum::name).toArray(String[]::new));
        for (var component : SourceSummary.class.getRecordComponents()) {
            assertFalse(List.class.isAssignableFrom(component.getType()));
        }
    }

    @Test
    void phaseErrorsAreCopiedCanonicalUniqueAndUnmodifiable() {
        var reconstruction = new ArrayList<>(List.of(DicomProcessingError.values()));
        var conversion = new ArrayList<>(List.of(DicomToNiftiError.values()));
        Collections.reverse(reconstruction);
        Collections.reverse(conversion);
        var evidence = new ProcessingEvidence(Scope.CONVERSION, false, Optional.empty(),
                reconstruction, conversion, Optional.empty());
        reconstruction.clear();
        conversion.clear();
        assertEquals(List.of(DicomProcessingError.values()), evidence.reconstructionErrors());
        assertEquals(List.of(DicomToNiftiError.values()), evidence.conversionErrors());
        assertThrowsExactly(UnsupportedOperationException.class, () -> evidence.reconstructionErrors().clear());
        assertThrowsExactly(UnsupportedOperationException.class, () -> evidence.conversionErrors().clear());
        reject("Processing errors must be non-null and unique", () -> new ProcessingEvidence(Scope.RECONSTRUCTION,
                false, Optional.empty(), List.of(DicomProcessingError.EMPTY_INPUT, DicomProcessingError.EMPTY_INPUT),
                List.of(), Optional.empty()));
        reject("Processing errors must be non-null and unique", () -> new ProcessingEvidence(Scope.CONVERSION,
                false, Optional.empty(), List.of(), List.of(DicomToNiftiError.OUTPUT_WRITE_FAILED,
                        DicomToNiftiError.OUTPUT_WRITE_FAILED), Optional.empty()));
    }

    @Test
    void errorPermutationsHaveEqualValuesWithoutCrossNamespaceStorage() {
        var source = Optional.of(summary(false));
        var a = new ProcessingEvidence(Scope.CONVERSION, false, source,
                List.of(DicomProcessingError.EMPTY_INPUT, DicomProcessingError.SERIES_NOT_SELECTED),
                List.of(DicomToNiftiError.OUTPUT_WRITE_FAILED, DicomToNiftiError.INVALID_OUTPUT), Optional.empty());
        var b = new ProcessingEvidence(Scope.CONVERSION, false, source,
                List.of(DicomProcessingError.SERIES_NOT_SELECTED, DicomProcessingError.EMPTY_INPUT),
                List.of(DicomToNiftiError.INVALID_OUTPUT, DicomToNiftiError.OUTPUT_WRITE_FAILED), Optional.empty());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    // M05
    @Test
    void requiredEvidenceComponentsAndErrorEntriesRejectWithFixedMessages() {
        reject("Processing evidence components must be non-null", () -> new ProcessingEvidence(null,
                true, Optional.empty(), List.of(), List.of(), Optional.empty()));
        reject("Processing evidence components must be non-null", () -> new ProcessingEvidence(Scope.RECONSTRUCTION,
                true, null, List.of(), List.of(), Optional.empty()));
        reject("Processing evidence components must be non-null", () -> new ProcessingEvidence(Scope.RECONSTRUCTION,
                true, Optional.empty(), null, List.of(), Optional.empty()));
        reject("Processing evidence components must be non-null", () -> new ProcessingEvidence(Scope.RECONSTRUCTION,
                true, Optional.empty(), List.of(), null, Optional.empty()));
        reject("Processing evidence components must be non-null", () -> new ProcessingEvidence(Scope.RECONSTRUCTION,
                true, Optional.empty(), List.of(), List.of(), null));
        reject("Processing errors must be non-null and unique", () -> new ProcessingEvidence(Scope.RECONSTRUCTION,
                false, Optional.empty(), Arrays.asList((DicomProcessingError) null), List.of(), Optional.empty()));
        reject("Processing errors must be non-null and unique", () -> new ProcessingEvidence(Scope.CONVERSION,
                false, Optional.empty(), List.of(), Arrays.asList((DicomToNiftiError) null), Optional.empty()));
    }

    @Test
    void phaseSuccessErrorAndFactPresenceMatrixIsExhaustive() {
        for (var scope : Scope.values()) {
            for (boolean successful : new boolean[] {false, true}) {
                for (boolean hasReconstruction : new boolean[] {false, true}) {
                    for (boolean hasConversion : new boolean[] {false, true}) {
                        for (boolean hasFacts : new boolean[] {false, true}) {
                            var reconstruction = hasReconstruction ? List.of(DicomProcessingError.EMPTY_INPUT)
                                    : List.<DicomProcessingError>of();
                            var conversion = hasConversion ? List.of(DicomToNiftiError.OUTPUT_WRITE_FAILED)
                                    : List.<DicomToNiftiError>of();
                            var facts = hasFacts ? Optional.of(facts()) : Optional.<ConversionFacts>empty();
                            boolean coherent = scope == Scope.RECONSTRUCTION
                                    ? !hasConversion && !hasFacts && successful != hasReconstruction
                                    : successful ? !hasReconstruction && !hasConversion && hasFacts
                                            : (hasReconstruction || hasConversion) && !hasFacts;
                            for (boolean hasSummary : new boolean[] {false, true}) {
                                var source = hasSummary ? Optional.of(summary(false)) : Optional.<SourceSummary>empty();
                                if (coherent) {
                                    var evidence = new ProcessingEvidence(scope, successful, source,
                                            reconstruction, conversion, facts);
                                    assertEquals(scope, evidence.scope());
                                    assertEquals(successful, evidence.phaseSuccessful());
                                    assertEquals(facts, evidence.conversionFacts());
                                } else {
                                    reject("Processing evidence does not match phase", () -> new ProcessingEvidence(scope,
                                            successful, source, reconstruction, conversion, facts));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void provenanceFlagIsIndependentOfPhaseAndOverallExecution() {
        for (boolean legacyFlag : new boolean[] {false, true}) {
            var source = summary(legacyFlag);
            var evidence = new ProcessingEvidence(Scope.CONVERSION, true, Optional.of(source),
                    List.of(), List.of(), Optional.of(facts()));
            assertSame(source, evidence.sourceSummary().orElseThrow());
            assertEquals(legacyFlag, evidence.sourceSummary().orElseThrow().provenanceSuccessful());
            assertEquals(true, evidence.phaseSuccessful());
        }
    }

    @Test
    void sourceSummaryNullsFingerprintAndVersionTokensReject() {
        reject("Source summary components must be non-null", () -> new SourceSummary(null, "1", "5", TIME, false, 0, 0));
        reject("Source summary components must be non-null", () -> new SourceSummary(Optional.empty(), null, "5", TIME, false, 0, 0));
        reject("Source summary components must be non-null", () -> new SourceSummary(Optional.empty(), "1", null, TIME, false, 0, 0));
        reject("Source summary components must be non-null", () -> new SourceSummary(Optional.empty(), "1", "5", null, false, 0, 0));
        for (var hash : List.of("", "a".repeat(63), "a".repeat(65), "A".repeat(64), "z".repeat(64))) {
            reject("Source fingerprint must be lowercase SHA-256", () -> new SourceSummary(Optional.of(hash), "1", "5", TIME, false, 0, 0));
        }
        for (var token : List.of("", "a".repeat(65), "with space", "a/b", "a\\b", "a:b", "a\n", "é")) {
            reject("Source versions must be bounded safe tokens", () -> new SourceSummary(Optional.empty(), token, "5", TIME, false, 0, 0));
            reject("Source versions must be bounded safe tokens", () -> new SourceSummary(Optional.empty(), "1", token, TIME, false, 0, 0));
        }
    }

    @Test
    void sourceSummaryCountsAndSuppliedFieldsRemainExact() {
        for (var counts : List.of(new int[] {-1, 0}, new int[] {1, -1}, new int[] {1, 2})) {
            reject("Source summary counts are invalid", () -> new SourceSummary(Optional.empty(), "1", "5", TIME, false,
                    counts[0], counts[1]));
        }
        var hash = "a".repeat(64);
        var version = "v".repeat(64);
        var summary = new SourceSummary(Optional.of(hash), version, "5.33.0-SNAPSHOT+build_1", TIME, false,
                Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertSame(hash, summary.selectedSourceFingerprint().orElseThrow());
        assertSame(version, summary.softwareVersion());
        assertSame(TIME, summary.completedAt());
        assertEquals(Integer.MAX_VALUE, summary.acceptedSlices());
        assertEquals(0, new SourceSummary(Optional.empty(), "1", "5", TIME, false, 0, 0).inputCount());
    }

    @Test
    void conversionFactNullReferencesAndDimensionArithmeticReject() {
        reject("Conversion fact components must be non-null", () -> facts(2, 3, 1, null, 6, 1, 1, 0, AFFINE, INTENSITY));
        reject("Conversion fact components must be non-null", () -> facts(2, 3, 1, ScalarType.INT16, 6, 1, 1, 0, null, INTENSITY));
        reject("Conversion fact components must be non-null", () -> facts(2, 3, 1, ScalarType.INT16, 6, 1, 1, 0, AFFINE, null));
        for (var dimensions : List.of(new int[] {0, 3, 1}, new int[] {2, -1, 1}, new int[] {2, 3, 0})) {
            reject("Conversion fact dimensions must be positive", () -> facts(dimensions[0], dimensions[1], dimensions[2],
                    ScalarType.INT16, 6, 1, 1, 0, AFFINE, INTENSITY));
        }
        reject("Conversion fact voxel count does not match dimensions", () -> facts(2, 3, 1, ScalarType.INT16, 5, 1, 1, 0, AFFINE, INTENSITY));
        reject("Conversion fact dimensions overflow voxel count", () -> facts(Integer.MAX_VALUE, Integer.MAX_VALUE, 3,
                ScalarType.INT16, Long.MAX_VALUE, 1, 1, 0, AFFINE, INTENSITY));
        long exact = (long) Integer.MAX_VALUE * Integer.MAX_VALUE;
        assertEquals(exact, facts(Integer.MAX_VALUE, Integer.MAX_VALUE, 1, ScalarType.UINT16,
                exact, 1, 1, 0, AFFINE, INTENSITY).voxelCount());
    }

    @Test
    void conversionSpacingMatchesExistingReportContract() {
        for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1, 0}) {
            reject("Conversion fact spacing must be finite and valid", () -> facts(2, 3, 1, ScalarType.INT16, 6, value, 1, 0, AFFINE, INTENSITY));
            reject("Conversion fact spacing must be finite and valid", () -> facts(2, 3, 1, ScalarType.INT16, 6, 1, value, 0, AFFINE, INTENSITY));
        }
        for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1}) {
            reject("Conversion fact spacing must be finite and valid", () -> facts(2, 3, 1, ScalarType.INT16, 6, 1, 1, value, AFFINE, INTENSITY));
        }
        assertEquals(0, facts(2, 3, 2, ScalarType.INT16, 12, Double.MIN_VALUE,
                Double.MAX_VALUE, 0, AFFINE, INTENSITY).sliceSpacingMm());
    }

    @Test
    void typedAffineIntensityScalarAndFlagsAreSuppliedFactsOnly() {
        for (var scalar : ScalarType.values()) {
            for (int flags = 0; flags < 16; flags++) {
                var facts = new ConversionFacts(2, 3, 1, scalar, 6, 1, 2, 0, AFFINE, INTENSITY,
                        (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, (flags & 8) != 0);
                assertSame(AFFINE, facts.niftiRasVoxelToWorld());
                assertSame(INTENSITY, facts.intensityTransform());
                assertEquals(scalar, facts.scalarType());
                assertEquals((flags & 1) != 0, facts.storedVoxelValuesPreserved());
                assertEquals((flags & 2) != 0, facts.resampled());
                assertEquals((flags & 4) != 0, facts.interpolated());
                assertEquals((flags & 8) != 0, facts.voxelOrderChanged());
            }
        }
        assertArrayEquals(new String[] {"width", "height", "depth", "scalarType", "voxelCount", "rowSpacingMm",
                "columnSpacingMm", "sliceSpacingMm", "niftiRasVoxelToWorld", "intensityTransform", "storedVoxelValuesPreserved",
                "resampled", "interpolated", "voxelOrderChanged"}, Arrays.stream(ConversionFacts.class.getRecordComponents())
                        .map(c -> c.getName()).toArray(String[]::new));
    }

    private static SourceSummary summary(boolean legacySuccess) {
        return new SourceSummary(Optional.of("a".repeat(64)), "1.0-SNAPSHOT", "5.33.0", TIME, legacySuccess, 2, 2);
    }
    private static ConversionFacts facts() { return facts(2, 3, 1, ScalarType.INT16, 6, 1, 1, 0, AFFINE, INTENSITY); }
    private static ConversionFacts facts(int w, int h, int d, ScalarType type, long count,
            double row, double column, double slice, AffineMatrix4 affine, IntensityTransform intensity) {
        return new ConversionFacts(w, h, d, type, count, row, column, slice, affine, intensity, true, false, false, false);
    }
    private static void reject(String message, Executable action) {
        var failure = assertThrowsExactly(IllegalArgumentException.class, action);
        assertEquals(message, failure.getMessage());
        assertEquals(null, failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
    }
}
