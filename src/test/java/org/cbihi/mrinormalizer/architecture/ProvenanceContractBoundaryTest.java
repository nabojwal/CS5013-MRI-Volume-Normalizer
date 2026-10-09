package org.cbihi.mrinormalizer.architecture;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.JarURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.cbihi.mrinormalizer.application.dataset.model.FormatAssessment;
import org.cbihi.mrinormalizer.application.port.out.ManifestStore;
import org.cbihi.mrinormalizer.application.port.out.PublicReportWriter;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.application.result.DicomProcessingResult;
import org.cbihi.mrinormalizer.application.result.DicomToNiftiResult;
import org.cbihi.mrinormalizer.domain.error.DicomProcessingError;
import org.cbihi.mrinormalizer.domain.error.DicomToNiftiError;
import org.cbihi.mrinormalizer.domain.model.AffineMatrix4;
import org.cbihi.mrinormalizer.domain.model.DetectionDiagnostic;
import org.cbihi.mrinormalizer.domain.model.DetectionResult;
import org.cbihi.mrinormalizer.domain.model.IntensityTransform;
import org.cbihi.mrinormalizer.domain.model.OutputTarget;
import org.cbihi.mrinormalizer.domain.model.ScalarType;
import org.junit.jupiter.api.Test;

/** B01/B02 compiled-declaration boundary freeze; behavior remains owned by the accepted slice tests. */
class ProvenanceContractBoundaryTest {
    private static final String PACKAGE = "org.cbihi.mrinormalizer.application.provenance.manifest";
    private static final List<Class<?>> TOP_LEVEL = List.of(RelativePath.class, ContentDigest.class, SourceFileRecord.class,
            ManifestFailure.class, ProcessingEvidence.class, ManifestOperation.class, ProvenanceManifest.class,
            CheckpointRecord.class, ManifestReceipt.class, ManifestState.class, ProvenancePersistenceException.class,
            ManifestReplay.class, PublicJobReport.class, ManifestProjection.class);
    private static final List<Class<?>> RECORDS = List.of(RelativePath.class, ContentDigest.class, SourceFileRecord.class,
            ManifestFailure.class, ProcessingEvidence.class, ProcessingEvidence.SourceSummary.class,
            ProcessingEvidence.ConversionFacts.class, ManifestOperation.class, ProvenanceManifest.class,
            CheckpointRecord.class, CheckpointRecord.Observation.class, ManifestReceipt.class, ManifestState.class,
            ManifestState.OperationState.class, PublicJobReport.class, PublicJobReport.OperationCount.class,
            PublicJobReport.FailureCount.class);
    private static final Map<Class<?>, List<String>> ENUMS = Map.ofEntries(
            Map.entry(RelativePath.Root.class, List.of("SOURCE", "OUTPUT")),
            Map.entry(ManifestFailure.Phase.class, List.of("HASHING", "EXECUTION", "POST_WRITE_VALIDATION", "PERSISTENCE")),
            Map.entry(ManifestFailure.Code.class, List.of("INVALID_MANIFEST", "INVALID_REFERENCE", "CONTAINMENT_UNPROVEN",
                    "INPUT_UNAVAILABLE", "SOURCE_CHANGED", "HASH_FAILED", "CHECKPOINT_CONFLICT", "WRITE_FAILED",
                    "PUBLICATION_UNAVAILABLE", "READ_FAILED", "UNSUPPORTED_SCHEMA", "CORRUPT_CHECKPOINT",
                    "ACCESS_CONTROL_UNAVAILABLE", "RESOURCE_LIMIT", "CLEANUP_FAILED", "RECOVERY_REQUIRED",
                    "OUTPUT_CONFLICT", "VERIFICATION_FAILED", "INTERRUPTED")),
            Map.entry(ProcessingEvidence.Scope.class, List.of("RECONSTRUCTION", "CONVERSION")),
            Map.entry(ManifestOperation.Kind.class, List.of("COPY", "CONVERT_DICOM_TO_NIFTI")),
            Map.entry(CheckpointRecord.Kind.class, List.of("OPERATION_OBSERVED", "JOB_OBSERVED")),
            Map.entry(CheckpointRecord.State.class, List.of("NOT_STARTED", "IN_PROGRESS", "COMPLETED", "IDENTICAL_EXISTING",
                    "WRITTEN_UNVERIFIED", "SKIPPED_POLICY", "BLOCKED", "FAILED", "RECOVERY_REQUIRED")),
            Map.entry(CheckpointRecord.Disposition.class, List.of("NOT_REQUESTED_BY_POLICY", "VALIDATION_REQUIRED",
                    "UNSUPPORTED_INPUT", "INPUT_UNAVAILABLE", "RECOVERY_RECONCILIATION_REQUIRED")),
            Map.entry(ManifestState.JobState.class, List.of("PLANNED", "RUNNING", "COMPLETED", "FAILED", "RECOVERY_REQUIRED")),
            Map.entry(ProvenancePersistenceException.PublicationOutcome.class, List.of("NOT_PUBLISHED", "PUBLISHED", "UNKNOWN")),
            Map.entry(PublicJobReport.FailureNamespace.class, List.of("DETECTION", "RECONSTRUCTION", "CONVERSION", "HASHING",
                    "EXECUTION", "POST_WRITE_VALIDATION", "PERSISTENCE")));

