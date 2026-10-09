package org.cbihi.mrinormalizer.infrastructure.filesystem;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.cbihi.mrinormalizer.application.dataset.model.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestState.JobState;
import org.cbihi.mrinormalizer.domain.error.DicomProcessingError;
import org.cbihi.mrinormalizer.domain.error.DicomToNiftiError;
import org.cbihi.mrinormalizer.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** J01-J04: independent golden bytes, a closed language and exact typed round trips. */
class JsonManifestCodecTest {
    private static final UUID JOB = new UUID(0, 1);
    private static final Instant TIME = Instant.parse("2026-10-04T00:00:00Z");
    private static final String HASH = "a".repeat(64);
    private static final String ID = "b".repeat(64);
    private static final String UNICODE_PATH = "study/quoted \"MRI\" 😀.dcm";
    private static final String PLAN_PREFIX = "{\"schema\":\"org.cbihi.mrinormalizer.provenance-plan\",\"schemaVersion\":1,\"jobId\":\"00000000-0000-0000-0000-000000000001\",\"createdAt\":\"2026-10-04T00:00:00Z\",\"roots\":[\"SOURCE\",\"OUTPUT\"],\"sources\":[";
    private static final String EMPTY_PLAN = PLAN_PREFIX + "],\"operations\":[]}\n";
    private static final String CHECKPOINT_PREFIX = "{\"schema\":\"org.cbihi.mrinormalizer.provenance-checkpoint\",\"schemaVersion\":1,\"jobId\":\"00000000-0000-0000-0000-000000000001\",\"sequence\":1,\"previousRecordSha256\":\"" + HASH + "\",\"recordedAt\":\"2026-10-04T00:00:00Z\",\"kind\":\"";
    private static final String RUNNING = CHECKPOINT_PREFIX + "JOB_OBSERVED\",\"operationId\":null,\"observation\":null,\"jobState\":\"RUNNING\",\"failures\":[]}\n";
    private final JsonManifestCodec codec = new JsonManifestCodec();

    // J01
    @Test
    void planAndCheckpointGoldenBytesAreCanonical() {
        assertArrayEquals(utf8(EMPTY_PLAN), codec.encode(emptyPlan()));
        assertArrayEquals(utf8(RUNNING), codec.encode(job(JobState.RUNNING)));
        String intent = CHECKPOINT_PREFIX + "OPERATION_OBSERVED\",\"operationId\":\"" + ID
                + "\",\"observation\":{\"state\":\"IN_PROGRESS\",\"startedAt\":\"2026-10-04T00:00:00Z\",\"finishedAt\":null,\"outputDigest\":null,\"matchedExpectedSourceCount\":1,\"disposition\":null,\"processingEvidence\":null},\"jobState\":null,\"failures\":[]}\n";
        assertArrayEquals(utf8(intent), codec.encode(operation(State.IN_PROGRESS)));
        for (var bytes : List.of(codec.encode(emptyPlan()), codec.encode(job(JobState.RUNNING)))) {
            assertEquals('{', bytes[0]);
            assertEquals('\n', bytes[bytes.length - 1]);
            assertEquals(1, text(bytes).chars().filter(c -> c == '\n').count());
        }
    }

    @Test
    void fullSourceGoldenBytesPreserveUnicodeAndDetection() {
        String expected = PLAN_PREFIX + "{\"source\":{\"root\":\"SOURCE\",\"path\":\"study/quoted \\\"MRI\\\" 😀.dcm\"},\"digest\":{\"sizeBytes\":9223372036854775807,\"sha256\":\"" + HASH
                + "\"},\"assessment\":{\"initialDetection\":{\"outcome\":\"DICOM\",\"diagnostic\":\"NONE\",\"extensionMismatch\":true},\"format\":\"DICOM\",\"variant\":\"DICOM_UNSPECIFIED\",\"validity\":\"NOT_ASSESSED\",\"support\":\"NOT_ASSESSED\",\"readiness\":\"REQUIRES_VALIDATION\",\"reasons\":[\"VALIDATION_NOT_PERFORMED\"]},\"failures\":[]}],\"operations\":[{\"operationId\":\"" + ID
                + "\",\"kind\":\"COPY\",\"sources\":[{\"root\":\"SOURCE\",\"path\":\"study/quoted \\\"MRI\\\" 😀.dcm\"}],\"destination\":{\"root\":\"OUTPUT\",\"path\":\"organized/result.dcm\"}}]}\n";
        assertArrayEquals(utf8(expected), codec.encode(unicodePlan()));
        assertEquals(unicodePlan(), codec.decodePlan(utf8(expected)));
    }

    @Test
    void allNestedEvidenceFieldsHaveFixedOrderAndExplicitNulls() {
        String json = text(codec.encode(operation(State.WRITTEN_UNVERIFIED)));
        assertTrue(json.contains("\"scope\":\"CONVERSION\",\"phaseSuccessful\":true,\"sourceSummary\":{\"selectedSourceFingerprint\":{\"kind\":\"SELECTED_SERIES_SHA256\",\"sha256\":\"" + HASH + "\"},\"softwareVersion\":\"0.1.0\",\"dcm4cheVersion\":\"5.33.0\","));
        assertTrue(json.contains("\"reconstructionErrors\":[],\"conversionErrors\":[],\"conversionFacts\":{\"width\":1048576,\"height\":1048576,\"depth\":8193,\"scalarType\":\"INT16\",\"voxelCount\":9008298766368768,"));
        assertTrue(json.contains("\"intensityTransform\":{\"declared\":true,\"slope\":-2.0,\"intercept\":-0.0},\"storedVoxelValuesPreserved\":true,\"resampled\":false,\"interpolated\":false,\"voxelOrderChanged\":false,\"postWriteValidation\":\"NOT_PERFORMED\""));
        assertTrue(json.contains("\"niftiRasVoxelToWorld\":[[1.0,0.0,0.0,1.7976931348623157E308],[0.0,1.0,0.0,0.0],[0.0,0.0,1.0,0.0],[-0.0,0.0,0.0,1.0]]"));
    }

    @Test
    void independentFixtureExportUsesActualCodecBytes() throws Exception {
        var fixtures = List.of(codec.encode(emptyPlan()), codec.encode(unicodePlan()), codec.encode(job(JobState.RUNNING)),
                codec.encode(operation(State.WRITTEN_UNVERIFIED)), codec.encode(operation(State.FAILED)), codec.encode(operation(State.RECOVERY_REQUIRED)));
        for (var fixture : fixtures) assertEquals('\n', fixture[fixture.length - 1]);
        String directory = System.getProperty("provenance.fixture.directory");
        if (directory != null) {
            var folder = Path.of(directory);
            Files.createDirectories(folder);
            var names = List.of("plan-empty", "plan-unicode", "checkpoint-running", "checkpoint-conversion", "checkpoint-failed", "checkpoint-recovery");
            for (int i = 0; i < fixtures.size(); i++) Files.write(folder.resolve(names.get(i) + ".json"), fixtures.get(i));
        }
    }

