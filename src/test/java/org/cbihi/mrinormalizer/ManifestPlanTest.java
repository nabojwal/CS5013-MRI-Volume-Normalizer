package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import org.cbihi.mrinormalizer.application.dataset.model.FormatAssessment;
import org.cbihi.mrinormalizer.application.provenance.manifest.ContentDigest;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestOperation;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestOperation.Kind;
import org.cbihi.mrinormalizer.application.provenance.manifest.ProvenanceManifest;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath.Root;
import org.cbihi.mrinormalizer.application.provenance.manifest.SourceFileRecord;
import org.cbihi.mrinormalizer.domain.model.DetectionDiagnostic;
import org.cbihi.mrinormalizer.domain.model.DetectionOutcome;
import org.cbihi.mrinormalizer.domain.model.DetectionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** M01-M03 immutable execution-evidence plan, with supplied synthetic facts only. */
class ManifestPlanTest {
    private static final UUID JOB = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant TIME = Instant.parse("2026-10-04T00:00:00Z");
    private static final ContentDigest DIGEST = new ContentDigest(1, "a".repeat(64));

    // M01
    @Test
    void requiredComponentsAndVersionRejectWithFixedMessages() {
        reject("Operation components must be non-null", () -> new ManifestOperation(null, Kind.COPY,
                List.of(path("a")), output("out")));
        reject("Operation components must be non-null", () -> new ManifestOperation(id(1), null,
                List.of(path("a")), output("out")));
        reject("Operation components must be non-null", () -> new ManifestOperation(id(1), Kind.COPY,
                null, output("out")));
        reject("Operation components must be non-null", () -> new ManifestOperation(id(1), Kind.COPY,
                List.of(path("a")), null));
        reject("Manifest components must be non-null", () -> new ProvenanceManifest(1, null, TIME, List.of(), List.of()));
        reject("Manifest components must be non-null", () -> new ProvenanceManifest(1, JOB, null, List.of(), List.of()));
        reject("Manifest components must be non-null", () -> new ProvenanceManifest(1, JOB, TIME, null, List.of()));
        reject("Manifest components must be non-null", () -> new ProvenanceManifest(1, JOB, TIME, List.of(), null));
        for (int version : new int[] {Integer.MIN_VALUE, 0, 2, Integer.MAX_VALUE}) {
            reject("Manifest schema version is unsupported", () -> new ProvenanceManifest(version, JOB, TIME, List.of(), List.of()));
        }
    }

    @Test
    void operationIdsAndKindsRemainExactlyApproved() {
        for (var value : List.of("", "0".repeat(63), "0".repeat(65), "A".repeat(64), "g".repeat(64), id(1) + "\n")) {
            reject("Operation identifier must be lowercase hex", () -> new ManifestOperation(value, Kind.COPY,
                    List.of(path("a")), output("out")));
        }
        assertArrayEquals(new String[] {"COPY", "CONVERT_DICOM_TO_NIFTI"},
                Arrays.stream(Kind.values()).map(Enum::name).toArray(String[]::new));
        assertEquals("0".repeat(64), copy(0, path("a"), "out").operationId());
        assertEquals("f".repeat(64), new ManifestOperation("f".repeat(64), Kind.COPY,
                List.of(path("a")), output("out")).operationId());
    }

    @Test
    void operationCardinalityRootsNullsAndDuplicatesReject() {
        for (var kind : Kind.values()) {
            reject("Operation source cardinality is invalid", () -> new ManifestOperation(id(1), kind, List.of(), output("out")));
            reject("Operation destination requires OUTPUT root", () -> new ManifestOperation(id(1), kind,
                    List.of(path("a")), path("out")));
            reject("Operation sources must be unique SOURCE references", () -> new ManifestOperation(id(1), kind,
                    Arrays.asList((RelativePath) null), output("out")));
            reject("Operation sources must be unique SOURCE references", () -> new ManifestOperation(id(1), kind,
                    List.of(output("a")), output("out")));
        }
        reject("Operation source cardinality is invalid", () -> new ManifestOperation(id(1), Kind.COPY,
                List.of(path("a"), path("b")), output("out")));
        reject("Operation sources must be unique SOURCE references", () -> conversion(1,
                List.of(path("a"), path("a")), "out"));
    }

