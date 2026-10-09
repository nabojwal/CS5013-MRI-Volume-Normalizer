package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.cbihi.mrinormalizer.application.dataset.model.ConversionReadiness;
import org.cbihi.mrinormalizer.application.dataset.model.FormatAssessment;
import org.cbihi.mrinormalizer.application.dataset.model.SupportStatus;
import org.cbihi.mrinormalizer.application.dataset.model.ValidityStatus;
import org.cbihi.mrinormalizer.application.provenance.manifest.ContentDigest;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Code;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Phase;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath.Root;
import org.cbihi.mrinormalizer.application.provenance.manifest.SourceFileRecord;
import org.cbihi.mrinormalizer.domain.model.DetectionDiagnostic;
import org.cbihi.mrinormalizer.domain.model.DetectionOutcome;
import org.cbihi.mrinormalizer.domain.model.DetectionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** F1-S1A supplied-value contracts only; no filesystem fixtures or hashing. */
class ManifestValueTest {

    private static final String HASH = "0123456789abcdef".repeat(4);

    // V01: fixed constructor messages, including every nullable component.
    @Test
    void rejectsNullCoreComponentsWithFixedMessages() {
        reject("Relative path components must be non-null", () -> new RelativePath(null, "scan"));
        reject("Relative path components must be non-null", () -> new RelativePath(Root.SOURCE, null));
        reject("Content digest hash must be non-null", () -> new ContentDigest(0, null));
        reject("Failure components must be non-null", () -> new ManifestFailure(null, Code.HASH_FAILED));
        reject("Failure components must be non-null", () -> new ManifestFailure(Phase.HASHING, null));
        var source = source();
        var assessment = assessment();
        reject("Source record components must be non-null",
                () -> new SourceFileRecord(null, Optional.empty(), assessment, List.of()));
        reject("Source record components must be non-null",
                () -> new SourceFileRecord(source, null, assessment, List.of()));
        reject("Source record components must be non-null",
                () -> new SourceFileRecord(source, Optional.empty(), null, List.of()));
        reject("Source record components must be non-null",
                () -> new SourceFileRecord(source, Optional.empty(), assessment, null));
        reject("Failures must be non-null and unique", () -> new SourceFileRecord(source,
                Optional.empty(), assessment, Arrays.asList((ManifestFailure) null)));
    }

    @Test
    void suppliedDigestRejectsMalformedHashesAndNegativeSizes() {
        for (var value : List.of("", "a".repeat(63), "a".repeat(65), "A".repeat(64),
                "g".repeat(64), " " + HASH, HASH + "\n", "é".repeat(64), "0x" + HASH)) {
            reject("Content digest hash must be lowercase SHA-256",
                    () -> new ContentDigest(0, value));
        }
        reject("Content digest size must be nonnegative", () -> new ContentDigest(-1, HASH));
        reject("Content digest size must be nonnegative", () -> new ContentDigest(Long.MIN_VALUE, HASH));
    }

    @Test
    void suppliedDigestAcceptsExactSizeAndHexBoundaries() {
        for (long size : new long[] {0, 1, Long.MAX_VALUE}) {
            for (var hash : List.of("0".repeat(64), "f".repeat(64), HASH)) {
                var digest = new ContentDigest(size, hash);
                assertEquals(size, digest.sizeBytes());
                assertSame(hash, digest.sha256());
            }
        }
    }

    @Test
    void sourceRecordRequiresSourceRootAndBoundsFailures() {
        reject("Source record requires SOURCE root", () -> new SourceFileRecord(
                new RelativePath(Root.OUTPUT, "scan"), Optional.empty(), assessment(), List.of()));
        var failure = new ManifestFailure(Phase.HASHING, Code.HASH_FAILED);
        reject("Source record failure limit exceeded", () -> new SourceFileRecord(
                source(), Optional.empty(), assessment(), Collections.nCopies(65, failure)));
        reject("Failures must be non-null and unique", () -> new SourceFileRecord(
                source(), Optional.empty(), assessment(), Collections.nCopies(64, failure)));
    }

