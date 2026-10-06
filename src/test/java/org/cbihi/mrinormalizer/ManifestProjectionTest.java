package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.cbihi.mrinormalizer.application.dataset.model.FormatAssessment;
import org.cbihi.mrinormalizer.application.provenance.ProvenanceRecord;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.Disposition;
import org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.Observation;
import org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.State;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Code;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Phase;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestState.JobState;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestState.OperationState;
import org.cbihi.mrinormalizer.application.provenance.manifest.PublicJobReport.FailureCount;
import org.cbihi.mrinormalizer.application.provenance.manifest.PublicJobReport.FailureNamespace;
import org.cbihi.mrinormalizer.application.provenance.manifest.PublicJobReport.OperationCount;
import org.cbihi.mrinormalizer.application.result.DicomProcessingResult;
import org.cbihi.mrinormalizer.application.result.DicomToNiftiResult;
import org.cbihi.mrinormalizer.application.validation.ConversionValidationReport;
import org.cbihi.mrinormalizer.domain.error.DicomProcessingError;
import org.cbihi.mrinormalizer.domain.error.DicomToNiftiError;
import org.cbihi.mrinormalizer.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** P01-P03; authored before the S5A production values. No encoder or platform qualification. */
class ManifestProjectionTest {
    private static final Instant CREATED = Instant.parse("2026-10-06T00:00:00Z");
    private static final Instant START = CREATED.plusSeconds(1);
    private static final Instant FINISH = CREATED.plusSeconds(2);
    private static final Instant NOW = CREATED.plusSeconds(3);
    private static final UUID JOB = new UUID(0, 7);
    private static final String HASH = "a".repeat(64);
    private static final ContentDigest DIGEST = new ContentDigest(12, "b".repeat(64));
    private static final AffineMatrix4 AFFINE = new AffineMatrix4(new double[][] {
            {0, -2, 0, 11}, {3, 0, 0, -17}, {0, 0, -4, 23}, {0, 0, 0, 1}});
    private static final IntensityTransform INTENSITY = new IntensityTransform(true, -2, 7);
    private static final OutputTarget TARGET = new OutputTarget("C:/Patient Alice/PID-123/1.2.840.77/out.nii");
    private static final ManifestFailure RECOVERY = new ManifestFailure(Phase.EXECUTION, Code.RECOVERY_REQUIRED);
    private static final ManifestFailure CLEANUP = new ManifestFailure(Phase.EXECUTION, Code.CLEANUP_FAILED);
    private static final ManifestFailure VERIFICATION = new ManifestFailure(Phase.EXECUTION, Code.VERIFICATION_FAILED);

    // P01: actual result, provenance and typed validation-report values.
    @Test void projectsRealM6AndM7WithoutDuplicatedErrors() {
        var provenance = provenance(false, List.of());
        var reconstruction = ManifestProjection.reconstruction(DicomProcessingResult.success(volume(), provenance));
        assertEquals(ProcessingEvidence.Scope.RECONSTRUCTION, reconstruction.scope());
        assertTrue(reconstruction.phaseSuccessful()); assertFalse(reconstruction.sourceSummary().orElseThrow().provenanceSuccessful());
        assertTrue(reconstruction.reconstructionErrors().isEmpty()); assertTrue(reconstruction.conversionErrors().isEmpty());
        assertTrue(reconstruction.conversionFacts().isEmpty());
        var conversion = ManifestProjection.conversion(DicomToNiftiResult.success(TARGET, provenance, report(ScalarType.INT16, true, false, false, false)));
        assertEquals(ProcessingEvidence.Scope.CONVERSION, conversion.scope()); assertTrue(conversion.phaseSuccessful());
        assertTrue(conversion.reconstructionErrors().isEmpty()); assertTrue(conversion.conversionErrors().isEmpty());
        assertTrue(conversion.conversionFacts().isPresent());
        var summary = conversion.sourceSummary().orElseThrow();
        assertEquals(Optional.of(HASH), summary.selectedSourceFingerprint());
        assertEquals("mrinormalizer-1.0", summary.softwareVersion()); assertEquals("5.33.0", summary.dcm4cheVersion());
        assertEquals(FINISH, summary.completedAt()); assertFalse(summary.provenanceSuccessful());
        assertEquals(5, summary.inputCount()); assertEquals(3, summary.acceptedSlices());
    }

    @Test void reconstructionPreservesEveryOriginalErrorAndIndependentProvenanceFlag() {
        for (var error : DicomProcessingError.values()) {
            var result = DicomProcessingResult.failure(error, provenance(true, List.of(error)));
            var projected = ManifestProjection.reconstruction(result);
            assertFalse(projected.phaseSuccessful()); assertTrue(projected.sourceSummary().orElseThrow().provenanceSuccessful());
            assertEquals(List.of(error), projected.reconstructionErrors());
            assertTrue(projected.conversionErrors().isEmpty()); assertTrue(projected.conversionFacts().isEmpty());
        }
        var errors = new ArrayList<>(List.of(DicomProcessingError.values())); Collections.reverse(errors);
        var projected = ManifestProjection.reconstruction(new DicomProcessingResult(null, errors, provenance(false, errors)));
        assertEquals(List.of(DicomProcessingError.values()), projected.reconstructionErrors());
        assertEquals(20, projected.reconstructionErrors().size());
    }