    // J02
    @Test
    void onlyFixedCanonicalSchemaFormsDecode() {
        for (var candidate : List.of(EMPTY_PLAN.replace("\"schemaVersion\":1,\"jobId\"", "\"jobId\":null,\"schemaVersion\":1,\"jobId\""),
                EMPTY_PLAN.replace("\"schema\":", "\"schema\" :"), " " + EMPTY_PLAN, EMPTY_PLAN + " ", EMPTY_PLAN + "{}",
                EMPTY_PLAN.replace(",\"schemaVersion\":1", ",\"extra\":{},\"schemaVersion\":1"), EMPTY_PLAN.replace("[]", "[{}]"))) {
            reject(() -> codec.decodePlan(utf8(candidate)));
        }
        reject(() -> codec.decodePlan(utf8(EMPTY_PLAN.replace("\"schemaVersion\":1,\"jobId\":\"00000000-0000-0000-0000-000000000001\"",
                "\"jobId\":\"00000000-0000-0000-0000-000000000001\",\"schemaVersion\":1"))));
        assertEquals(emptyPlan(), codec.decodePlan(utf8(EMPTY_PLAN)));
    }

    @Test
    void alternateNumbersAndEscapesAreRejected() {
        String json = text(codec.encode(unicodePlan()));
        for (var alternate : List.of("9.223372036854775807e18", "09223372036854775807", "+9223372036854775807", "9223372036854775807.0")) {
            reject(() -> codec.decodePlan(utf8(json.replace("9223372036854775807", alternate))));
        }
        for (var candidate : List.of(json.replace("😀", "\\ud83d\\ude00"), json.replace("study/", "study\\/"), json.replace("DICOM", "\\u0044ICOM"),
                json.replace("2026-10-04T00:00:00Z", "2026-10-04T00:00:00.000Z"), json.replace("2026-10-04T00:00:00Z", "2026-10-04T01:00:00+01:00"))) {
            reject(() -> codec.decodePlan(utf8(candidate)));
        }
        String checkpoint = text(codec.encode(operation(State.WRITTEN_UNVERIFIED)));
        for (var alternate : List.of("1", "1.00", "1e0", "1.0e0", "0x1.0p0")) {
            reject(() -> codec.decodeCheckpoint(utf8(checkpoint.replace("[1.0,0.0", "[" + alternate + ",0.0"))));
        }
    }

    @Test
    void extraMissingDuplicateAndRemovedDiagnosticFieldsReject() {
        for (var candidate : List.of(RUNNING.replace("\"observation\":null,", ""), RUNNING.replace("\"sequence\":1", "\"sequence\":1,\"sequence\":1"),
                RUNNING.replace("\"failures\":[]", "\"failures\":[],\"message\":\"private/path\""), RUNNING.replace("\"sequence\":1", "\"sequence\":{\"arbitrary\":[1]}"))) {
            reject(() -> codec.decodeCheckpoint(utf8(candidate)));
        }
        String failure = text(codec.encode(operation(State.FAILED)));
        for (var candidate : List.of(failure.replace("\"code\":\"WRITE_FAILED\"", "\"code\":\"WRITE_FAILED\",\"detectionDiagnostic\":\"IO_ERROR\""),
                failure.replace("\"PERSISTENCE\"", "\"DETECTION\""), failure.replace("WRITE_FAILED", "EXISTING_DIAGNOSTIC"),
                failure.replace("previousRecordSha256", "previousRecordSha 256"), failure.replace("\"sha256\"", "\"sha 256\""))) {
            if (!candidate.equals(failure)) reject(() -> codec.decodeCheckpoint(utf8(candidate)));
        }
        reject(() -> codec.decodePlan(utf8(text(codec.encode(unicodePlan())).replace("sha256", "sha 256"))));
    }

    @Test
    void modelContradictionsCannotBeHiddenInCanonicalJson() {
        String plan = text(codec.encode(unicodePlan()));
        for (var candidate : List.of(plan.replace("\"outcome\":\"DICOM\"", "\"outcome\":\"UNKNOWN\""), plan.replace("DICOM_UNSPECIFIED", "NIFTI_UNSPECIFIED"),
                plan.replace("REQUIRES_VALIDATION", "READY"), plan.replace("\"sizeBytes\":9223372036854775807", "\"sizeBytes\":-1"), plan.replace("SOURCE", "OUTPUT"))) {
            reject(() -> codec.decodePlan(utf8(candidate)));
        }
        String checkpoint = text(codec.encode(operation(State.WRITTEN_UNVERIFIED)));
        for (var candidate : List.of(checkpoint.replace("\"width\":1048576", "\"width\":0"), checkpoint.replace("9008298766368768", "1"),
                checkpoint.replace("\"slope\":-2.0", "\"slope\":0.0"), checkpoint.replace("WRITTEN_UNVERIFIED", "IN_PROGRESS"),
                checkpoint.replace("\"phaseSuccessful\":true", "\"phaseSuccessful\":false"))) {
            reject(() -> codec.decodeCheckpoint(utf8(candidate)));
        }
    }

    @Test
    void schemasVersionsEnumsAndRequiredLiteralsAreClosed() {
        for (var candidate : List.of(EMPTY_PLAN.replace("schemaVersion\":1", "schemaVersion\":2"), EMPTY_PLAN.replace("provenance-plan", "other"),
                EMPTY_PLAN.replace("[\"SOURCE\",\"OUTPUT\"]", "[\"OUTPUT\",\"SOURCE\"]"))) reject(() -> codec.decodePlan(utf8(candidate)));
        String checkpoint = text(codec.encode(operation(State.WRITTEN_UNVERIFIED)));
        for (var candidate : List.of(checkpoint.replace("schemaVersion\":1", "schemaVersion\":2"), checkpoint.replace("WRITTEN_UNVERIFIED", "VERIFIED"),
                checkpoint.replace("SELECTED_SERIES_SHA256", "SELECTED_SERIES_SHA 256"), checkpoint.replace("NOT_PERFORMED", "PASSED"),
                checkpoint.replace("INT16", "FLOAT32"))) reject(() -> codec.decodeCheckpoint(utf8(candidate)));
        reject(() -> codec.decodeCheckpoint(utf8(EMPTY_PLAN)));
        reject(() -> codec.decodePlan(utf8(RUNNING)));
    }