    // B01 matrix anchor. Every shape below is read from actual compiled declarations.
    @Test void actualApisGenericsAndResponsibilitiesAreConfined() throws Exception {
        freezeRecords(); freezeEnums(); freezeServicesAndPorts();
        for (var type : boundary()) {
            assertEquals(0, type.getTypeParameters().length, type.getName());
            for (var constructor : type.getConstructors()) {
                assertEquals(0, constructor.getTypeParameters().length); assertFalse(constructor.isVarArgs());
                assertEquals(0, constructor.getGenericExceptionTypes().length);
                for (var parameter : constructor.getGenericParameterTypes()) approved(parameter, false);
            }
            for (var method : publicDeclarations(type)) {
                assertEquals(0, method.getTypeParameters().length); assertFalse(method.isVarArgs());
                assertEquals(0, method.getGenericExceptionTypes().length);
                boolean language = languageMethod(type, method);
                approved(method.getGenericReturnType(), language);
                for (var parameter : method.getGenericParameterTypes()) approved(parameter, language);
            }
            for (var field : type.getFields()) {
                assertTrue(type.isEnum() && field.isEnumConstant(), "Unexpected public field: " + field);
                assertEquals(type, field.getType());
            }
            for (var component : type.isRecord() ? type.getRecordComponents() : new java.lang.reflect.RecordComponent[0])
                approved(component.getGenericType(), false);
            for (var parent : type.getGenericInterfaces()) approved(parent, true);
            // The one approved exception is RuntimeException by design; arbitrary
            // Throwable/message/cause constructor and declared-method payloads are not.
            if (type == ProvenancePersistenceException.class) assertEquals(RuntimeException.class, type.getSuperclass());
            else approved(type.getGenericSuperclass(), true);
        }
    }

    // B02 matrix anchor: original diagnostics, aggregate privacy and plan/delta ownership.
    @Test void phaseErrorsPrivacyAndOwnedSliceShapeAreFrozen() throws Exception {
        originalDiagnosticHomesRemainSoleAndTyped(); publicAggregateShapeHasNoRestrictedChannels();
        planCheckpointAndCurrentViewResponsibilitiesStayDistinct();
        freezeRecord(ManifestFailure.class, c("phase", ManifestFailure.Phase.class), c("code", ManifestFailure.Code.class));
        freezeEnum(ManifestFailure.Phase.class, ENUMS.get(ManifestFailure.Phase.class));
        freezeEnum(ManifestFailure.Code.class, ENUMS.get(ManifestFailure.Code.class));
        freezeServicesAndPorts(); // No READY, recognition, validation, execution or reopening method can be added.
    }

    @Test void recordComponentsConstructorsAndGenericsRemainExactlyFrozen() throws Exception { freezeRecords(); }

    @Test void allElevenEnumDeclarationsRemainExactlyFrozen() throws Exception { freezeEnums(); }