    @Test void conversionPreservesEveryOriginalErrorInItsOwnNamespace() {
        for (var error : DicomToNiftiError.values()) {
            var evidence = ManifestProjection.conversion(DicomToNiftiResult.outputFailure(error, provenance(true, List.of())));
            assertFalse(evidence.phaseSuccessful()); assertTrue(evidence.conversionFacts().isEmpty());
            assertEquals(List.of(error), evidence.conversionErrors()); assertTrue(evidence.reconstructionErrors().isEmpty());
        }
        for (var error : DicomProcessingError.values()) {
            var evidence = ManifestProjection.conversion(DicomToNiftiResult.failure(List.of(error), provenance(false, List.of(error))));
            assertEquals(List.of(error), evidence.reconstructionErrors()); assertTrue(evidence.conversionErrors().isEmpty());
            assertFalse(evidence.phaseSuccessful()); assertTrue(evidence.conversionFacts().isEmpty());
        }
        var evidence = ManifestProjection.conversion(new DicomToNiftiResult(null, List.of(DicomProcessingError.values()),
                List.of(DicomToNiftiError.values()), provenance(false, List.of(DicomProcessingError.values())), null));
        assertEquals(List.of(DicomProcessingError.values()), evidence.reconstructionErrors());
        assertEquals(List.of(DicomToNiftiError.values()), evidence.conversionErrors());
        assertFalse(Arrays.stream(ProcessingEvidence.class.getRecordComponents()).anyMatch(c -> c.getType() == ManifestFailure.class));
        assertFalse(Arrays.stream(ProcessingEvidence.SourceSummary.class.getRecordComponents()).anyMatch(c -> List.class.isAssignableFrom(c.getType())));
    }

    @Test void provenanceErrorAgreementIsCheckedWithoutDuplicatingStorage() {
        var errors = List.of(DicomProcessingError.INPUT_NOT_FOUND, DicomProcessingError.EMPTY_INPUT);
        var reversed = List.of(DicomProcessingError.EMPTY_INPUT, DicomProcessingError.INPUT_NOT_FOUND);
        var accepted = ManifestProjection.reconstruction(new DicomProcessingResult(null, errors, provenance(false, reversed)));
        assertEquals(reversed, accepted.reconstructionErrors());
        for (var contradictory : List.of(List.<DicomProcessingError>of(), List.of(DicomProcessingError.SERIES_NOT_SELECTED))) {
            rejectProjection(() -> ManifestProjection.reconstruction(new DicomProcessingResult(null, errors, provenance(false, contradictory))));
            rejectProjection(() -> ManifestProjection.conversion(new DicomToNiftiResult(null, errors, List.of(), provenance(false, contradictory), null)));
        }
        rejectProjection(() -> ManifestProjection.conversion(DicomToNiftiResult.success(TARGET,
                provenance(true, List.of(DicomProcessingError.EMPTY_INPUT)), report(ScalarType.INT16, true, false, false, false))));
        rejectProjection(() -> ManifestProjection.reconstruction(new DicomProcessingResult(null,
                List.of(DicomProcessingError.EMPTY_INPUT, DicomProcessingError.EMPTY_INPUT), null)));
    }

    @Test void successfulConversionPreservesEveryTypedReportFieldWithoutTarget() {
        for (var type : ScalarType.values()) for (int flags = 0; flags < 16; flags++) {
            var original = report(type, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, (flags & 8) != 0);
            var facts = ManifestProjection.conversion(DicomToNiftiResult.success(TARGET, null, original)).conversionFacts().orElseThrow();
            assertEquals(original.width(), facts.width()); assertEquals(original.height(), facts.height());
            assertEquals(original.depth(), facts.depth()); assertEquals(type, facts.scalarType()); assertEquals(24, facts.voxelCount());
            assertEquals(original.rowSpacingMm(), facts.rowSpacingMm()); assertEquals(original.columnSpacingMm(), facts.columnSpacingMm());
            assertEquals(original.sliceSpacingMm(), facts.sliceSpacingMm());
            for (int row = 0; row < 4; row++) for (int column = 0; column < 4; column++)
                assertEquals(AFFINE.get(row, column), facts.niftiRasVoxelToWorld().get(row, column));
            assertEquals(INTENSITY, facts.intensityTransform());
            assertEquals(original.storedVoxelValuesPreserved(), facts.storedVoxelValuesPreserved());
            assertEquals(original.resampled(), facts.resampled()); assertEquals(original.interpolated(), facts.interpolated());
            assertEquals(original.voxelOrderChanged(), facts.voxelOrderChanged());
            assertFalse(facts.toString().contains(TARGET.reference()));
        }
        assertArrayEquals(new String[] {"width", "height", "depth", "scalarType", "voxelCount", "rowSpacingMm", "columnSpacingMm",
                "sliceSpacingMm", "niftiRasVoxelToWorld", "intensityTransform", "storedVoxelValuesPreserved", "resampled", "interpolated", "voxelOrderChanged"},
                componentNames(ProcessingEvidence.ConversionFacts.class));
        assertThrows(IllegalArgumentException.class, () -> new DicomToNiftiResult(new OutputTarget("other"), List.of(), List.of(), null,
                report(ScalarType.INT16, true, false, false, false)));
        assertThrows(IllegalArgumentException.class, () -> new DicomToNiftiResult(null, List.of(), List.of(), null,
                report(ScalarType.INT16, true, false, false, false)));
    }

    @Test void absentProvenanceAndFingerprintRemainAbsent() {
        assertTrue(ManifestProjection.reconstruction(DicomProcessingResult.success(volume(), null)).sourceSummary().isEmpty());
        assertTrue(ManifestProjection.conversion(DicomToNiftiResult.outputFailure(DicomToNiftiError.INVALID_OUTPUT, null)).sourceSummary().isEmpty());
        var provenance = new ProvenanceRecord(null, "v1", "5.33.0", FINISH, true, 5, 3, "Patient Alice geometry", "PID-123 pixels", List.of());
        var evidence = ManifestProjection.reconstruction(DicomProcessingResult.success(volume(), provenance));
        assertTrue(evidence.sourceSummary().orElseThrow().selectedSourceFingerprint().isEmpty());
        assertFalse(evidence.toString().contains("Patient Alice")); assertFalse(evidence.toString().contains("PID-123"));
    }