    @Test
    void approvedRecordAndEnumShapesHaveNoExplicitPublicMethods() {
        assertShape(RelativePath.class, new String[] {"root", "path"},
                new Class<?>[] {Root.class, String.class});
        assertShape(ContentDigest.class, new String[] {"sizeBytes", "sha256"},
                new Class<?>[] {long.class, String.class});
        assertShape(SourceFileRecord.class, new String[] {"source", "digest", "assessment", "failures"},
                new Class<?>[] {RelativePath.class, Optional.class, FormatAssessment.class, List.class});
        assertShape(ManifestFailure.class, new String[] {"phase", "code"},
                new Class<?>[] {Phase.class, Code.class});
        var components = SourceFileRecord.class.getRecordComponents();
        assertEquals("java.util.Optional<" + ContentDigest.class.getName() + ">",
                components[1].getGenericType().getTypeName());
        assertEquals("java.util.List<" + ManifestFailure.class.getName() + ">",
                components[3].getGenericType().getTypeName());
        assertArrayEquals(new String[] {"SOURCE", "OUTPUT"},
                Arrays.stream(Root.values()).map(Enum::name).toArray(String[]::new));
        assertArrayEquals(new String[] {"HASHING", "EXECUTION", "POST_WRITE_VALIDATION", "PERSISTENCE"},
                Arrays.stream(Phase.values()).map(Enum::name).toArray(String[]::new));
        assertArrayEquals(new String[] {"INVALID_MANIFEST", "INVALID_REFERENCE", "CONTAINMENT_UNPROVEN",
                "INPUT_UNAVAILABLE", "SOURCE_CHANGED", "HASH_FAILED", "CHECKPOINT_CONFLICT", "WRITE_FAILED",
                "PUBLICATION_UNAVAILABLE", "READ_FAILED", "UNSUPPORTED_SCHEMA", "CORRUPT_CHECKPOINT",
                "ACCESS_CONTROL_UNAVAILABLE", "RESOURCE_LIMIT", "CLEANUP_FAILED", "RECOVERY_REQUIRED",
                "OUTPUT_CONFLICT", "VERIFICATION_FAILED", "INTERRUPTED"},
                Arrays.stream(Code.values()).map(Enum::name).toArray(String[]::new));
    }

    // V02: portable lexical references, never normalized or resolved.
    @Test
    void relativePathsRejectUnsafeReferenceForms() {
        for (var path : List.of("", "/scan", "scan/", "scan//slice", ".", "..", "a/./b", "a/../b",
                "a\\b", "C:/scan", "C:scan", "//server/share", "\\\\server\\share", "file:scan",
                "scan.", "scan ", "a./b", "a /b", "a/\u0000b", "a/\nb", "a/\tb", "a/\u007fb",
                "a/\u0085b", "a/\u009fb", "a/\ud800b", "a/\udc00b")) {
            for (var root : Root.values()) {
                reject("Relative path must be canonical and portable", () -> new RelativePath(root, path));
            }
        }
    }

    @Test
    void relativePathsRejectReservedDeviceBasesIncludingExtensions() {
        var devices = new ArrayList<>(List.of("CON", "PRN", "AUX", "NUL", "CONIN$", "CONOUT$"));
        for (int i = 1; i <= 9; i++) {
            devices.add("COM" + i);
            devices.add("LPT" + i);
        }
        devices.addAll(List.of("COM¹", "COM²", "COM³", "LPT¹", "LPT²", "LPT³"));
        for (var device : devices) {
            for (var name : List.of(device, device.toLowerCase(java.util.Locale.ROOT), device + ".nii.gz")) {
                reject("Relative path must be canonical and portable",
                        () -> new RelativePath(Root.SOURCE, "study/" + name + "/slice"));
            }
        }
    }