    @Test
    void plansResolveAllReferencesAndRejectDuplicateKeys() {
        var source = source("a", Optional.of(DIGEST));
        reject("Manifest sources must be non-null and unique", () -> plan(Arrays.asList((SourceFileRecord) null), List.of()));
        reject("Manifest sources must be non-null and unique", () -> plan(List.of(source, source), List.of()));
        var operation = copy(1, source.source(), "out");
        reject("Manifest operations must be non-null and unique", () -> plan(List.of(source), Arrays.asList((ManifestOperation) null)));
        reject("Manifest operations must be non-null and unique", () -> plan(List.of(source), List.of(operation, operation)));
        reject("Manifest operations must be non-null and unique", () -> plan(List.of(source),
                List.of(operation, copy(1, source.source(), "other"))));
        reject("Operation references unknown source", () -> plan(List.of(source), List.of(copy(1, path("missing"), "out"))));
        assertEquals(1, plan(List.of(source), List.of(operation)).operations().size());
    }

    @Test
    void equalDigestCopyMappingsRetainDistinctSources() {
        var a = source("a", Optional.of(DIGEST));
        var b = source("b", Optional.of(new ContentDigest(1, "a".repeat(64))));
        var result = plan(List.of(b, a), List.of(copy(2, b.source(), "shared"), copy(1, a.source(), "shared")));
        assertEquals(List.of(a, b), result.sources());
        assertEquals(2, result.operations().size());
        assertFalse(result.operations().get(0).sources().equals(result.operations().get(1).sources()));
        assertEquals(result.operations().get(0).destination(), result.operations().get(1).destination());
    }

    @Test
    void incompatibleAndConversionDestinationSharingRejects() {
        var a = source("a", Optional.of(DIGEST));
        for (var digest : List.of(Optional.<ContentDigest>empty(), Optional.of(new ContentDigest(2, DIGEST.sha256())),
                Optional.of(new ContentDigest(1, "b".repeat(64))))) {
            var b = source("b", digest);
            reject("Manifest destination mappings conflict", () -> plan(List.of(a, b),
                    List.of(copy(1, a.source(), "shared"), copy(2, b.source(), "shared"))));
        }
        reject("Manifest destination mappings conflict", () -> plan(List.of(a), List.of(
                conversion(1, List.of(a.source()), "shared"), conversion(2, List.of(a.source()), "shared"))));
        reject("Manifest destination mappings conflict", () -> plan(List.of(a), List.of(
                copy(1, a.source(), "shared"), conversion(2, List.of(a.source()), "shared"))));
    }

    @Test
    void lexicalAncestorDestinationsConflictWithoutFilesystemInference() {
        var a = source("a", Optional.of(DIGEST));
        for (var destinations : List.of(List.of("out", "out/child"), List.of("out/child", "out"))) {
            reject("Manifest destination mappings conflict", () -> plan(List.of(a), List.of(
                    copy(1, a.source(), destinations.get(0)), copy(2, a.source(), destinations.get(1)))));
        }
        assertEquals(3, plan(List.of(a), List.of(copy(1, a.source(), "out"), copy(2, a.source(), "out2"),
                copy(3, a.source(), "OUT/child"))).operations().size());
    }

    @Test
    void countLimitsRejectBeforeDuplicateProcessing() {
        var source = source("a", Optional.empty());
        var operation = copy(1, source.source(), "out");
        reject("Manifest count limit exceeded", () -> plan(Collections.nCopies(100001, source), List.of()));
        reject("Manifest count limit exceeded", () -> plan(List.of(source), Collections.nCopies(100001, operation)));
        reject("Operation source cardinality is invalid", () -> conversion(1, Collections.nCopies(100001, source.source()), "out"));
    }