    @Test void nullIncoherentAndSensitiveInvalidResultsRejectWithFixedMessages() {
        rejectProjection(() -> ManifestProjection.reconstruction(null)); rejectProjection(() -> ManifestProjection.conversion(null));
        rejectProjection(() -> ManifestProjection.publicReport(null));
        rejectProjection(() -> ManifestProjection.reconstruction(new DicomProcessingResult(null, List.of(), null)));
        rejectProjection(() -> ManifestProjection.conversion(new DicomToNiftiResult(null, List.of(), List.of(), null, null)));
        var bad = new ProvenanceRecord("Patient Alice/PID-123", "v1", "5.33.0", FINISH, true, 1, 1,
                "sensitive geometry", "sensitive pixels", List.of());
        var error = assertThrowsExactly(IllegalArgumentException.class,
                () -> ManifestProjection.reconstruction(DicomProcessingResult.success(volume(), bad)));
        assertFalse(error.getMessage().contains("Patient Alice")); assertNull(error.getCause());
    }

    // P02: restricted evidence stays exact; aggregate public values omit it.
    @Test void restrictedPathsRemainSensitivePublicFieldsDoNot() {
        String sourceText = "Patient Alice/PID-123/1.2.840.77/Écho MiXeD 𐀀.dcm";
        String destinationText = "Patient Alice/PID-123/1.2.840.77/Écho MiXeD 𐀀.nii";
        var source = source(sourceText, DetectionDiagnostic.NONE, List.of());
        var operation = operation(1, ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI, source.source(), destinationText);
        var plan = new ProvenanceManifest(1, JOB, CREATED, List.of(source), List.of(operation));
        var evidence = ManifestProjection.conversion(DicomToNiftiResult.success(TARGET, provenance(true, List.of()),
                report(ScalarType.INT16, true, false, false, false)));
        var state = current(plan, List.of(new OperationState(id(1), observed(State.WRITTEN_UNVERIFIED, Optional.of(evidence)), List.of())), List.of());
        var report = ManifestProjection.publicReport(state);
        assertEquals(sourceText, state.plan().sources().get(0).source().path());
        assertEquals(destinationText, state.plan().operations().get(0).destination().path());
        assertEquals(Optional.of(DIGEST), state.plan().sources().get(0).digest());
        for (var sensitive : List.of(sourceText, destinationText, "Patient Alice", "PID-123", "1.2.840.77", "Écho", HASH,
                DIGEST.sha256(), id(1), JOB.toString(), CREATED.toString(), TARGET.reference(), "mrinormalizer-1.0", "5.33.0"))
            assertFalse(report.toString().contains(sensitive), sensitive);
        assertEquals(1, report.sourceCount()); assertEquals(1, count(report, ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI, State.WRITTEN_UNVERIFIED));
        assertTrue(report.failureCounts().isEmpty());
    }

    @Test void publicValueAndProjectionApisAreExactlyFrozen() {
        assertTrue(PublicJobReport.class.isRecord());
        assertArrayEquals(new String[] {"schemaVersion", "state", "sourceCount", "operationCounts", "failureCounts"}, componentNames(PublicJobReport.class));
        assertArrayEquals(new String[] {"kind", "state", "count"}, componentNames(OperationCount.class));
        assertArrayEquals(new String[] {"namespace", "code", "count"}, componentNames(FailureCount.class));
        assertArrayEquals(new Class<?>[] {int.class, JobState.class, long.class, List.class, List.class},
                Arrays.stream(PublicJobReport.class.getRecordComponents()).map(c -> c.getType()).toArray(Class<?>[]::new));
        assertArrayEquals(new Class<?>[] {ManifestOperation.Kind.class, State.class, long.class},
                Arrays.stream(OperationCount.class.getRecordComponents()).map(c -> c.getType()).toArray(Class<?>[]::new));
        assertArrayEquals(new Class<?>[] {FailureNamespace.class, String.class, long.class},
                Arrays.stream(FailureCount.class.getRecordComponents()).map(c -> c.getType()).toArray(Class<?>[]::new));
        assertEquals("java.util.List<" + OperationCount.class.getName() + ">", PublicJobReport.class.getRecordComponents()[3].getGenericType().getTypeName());
        assertEquals("java.util.List<" + FailureCount.class.getName() + ">", PublicJobReport.class.getRecordComponents()[4].getGenericType().getTypeName());
        assertArrayEquals(new String[] {"DETECTION", "RECONSTRUCTION", "CONVERSION", "HASHING", "EXECUTION", "POST_WRITE_VALIDATION", "PERSISTENCE"},
                Arrays.stream(FailureNamespace.values()).map(Enum::name).toArray(String[]::new));
        for (var type : List.of(PublicJobReport.class, OperationCount.class, FailureCount.class)) {
            assertEquals(1, type.getConstructors().length);
            var allowed = new ArrayList<>(List.of(componentNames(type))); allowed.addAll(List.of("equals", "hashCode", "toString"));
            for (var method : type.getDeclaredMethods()) if (Modifier.isPublic(method.getModifiers())) assertTrue(allowed.contains(method.getName()));
            for (var component : type.getRecordComponents()) assertFalse(List.of(RelativePath.class, ContentDigest.class, UUID.class,
                    Instant.class, ManifestReceipt.class, ProcessingEvidence.class, OutputTarget.class,
                    ProcessingEvidence.SourceSummary.class, ProcessingEvidence.ConversionFacts.class).contains(component.getType()));
        }
        assertTrue(Modifier.isFinal(ManifestProjection.class.getModifiers()));
        assertEquals(0, ManifestProjection.class.getConstructors().length);
        assertTrue(Arrays.stream(ManifestProjection.class.getDeclaredConstructors()).allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        assertEquals(List.of("conversion", "publicReport", "reconstruction"), Arrays.stream(ManifestProjection.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers())).map(java.lang.reflect.Method::getName).sorted().toList());
        for (var method : ManifestProjection.class.getDeclaredMethods()) if (Modifier.isPublic(method.getModifiers())) assertTrue(Modifier.isStatic(method.getModifiers()));
        assertEquals(ProcessingEvidence.class, assertDoesNotThrow(() -> ManifestProjection.class.getMethod("reconstruction", DicomProcessingResult.class)).getReturnType());
        assertEquals(ProcessingEvidence.class, assertDoesNotThrow(() -> ManifestProjection.class.getMethod("conversion", DicomToNiftiResult.class)).getReturnType());
        assertEquals(PublicJobReport.class, assertDoesNotThrow(() -> ManifestProjection.class.getMethod("publicReport", ManifestState.class)).getReturnType());
    }