    @Test
    void unorderedOrDuplicateModelListsAreRejected() {
        var a = source("a", assessment());
        var b = source("b", assessment());
        String json = text(codec.encode(new ProvenanceManifest(1, JOB, TIME, List.of(b, a), List.of())));
        String first = json.substring(json.indexOf("{\"source\""), json.indexOf("},{\"source\"") + 1);
        String second = json.substring(json.indexOf("},{\"source\"") + 2, json.indexOf("],\"operations\""));
        reject(() -> codec.decodePlan(utf8(json.replace(first + "," + second, second + "," + first))));
        reject(() -> codec.decodePlan(utf8(json.replace(first + "," + second, first + "," + first))));
        var errors = evidence(false, false);
        String checkpoint = text(codec.encode(failedWith(errors)));
        reject(() -> codec.decodeCheckpoint(utf8(checkpoint.replace("\"SERIES_NOT_SELECTED\",\"SERIES_NOT_FOUND\"", "\"SERIES_NOT_FOUND\",\"SERIES_NOT_SELECTED\""))));
        reject(() -> codec.decodeCheckpoint(utf8(checkpoint.replace("\"SERIES_NOT_SELECTED\"", "\"SERIES_NOT_SELECTED\",\"SERIES_NOT_SELECTED\""))));
        var raw = DetectionResult.identified(DetectionOutcome.NIFTI);
        var assessed = new FormatAssessment(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE,
                ValidityStatus.INVALID, SupportStatus.UNSUPPORTED, ConversionReadiness.BLOCKED,
                List.of(AssessmentReason.VALIDATION_FAILED, AssessmentReason.UNSUPPORTED_PROFILE));
        String reasons = text(codec.encode(new ProvenanceManifest(1, JOB, TIME, List.of(source("blocked", assessed)), List.of())));
        reject(() -> codec.decodePlan(utf8(reasons.replace("\"UNSUPPORTED_PROFILE\",\"VALIDATION_FAILED\"", "\"VALIDATION_FAILED\",\"UNSUPPORTED_PROFILE\""))));
        var failures = List.of(failure(), recovery());
        var observed = operation(State.RECOVERY_REQUIRED);
        String uncertain = text(codec.encode(new CheckpointRecord(1, JOB, 1, HASH, TIME, observed.kind(), observed.operationId(), observed.observation(), Optional.empty(), failures)));
        String execution = "{\"phase\":\"EXECUTION\",\"code\":\"RECOVERY_REQUIRED\"}";
        String persistence = "{\"phase\":\"PERSISTENCE\",\"code\":\"WRITE_FAILED\"}";
        reject(() -> codec.decodeCheckpoint(utf8(uncertain.replace(execution + "," + persistence, persistence + "," + execution))));
    }

    @Test
    void nullWrongScalarTypesAndTruncatedFormsReject() {
        reject(() -> codec.decodePlan(null));
        reject(() -> codec.decodeCheckpoint(null));
        for (int i = 0; i < EMPTY_PLAN.length(); i++) {
            final int length = i;
            reject(() -> codec.decodePlan(utf8(EMPTY_PLAN.substring(0, length))));
        }
        for (var candidate : List.of(RUNNING.replace("\"sequence\":1", "\"sequence\":\"1\""), RUNNING.replace("\"failures\":[]", "\"failures\":null"),
                RUNNING.replace("\"jobState\":\"RUNNING\"", "\"jobState\":true"))) reject(() -> codec.decodeCheckpoint(utf8(candidate)));
        reject(() -> codec.decodePlan(utf8(EMPTY_PLAN.replace("\"sources\":[]", "\"sources\":null"))));
    }