    @Test void compiledOwnedPackageAndPublicNestedTypeInventoryIsClosed() throws Exception {
        var compiled = new HashSet<Class<?>>();
        String resource = PACKAGE.replace('.', '/');
        var loader = ProvenanceManifest.class.getClassLoader(); var roots = loader.getResources(resource);
        while (roots.hasMoreElements()) {
            var url = roots.nextElement();
            if (url.getProtocol().equals("file")) {
                try (var entries = Files.list(Path.of(url.toURI()))) {
                    for (var path : entries.filter(p -> p.getFileName().toString().endsWith(".class")).toList())
                        addTopLevel(compiled, path.getFileName().toString(), loader);
                }
            } else if (url.getProtocol().equals("jar")) {
                var connection = (JarURLConnection) url.openConnection(); connection.setUseCaches(false);
                try (var jar = connection.getJarFile()) {
                    var entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        String name = entries.nextElement().getName();
                        if (name.startsWith(resource + "/")) {
                            String leaf = name.substring(resource.length() + 1);
                            if (!leaf.contains("/")) addTopLevel(compiled, leaf, loader);
                        }
                    }
                }
            } else fail("Unsupported compiled-class resource protocol: " + url.getProtocol());
        }
        assertEquals(new HashSet<>(TOP_LEVEL), compiled, "Actual compiled F1 package inventory");
        for (var outer : boundary()) {
            if (TOP_LEVEL.contains(outer)) assertEquals(PACKAGE, outer.getPackageName());
            var expected = new HashSet<Class<?>>();
            for (var candidate : boundary()) if (candidate.getDeclaringClass() == outer) expected.add(candidate);
            var actual = new HashSet<>(Arrays.stream(outer.getDeclaredClasses()).filter(t -> Modifier.isPublic(t.getModifiers())).toList());
            assertEquals(expected, actual, outer.getName());
        }
    }

    @Test void replayProjectionAndPersistencePortsHaveOnlyAcceptedMethods() throws Exception { freezeServicesAndPorts(); }

    @Test void originalDiagnosticHomesRemainSoleAndTyped() throws Exception {
        assertEquals(FormatAssessment.class, SourceFileRecord.class.getDeclaredMethod("assessment").getReturnType());
        assertEquals(DetectionResult.class, FormatAssessment.class.getDeclaredMethod("initialDetection").getReturnType());
        assertEquals(DetectionDiagnostic.class, DetectionResult.class.getDeclaredMethod("diagnostic").getReturnType());
        assertShape(SourceFileRecord.class.getDeclaredMethod("failures").getGenericReturnType(), list(ManifestFailure.class));
        var homes = Map.of(DetectionDiagnostic.class, Set.<String>of(),
                DicomProcessingError.class, Set.of(ProcessingEvidence.class.getName() + "#reconstructionErrors"),
                DicomToNiftiError.class, Set.of(ProcessingEvidence.class.getName() + "#conversionErrors"));
        for (var diagnostic : homes.keySet()) {
            var actual = new HashSet<String>();
            for (var record : RECORDS) for (var component : record.getRecordComponents())
                if (contains(component.getGenericType(), diagnostic)) actual.add(record.getName() + "#" + component.getName());
            assertEquals(homes.get(diagnostic), actual, diagnostic.getName());
        }
        assertTrue(Arrays.stream(ProcessingEvidence.SourceSummary.class.getRecordComponents())
                .noneMatch(c -> c.getName().toLowerCase(java.util.Locale.ROOT).contains("error")));
        for (var type : List.of(CheckpointRecord.class, ManifestState.OperationState.class))
            assertShape(type.getDeclaredMethod("failures").getGenericReturnType(), list(ManifestFailure.class));
        assertShape(ManifestState.class.getDeclaredMethod("jobFailures").getGenericReturnType(), list(ManifestFailure.class));
        assertShape(ProvenancePersistenceException.class.getDeclaredMethod("failures").getGenericReturnType(), list(ManifestFailure.class));
    }

    @Test void publicAggregateShapeHasNoRestrictedChannels() throws Exception {
        freezePublicRecords(); freezeEnum(PublicJobReport.FailureNamespace.class, ENUMS.get(PublicJobReport.FailureNamespace.class));
        var allowed = Set.<Class<?>>of(int.class, long.class, String.class, List.class, PublicJobReport.class,
                PublicJobReport.OperationCount.class, PublicJobReport.FailureCount.class, PublicJobReport.FailureNamespace.class,
                ManifestState.JobState.class, ManifestOperation.Kind.class, CheckpointRecord.State.class);
        for (var type : List.of(PublicJobReport.class, PublicJobReport.OperationCount.class, PublicJobReport.FailureCount.class)) {
            for (var component : type.getRecordComponents()) {
                assertOnly(component.getGenericType(), allowed);
                if (component.getType() == String.class) {
                    assertEquals(PublicJobReport.FailureCount.class, type); assertEquals("code", component.getName());
                }
            }
        }
        // schemaVersion is the approved integer wire version, not a software token.
        assertEquals(int.class, PublicJobReport.class.getDeclaredMethod("schemaVersion").getReturnType());
    }

    @Test void planCheckpointAndCurrentViewResponsibilitiesStayDistinct() throws Exception {
        freezePlanAndCheckpoint();
        var checkpointForbidden = Set.<Class<?>>of(ProvenanceManifest.class, SourceFileRecord.class, ManifestOperation.class,
                RelativePath.class, ManifestState.class, ManifestReplay.class, ManifestReceipt.class, Map.class);
        for (var type : List.of(CheckpointRecord.class, CheckpointRecord.Observation.class))
            for (var component : type.getRecordComponents()) for (var forbidden : checkpointForbidden)
                assertFalse(contains(component.getGenericType(), forbidden), component.getName());
        var planForbidden = Set.<Class<?>>of(ManifestReceipt.class, ManifestState.class, ManifestState.JobState.class,
                CheckpointRecord.class, CheckpointRecord.Observation.class, ManifestReplay.class, ProcessingEvidence.class);
        for (var component : ProvenanceManifest.class.getRecordComponents()) for (var forbidden : planForbidden)
            assertFalse(contains(component.getGenericType(), forbidden), component.getName());
        freezeRecord(ManifestState.class, c("plan", ProvenanceManifest.class), c("receipt", ManifestReceipt.class),
                c("state", ManifestState.JobState.class), c("recordedAt", Instant.class),
                c("operations", list(ManifestState.OperationState.class)), c("jobFailures", list(ManifestFailure.class)));
        freezeRecord(ManifestState.OperationState.class, c("operationId", String.class),
                c("observation", CheckpointRecord.Observation.class), c("failures", list(ManifestFailure.class)));
    }

    @Test void persistenceExceptionHasNoMessageCauseOrArbitraryPayloadConstructor() throws Exception {
        assertEquals(RuntimeException.class, ProvenancePersistenceException.class.getSuperclass());
        freezeServicesAndPorts();
        var constructor = ProvenancePersistenceException.class.getConstructors()[0];
        for (var parameter : constructor.getGenericParameterTypes()) {
            assertFalse(contains(parameter, String.class)); assertFalse(contains(parameter, Throwable.class));
        }
        // Inherited RuntimeException/Throwable language APIs are not new F1 payload
        // contracts. Only F1 declarations and its single typed constructor are frozen.
        assertEquals(Set.of("failures", "outcome", "knownPublication"),
                publicDeclarations(ProvenancePersistenceException.class).stream().map(Method::getName).collect(java.util.stream.Collectors.toSet()));
    }

    private static void freezeRecords() throws Exception {
        freezeRecord(RelativePath.class, c("root", RelativePath.Root.class), c("path", String.class));
        freezeRecord(ContentDigest.class, c("sizeBytes", long.class), c("sha256", String.class));
        freezeRecord(SourceFileRecord.class, c("source", RelativePath.class), c("digest", optional(ContentDigest.class)),
                c("assessment", FormatAssessment.class), c("failures", list(ManifestFailure.class)));
        freezeRecord(ManifestFailure.class, c("phase", ManifestFailure.Phase.class), c("code", ManifestFailure.Code.class));
        freezeRecord(ProcessingEvidence.class, c("scope", ProcessingEvidence.Scope.class), c("phaseSuccessful", boolean.class),
                c("sourceSummary", optional(ProcessingEvidence.SourceSummary.class)), c("reconstructionErrors", list(DicomProcessingError.class)),
                c("conversionErrors", list(DicomToNiftiError.class)), c("conversionFacts", optional(ProcessingEvidence.ConversionFacts.class)));
        freezeRecord(ProcessingEvidence.SourceSummary.class, c("selectedSourceFingerprint", optional(String.class)), c("softwareVersion", String.class),
                c("dcm4cheVersion", String.class), c("completedAt", Instant.class), c("provenanceSuccessful", boolean.class),
                c("inputCount", int.class), c("acceptedSlices", int.class));
        freezeRecord(ProcessingEvidence.ConversionFacts.class, c("width", int.class), c("height", int.class), c("depth", int.class),
                c("scalarType", ScalarType.class), c("voxelCount", long.class), c("rowSpacingMm", double.class), c("columnSpacingMm", double.class),
                c("sliceSpacingMm", double.class), c("niftiRasVoxelToWorld", AffineMatrix4.class), c("intensityTransform", IntensityTransform.class),
                c("storedVoxelValuesPreserved", boolean.class), c("resampled", boolean.class), c("interpolated", boolean.class), c("voxelOrderChanged", boolean.class));
        freezeRecord(ManifestOperation.class, c("operationId", String.class), c("kind", ManifestOperation.Kind.class),
                c("sources", list(RelativePath.class)), c("destination", RelativePath.class));
        freezePlanAndCheckpoint();
        freezeRecord(ManifestReceipt.class, c("jobId", UUID.class), c("sequence", long.class), c("planSha256", String.class), c("headSha256", String.class));
        freezeRecord(ManifestState.class, c("plan", ProvenanceManifest.class), c("receipt", ManifestReceipt.class), c("state", ManifestState.JobState.class),
                c("recordedAt", Instant.class), c("operations", list(ManifestState.OperationState.class)), c("jobFailures", list(ManifestFailure.class)));
        freezeRecord(ManifestState.OperationState.class, c("operationId", String.class), c("observation", CheckpointRecord.Observation.class), c("failures", list(ManifestFailure.class)));
        freezePublicRecords();
    }

    private static void freezePlanAndCheckpoint() throws Exception {
        freezeRecord(ProvenanceManifest.class, c("schemaVersion", int.class), c("jobId", UUID.class), c("createdAt", Instant.class),
                c("sources", list(SourceFileRecord.class)), c("operations", list(ManifestOperation.class)));
        freezeRecord(CheckpointRecord.class, c("schemaVersion", int.class), c("jobId", UUID.class), c("sequence", long.class),
                c("previousRecordSha256", String.class), c("recordedAt", Instant.class), c("kind", CheckpointRecord.Kind.class),
                c("operationId", optional(String.class)), c("observation", optional(CheckpointRecord.Observation.class)),
                c("jobState", optional(ManifestState.JobState.class)), c("failures", list(ManifestFailure.class)));
        freezeRecord(CheckpointRecord.Observation.class, c("state", CheckpointRecord.State.class), c("startedAt", optional(Instant.class)),
                c("finishedAt", optional(Instant.class)), c("outputDigest", optional(ContentDigest.class)), c("matchedExpectedSourceCount", int.class),
                c("disposition", optional(CheckpointRecord.Disposition.class)), c("processingEvidence", optional(ProcessingEvidence.class)));
    }

    private static void freezePublicRecords() throws Exception {
        freezeRecord(PublicJobReport.class, c("schemaVersion", int.class), c("state", ManifestState.JobState.class), c("sourceCount", long.class),
                c("operationCounts", list(PublicJobReport.OperationCount.class)), c("failureCounts", list(PublicJobReport.FailureCount.class)));
        freezeRecord(PublicJobReport.OperationCount.class, c("kind", ManifestOperation.Kind.class), c("state", CheckpointRecord.State.class), c("count", long.class));
        freezeRecord(PublicJobReport.FailureCount.class, c("namespace", PublicJobReport.FailureNamespace.class), c("code", String.class), c("count", long.class));
    }

    private static void freezeEnums() throws Exception { for (var entry : ENUMS.entrySet()) freezeEnum(entry.getKey(), entry.getValue()); }

    private static void freezeServicesAndPorts() throws Exception {
        for (var type : List.of(ManifestReplay.class, ManifestProjection.class, ProvenancePersistenceException.class)) {
            assertTrue(Modifier.isPublic(type.getModifiers()) && Modifier.isFinal(type.getModifiers()));
            assertFalse(type.isRecord()); assertFalse(type.isEnum()); assertEquals(0, type.getGenericInterfaces().length);
        }
        freezeConstructor(ManifestReplay.class, value(ProvenanceManifest.class), value(String.class));
        freezeMethods(ManifestReplay.class, method("validateNext", Modifier.PUBLIC | Modifier.SYNCHRONIZED, value(void.class), value(CheckpointRecord.class)),
                method("accept", Modifier.PUBLIC | Modifier.SYNCHRONIZED, value(ManifestReceipt.class), value(CheckpointRecord.class), value(String.class)),
                method("current", Modifier.PUBLIC | Modifier.SYNCHRONIZED, value(ManifestState.class)));
        assertEquals(0, ManifestProjection.class.getConstructors().length);
        assertEquals(1, ManifestProjection.class.getDeclaredConstructors().length);
        var projectionConstructor = ManifestProjection.class.getDeclaredConstructors()[0];
        assertTrue(Modifier.isPrivate(projectionConstructor.getModifiers())); assertEquals(0, projectionConstructor.getParameterCount());
        freezeMethods(ManifestProjection.class,
                method("reconstruction", Modifier.PUBLIC | Modifier.STATIC, value(ProcessingEvidence.class), value(DicomProcessingResult.class)),
                method("conversion", Modifier.PUBLIC | Modifier.STATIC, value(ProcessingEvidence.class), value(DicomToNiftiResult.class)),
                method("publicReport", Modifier.PUBLIC | Modifier.STATIC, value(PublicJobReport.class), value(ManifestState.class)));
        freezeConstructor(ProvenancePersistenceException.class, list(ManifestFailure.class), value(ProvenancePersistenceException.PublicationOutcome.class), optional(ManifestReceipt.class));
        freezeMethods(ProvenancePersistenceException.class, method("failures", Modifier.PUBLIC, list(ManifestFailure.class)),
                method("outcome", Modifier.PUBLIC, value(ProvenancePersistenceException.PublicationOutcome.class)), method("knownPublication", Modifier.PUBLIC, optional(ManifestReceipt.class)));
        assertTrue(ManifestStore.class.isInterface()); assertTrue(PublicReportWriter.class.isInterface());
        assertEquals(List.of(AutoCloseable.class), Arrays.asList(ManifestStore.class.getGenericInterfaces()));
        assertEquals(0, PublicReportWriter.class.getGenericInterfaces().length);
        assertEquals(0, ManifestStore.class.getConstructors().length); assertEquals(0, PublicReportWriter.class.getConstructors().length);
        freezeMethods(ManifestStore.class, method("create", Modifier.PUBLIC | Modifier.ABSTRACT, value(ManifestReceipt.class), value(ProvenanceManifest.class)),
                method("append", Modifier.PUBLIC | Modifier.ABSTRACT, value(ManifestReceipt.class), value(CheckpointRecord.class), value(ManifestReceipt.class)),
                method("replay", Modifier.PUBLIC | Modifier.ABSTRACT, optional(ManifestState.class), optional(ManifestReceipt.class)),
                method("close", Modifier.PUBLIC | Modifier.ABSTRACT, value(void.class)));
        freezeMethods(PublicReportWriter.class, method("write", Modifier.PUBLIC | Modifier.ABSTRACT, value(void.class), value(PublicJobReport.class), value(OutputTarget.class)));
    }

    @Test void amendmentAddsOnlyFinalControlledCodeAndNeverPersistenceInterruption() throws Exception {
        var codes = ManifestFailure.Code.values();
        assertEquals(19, codes.length); assertEquals("INTERRUPTED", codes[18].name());
        assertEquals(17, ManifestFailure.Code.VERIFICATION_FAILED.ordinal());
        freezeEnum(ManifestFailure.Code.class, ENUMS.get(ManifestFailure.Code.class));
        freezeEnum(ManifestFailure.Phase.class, ENUMS.get(ManifestFailure.Phase.class));
        freezeRecords(); freezeServicesAndPorts();
        for (var phase : List.of(ManifestFailure.Phase.HASHING, ManifestFailure.Phase.EXECUTION)) {
            var fact = new ManifestFailure(phase, ManifestFailure.Code.valueOf("INTERRUPTED"));
            assertThrowsExactly(IllegalArgumentException.class, () -> new ProvenancePersistenceException(List.of(fact),
                    ProvenancePersistenceException.PublicationOutcome.NOT_PUBLISHED, Optional.empty()));
        }
        assertThrowsExactly(IllegalArgumentException.class, () -> new ManifestFailure(
                ManifestFailure.Phase.PERSISTENCE, ManifestFailure.Code.valueOf("INTERRUPTED")));
    }

    private static void freezeRecord(Class<?> type, Component... expected) throws Exception {
        assertTrue(type.isRecord(), type.getName()); assertTrue(Modifier.isPublic(type.getModifiers()) && Modifier.isFinal(type.getModifiers()));
        assertEquals(Record.class, type.getSuperclass()); assertEquals(0, type.getGenericInterfaces().length);
        var components = type.getRecordComponents(); assertEquals(expected.length, components.length, type.getName());
        var methods = new HashSet<Method>();
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i].name, components[i].getName()); assertShape(components[i].getGenericType(), expected[i].shape);
            assertEquals(expected[i].shape.raw, components[i].getType());
            Method accessor = components[i].getAccessor(); assertEquals(Modifier.PUBLIC, accessor.getModifiers());
            assertShape(accessor.getGenericReturnType(), expected[i].shape); methods.add(accessor);
        }
        methods.add(type.getDeclaredMethod("equals", Object.class)); methods.add(type.getDeclaredMethod("hashCode")); methods.add(type.getDeclaredMethod("toString"));
        assertEquals(methods, new HashSet<>(publicDeclarations(type)), type.getName());
        assertEquals(boolean.class, type.getDeclaredMethod("equals", Object.class).getReturnType());
        assertEquals(int.class, type.getDeclaredMethod("hashCode").getReturnType());
        assertEquals(String.class, type.getDeclaredMethod("toString").getReturnType());
        freezeConstructor(type, Arrays.stream(expected).map(Component::shape).toArray(Shape[]::new));
        assertEquals(0, type.getFields().length);
        for (var field : type.getDeclaredFields()) if (!Modifier.isStatic(field.getModifiers()))
            assertEquals(Modifier.PRIVATE | Modifier.FINAL, field.getModifiers());
    }

    private static void freezeEnum(Class<?> type, List<String> names) throws Exception {
        assertTrue(type.isEnum()); assertTrue(Modifier.isPublic(type.getModifiers()) && Modifier.isFinal(type.getModifiers()));
        assertEquals(names, Arrays.stream(type.getEnumConstants()).map(v -> ((Enum<?>) v).name()).toList());
        assertEquals(0, type.getConstructors().length); assertEquals(0, type.getGenericInterfaces().length);
        assertShape(type.getGenericSuperclass(), new Shape(Enum.class, List.of(value(type))));
        var values = type.getDeclaredMethod("values"); var valueOf = type.getDeclaredMethod("valueOf", String.class);
        assertEquals(Modifier.PUBLIC | Modifier.STATIC, values.getModifiers()); assertEquals(Modifier.PUBLIC | Modifier.STATIC, valueOf.getModifiers());
        assertEquals(type.arrayType(), values.getReturnType()); assertEquals(type, valueOf.getReturnType());
        assertEquals(Set.of(values, valueOf), new HashSet<>(publicDeclarations(type)));
        assertEquals(names.size(), type.getFields().length);
    }

    private static void freezeConstructor(Class<?> type, Shape... parameters) throws Exception {
        var actual = type.getConstructor(Arrays.stream(parameters).map(Shape::raw).toArray(Class<?>[]::new));
        assertEquals(List.of(actual), Arrays.asList(type.getConstructors()), type.getName());
        assertEquals(Modifier.PUBLIC, actual.getModifiers()); assertFalse(actual.isVarArgs()); assertEquals(0, actual.getTypeParameters().length);
        assertEquals(0, actual.getExceptionTypes().length);
        for (int i = 0; i < parameters.length; i++) assertShape(actual.getGenericParameterTypes()[i], parameters[i]);
    }

    private static void freezeMethods(Class<?> type, MethodShape... expected) throws Exception {
        var methods = new HashSet<Method>();
        for (var signature : expected) {
            var method = type.getDeclaredMethod(signature.name, Arrays.stream(signature.parameters).map(Shape::raw).toArray(Class<?>[]::new));
            assertEquals(signature.modifiers, method.getModifiers()); assertShape(method.getGenericReturnType(), signature.result);
            assertEquals(0, method.getTypeParameters().length); assertFalse(method.isVarArgs()); assertEquals(0, method.getExceptionTypes().length);
            for (int i = 0; i < signature.parameters.length; i++) assertShape(method.getGenericParameterTypes()[i], signature.parameters[i]);
            methods.add(method);
        }
        assertEquals(methods, new HashSet<>(publicDeclarations(type)), type.getName());
    }

    private static void approved(Type type, boolean language) {
        if (type == null) return;
        if (type instanceof ParameterizedType parameterized) {
            approved(parameterized.getRawType(), language); assertNull(parameterized.getOwnerType());
            for (var argument : parameterized.getActualTypeArguments()) approved(argument, language);
            return;
        }
        assertInstanceOf(Class.class, type, "No wildcard/type-variable/erased implementation contract");
        var raw = (Class<?>) type;
        if (raw.isArray()) {
            assertTrue(language && raw.getComponentType().isEnum() && ENUMS.containsKey(raw.getComponentType()), "Unexpected array API"); return;
        }
        var allowed = new HashSet<>(boundary());
        allowed.addAll(List.of(String.class, List.class, Optional.class, UUID.class, Instant.class, FormatAssessment.class,
                AffineMatrix4.class, IntensityTransform.class, ScalarType.class, DicomProcessingError.class, DicomToNiftiError.class,
                DicomProcessingResult.class, DicomToNiftiResult.class, OutputTarget.class));
        if (language) allowed.addAll(List.of(Object.class, Record.class, Enum.class, AutoCloseable.class));
        assertTrue(raw.isPrimitive() || allowed.contains(raw), "Unapproved F1 boundary type: " + raw.getName());
    }

    private static void assertShape(Type actual, Shape expected) {
        if (expected.arguments.isEmpty()) { assertEquals(expected.raw, actual); return; }
        assertInstanceOf(ParameterizedType.class, actual); var generic = (ParameterizedType) actual;
        assertEquals(expected.raw, generic.getRawType()); assertNull(generic.getOwnerType());
        var arguments = generic.getActualTypeArguments(); assertEquals(expected.arguments.size(), arguments.length);
        for (int i = 0; i < arguments.length; i++) assertShape(arguments[i], expected.arguments.get(i));
    }

    private static boolean contains(Type actual, Class<?> searched) {
        if (actual instanceof Class<?> raw) return raw == searched || raw.isArray() && contains(raw.getComponentType(), searched);
        if (actual instanceof ParameterizedType generic) return contains(generic.getRawType(), searched)
                || Arrays.stream(generic.getActualTypeArguments()).anyMatch(t -> contains(t, searched));
        fail("Unexpected generic boundary shape: " + actual); return false;
    }
    private static void assertOnly(Type actual, Set<Class<?>> allowed) {
        if (actual instanceof Class<?> raw) { assertTrue(allowed.contains(raw), raw.getName()); return; }
        assertInstanceOf(ParameterizedType.class, actual); var generic = (ParameterizedType) actual;
        assertOnly(generic.getRawType(), allowed);
        for (var argument : generic.getActualTypeArguments()) assertOnly(argument, allowed);
    }
    private static List<Method> publicDeclarations(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers())).toList();
    }
    private static boolean languageMethod(Class<?> owner, Method method) {
        return owner.isRecord() && Set.of("equals", "hashCode", "toString").contains(method.getName())
                || owner.isEnum() && Set.of("values", "valueOf").contains(method.getName());
    }
    private static List<Class<?>> boundary() {
        var all = new HashSet<>(TOP_LEVEL); all.addAll(RECORDS); all.addAll(ENUMS.keySet());
        all.add(ManifestStore.class); all.add(PublicReportWriter.class); return List.copyOf(all);
    }
    private static void addTopLevel(Set<Class<?>> found, String leaf, ClassLoader loader) throws Exception {
        if (!leaf.endsWith(".class") || leaf.equals("package-info.class") || leaf.equals("module-info.class")) return;
        var type = Class.forName(PACKAGE + "." + leaf.substring(0, leaf.length() - 6), false, loader);
        if (type.getEnclosingClass() == null && Modifier.isPublic(type.getModifiers())) found.add(type);
    }
    private record Shape(Class<?> raw, List<Shape> arguments) { }
    private record Component(String name, Shape shape) { }
    private record MethodShape(String name, int modifiers, Shape result, Shape[] parameters) { }
    private static Shape value(Class<?> type) { return new Shape(type, List.of()); }
    private static Shape list(Class<?> argument) { return new Shape(List.class, List.of(value(argument))); }
    private static Shape optional(Class<?> argument) { return new Shape(Optional.class, List.of(value(argument))); }
    private static Component c(String name, Class<?> type) { return c(name, value(type)); }
    private static Component c(String name, Shape shape) { return new Component(name, shape); }
    private static MethodShape method(String name, int modifiers, Shape result, Shape... parameters) { return new MethodShape(name, modifiers, result, parameters); }
}