    @Test void publicSchemaIsClosedAndContainsOnlyAggregateFields() throws Exception {
        // Test-only inspection of the schema DOCUMENT; no runtime parser/encoder dependency.
        var schema = Files.readString(Path.of("docs/schemas/public-job-report-v1.schema.json"));
        assertTrue(schema.contains("https://json-schema.org/draft/2020-12/schema"));
        assertTrue(schema.contains("\"const\": \"org.cbihi.mrinormalizer.public-job-report\""));
        assertEquals(List.of("schema", "schemaVersion", "state", "sourceCount", "operationCounts", "failureCounts"),
                matches(schema, "(?m)^    \"([A-Za-z]+)\": \\{"));
        assertEquals(matches(schema, "\"type\": \"(object)\"").size(), matches(schema, "\"additionalProperties\": (false)").size());
        var relationship = Pattern.compile("\"namespace\"\\s*:\\s*\\{\\s*\"const\"\\s*:\\s*\"([A-Z_]+)\"\\s*},\\s*\"code\"\\s*:\\s*\\{\\s*\"enum\"\\s*:\\s*\\[([^]]+)]", Pattern.DOTALL).matcher(schema);
        int branches = 0;
        while (relationship.find()) {
            var namespace = FailureNamespace.values()[branches++];
            assertEquals(namespace.name(), relationship.group(1));
            assertEquals(allowedCodes(namespace), matches(relationship.group(2), "\"([A-Z_]+)\""));
        }
        assertEquals(7, branches);
        for (var field : List.of("path", "source", "destination", "jobId", "operationId", "digest", "sha256", "timestamp", "recordedAt",
                "geometry", "intensityTransform", "softwareVersion", "dcm4cheVersion", "metadata", "message", "stackTrace", "reference"))
            assertFalse(schema.contains("\"" + field + "\":"), field);
    }

    // P03: only the supplied current observation is aggregated, never its history.
    @Test void countsCurrentObservationsNotRepeatedHistory() {
        var source = source("synthetic/nonexistent/dicom", DetectionDiagnostic.NONE, List.of());
        var plan = new ProvenanceManifest(1, JOB, CREATED, List.of(source),
                List.of(operation(1, ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI, source.source(), "out")));
        var replay = new ManifestReplay(plan, HASH);
        acceptJob(replay, JobState.RUNNING, List.of());
        acceptOperation(replay, observed(State.IN_PROGRESS, Optional.empty()), List.of());
        var evidence = ManifestProjection.conversion(new DicomToNiftiResult(null, List.of(DicomProcessingError.INPUT_NOT_FOUND),
                List.of(DicomToNiftiError.OUTPUT_WRITE_FAILED), provenance(false, List.of(DicomProcessingError.INPUT_NOT_FOUND)), null));
        acceptOperation(replay, observed(State.FAILED, Optional.of(evidence)), List.of(VERIFICATION));
        var before = replay.current().operations().get(0).observation();
        var retained = new Observation(State.RECOVERY_REQUIRED, before.startedAt(), before.finishedAt(), before.outputDigest(),
                before.matchedExpectedSourceCount(), Optional.of(Disposition.RECOVERY_RECONCILIATION_REQUIRED), before.processingEvidence());
        acceptOperation(replay, retained, List.of(VERIFICATION, RECOVERY));
        acceptOperation(replay, retained, List.of(VERIFICATION, RECOVERY, CLEANUP));
        var persistence = new ManifestFailure(Phase.PERSISTENCE, Code.WRITE_FAILED);
        acceptJob(replay, JobState.RECOVERY_REQUIRED, List.of(RECOVERY, persistence));
        acceptJob(replay, JobState.RECOVERY_REQUIRED, List.of(RECOVERY, persistence, CLEANUP));
        var report = ManifestProjection.publicReport(replay.current());
        assertEquals(JobState.RECOVERY_REQUIRED, report.state());
        assertEquals(1, failures(report, FailureNamespace.RECONSTRUCTION, "INPUT_NOT_FOUND"));
        assertEquals(1, failures(report, FailureNamespace.CONVERSION, "OUTPUT_WRITE_FAILED"));
        assertEquals(1, failures(report, FailureNamespace.EXECUTION, "VERIFICATION_FAILED"));
        assertEquals(2, failures(report, FailureNamespace.EXECUTION, "RECOVERY_REQUIRED"));
        assertEquals(2, failures(report, FailureNamespace.EXECUTION, "CLEANUP_FAILED"));
        assertEquals(1, failures(report, FailureNamespace.PERSISTENCE, "WRITE_FAILED"));
        assertEquals(1, count(report, ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI, State.RECOVERY_REQUIRED));
        assertEquals(0, count(report, ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI, State.FAILED));
    }