    @Test
    void exactSourceAndReferenceLimitsAreRepresentable() {
        var sources = new ArrayList<SourceFileRecord>(100000);
        var references = new ArrayList<RelativePath>(100000);
        for (int i = 0; i < 100000; i++) {
            var source = source("source/" + i, Optional.empty());
            sources.add(source);
            references.add(source.source());
        }
        var a = conversion(1, references, "a");
        var b = conversion(2, references, "b");
        var exact = plan(sources, List.of(a, b));
        assertEquals(100000, exact.sources().size());
        assertEquals(200000, exact.operations().stream().mapToInt(o -> o.sources().size()).sum());
        reject("Manifest source reference limit exceeded", () -> plan(sources,
                List.of(a, b, copy(3, references.get(0), "c"))));
    }

    @Test
    void exactOperationLimitIsRepresentable() {
        var source = source("a", Optional.of(DIGEST));
        var operations = new ArrayList<ManifestOperation>(100000);
        for (int i = 0; i < 100000; i++) {
            operations.add(copy(i, source.source(), "same"));
        }
        assertEquals(100000, plan(List.of(source), operations).operations().size());
    }

    // M02
    @Test
    void frozenPlanSeparatesExpectedEvidenceFromExecution() {
        var assessment = FormatAssessment.fromDetection(DetectionResult.identified(DetectionOutcome.DICOM));
        var missing = new SourceFileRecord(path("synthetic/nonexistent"), Optional.empty(), assessment, List.of());
        var operation = copy(7, missing.source(), "accepted-layout/scan");
        var withoutEvidence = plan(List.of(missing), List.of(operation));
        var supplied = new SourceFileRecord(missing.source(), Optional.of(DIGEST), assessment, List.of());
        var withEvidence = plan(List.of(supplied), List.of(operation));
        assertTrue(withoutEvidence.sources().get(0).digest().isEmpty());
        assertSame(operation, withEvidence.operations().get(0));
        assertSame(DIGEST, withEvidence.sources().get(0).digest().orElseThrow());
        assertSame(assessment, withEvidence.sources().get(0).assessment());
        assertEquals(operation.operationId(), withEvidence.operations().get(0).operationId());
        assertEquals(operation.destination(), withEvidence.operations().get(0).destination());
    }

    @Test
    void listsAreCopiedOrderedAndUnmodifiable() {
        var a = source("a", Optional.empty());
        var b = source("b", Optional.empty());
        var references = new ArrayList<>(List.of(b.source(), a.source()));
        var conversion = conversion(2, references, "convert");
        references.clear();
        assertEquals(List.of(a.source(), b.source()), conversion.sources());
        assertThrowsExactly(UnsupportedOperationException.class, () -> conversion.sources().clear());
        var sourceList = new ArrayList<>(List.of(b, a));
        var operationList = new ArrayList<>(List.of(conversion, copy(1, a.source(), "copy")));
        var result = plan(sourceList, operationList);
        sourceList.clear();
        operationList.clear();
        assertEquals(List.of(a, b), result.sources());
        assertEquals(id(1), result.operations().get(0).operationId());
        assertThrowsExactly(UnsupportedOperationException.class, () -> result.sources().clear());
        assertThrowsExactly(UnsupportedOperationException.class, () -> result.operations().clear());
    }

    @Test
    void canonicalOrderingUsesUnsignedUtf8RatherThanUtf16() {
        var bmp = source("\ue000", Optional.empty());
        var supplementary = source("😀", Optional.empty());
        var result = plan(List.of(supplementary, bmp), List.of(conversion(1,
                List.of(supplementary.source(), bmp.source()), "out")));
        assertEquals(List.of(bmp, supplementary), result.sources());
        assertEquals(List.of(bmp.source(), supplementary.source()), result.operations().get(0).sources());
        assertEquals(result, plan(List.of(bmp, supplementary), List.of(conversion(1,
                List.of(bmp.source(), supplementary.source()), "out"))));
    }

    @Test
    void emptyAndNonexecutingInventoriesRemainPlansOnly() {
        assertEquals(List.of(), plan(List.of(), List.of()).operations());
        var source = source("a", Optional.empty());
        assertEquals(List.of(source), plan(List.of(source), List.of()).sources());
        assertEquals(1, plan(List.of(source), List.of(copy(1, source.source(), "out"))).operations().size());
    }