    @Test
    void relativePathsPreserveAllowedTextAndIdentityExactly() {
        for (var path : List.of("Study Name/Patient Synthetic/1.2.840.10008/scan.nii.gz", "日本語/é/😀",
                "e\u0301/scan", "é/scan", "SCAN/slice", "scan/slice", "%2e%2e/scan", "a b/c d",
                "CONSOLE/COM0/LPT10/scan", " leading/scan", "a...b/.hidden")) {
            for (var root : Root.values()) {
                var value = new RelativePath(root, path);
                assertSame(path, value.path());
                assertEquals(root, value.root());
            }
        }
        assertFalse(new RelativePath(Root.SOURCE, "é/scan")
                .equals(new RelativePath(Root.SOURCE, "e\u0301/scan")));
        assertFalse(new RelativePath(Root.SOURCE, "SCAN")
                .equals(new RelativePath(Root.SOURCE, "scan")));
        assertFalse(new RelativePath(Root.SOURCE, "scan")
                .equals(new RelativePath(Root.OUTPUT, "scan")));
    }

    @Test
    void relativePathUtf8LimitCountsScalarWidthsExactly() {
        for (var path : List.of("a".repeat(4096), "é".repeat(2048), "界".repeat(1365) + "a",
                "😀".repeat(1024))) {
            assertEquals(path, new RelativePath(Root.SOURCE, path).path());
            reject("Relative path must be canonical and portable",
                    () -> new RelativePath(Root.SOURCE, path + "a"));
        }
        reject("Relative path must be canonical and portable",
                () -> new RelativePath(Root.SOURCE, "界".repeat(1366)));
    }

    // V03: supplied facts preserve recognition; neither paths nor hashes are read.
    @Test
    void suppliedDigestAndSyntheticSourceDoNotUpgradeAssessment() {
        var digest = new ContentDigest(123, HASH);
        var raw = new DetectionResult(DetectionOutcome.NIFTI_GZ, DetectionDiagnostic.NONE, true);
        var assessment = FormatAssessment.fromDetection(raw);
        var source = new RelativePath(Root.SOURCE, "synthetic-does-not-exist/wrong.extension");
        var record = new SourceFileRecord(source, Optional.of(digest), assessment, List.of());
        assertSame(source, record.source());
        assertSame(digest, record.digest().orElseThrow());
        assertSame(assessment, record.assessment());
        assertSame(raw, record.assessment().initialDetection());
        assertTrue(record.assessment().initialDetection().extensionMismatch());
        assertEquals(ValidityStatus.NOT_ASSESSED, record.assessment().validity());
        assertEquals(SupportStatus.NOT_ASSESSED, record.assessment().support());
        assertEquals(ConversionReadiness.REQUIRES_VALIDATION, record.assessment().readiness());
    }