    @Test void allEighteenOperationBinsIncludeZerosAndCountOnlyCurrentStates() {
        for (var kind : ManifestOperation.Kind.values()) for (var state : State.values()) {
            // Verified COPY outcomes and unverified conversion outcomes retain their owning kinds.
            if (kind == ManifestOperation.Kind.COPY && state == State.WRITTEN_UNVERIFIED
                    || kind == ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI && (state == State.COMPLETED || state == State.IDENTICAL_EXISTING)) continue;
            var source = source("src", DetectionDiagnostic.NONE, List.of());
            var plan = new ProvenanceManifest(1, JOB, CREATED, List.of(source), List.of(operation(1, kind, source.source(), "a"), operation(2, kind, source.source(), "b")));
            var failures = state == State.FAILED ? List.of(VERIFICATION) : state == State.RECOVERY_REQUIRED ? List.of(RECOVERY) : List.<ManifestFailure>of();
            var observation = observed(state, Optional.empty());
            var otherObservation = state == State.IN_PROGRESS ? observed(State.NOT_STARTED, Optional.empty()) : observation;
            var report = ManifestProjection.publicReport(current(plan, List.of(new OperationState(id(2), otherObservation, failures),
                    new OperationState(id(1), observation, failures)), List.of()));
            int expectedCount = state == State.IN_PROGRESS ? 1 : 2;
            assertEquals(18, report.operationCounts().size()); assertEquals(expectedCount, count(report, kind, state));
            int index = 0;
            for (var expectedKind : ManifestOperation.Kind.values()) for (var expectedState : State.values()) {
                long expected = expectedKind == kind && expectedState == state ? expectedCount
                        : expectedKind == kind && state == State.IN_PROGRESS && expectedState == State.NOT_STARTED ? 1 : 0;
                assertEquals(new OperationCount(expectedKind, expectedState, expected), report.operationCounts().get(index++));
            }
        }
        var empty = new ProvenanceManifest(1, JOB, CREATED, List.of(), List.of());
        var report = ManifestProjection.publicReport(new ManifestReplay(empty, HASH).current());
        assertEquals(0, report.sourceCount()); assertEquals(18, report.operationCounts().size());
        assertTrue(report.operationCounts().stream().allMatch(c -> c.count() == 0)); assertTrue(report.failureCounts().isEmpty());
    }

    @Test void distinctEqualDigestSourcesAndRepeatedReferencesCountBySourceKey() {
        var a = source("first", DetectionDiagnostic.INPUT_NOT_FOUND, List.of());
        var b = source("second", DetectionDiagnostic.INPUT_NOT_FOUND, List.of());
        var plan = new ProvenanceManifest(1, JOB, CREATED, List.of(b, a), List.of(
                operation(3, ManifestOperation.Kind.COPY, a.source(), "other"),
                operation(2, ManifestOperation.Kind.COPY, b.source(), "shared"),
                operation(1, ManifestOperation.Kind.COPY, a.source(), "shared")));
        var report = ManifestProjection.publicReport(new ManifestReplay(plan, HASH).current());
        assertEquals(a.digest(), b.digest()); assertEquals(2, report.sourceCount());
        assertEquals(3, count(report, ManifestOperation.Kind.COPY, State.NOT_STARTED));
        assertEquals(2, failures(report, FailureNamespace.DETECTION, "INPUT_NOT_FOUND"));
    }

    @Test void everyOriginalDiagnosticUsesItsOwnNamespaceAndNoneIsOmitted() {
        var sources = new ArrayList<SourceFileRecord>();
        for (var diagnostic : DetectionDiagnostic.values()) sources.add(source("source-" + diagnostic.ordinal(), diagnostic, List.of()));
        var plan = new ProvenanceManifest(1, JOB, CREATED, sources, List.of());
        var report = ManifestProjection.publicReport(new ManifestReplay(plan, HASH).current());
        assertEquals(DetectionDiagnostic.values().length, report.sourceCount()); assertEquals(11, report.failureCounts().size());
        assertEquals(0, failures(report, FailureNamespace.DETECTION, "NONE"));
        for (var diagnostic : DetectionDiagnostic.values()) if (diagnostic != DetectionDiagnostic.NONE)
            assertEquals(1, failures(report, FailureNamespace.DETECTION, diagnostic.name()));
        var source = source("src", DetectionDiagnostic.NONE, List.of());
        var evidence = new ProcessingEvidence(ProcessingEvidence.Scope.CONVERSION, false, Optional.empty(),
                List.of(DicomProcessingError.values()), List.of(DicomToNiftiError.values()), Optional.empty());
        var errorPlan = new ProvenanceManifest(1, JOB, CREATED, List.of(source), List.of(operation(1, ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI, source.source(), "out")));
        var errors = ManifestProjection.publicReport(current(errorPlan, List.of(new OperationState(id(1), observed(State.FAILED, Optional.of(evidence)), List.of())), List.of()));
        assertEquals(23, errors.failureCounts().size());
        for (var error : DicomProcessingError.values()) assertEquals(1, failures(errors, FailureNamespace.RECONSTRUCTION, error.name()));
        for (var error : DicomToNiftiError.values()) assertEquals(1, failures(errors, FailureNamespace.CONVERSION, error.name()));
        assertTrue(errors.failureCounts().stream().noneMatch(f -> f.namespace().ordinal() >= FailureNamespace.HASHING.ordinal()));
    }