    @Test
    void operationAndManifestRecordShapesRemainFrozen() {
        assertArrayEquals(new String[] {"operationId", "kind", "sources", "destination"},
                Arrays.stream(ManifestOperation.class.getRecordComponents()).map(c -> c.getName()).toArray(String[]::new));
        assertArrayEquals(new String[] {"schemaVersion", "jobId", "createdAt", "sources", "operations"},
                Arrays.stream(ProvenanceManifest.class.getRecordComponents()).map(c -> c.getName()).toArray(String[]::new));
        assertEquals("java.util.List<" + RelativePath.class.getName() + ">",
                ManifestOperation.class.getRecordComponents()[2].getGenericType().getTypeName());
        assertEquals("java.util.List<" + SourceFileRecord.class.getName() + ">",
                ProvenanceManifest.class.getRecordComponents()[3].getGenericType().getTypeName());
        assertEquals("java.util.List<" + ManifestOperation.class.getName() + ">",
                ProvenanceManifest.class.getRecordComponents()[4].getGenericType().getTypeName());
    }

    // M03: every accepted M5 raw pair, preserving both extension hints.
    @Test
    void assessmentPreservesAcceptedF0Combinations() {
        var rawCases = new ArrayList<DetectionResult>();
        for (var outcome : List.of(DetectionOutcome.DICOM, DetectionOutcome.NIFTI, DetectionOutcome.NIFTI_GZ)) {
            rawCases.add(DetectionResult.identified(outcome));
        }
        for (var diagnostic : List.of(DetectionDiagnostic.INVALID_DICOM, DetectionDiagnostic.INVALID_NIFTI, DetectionDiagnostic.INVALID_GZIP)) {
            rawCases.add(DetectionResult.corrupt(diagnostic));
        }
        for (var diagnostic : List.of(DetectionDiagnostic.INPUT_NOT_FOUND, DetectionDiagnostic.INPUT_IS_DIRECTORY,
                DetectionDiagnostic.INPUT_NOT_READABLE, DetectionDiagnostic.INVALID_INPUT_REFERENCE, DetectionDiagnostic.IO_ERROR,
                DetectionDiagnostic.EMPTY_INPUT, DetectionDiagnostic.INPUT_TOO_LARGE, DetectionDiagnostic.UNSUPPORTED_FORMAT)) {
            rawCases.add(DetectionResult.unknown(diagnostic));
        }
        assertEquals(14, rawCases.size());
        for (var original : rawCases) {
            for (boolean mismatch : new boolean[] {false, true}) {
                var raw = original.withExtensionMismatch(mismatch);
                var assessment = FormatAssessment.fromDetection(raw);
                var source = new SourceFileRecord(path("a"), Optional.empty(), assessment, List.of());
                var result = plan(List.of(source), List.of());
                assertSame(assessment, result.sources().get(0).assessment());
                assertSame(raw, result.sources().get(0).assessment().initialDetection());
                assertEquals(mismatch, result.sources().get(0).assessment().initialDetection().extensionMismatch());
            }
        }
    }

    private static ProvenanceManifest plan(List<SourceFileRecord> sources, List<ManifestOperation> operations) {
        return new ProvenanceManifest(1, JOB, TIME, sources, operations);
    }

    private static SourceFileRecord source(String path, Optional<ContentDigest> digest) {
        return new SourceFileRecord(path(path), digest,
                FormatAssessment.fromDetection(DetectionResult.identified(DetectionOutcome.DICOM)), List.of());
    }

    private static RelativePath path(String value) { return new RelativePath(Root.SOURCE, value); }
    private static RelativePath output(String value) { return new RelativePath(Root.OUTPUT, value); }
    private static String id(int value) {
        var suffix = Integer.toHexString(value);
        return "0".repeat(64 - suffix.length()) + suffix;
    }
    private static ManifestOperation copy(int id, RelativePath source, String destination) {
        return new ManifestOperation(id(id), Kind.COPY, List.of(source), output(destination));
    }
    private static ManifestOperation conversion(int id, List<RelativePath> sources, String destination) {
        return new ManifestOperation(id(id), Kind.CONVERT_DICOM_TO_NIFTI, sources, output(destination));
    }
    private static void reject(String message, Executable action) {
        var failure = assertThrowsExactly(IllegalArgumentException.class, action);
        assertEquals(message, failure.getMessage());
        assertEquals(null, failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
    }
}