    // J03
    @Test
    void numbersUnicodeAndBoundsRemainExact() {
        var plan = unicodePlan();
        assertEquals(Long.MAX_VALUE, codec.decodePlan(codec.encode(plan)).sources().get(0).digest().orElseThrow().sizeBytes());
        assertEquals(UNICODE_PATH, codec.decodePlan(codec.encode(plan)).sources().get(0).source().path());
        var written = operation(State.WRITTEN_UNVERIFIED);
        var large = new CheckpointRecord(1, JOB, Long.MAX_VALUE, HASH, TIME, written.kind(), written.operationId(), written.observation(), written.jobState(), written.failures());
        var decoded = codec.decodeCheckpoint(codec.encode(large));
        assertEquals(Long.MAX_VALUE, decoded.sequence());
        var facts = decoded.observation().orElseThrow().processingEvidence().orElseThrow().conversionFacts().orElseThrow();
        assertEquals(9008298766368768L, facts.voxelCount());
        assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(facts.intensityTransform().intercept()));
        assertEquals(Double.MIN_VALUE, facts.rowSpacingMm());
        assertEquals(Double.MAX_VALUE, facts.niftiRasVoxelToWorld().get(0, 3));
        var unicodeOrder = new ProvenanceManifest(1, JOB, TIME, List.of(source("😀", assessment()), source("\ue000", assessment())), List.of());
        String ordered = text(codec.encode(unicodeOrder));
        assertTrue(ordered.indexOf("\"path\":\"\ue000\"") < ordered.indexOf("\"path\":\"😀\""));
        roundTrip(unicodeOrder);
        for (var time : List.of(Instant.MIN, Instant.MAX, TIME.plusNanos(1), TIME.plusNanos(1000), TIME.plusMillis(1))) {
            roundTrip(new ProvenanceManifest(1, JOB, time, List.of(), List.of()));
        }
    }

    @Test
    void malformedUtf8AndControlCharactersReject() {
        for (byte[] bytes : List.of(new byte[] {(byte) 0xc0, (byte) 0xaf}, new byte[] {(byte) 0xed, (byte) 0xa0, (byte) 0x80},
                new byte[] {(byte) 0xf4, (byte) 0x90, (byte) 0x80, (byte) 0x80}, new byte[] {(byte) 0xf0, (byte) 0x9f})) {
            reject(() -> codec.decodePlan(bytes));
            reject(() -> codec.decodeCheckpoint(bytes));
        }
        reject(() -> codec.decodePlan(utf8("\ufeff" + EMPTY_PLAN)));
        String plan = text(codec.encode(unicodePlan()));
        for (var replacement : List.of("\u0000", "\n", "\u0085", "\\ud800", "\\n", "\\u0000")) {
            reject(() -> codec.decodePlan(utf8(plan.replace("😀", replacement))));
        }
    }

    @Test
    void integerOverflowAndNonfiniteNoncanonicalDoublesReject() {
        for (var number : List.of("9223372036854775808", "-9223372036854775809", "1e1", "01", "1.0", "-0")) {
            reject(() -> codec.decodeCheckpoint(utf8(RUNNING.replace("\"sequence\":1", "\"sequence\":" + number))));
        }
        String json = text(codec.encode(operation(State.WRITTEN_UNVERIFIED)));
        for (var number : List.of("NaN", "Infinity", "-Infinity", "1.0E309", "1.0E-999", "+1.0", "1.000", "1.0e0")) {
            reject(() -> codec.decodeCheckpoint(utf8(json.replace("\"columnSpacingMm\":1.0", "\"columnSpacingMm\":" + number))));
        }
        reject(() -> codec.decodeCheckpoint(utf8(json.replace("\"width\":1048576", "\"width\":2147483648"))));
    }

    @Test
    void planAndCheckpointByteCapsRejectBeforeDecoding() {
        reject(() -> codec.decodePlan(new byte[64 * 1024 * 1024 + 1]));
        reject(() -> codec.decodeCheckpoint(new byte[16 * 1024 + 1]));
        reject(() -> codec.decodePlan(new byte[0]));
        reject(() -> codec.decodeCheckpoint(new byte[0]));
    }

    @Test
    void boundedTypedArraysAndStringsReject() {
        String failure = "{\"phase\":\"PERSISTENCE\",\"code\":\"WRITE_FAILED\"}";
        reject(() -> codec.decodeCheckpoint(utf8(RUNNING.replace("\"failures\":[]", "\"failures\":[" + String.join(",", java.util.Collections.nCopies(65, failure)) + "]"))));
        String plan = text(codec.encode(unicodePlan()));
        reject(() -> codec.decodePlan(utf8(plan.replace("study/quoted \\\"MRI\\\" 😀.dcm", "x".repeat(4097)))));
        String checkpoint = text(codec.encode(operation(State.WRITTEN_UNVERIFIED)));
        reject(() -> codec.decodeCheckpoint(utf8(checkpoint.replace("\"softwareVersion\":\"0.1.0\"", "\"softwareVersion\":\"" + "x".repeat(65) + "\""))));
        reject(() -> codec.decodePlan(utf8(EMPTY_PLAN.replace("\"sources\":[]", "\"sources\":[" + "[".repeat(40) + "null" + "]".repeat(40) + "]"))));
    }

    @Test
    void encoderEnforcesPlanBudget() {
        var sources = new ArrayList<SourceFileRecord>();
        var assessment = assessment();
        String suffix = "x".repeat(4070);
        for (int i = 0; i < 17000; i++) sources.add(source("source-" + i + "/" + suffix, assessment));
        var large = new ProvenanceManifest(1, JOB, TIME, sources, List.of());
        reject(() -> codec.encode(large));
    }

    @Test
    void exactSourceAndReferenceLimitsDecodeWithoutTruncation() {
        var sources = new ArrayList<SourceFileRecord>(100000);
        var references = new ArrayList<RelativePath>(100000);
        var assessment = assessment();
        for (int i = 0; i < 100000; i++) {
            var source = source("p" + i, assessment);
            sources.add(source); references.add(source.source());
        }
        var operations = List.of(new ManifestOperation("0".repeat(64), ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI, references,
                        new RelativePath(RelativePath.Root.OUTPUT, "a")),
                new ManifestOperation("1".repeat(64), ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI, references,
                        new RelativePath(RelativePath.Root.OUTPUT, "b")));
        var plan = new ProvenanceManifest(1, JOB, TIME, sources, operations);
        var bytes = codec.encode(plan);
        var decoded = codec.decodePlan(bytes);
        assertEquals(100000, decoded.sources().size());
        assertEquals(200000, decoded.operations().stream().mapToInt(o -> o.sources().size()).sum());
        String json = text(bytes);
        var extra = source("extra", assessment);
        String single = text(codec.encode(new ProvenanceManifest(1, JOB, TIME, List.of(extra), List.of())));
        String extraSource = single.substring(PLAN_PREFIX.length(), single.indexOf("],\"operations\""));
        int boundary = json.indexOf("],\"operations\":[");
        reject(() -> codec.decodePlan(utf8(json.substring(0, boundary) + "," + extraSource + json.substring(boundary))));
        var additional = new ManifestOperation("d".repeat(64), ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI,
                List.of(references.get(0)), new RelativePath(RelativePath.Root.OUTPUT, "third"));
        String one = text(codec.encode(new ProvenanceManifest(1, JOB, TIME, List.of(sources.get(0)), List.of(additional))));
        String extraOperation = one.substring(one.indexOf("],\"operations\":[") + "],\"operations\":[".length(), one.length() - 3);
        reject(() -> codec.decodePlan(utf8(json.substring(0, json.length() - 3) + "," + extraOperation + "]}\n")));
    }

    @Test
    void exactOperationLimitDecodesAndNextEntryRejects() {
        var source = source("source", assessment());
        var operations = new ArrayList<ManifestOperation>(100000);
        for (int i = 0; i < 100000; i++) {
            String hex = Integer.toHexString(i + 1);
            operations.add(new ManifestOperation("0".repeat(64 - hex.length()) + hex, ManifestOperation.Kind.COPY,
                    List.of(source.source()), new RelativePath(RelativePath.Root.OUTPUT, "out" + i)));
        }
        var bytes = codec.encode(new ProvenanceManifest(1, JOB, TIME, List.of(source), operations));
        assertEquals(100000, codec.decodePlan(bytes).operations().size());
        var extra = new ManifestOperation("f".repeat(64), ManifestOperation.Kind.COPY, List.of(source.source()), new RelativePath(RelativePath.Root.OUTPUT, "extra"));
        String one = text(codec.encode(new ProvenanceManifest(1, JOB, TIME, List.of(source), List.of(extra))));
        String extraOperation = one.substring(one.indexOf("],\"operations\":[") + "],\"operations\":[".length(), one.length() - 3);
        String json = text(bytes);
        reject(() -> codec.decodePlan(utf8(json.substring(0, json.length() - 3) + "," + extraOperation + "]}\n")));
    }

    @Test
    void nullInputsAndDecoderFailuresUseFixedNonidentifyingMessages() {
        reject(() -> codec.encode((ProvenanceManifest) null));
        reject(() -> codec.encode((CheckpointRecord) null));
        var failure = assertThrowsExactly(IllegalArgumentException.class, () -> codec.decodePlan(utf8("/private/subject-123")));
        assertEquals("Invalid canonical provenance JSON", failure.getMessage());
        assertNull(failure.getCause());
    }

    // J04
    @Test
    void roundTripsEverySupportedInternalRecord() {
        roundTrip(emptyPlan());
        roundTrip(unicodePlan());
        var noDigest = new SourceFileRecord(new RelativePath(RelativePath.Root.SOURCE, "pending"), Optional.empty(), assessment(), List.of());
        roundTrip(new ProvenanceManifest(1, JOB, TIME, List.of(noDigest), List.of()));
        for (var state : State.values()) if (state != State.NOT_STARTED) roundTrip(operation(state));
        for (var state : JobState.values()) if (state != JobState.PLANNED) roundTrip(job(state));
    }

    @Test
    void processingEvidenceRoundTripsWithoutDuplicateAuthority() {
        for (boolean reconstruction : new boolean[] {true, false}) {
            roundTrip(failedWith(evidence(false, reconstruction)));
        }
        roundTrip(operation(State.WRITTEN_UNVERIFIED));
        var phase = new ProcessingEvidence(ProcessingEvidence.Scope.RECONSTRUCTION, true, Optional.empty(), List.of(), List.of(), Optional.empty());
        var previous = operation(State.FAILED);
        var observed = new Observation(State.FAILED, Optional.of(TIME), Optional.of(TIME), Optional.empty(), 0, Optional.empty(), Optional.of(phase));
        roundTrip(new CheckpointRecord(1, JOB, 1, HASH, TIME, Kind.OPERATION_OBSERVED, Optional.of(ID), Optional.of(observed), Optional.empty(), previous.failures()));
        var noFingerprint = new ProcessingEvidence.SourceSummary(Optional.empty(), "v".repeat(64), "v".repeat(64), TIME, true, Integer.MAX_VALUE, Integer.MAX_VALUE);
        var supplied = new ProcessingEvidence(ProcessingEvidence.Scope.RECONSTRUCTION, true, Optional.of(noFingerprint), List.of(), List.of(), Optional.empty());
        var failedLater = new Observation(State.FAILED, Optional.of(TIME), Optional.of(TIME), Optional.empty(), 0, Optional.empty(), Optional.of(supplied));
        roundTrip(new CheckpointRecord(1, JOB, 1, HASH, TIME, Kind.OPERATION_OBSERVED, Optional.of(ID), Optional.of(failedLater), Optional.empty(), previous.failures()));
        var allowed = new ArrayList<ManifestFailure>();
        for (var phaseValue : ManifestFailure.Phase.values()) {
            for (var code : ManifestFailure.Code.values()) {
                try { allowed.add(new ManifestFailure(phaseValue, code)); }
                catch (IllegalArgumentException ignored) { /* Owning model rejects inapplicable pairs. */ }
            }
        }
        assertEquals(38, allowed.size());
        var recoveryRecord = operation(State.RECOVERY_REQUIRED);
        roundTrip(new CheckpointRecord(1, JOB, 1, HASH, TIME, recoveryRecord.kind(), recoveryRecord.operationId(), recoveryRecord.observation(), Optional.empty(), allowed));
    }

    @Test
    void assessmentVariantsAndOrderedReasonsRoundTrip() {
        var assessments = new ArrayList<FormatAssessment>();
        for (var outcome : List.of(DetectionOutcome.DICOM, DetectionOutcome.NIFTI, DetectionOutcome.NIFTI_GZ)) {
            for (boolean mismatch : new boolean[] {false, true}) assessments.add(FormatAssessment.fromDetection(DetectionResult.identified(outcome).withExtensionMismatch(mismatch)));
        }
        for (var diagnostic : DetectionDiagnostic.values()) {
            if (diagnostic == DetectionDiagnostic.INVALID_DICOM || diagnostic == DetectionDiagnostic.INVALID_NIFTI || diagnostic == DetectionDiagnostic.INVALID_GZIP) assessments.add(FormatAssessment.fromDetection(DetectionResult.corrupt(diagnostic)));
            else if (diagnostic != DetectionDiagnostic.NONE) assessments.add(FormatAssessment.fromDetection(DetectionResult.unknown(diagnostic)));
        }
        var raw = DetectionResult.identified(DetectionOutcome.NIFTI);
        assessments.add(new FormatAssessment(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE, ValidityStatus.VALID, SupportStatus.SUPPORTED, ConversionReadiness.READY, List.of()));
        for (var variant : List.of(FormatVariant.NIFTI_2_SINGLE_FILE, FormatVariant.NIFTI_PAIR)) assessments.add(new FormatAssessment(raw, ImagingFormat.NIFTI, variant, ValidityStatus.VALID, SupportStatus.UNSUPPORTED, ConversionReadiness.BLOCKED, List.of(AssessmentReason.UNSUPPORTED_FORMAT_VARIANT)));
        assessments.add(new FormatAssessment(raw, ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE, ValidityStatus.INVALID, SupportStatus.UNSUPPORTED, ConversionReadiness.BLOCKED, List.of(AssessmentReason.VALIDATION_FAILED, AssessmentReason.UNSUPPORTED_PROFILE)));
        for (var assessment : assessments) roundTrip(new ProvenanceManifest(1, JOB, TIME, List.of(source("evidence", assessment)), List.of()));
    }

    @Test
    void codecApiAndDependencyBoundaryRemainPackagePrivate() throws Exception {
        assertTrue(Modifier.isFinal(JsonManifestCodec.class.getModifiers()));
        assertFalse(Modifier.isPublic(JsonManifestCodec.class.getModifiers()));
        assertEquals(1, JsonManifestCodec.class.getDeclaredConstructors().length);
        assertEquals(0, JsonManifestCodec.class.getDeclaredConstructors()[0].getParameterCount());
        assertEquals(5, Arrays.stream(JsonManifestCodec.class.getDeclaredMethods()).filter(m -> !Modifier.isPrivate(m.getModifiers())).count());
        assertEquals(byte[].class, JsonManifestCodec.class.getDeclaredMethod("encode", ProvenanceManifest.class).getReturnType());
        assertEquals(byte[].class, JsonManifestCodec.class.getDeclaredMethod("encode", CheckpointRecord.class).getReturnType());
        assertEquals(ProvenanceManifest.class, JsonManifestCodec.class.getDeclaredMethod("decodePlan", byte[].class).getReturnType());
        assertEquals(CheckpointRecord.class, JsonManifestCodec.class.getDeclaredMethod("decodeCheckpoint", byte[].class).getReturnType());
        for (var method : JsonManifestCodec.class.getDeclaredMethods()) assertFalse(Modifier.isPublic(method.getModifiers()));
        String source = Files.readString(Path.of("src/main/java/org/cbihi/mrinormalizer/infrastructure/filesystem/JsonManifestCodec.java"));
        for (var forbidden : List.of("java.lang.reflect", "com.fasterxml", "com.google.gson", "java.nio.file", "parseValue(", "Map<String", "ManifestStore")) assertFalse(source.contains(forbidden));
    }

    @Test
    void schemaDocumentsDescribeExactlyTheRestrictedShapes() throws Exception {
        for (var name : List.of("plan", "checkpoint")) {
            String schema = Files.readString(Path.of("docs/schemas/provenance-" + name + "-v1.schema.json"));
            assertTrue(schema.contains("https://json-schema.org/draft/2020-12/schema"));
            assertTrue(schema.contains("org.cbihi.mrinormalizer.provenance-" + name));
            assertTrue(schema.contains("\"additionalProperties\": false"));
            assertFalse(schema.contains("detectionDiagnostic"));
            assertFalse(schema.contains("EXISTING_DIAGNOSTIC"));
            assertFalse(schema.contains("sha 256"));
        }
    }

    @Test
    void amendedFailurePairsHaveExactCanonicalTokensAndRoundTrip() throws Exception {
        String directory = System.getProperty("provenance.fixture.directory");
        for (var pair : List.of("HASHING/READ_FAILED", "HASHING/ACCESS_CONTROL_UNAVAILABLE", "HASHING/INTERRUPTED",
                "EXECUTION/WRITE_FAILED", "EXECUTION/PUBLICATION_UNAVAILABLE", "EXECUTION/READ_FAILED",
                "EXECUTION/ACCESS_CONTROL_UNAVAILABLE", "EXECUTION/RESOURCE_LIMIT", "EXECUTION/INTERRUPTED")) {
            var tokens = pair.split("/");
            var failure = new ManifestFailure(ManifestFailure.Phase.valueOf(tokens[0]), ManifestFailure.Code.valueOf(tokens[1]));
            var original = operation(State.FAILED);
            var record = new CheckpointRecord(1, JOB, 1, HASH, TIME, original.kind(), original.operationId(),
                    original.observation(), original.jobState(), List.of(failure));
            var expected = text(codec.encode(original)).replace("\"phase\":\"PERSISTENCE\",\"code\":\"WRITE_FAILED\"",
                    "\"phase\":\"" + tokens[0] + "\",\"code\":\"" + tokens[1] + "\"");
            assertArrayEquals(utf8(expected), codec.encode(record));
            roundTrip(record);
            var originalSource = unicodePlan().sources().get(0);
            var plan = new ProvenanceManifest(1, JOB, TIME, List.of(new SourceFileRecord(originalSource.source(),
                    originalSource.digest(), originalSource.assessment(), List.of(failure))), unicodePlan().operations());
            roundTrip(plan);
            var report = publicReport(JobState.FAILED, List.of(new PublicJobReport.FailureCount(
                    PublicJobReport.FailureNamespace.valueOf(tokens[0]), tokens[1], 1)));
            assertArrayEquals(utf8(publicGolden("FAILED", "[{\"namespace\":\"" + tokens[0]
                    + "\",\"code\":\"" + tokens[1] + "\",\"count\":1}]")), codec.encode(report));
            reject(() -> codec.decodeCheckpoint(utf8(expected.replace("\"phase\":\"" + tokens[0] + "\"",
                    "\"phase\":\"POST_WRITE_VALIDATION\""))));
            if (directory != null) {
                var folder = Path.of(directory); Files.createDirectories(folder);
                String suffix = tokens[0].toLowerCase(java.util.Locale.ROOT) + "-" + tokens[1].toLowerCase(java.util.Locale.ROOT);
                Files.write(folder.resolve("amended-checkpoint-" + suffix + ".json"), codec.encode(record));
                Files.write(folder.resolve("amended-plan-" + suffix + ".json"), codec.encode(plan));
                Files.write(folder.resolve("amended-public-" + suffix + ".json"), codec.encode(report));
            }
        }
        String interrupted = text(codec.encode(job(JobState.FAILED))).replace("WRITE_FAILED", "INTERRUPTED");
        reject(() -> codec.decodeCheckpoint(utf8(interrupted))); // PERSISTENCE/INTERRUPTED remains forbidden.
    }

    @Test
    void restrictedSchemasFreezeEveryFailureConditional() throws Exception {
        var pattern = java.util.regex.Pattern.compile("\"code\"\\s*:\\s*\\{\\s*\"const\"\\s*:\\s*\"([A-Z_]+)\"\\s*}\\s*}\\s*},\\s*\"then\"\\s*:\\s*\\{\\s*\"properties\"\\s*:\\s*\\{\\s*\"phase\"\\s*:\\s*\\{\\s*\"type\"\\s*:\\s*\"string\",\\s*\"enum\"\\s*:\\s*\\[([^]]+)]");
        for (var name : List.of("plan", "checkpoint")) {
            var matcher = pattern.matcher(Files.readString(Path.of("docs/schemas/provenance-" + name + "-v1.schema.json")));
            int index = 0;
            while (matcher.find()) {
                assertEquals(ManifestFailure.Code.values()[index++].name(), matcher.group(1));
                var allowed = switch (matcher.group(1)) {
                    case "INVALID_MANIFEST", "CHECKPOINT_CONFLICT", "UNSUPPORTED_SCHEMA", "CORRUPT_CHECKPOINT" -> List.of("PERSISTENCE");
                    case "INVALID_REFERENCE", "CONTAINMENT_UNPROVEN", "INPUT_UNAVAILABLE", "READ_FAILED", "ACCESS_CONTROL_UNAVAILABLE", "RESOURCE_LIMIT" -> List.of("HASHING", "EXECUTION", "PERSISTENCE");
                    case "SOURCE_CHANGED", "HASH_FAILED" -> List.of("HASHING");
                    case "WRITE_FAILED", "PUBLICATION_UNAVAILABLE", "CLEANUP_FAILED", "RECOVERY_REQUIRED", "OUTPUT_CONFLICT" -> List.of("EXECUTION", "PERSISTENCE");
                    case "VERIFICATION_FAILED" -> List.of("EXECUTION", "POST_WRITE_VALIDATION");
                    case "INTERRUPTED" -> List.of("HASHING", "EXECUTION");
                    default -> throw new AssertionError("Unexpected schema failure code");
                };
                var phases = new ArrayList<String>();
                var token = java.util.regex.Pattern.compile("\"([A-Z_]+)\"").matcher(matcher.group(2));
                while (token.find()) phases.add(token.group(1));
                assertEquals(allowed, phases, matcher.group(1));
            }
            assertEquals(19, index);
        }
    }

    private void roundTrip(ProvenanceManifest plan) {
        var bytes = codec.encode(plan);
        assertEquals(plan, codec.decodePlan(bytes));
        assertArrayEquals(bytes, codec.encode(codec.decodePlan(bytes)));
    }

    private void roundTrip(CheckpointRecord record) {
        var bytes = codec.encode(record);
        assertEquals(record, codec.decodeCheckpoint(bytes));
        assertArrayEquals(bytes, codec.encode(codec.decodeCheckpoint(bytes)));
    }

    private static FormatAssessment assessment() { return FormatAssessment.fromDetection(DetectionResult.identified(DetectionOutcome.DICOM).withExtensionMismatch(true)); }
    private static SourceFileRecord source(String path, FormatAssessment assessment) {
        return new SourceFileRecord(new RelativePath(RelativePath.Root.SOURCE, path), Optional.of(new ContentDigest(Long.MAX_VALUE, HASH)), assessment, List.of());
    }
    private static ProvenanceManifest emptyPlan() { return new ProvenanceManifest(1, JOB, TIME, List.of(), List.of()); }
    private static ProvenanceManifest unicodePlan() {
        var source = source(UNICODE_PATH, assessment());
        return new ProvenanceManifest(1, JOB, TIME, List.of(source), List.of(new ManifestOperation(ID, ManifestOperation.Kind.COPY, List.of(source.source()), new RelativePath(RelativePath.Root.OUTPUT, "organized/result.dcm"))));
    }
    private static ManifestFailure failure() { return new ManifestFailure(ManifestFailure.Phase.PERSISTENCE, ManifestFailure.Code.WRITE_FAILED); }
    private static ManifestFailure recovery() { return new ManifestFailure(ManifestFailure.Phase.EXECUTION, ManifestFailure.Code.RECOVERY_REQUIRED); }
    private static CheckpointRecord job(JobState state) {
        return new CheckpointRecord(1, JOB, 1, HASH, TIME, Kind.JOB_OBSERVED, Optional.empty(), Optional.empty(), Optional.of(state),
                state == JobState.FAILED ? List.of(failure()) : state == JobState.RECOVERY_REQUIRED ? List.of(recovery()) : List.of());
    }
    private static CheckpointRecord operation(State state) {
        var digest = Optional.of(new ContentDigest(Long.MAX_VALUE, HASH));
        var observed = switch (state) {
            case NOT_STARTED -> throw new IllegalArgumentException("No initial checkpoint");
            case IN_PROGRESS -> new Observation(state, Optional.of(TIME), Optional.empty(), Optional.empty(), 1, Optional.empty(), Optional.empty());
            case COMPLETED, IDENTICAL_EXISTING -> new Observation(state, Optional.of(TIME), Optional.of(TIME), digest, 1, Optional.empty(), Optional.empty());
            case WRITTEN_UNVERIFIED -> new Observation(state, Optional.of(TIME), Optional.of(TIME), digest, 1, Optional.empty(), Optional.of(evidence(true, false)));
            case SKIPPED_POLICY -> new Observation(state, Optional.empty(), Optional.of(TIME), Optional.empty(), 0, Optional.of(Disposition.NOT_REQUESTED_BY_POLICY), Optional.empty());
            case BLOCKED -> new Observation(state, Optional.empty(), Optional.of(TIME), Optional.empty(), 0, Optional.of(Disposition.INPUT_UNAVAILABLE), Optional.empty());
            case FAILED -> new Observation(state, Optional.of(TIME), Optional.of(TIME), Optional.empty(), 0, Optional.empty(), Optional.empty());
            case RECOVERY_REQUIRED -> new Observation(state, Optional.of(TIME), Optional.empty(), digest, 1, Optional.of(Disposition.RECOVERY_RECONCILIATION_REQUIRED), Optional.empty());
        };
        return new CheckpointRecord(1, JOB, 1, HASH, TIME, Kind.OPERATION_OBSERVED, Optional.of(ID), Optional.of(observed), Optional.empty(),
                state == State.FAILED ? List.of(failure()) : state == State.RECOVERY_REQUIRED ? List.of(recovery()) : List.of());
    }
    private static ProcessingEvidence evidence(boolean successful, boolean reconstruction) {
        var summary = new ProcessingEvidence.SourceSummary(Optional.of(HASH), "0.1.0", "5.33.0", TIME, successful, 2, 1);
        var facts = new ProcessingEvidence.ConversionFacts(1048576, 1048576, 8193, ScalarType.INT16, 9008298766368768L,
                Double.MIN_VALUE, 1.0, -0.0, new AffineMatrix4(new double[][] {{1, 0, 0, Double.MAX_VALUE}, {0, 1, 0, 0}, {0, 0, 1, 0}, {-0.0, 0, 0, 1}}),
                new IntensityTransform(true, -2.0, -0.0), true, false, false, false);
        return new ProcessingEvidence(reconstruction ? ProcessingEvidence.Scope.RECONSTRUCTION : ProcessingEvidence.Scope.CONVERSION, successful, Optional.of(summary),
                successful ? List.of() : List.of(DicomProcessingError.values()), successful || reconstruction ? List.of() : List.of(DicomToNiftiError.values()),
                successful && !reconstruction ? Optional.of(facts) : Optional.empty());
    }
    private static CheckpointRecord failedWith(ProcessingEvidence evidence) {
        var observed = new Observation(State.FAILED, Optional.of(TIME), Optional.of(TIME), Optional.empty(), 0, Optional.empty(), Optional.of(evidence));
        return new CheckpointRecord(1, JOB, 1, HASH, TIME, Kind.OPERATION_OBSERVED, Optional.of(ID), Optional.of(observed), Optional.empty(), List.of());
    }
    private static byte[] utf8(String json) { return json.getBytes(StandardCharsets.UTF_8); }
    private static String text(byte[] json) { return new String(json, StandardCharsets.UTF_8); }
    private static void reject(Executable action) {
        var failure = assertThrowsExactly(IllegalArgumentException.class, action);
        assertNotNull(failure.getMessage());
        assertFalse(failure.getMessage().contains("subject-123"));
        assertNull(failure.getCause());
    }

    // S5B encoder-only additions; existing plan/checkpoint fixtures stay unchanged.
    @Test void publicGoldenBytesHaveExactClosedOrderAndAllEighteenBins() {
        var report = publicReport(JobState.RUNNING, List.of(
                new PublicJobReport.FailureCount(PublicJobReport.FailureNamespace.DETECTION, "INPUT_NOT_FOUND", 2),
                new PublicJobReport.FailureCount(PublicJobReport.FailureNamespace.PERSISTENCE, "WRITE_FAILED", 3)));
        String expected = publicGolden("RUNNING", "[{\"namespace\":\"DETECTION\",\"code\":\"INPUT_NOT_FOUND\",\"count\":2},"
                + "{\"namespace\":\"PERSISTENCE\",\"code\":\"WRITE_FAILED\",\"count\":3}]");
        assertArrayEquals(utf8(expected), codec.encode(report));
        assertEquals(18, report.operationCounts().size());
    }

    @Test void publicEnumsAndIntegerTokensNeverDependOnLocale() {
        var previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"));
            for (var state : JobState.values()) assertEquals(publicGolden(state.name(), "[]"), text(codec.encode(publicReport(state, List.of()))));
            var report = publicReport(JobState.FAILED, List.of(new PublicJobReport.FailureCount(
                    PublicJobReport.FailureNamespace.PERSISTENCE, "WRITE_FAILED", Long.MAX_VALUE)));
            assertEquals(publicGolden("FAILED", "[{\"namespace\":\"PERSISTENCE\",\"code\":\"WRITE_FAILED\",\"count\":9223372036854775807}]"), text(codec.encode(report)));
        } finally { java.util.Locale.setDefault(previous); }
    }

    @Test void publicUtf8HasNoBomExactlyOneLfAndDeterministicCompleteBytes() {
        var report = publicReport(JobState.PLANNED, List.of()); var bytes = codec.encode(report);
        assertEquals('{', bytes[0]); assertEquals('\n', bytes[bytes.length - 1]);
        assertEquals(1, text(bytes).chars().filter(c -> c == '\n').count());
        assertFalse(text(bytes).contains("\uFEFF")); assertFalse(text(bytes).contains("\r"));
        for (int i = 0; i < 10; i++) assertArrayEquals(bytes, codec.encode(report));
        assertTrue(bytes.length < 16 * 1024 * 1024);
    }

    @Test void publicNullUsesEstablishedFixedCodecFailure() {
        var failure = assertThrowsExactly(IllegalArgumentException.class, () -> codec.encode((PublicJobReport) null));
        assertEquals("Invalid canonical provenance JSON", failure.getMessage()); assertNull(failure.getCause());
    }

    @Test void publicWireHasNoRestrictedChannelsAndNoDecoder() throws Exception {
        String json = text(codec.encode(publicReport(JobState.COMPLETED, List.of())));
        for (var field : List.of("reference", "path", "jobId", "operationId", "sha256", "digest", "sizeBytes", "createdAt",
                "recordedAt", "sourceSummary", "conversionFacts", "geometry", "metadata", "softwareVersion", "exception"))
            assertFalse(json.contains("\"" + field + "\""), field);
        assertEquals(byte[].class, JsonManifestCodec.class.getDeclaredMethod("encode", PublicJobReport.class).getReturnType());
        assertFalse(Arrays.stream(JsonManifestCodec.class.getDeclaredMethods()).anyMatch(m -> m.getReturnType() == PublicJobReport.class
                || m.getName().equals("decodePublicReport")));
        assertFalse(Modifier.isPublic(JsonManifestCodec.class.getModifiers()));
    }

    @Test void maximalClosedFailureCorpusRemainsCompleteWithinExplicitBudget() throws Exception {
        var failures = new ArrayList<PublicJobReport.FailureCount>();
        for (var ns : PublicJobReport.FailureNamespace.values()) {
            switch (ns) {
                case DETECTION -> { for (var code : DetectionDiagnostic.values()) if (code != DetectionDiagnostic.NONE)
                    failures.add(new PublicJobReport.FailureCount(ns, code.name(), 1)); }
                case RECONSTRUCTION -> { for (var code : DicomProcessingError.values()) failures.add(new PublicJobReport.FailureCount(ns, code.name(), 1)); }
                case CONVERSION -> { for (var code : DicomToNiftiError.values()) failures.add(new PublicJobReport.FailureCount(ns, code.name(), 1)); }
                default -> { for (var code : ManifestFailure.Code.values()) {
                    try { new ManifestFailure(ManifestFailure.Phase.valueOf(ns.name()), code); }
                    catch (IllegalArgumentException inapplicable) { continue; }
                    failures.add(new PublicJobReport.FailureCount(ns, code.name(), 1));
                } }
            }
        }
        assertEquals(72, failures.size());
        var bins = new ArrayList<>(publicReport(JobState.FAILED, List.of()).operationCounts());
        for (int i = 0; i < bins.size(); i++) bins.set(i, new PublicJobReport.OperationCount(bins.get(i).kind(), bins.get(i).state(), i == 0 ? 100000 : 0));
        var bytes = codec.encode(new PublicJobReport(1, JobState.FAILED, 100000, bins, failures));
        assertTrue(bytes.length <= 16 * 1024 * 1024);
        for (var f : failures) assertTrue(text(bytes).contains("{\"namespace\":\"" + f.namespace().name() + "\",\"code\":\"" + f.code() + "\",\"count\":1}"));
        var field = JsonManifestCodec.class.getDeclaredField("PUBLIC_REPORT_BYTES"); field.setAccessible(true);
        assertEquals(16 * 1024 * 1024, field.getInt(null));
        // A valid schema-1 report has only 18 bins and 72 closed namespace/code pairs;
        // an over-budget valid model cannot be constructed without weakening S5A.
    }

    private static PublicJobReport publicReport(JobState state, List<PublicJobReport.FailureCount> failures) {
        var bins = new ArrayList<PublicJobReport.OperationCount>(); int count = 0;
        for (var kind : ManifestOperation.Kind.values()) for (var observation : State.values())
            bins.add(new PublicJobReport.OperationCount(kind, observation, count++));
        return new PublicJobReport(1, state, 100000, bins, failures);
    }
    private static String publicGolden(String state, String failures) {
        String[] states = {"NOT_STARTED", "IN_PROGRESS", "COMPLETED", "IDENTICAL_EXISTING", "WRITTEN_UNVERIFIED", "SKIPPED_POLICY", "BLOCKED", "FAILED", "RECOVERY_REQUIRED"};
        var bins = new ArrayList<String>(); int count = 0;
        for (var kind : List.of("COPY", "CONVERT_DICOM_TO_NIFTI")) for (var observation : states)
            bins.add("{\"kind\":\"" + kind + "\",\"state\":\"" + observation + "\",\"count\":" + count++ + "}");
        return "{\"schema\":\"org.cbihi.mrinormalizer.public-job-report\",\"schemaVersion\":1,\"state\":\"" + state
                + "\",\"sourceCount\":100000,\"operationCounts\":[" + String.join(",", bins) + "],\"failureCounts\":" + failures + "}\n";
    }
}