    @Test void everyWorkflowPhaseCodePairUsesItsExactNamespace() {
        var workflow = validFailures();
        var source = source("src", DetectionDiagnostic.NONE, workflow);
        var plan = new ProvenanceManifest(1, JOB, CREATED, List.of(source), List.of(operation(1, ManifestOperation.Kind.COPY, source.source(), "out")));
        var report = ManifestProjection.publicReport(current(plan,
                List.of(new OperationState(id(1), observed(State.BLOCKED, Optional.empty()), workflow)), workflow));
        assertEquals(workflow.size(), report.failureCounts().size());
        for (var failure : workflow) assertEquals(3, failures(report, FailureNamespace.valueOf(failure.phase().name()), failure.code().name()));
        for (int index = 1; index < report.failureCounts().size(); index++) {
            var before = report.failureCounts().get(index - 1); var after = report.failureCounts().get(index);
            assertTrue(before.namespace().ordinal() < after.namespace().ordinal() || before.namespace() == after.namespace()
                    && Code.valueOf(before.code()).ordinal() < Code.valueOf(after.code()).ordinal());
        }
    }

    @Test void sameSpellingInDifferentNamespacesRemainsDistinct() {
        var source = source("src", DetectionDiagnostic.INPUT_NOT_FOUND, List.of());
        var plan = new ProvenanceManifest(1, JOB, CREATED, List.of(source), List.of(operation(1, ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI, source.source(), "out")));
        var evidence = ManifestProjection.reconstruction(DicomProcessingResult.failure(DicomProcessingError.INPUT_NOT_FOUND, null));
        var report = ManifestProjection.publicReport(current(plan,
                List.of(new OperationState(id(1), observed(State.FAILED, Optional.of(evidence)), List.of())), List.of()));
        assertEquals(List.of(new FailureCount(FailureNamespace.DETECTION, "INPUT_NOT_FOUND", 1),
                new FailureCount(FailureNamespace.RECONSTRUCTION, "INPUT_NOT_FOUND", 1)), report.failureCounts());
    }

    @Test void publicCountsRejectNullNegativeMalformedAndNoncanonicalValues() {
        var bins = zeroBins();
        rejectPublic(() -> new PublicJobReport(0, JobState.PLANNED, 0, bins, List.of()));
        rejectPublic(() -> new PublicJobReport(2, JobState.PLANNED, 0, bins, List.of()));
        rejectPublic(() -> new PublicJobReport(1, null, 0, bins, List.of()));
        rejectPublic(() -> new PublicJobReport(1, JobState.PLANNED, -1, bins, List.of()));
        rejectPublic(() -> new PublicJobReport(1, JobState.PLANNED, 0, null, List.of()));
        rejectPublic(() -> new PublicJobReport(1, JobState.PLANNED, 0, bins, null));
        rejectPublic(() -> new PublicJobReport(1, JobState.PLANNED, 0, bins.subList(1, bins.size()), List.of()));
        var extra = new ArrayList<>(bins); extra.add(bins.get(0)); rejectPublic(() -> publicValue(extra, List.of()));
        var duplicate = new ArrayList<>(bins); duplicate.set(1, duplicate.get(0)); rejectPublic(() -> publicValue(duplicate, List.of()));
        var reordered = new ArrayList<>(bins); Collections.swap(reordered, 0, 1); rejectPublic(() -> publicValue(reordered, List.of()));
        var nullable = new ArrayList<>(bins); nullable.set(0, null); rejectPublic(() -> publicValue(nullable, List.of()));
        rejectPublic(() -> new OperationCount(null, State.NOT_STARTED, 0)); rejectPublic(() -> new OperationCount(ManifestOperation.Kind.COPY, null, 0));
        rejectPublic(() -> new OperationCount(ManifestOperation.Kind.COPY, State.NOT_STARTED, -1));
        var nullFailures = new ArrayList<FailureCount>(); nullFailures.add(null); rejectPublic(() -> publicValue(bins, nullFailures));
    }

    @Test void failureCodesAreClosedPerNamespaceAndCountsStrictlyPositive() {
        var tokens = new java.util.LinkedHashSet<String>();
        for (var code : DetectionDiagnostic.values()) tokens.add(code.name());
        for (var code : DicomProcessingError.values()) tokens.add(code.name());
        for (var code : DicomToNiftiError.values()) tokens.add(code.name());
        for (var code : Code.values()) tokens.add(code.name());
        for (var namespace : FailureNamespace.values()) {
            var allowed = allowedCodes(namespace);
            for (var code : tokens) {
                if (allowed.contains(code)) assertEquals(code, new FailureCount(namespace, code, 1).code());
                else rejectPublic(() -> new FailureCount(namespace, code, 1));
            }
            for (var code : allowed) {
                rejectPublic(() -> new FailureCount(namespace, code.toLowerCase(java.util.Locale.ROOT), 1));
                rejectPublic(() -> new FailureCount(namespace, namespace.name() + "." + code, 1));
            }
            for (var token : List.of("", "0", "INPUT NOT FOUND", "Patient Alice/PID-123", "FUTURE_TOKEN"))
                rejectPublic(() -> new FailureCount(namespace, token, 1));
        }
        rejectPublic(() -> new FailureCount(null, "INPUT_NOT_FOUND", 1));
        rejectPublic(() -> new FailureCount(FailureNamespace.DETECTION, null, 1));
        rejectPublic(() -> new FailureCount(FailureNamespace.DETECTION, "INPUT_NOT_FOUND", 0));
        rejectPublic(() -> new FailureCount(FailureNamespace.DETECTION, "INPUT_NOT_FOUND", -1));
        var a = new FailureCount(FailureNamespace.DETECTION, "INPUT_NOT_FOUND", 1);
        var b = new FailureCount(FailureNamespace.DETECTION, "INPUT_IS_DIRECTORY", 1);
        rejectPublic(() -> publicValue(zeroBins(), List.of(a, a)));
        rejectPublic(() -> publicValue(zeroBins(), List.of(a, new FailureCount(a.namespace(), a.code(), 2))));
        rejectPublic(() -> publicValue(zeroBins(), List.of(b, a)));
        rejectPublic(() -> publicValue(zeroBins(), List.of(new FailureCount(FailureNamespace.RECONSTRUCTION, "INPUT_NOT_FOUND", 1), a)));
    }