    @Test
    void absentDigestAndSuppliedEmptyOrWrapperDigestDoNotInventFailures() {
        var unavailable = FormatAssessment.fromDetection(DetectionResult.unknown(DetectionDiagnostic.INPUT_NOT_FOUND));
        var noDigest = new SourceFileRecord(source(), Optional.empty(), unavailable, List.of());
        assertTrue(noDigest.digest().isEmpty());
        assertTrue(noDigest.failures().isEmpty());
        assertSame(unavailable, noDigest.assessment());
        var emptyDigest = new ContentDigest(0,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        var wrapperDigest = new ContentDigest(123, HASH);
        for (var digest : List.of(emptyDigest, wrapperDigest)) {
            var record = new SourceFileRecord(source(), Optional.of(digest), unavailable, List.of());
            assertSame(digest, record.digest().orElseThrow());
            assertEquals(ConversionReadiness.BLOCKED, record.assessment().readiness());
            assertTrue(record.failures().isEmpty());
        }
    }

    @Test
    void suppliedWorkflowFailureDoesNotTranslateRawDetectionEvidence() {
        var raw = new DetectionResult(DetectionOutcome.UNKNOWN, DetectionDiagnostic.INPUT_TOO_LARGE, true);
        var assessment = FormatAssessment.fromDetection(raw);
        var failure = new ManifestFailure(Phase.HASHING, Code.SOURCE_CHANGED);
        var record = new SourceFileRecord(source(), Optional.empty(), assessment, List.of(failure));
        assertSame(raw, record.assessment().initialDetection());
        assertEquals(ValidityStatus.INCONCLUSIVE, record.assessment().validity());
        assertEquals(List.of(failure), record.failures());
    }

    // V04: independent expected matrix exhausts all 4 x 19 phase/code pairs.
    @Test
    void workflowFailuresHaveOnlyApprovedPhaseCodePairs() {
        for (var code : Code.values()) {
            var allowed = switch (code.name()) {
                case "INVALID_MANIFEST", "CHECKPOINT_CONFLICT", "UNSUPPORTED_SCHEMA", "CORRUPT_CHECKPOINT" ->
                    EnumSet.of(Phase.PERSISTENCE);
                case "INVALID_REFERENCE", "INPUT_UNAVAILABLE", "CONTAINMENT_UNPROVEN",
                        "READ_FAILED", "ACCESS_CONTROL_UNAVAILABLE", "RESOURCE_LIMIT" ->
                    EnumSet.of(Phase.HASHING, Phase.EXECUTION, Phase.PERSISTENCE);
                case "SOURCE_CHANGED", "HASH_FAILED" -> EnumSet.of(Phase.HASHING);
                case "INTERRUPTED" -> EnumSet.of(Phase.HASHING, Phase.EXECUTION);
                case "CLEANUP_FAILED", "RECOVERY_REQUIRED", "OUTPUT_CONFLICT",
                        "WRITE_FAILED", "PUBLICATION_UNAVAILABLE" ->
                    EnumSet.of(Phase.EXECUTION, Phase.PERSISTENCE);
                case "VERIFICATION_FAILED" -> EnumSet.of(Phase.EXECUTION, Phase.POST_WRITE_VALIDATION);
                default -> throw new AssertionError("Unexpected failure code");
            };
            for (var phase : Phase.values()) {
                if (allowed.contains(phase)) {
                    var failure = new ManifestFailure(phase, code);
                    assertEquals(phase, failure.phase());
                    assertEquals(code, failure.code());
                } else {
                    reject("Failure code does not apply to phase", () -> new ManifestFailure(phase, code));
                }
            }
        }
    }

    // V05: value equality and canonical immutable failure lists.
    @Test
    void collectionsAreCopiedCanonicalAndUnique() {
        var first = new ManifestFailure(Phase.HASHING, Code.HASH_FAILED);
        var second = new ManifestFailure(Phase.HASHING, Code.SOURCE_CHANGED);
        var third = new ManifestFailure(Phase.PERSISTENCE, Code.WRITE_FAILED);
        var input = new ArrayList<>(List.of(third, first, second));
        var value = new SourceFileRecord(source(), Optional.empty(), assessment(), input);
        input.clear();
        assertEquals(List.of(second, first, third), value.failures());
        assertThrowsExactly(UnsupportedOperationException.class, () -> value.failures().clear());
        assertThrowsExactly(UnsupportedOperationException.class, () -> value.failures().add(first));
        assertThrowsExactly(UnsupportedOperationException.class, () -> value.failures().set(0, third));
        reject("Failures must be non-null and unique", () -> new SourceFileRecord(source(),
                Optional.empty(), assessment(), List.of(first, new ManifestFailure(Phase.HASHING, Code.HASH_FAILED))));
    }

    @Test
    void allFailurePermutationsProduceTheSameValue() {
        var a = new ManifestFailure(Phase.HASHING, Code.HASH_FAILED);
        var b = new ManifestFailure(Phase.EXECUTION, Code.RECOVERY_REQUIRED);
        var c = new ManifestFailure(Phase.PERSISTENCE, Code.INVALID_MANIFEST);
        var assessment = assessment();
        var expected = new SourceFileRecord(source(), Optional.of(new ContentDigest(1, HASH)),
                assessment, List.of(a, b, c));
        for (var failures : List.of(List.of(a, b, c), List.of(a, c, b), List.of(b, a, c),
                List.of(b, c, a), List.of(c, a, b), List.of(c, b, a))) {
            var actual = new SourceFileRecord(source(), Optional.of(new ContentDigest(1, HASH)), assessment, failures);
            assertEquals(expected, actual);
            assertEquals(expected.hashCode(), actual.hashCode());
            assertEquals(expected.failures(), actual.failures());
        }
    }

    @Test
    void equalCodesInDifferentAllowedPhasesRemainDistinct() {
        var hash = new ManifestFailure(Phase.HASHING, Code.INPUT_UNAVAILABLE);
        var execution = new ManifestFailure(Phase.EXECUTION, Code.INPUT_UNAVAILABLE);
        var persistence = new ManifestFailure(Phase.PERSISTENCE, Code.INPUT_UNAVAILABLE);
        var value = new SourceFileRecord(source(), Optional.empty(), assessment(),
                List.of(persistence, execution, hash));
        assertEquals(List.of(hash, execution, persistence), value.failures());
    }

    @Test
    void allApplicableFailuresFitTheSourceLimitWithoutLoss() {
        var failures = new ArrayList<ManifestFailure>();
        for (var phase : Phase.values()) {
            for (var code : Code.values()) {
                try {
                    failures.add(new ManifestFailure(phase, code));
                } catch (IllegalArgumentException ignored) {
                    // Exhaustive applicability is asserted independently by V04.
                }
            }
        }
        var ordered = List.copyOf(failures);
        Collections.reverse(failures);
        var value = new SourceFileRecord(source(), Optional.empty(), assessment(), failures);
        assertEquals(38, value.failures().size());
        assertEquals(ordered, value.failures());
    }

    @Test
    void imagingFailuresCannotBecomePersistenceExceptions() {
        for (var phase : List.of(Phase.HASHING, Phase.EXECUTION)) {
            for (var token : List.of("READ_FAILED", "ACCESS_CONTROL_UNAVAILABLE", "RESOURCE_LIMIT", "INTERRUPTED")) {
                var failure = new ManifestFailure(phase, Code.valueOf(token));
                reject("Persistence failures must be unique persistence facts", () ->
                        new org.cbihi.mrinormalizer.application.provenance.manifest.ProvenancePersistenceException(
                                List.of(failure),
                                org.cbihi.mrinormalizer.application.provenance.manifest.ProvenancePersistenceException.PublicationOutcome.NOT_PUBLISHED,
                                Optional.empty()));
            }
        }
        reject("Failure code does not apply to phase", () -> new ManifestFailure(Phase.PERSISTENCE, Code.valueOf("INTERRUPTED")));
        reject("Failure code does not apply to phase", () -> new ManifestFailure(Phase.POST_WRITE_VALIDATION, Code.valueOf("INTERRUPTED")));
    }

    private static RelativePath source() {
        return new RelativePath(Root.SOURCE, "synthetic/scan");
    }

    private static FormatAssessment assessment() {
        return FormatAssessment.fromDetection(DetectionResult.identified(DetectionOutcome.DICOM));
    }

    private static void reject(String message, Executable action) {
        var failure = assertThrowsExactly(IllegalArgumentException.class, action);
        assertEquals(message, failure.getMessage());
        assertEquals(null, failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
    }

    private static void assertShape(Class<?> type, String[] names, Class<?>[] types) {
        assertTrue(type.isRecord());
        assertEquals("org.cbihi.mrinormalizer.application.provenance.manifest", type.getPackageName());
        var components = type.getRecordComponents();
        assertArrayEquals(names, Arrays.stream(components).map(c -> c.getName()).toArray(String[]::new));
        assertArrayEquals(types, Arrays.stream(components).map(c -> c.getType()).toArray(Class<?>[]::new));
        assertEquals(1, type.getConstructors().length);
        assertArrayEquals(types, type.getConstructors()[0].getParameterTypes());
        var allowedMethods = new ArrayList<>(List.of(names));
        allowedMethods.addAll(List.of("equals", "hashCode", "toString"));
        for (var method : type.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                assertFalse(Modifier.isStatic(method.getModifiers()));
                assertTrue(allowedMethods.contains(method.getName()), method.getName());
            }
        }
    }
}