    @Test void publicCollectionsAreDefensivelyCopiedUnmodifiableAndDeterministic() {
        var bins = zeroBins(); var failures = new ArrayList<>(List.of(new FailureCount(FailureNamespace.DETECTION, "IO_ERROR", 1)));
        var report = publicValue(bins, failures); bins.clear(); failures.clear();
        assertEquals(18, report.operationCounts().size()); assertEquals(1, report.failureCounts().size());
        assertThrowsExactly(UnsupportedOperationException.class, () -> report.operationCounts().clear());
        assertThrowsExactly(UnsupportedOperationException.class, () -> report.failureCounts().clear());
        var a = source("a", DetectionDiagnostic.IO_ERROR, List.of()); var b = source("b", DetectionDiagnostic.INPUT_NOT_FOUND, List.of());
        var first = new ProvenanceManifest(1, JOB, CREATED, List.of(a, b), List.of(operation(1, ManifestOperation.Kind.COPY, a.source(), "a"), operation(2, ManifestOperation.Kind.COPY, b.source(), "b")));
        var second = new ProvenanceManifest(1, JOB, CREATED, List.of(b, a), List.of(first.operations().get(1), first.operations().get(0)));
        assertEquals(ManifestProjection.publicReport(new ManifestReplay(first, HASH).current()), ManifestProjection.publicReport(new ManifestReplay(second, HASH).current()));
    }

    @Test void aggregateCountsUseCheckedArithmeticAndAcceptedSourceOperationBounds() {
        for (var state : JobState.values()) assertEquals(state, new PublicJobReport(1, state, 100000, zeroBins(), List.of()).state());
        rejectPublic(() -> new PublicJobReport(1, JobState.PLANNED, 100001, zeroBins(), List.of()));
        var maximum = zeroBins(); maximum.set(0, new OperationCount(ManifestOperation.Kind.COPY, State.NOT_STARTED, 100000));
        assertEquals(100000, publicValue(maximum, List.of()).operationCounts().get(0).count());
        maximum.set(1, new OperationCount(ManifestOperation.Kind.COPY, State.IN_PROGRESS, 1)); rejectPublic(() -> publicValue(maximum, List.of()));
        var overflow = zeroBins(); overflow.set(0, new OperationCount(ManifestOperation.Kind.COPY, State.NOT_STARTED, Long.MAX_VALUE));
        overflow.set(1, new OperationCount(ManifestOperation.Kind.COPY, State.IN_PROGRESS, 1)); rejectPublic(() -> publicValue(overflow, List.of()));
        var a = new FailureCount(FailureNamespace.DETECTION, "INPUT_NOT_FOUND", Long.MAX_VALUE);
        var b = new FailureCount(FailureNamespace.DETECTION, "INPUT_IS_DIRECTORY", 1);
        rejectPublic(() -> publicValue(zeroBins(), List.of(a, b)));
    }

    private static ImageVolume volume() {
        return new ImageVolume(new VolumeGeometry(2, 3, 4, 1.5, 2.5, 3.5, new double[] {11, -17, 23},
                new double[] {1, 0, 0}, new double[] {0, 1, 0}, new double[] {0, 0, 1}, CoordinateSystem.DICOM_PATIENT_LPS),
                new ImmutableVoxelData(2, 3, 4, ScalarType.INT16, new long[24]), INTENSITY);
    }
    private static ProvenanceRecord provenance(boolean successful, List<DicomProcessingError> errors) {
        return new ProvenanceRecord(HASH, "mrinormalizer-1.0", "5.33.0", FINISH, successful, 5, 3,
                "Patient Alice 1.2.840.77 geometry", "PID-123 legacy pixels", errors);
    }
    private static ConversionValidationReport report(ScalarType type, boolean preserved, boolean resampled, boolean interpolated, boolean reordered) {
        return new ConversionValidationReport(TARGET, 2, 3, 4, type, 24, 1.5, 2.5, 3.5, AFFINE, INTENSITY,
                preserved, resampled, interpolated, reordered);
    }
    private static SourceFileRecord source(String path, DetectionDiagnostic diagnostic, List<ManifestFailure> failures) {
        var detection = diagnostic == DetectionDiagnostic.NONE ? DetectionResult.identified(DetectionOutcome.DICOM)
                : switch (diagnostic) {
                    case INVALID_DICOM, INVALID_NIFTI, INVALID_GZIP -> DetectionResult.corrupt(diagnostic);
                    default -> DetectionResult.unknown(diagnostic);
                };
        return new SourceFileRecord(new RelativePath(RelativePath.Root.SOURCE, path), Optional.of(DIGEST), FormatAssessment.fromDetection(detection), failures);
    }
    private static ManifestOperation operation(int number, ManifestOperation.Kind kind, RelativePath source, String destination) {
        return new ManifestOperation(id(number), kind, List.of(source), new RelativePath(RelativePath.Root.OUTPUT, destination));
    }
    private static ManifestState current(ProvenanceManifest plan, List<OperationState> operations, List<ManifestFailure> failures) {
        return new ManifestState(plan, new ManifestReceipt(JOB, 1, HASH, HASH), JobState.RUNNING, NOW, operations, failures);
    }
    private static Observation observed(State state, Optional<ProcessingEvidence> evidence) {
        return switch (state) {
            case NOT_STARTED -> new Observation(state, Optional.empty(), Optional.empty(), Optional.empty(), 0, Optional.empty(), Optional.empty());
            case IN_PROGRESS -> new Observation(state, Optional.of(START), Optional.empty(), Optional.empty(), 1, Optional.empty(), Optional.empty());
            case COMPLETED, IDENTICAL_EXISTING -> new Observation(state, Optional.of(START), Optional.of(FINISH), Optional.of(DIGEST), 1, Optional.empty(), Optional.empty());
            case WRITTEN_UNVERIFIED -> new Observation(state, Optional.of(START), Optional.of(FINISH), Optional.of(DIGEST), 1, Optional.empty(),
                    evidence.isPresent() ? evidence : Optional.of(ManifestProjection.conversion(DicomToNiftiResult.success(TARGET, null, report(ScalarType.INT16, true, false, false, false)))));
            case SKIPPED_POLICY -> new Observation(state, Optional.empty(), Optional.of(FINISH), Optional.empty(), 0, Optional.of(Disposition.NOT_REQUESTED_BY_POLICY), Optional.empty());
            case BLOCKED -> new Observation(state, Optional.empty(), Optional.of(FINISH), Optional.empty(), 0, Optional.of(Disposition.VALIDATION_REQUIRED), evidence);
            case FAILED -> new Observation(state, Optional.of(START), Optional.of(FINISH), Optional.empty(), 0, Optional.empty(), evidence);
            case RECOVERY_REQUIRED -> new Observation(state, Optional.of(START), Optional.empty(), Optional.empty(), 1, Optional.of(Disposition.RECOVERY_RECONCILIATION_REQUIRED), evidence);
        };
    }
    private static ArrayList<OperationCount> zeroBins() {
        var bins = new ArrayList<OperationCount>();
        for (var kind : ManifestOperation.Kind.values()) for (var state : State.values()) bins.add(new OperationCount(kind, state, 0));
        return bins;
    }
    private static PublicJobReport publicValue(List<OperationCount> bins, List<FailureCount> failures) {
        return new PublicJobReport(1, JobState.RUNNING, 0, bins, failures);
    }
    private static long count(PublicJobReport report, ManifestOperation.Kind kind, State state) {
        return report.operationCounts().get(kind.ordinal() * State.values().length + state.ordinal()).count();
    }
    private static long failures(PublicJobReport report, FailureNamespace namespace, String code) {
        return report.failureCounts().stream().filter(f -> f.namespace() == namespace && f.code().equals(code)).mapToLong(FailureCount::count).sum();
    }
    private static List<ManifestFailure> validFailures() {
        var failures = new ArrayList<ManifestFailure>();
        for (var phase : Phase.values()) for (var code : Code.values()) {
            try { failures.add(new ManifestFailure(phase, code)); } catch (IllegalArgumentException inapplicable) { /* Exact accepted pair matrix. */ }
        }
        return failures;
    }
    private static List<String> allowedCodes(FailureNamespace namespace) {
        return switch (namespace) {
            case DETECTION -> Arrays.stream(DetectionDiagnostic.values()).filter(v -> v != DetectionDiagnostic.NONE).map(Enum::name).toList();
            case RECONSTRUCTION -> Arrays.stream(DicomProcessingError.values()).map(Enum::name).toList();
            case CONVERSION -> Arrays.stream(DicomToNiftiError.values()).map(Enum::name).toList();
            default -> validFailures().stream().filter(f -> f.phase().name().equals(namespace.name())).map(f -> f.code().name()).toList();
        };
    }
    private static void acceptJob(ManifestReplay replay, JobState state, List<ManifestFailure> failures) {
        var head = replay.current().receipt();
        replay.accept(new CheckpointRecord(1, JOB, head.sequence() + 1, head.headSha256(), NOW, CheckpointRecord.Kind.JOB_OBSERVED,
                Optional.empty(), Optional.empty(), Optional.of(state), failures), HASH);
    }
    private static void acceptOperation(ManifestReplay replay, Observation observation, List<ManifestFailure> failures) {
        var head = replay.current().receipt();
        replay.accept(new CheckpointRecord(1, JOB, head.sequence() + 1, head.headSha256(), NOW, CheckpointRecord.Kind.OPERATION_OBSERVED,
                Optional.of(id(1)), Optional.of(observation), Optional.empty(), failures), HASH);
    }
    private static String id(int number) { return String.format(java.util.Locale.ROOT, "%064x", number); }
    private static String[] componentNames(Class<?> type) { return Arrays.stream(type.getRecordComponents()).map(c -> c.getName()).toArray(String[]::new); }
    private static List<String> matches(String text, String regex) {
        var matcher = Pattern.compile(regex).matcher(text); var matches = new ArrayList<String>();
        while (matcher.find()) matches.add(matcher.group(1));
        return matches;
    }
    private static void rejectPublic(Executable action) {
        var error = assertThrowsExactly(IllegalArgumentException.class, action);
        assertEquals("Invalid public job report", error.getMessage()); assertNull(error.getCause());
    }
    private static void rejectProjection(Executable action) {
        var error = assertThrowsExactly(IllegalArgumentException.class, action);
        assertEquals("Invalid manifest projection", error.getMessage()); assertNull(error.getCause());
    }
}
