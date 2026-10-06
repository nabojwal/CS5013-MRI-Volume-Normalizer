package org.cbihi.mrinormalizer.infrastructure.filesystem;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.cbihi.mrinormalizer.application.dataset.model.FormatAssessment;
import org.cbihi.mrinormalizer.application.port.out.PublicReportWriter;
import org.cbihi.mrinormalizer.application.provenance.ProvenanceRecord;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.State;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Code;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Phase;
import org.cbihi.mrinormalizer.application.provenance.manifest.ProvenancePersistenceException.PublicationOutcome;
import org.cbihi.mrinormalizer.application.validation.ConversionValidationReport;
import org.cbihi.mrinormalizer.domain.model.*;
import org.cbihi.mrinormalizer.infrastructure.filesystem.JsonPublicReportWriter.Event;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** E01/E02: real provider publication, closed public bytes and independent fault boundaries. */
class PublicReportWriterTest {
    @TempDir Path temporary;
    private static final String HASH = "a".repeat(64);
    private static final String ID = "b".repeat(64);
    private static final Instant TIME = Instant.parse("2026-10-06T00:00:00Z");
    private static final LinkOption NOFOLLOW = LinkOption.NOFOLLOW_LINKS;

    // E01 matrix anchor: actual accepted S5A projection, golden wire, actual export.
    @Test void publicGoldenBytesAndTargetsAreLimited() throws Exception {
        var f = fixture();
        var report = projected();
        byte[] expected = golden().getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(expected, new JsonManifestCodec().encode(report));
        writer(f).write(report, target(f));
        assertArrayEquals(expected, Files.readAllBytes(f.target));
        String json = Files.readString(f.target);
        for (var forbidden : List.of("Patient Alice", "PID-123", "1.2.840.77", "Écho", HASH, ID,
                TIME.toString(), "00000000-0000-0000-0000-000000000007", "mrinormalizer-1.0", "5.33.0",
                "runtime-secret.nii", f.source.toString(), f.output.toString(), "jobId", "operationId", "sha256",
                "createdAt", "sizeBytes", "sourceSummary", "conversionFacts", "reference", "metadata", "geometry"))
            assertFalse(json.contains(forbidden), forbidden);
        preserved(f); assertNoTemporary(f);
    }

    @Test void portAndAdapterHaveExactlyFrozenPublicSurfaces() {
        assertTrue(PublicReportWriter.class.isInterface());
        var methods = PublicReportWriter.class.getDeclaredMethods(); assertEquals(1, methods.length);
        assertEquals("write", methods[0].getName()); assertEquals(void.class, methods[0].getReturnType());
        assertArrayEquals(new Class<?>[] {PublicJobReport.class, OutputTarget.class}, methods[0].getParameterTypes());
        assertFalse(methods[0].isDefault()); assertEquals(0, methods[0].getExceptionTypes().length);
        assertTrue(Modifier.isFinal(JsonPublicReportWriter.class.getModifiers()));
        assertEquals(1, JsonPublicReportWriter.class.getConstructors().length);
        assertArrayEquals(new Class<?>[] {Path.class, Path.class, Path.class},
                JsonPublicReportWriter.class.getConstructors()[0].getParameterTypes());
        var exposed = Arrays.stream(JsonPublicReportWriter.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers())).toList();
        assertEquals(1, exposed.size()); assertEquals("write", exposed.get(0).getName());
        assertEquals(void.class, exposed.get(0).getReturnType());
        assertArrayEquals(methods[0].getParameterTypes(), exposed.get(0).getParameterTypes());
    }

    @Test void equalExistingReportIsIdempotentWithoutRewrite() throws Exception {
        var f = fixture(); var writer = writer(f); writer.write(projected(), target(f));
        var before = Files.readAttributes(f.target, BasicFileAttributes.class, NOFOLLOW);
        writer.write(projected(), target(f));
        var after = Files.readAttributes(f.target, BasicFileAttributes.class, NOFOLLOW);
        assertEquals(before.fileKey(), after.fileKey()); assertEquals(before.creationTime(), after.creationTime());
        assertEquals(before.lastModifiedTime(), after.lastModifiedTime());
        assertArrayEquals(golden().getBytes(StandardCharsets.UTF_8), Files.readAllBytes(f.target));
        preserved(f); assertNoTemporary(f);
    }

    @Test void differentExistingBytesAreNeverOverwritten() throws Exception {
        var f = fixture(); Files.writeString(f.target, "pre-existing public bytes");
        var failure = assertThrows(ProvenancePersistenceException.class, () -> writer(f).write(projected(), target(f)));
        facts(failure, PublicationOutcome.NOT_PUBLISHED, Code.OUTPUT_CONFLICT);
        assertEquals("pre-existing public bytes", Files.readString(f.target)); preserved(f); assertNoTemporary(f);
    }

    @Test void forbiddenNamespacesAndMissingParentsFailWithoutDirectoryCreation() throws Exception {
        var f = fixture(); var writer = writer(f);
        for (var path : List.of(f.source.resolve("report.json"), temporary.resolve("outside.json"), f.manifest,
                f.manifest.resolve("public.json"), f.manifest.resolve("nested/report.json"),
                f.output.resolve("plan.json"), f.output.resolve(".lease"), f.output.resolve(".stage-journal.tmp"),
                f.output.resolve("00000000000000000001.json"), f.output.resolve("missing/report.json"))) {
            var error = assertThrows(ProvenancePersistenceException.class,
                    () -> writer.write(projected(), new OutputTarget(path.toString())));
            assertEquals(PublicationOutcome.NOT_PUBLISHED, error.outcome()); neutral(error);
        }
        assertFalse(Files.exists(f.output.resolve("missing"))); preserved(f); assertNoTemporary(f);
    }

    @Test void finalSymlinksIncludingDanglingLinksAreNeverIdempotent() throws Exception {
        var f = fixture(); var destination = temporary.resolve("other.json");
        Files.write(destination, new JsonManifestCodec().encode(projected()));
        Files.createSymbolicLink(f.target, destination);
        var error = assertThrows(ProvenancePersistenceException.class, () -> writer(f).write(projected(), target(f)));
        facts(error, PublicationOutcome.NOT_PUBLISHED, Code.CONTAINMENT_UNPROVEN);
        assertTrue(Files.isSymbolicLink(f.target)); assertArrayEquals(new JsonManifestCodec().encode(projected()), Files.readAllBytes(destination));
        Files.delete(f.target); Files.createSymbolicLink(f.target, temporary.resolve("absent"));
        var dangling = assertThrows(ProvenancePersistenceException.class, () -> writer(f).write(projected(), target(f)));
        facts(dangling, PublicationOutcome.NOT_PUBLISHED, Code.CONTAINMENT_UNPROVEN);
        assertTrue(Files.isSymbolicLink(f.target)); preserved(f);
    }

    @Test void safelyResolvableConfiguredAliasesRemainUsable() throws Exception {
        var f = fixture(); Path sourceAlias = temporary.resolve("source-alias"), outputAlias = temporary.resolve("output-alias");
        Files.createSymbolicLink(sourceAlias, f.source); Files.createSymbolicLink(outputAlias, f.output);
        new JsonPublicReportWriter(sourceAlias, outputAlias, outputAlias.resolve("manifest"))
                .write(projected(), new OutputTarget(outputAlias.resolve("public.json").toString()));
        assertArrayEquals(new JsonManifestCodec().encode(projected()), Files.readAllBytes(f.target)); preserved(f);
    }

    @Test void canonicalParentAliasesCannotEscapeOutputOrEnterManifest() throws Exception {
        var f = fixture(); var writer = writer(f);
        Files.createSymbolicLink(f.output.resolve("source-alias"), f.source);
        Files.createSymbolicLink(f.output.resolve("journal-alias"), f.manifest);
        for (var alias : List.of("source-alias", "journal-alias")) {
            var error = assertThrows(ProvenancePersistenceException.class,
                    () -> writer.write(projected(), new OutputTarget(f.output.resolve(alias + "/report.json").toString())));
            facts(error, PublicationOutcome.NOT_PUBLISHED, Code.CONTAINMENT_UNPROVEN);
        }
        preserved(f);
    }

    @Test void rootsMustBeDisjointAndNamespaceMustBeStrictlyInsideOutput() throws Exception {
        var f = fixture();
        for (var roots : List.of(new Path[] {f.source, f.source, f.source.resolve("manifest")},
                new Path[] {f.source, temporary, f.manifest}, new Path[] {f.source, f.output, f.output},
                new Path[] {f.source, f.output, temporary.resolve("manifest-outside")})) {
            var error = assertThrows(ProvenancePersistenceException.class,
                    () -> new JsonPublicReportWriter(roots[0], roots[1], roots[2]));
            facts(error, PublicationOutcome.NOT_PUBLISHED, Code.CONTAINMENT_UNPROVEN);
        }
        preserved(f);
    }

    @Test void missingManifestNamespaceIsReservedWithoutBeingCreated() throws Exception {
        var f = fixture(); Path reserved = f.output.resolve("reserved-not-created");
        var writer = new JsonPublicReportWriter(f.source, f.output, reserved);
        writer.write(projected(), target(f)); assertFalse(Files.exists(reserved));
        var error = assertThrows(ProvenancePersistenceException.class,
                () -> writer.write(projected(), new OutputTarget(reserved.resolve("public.json").toString())));
        assertEquals(PublicationOutcome.NOT_PUBLISHED, error.outcome()); neutral(error); preserved(f);
    }

    @Test void nullAndMalformedArgumentsHaveFixedNonidentifyingErrors() throws Exception {
        var f = fixture(); var writer = writer(f);
        for (Runnable misuse : List.<Runnable>of(() -> new JsonPublicReportWriter(null, f.output, f.manifest),
                () -> new JsonPublicReportWriter(f.source, null, f.manifest),
                () -> new JsonPublicReportWriter(f.source, f.output, null), () -> writer.write(null, target(f)),
                () -> writer.write(projected(), null), () -> writer.write(projected(), new OutputTarget(null)))) {
            var failure = assertThrowsExactly(IllegalArgumentException.class, misuse::run);
            assertEquals("Invalid public report arguments", failure.getMessage()); assertNull(failure.getCause());
        }
        for (var reference : List.of("relative/report.json", "Patient Alice\u0000PID-123")) {
            var failure = assertThrows(ProvenancePersistenceException.class, () -> writer.write(projected(), new OutputTarget(reference)));
            facts(failure, PublicationOutcome.NOT_PUBLISHED, Code.INVALID_REFERENCE);
        }
        preserved(f);
    }

    @Test void privateStageAndFinalDoNotChangeParentAccess() throws Exception {
        var f = fixture(); var view = Files.getFileAttributeView(f.output, PosixFileAttributeView.class, NOFOLLOW);
        var before = view == null ? Files.getFileAttributeView(f.output, java.nio.file.attribute.AclFileAttributeView.class, NOFOLLOW).getAcl()
                : view.readAttributes().permissions();
        writer(f).write(projected(), target(f));
        var after = view == null ? Files.getFileAttributeView(f.output, java.nio.file.attribute.AclFileAttributeView.class, NOFOLLOW).getAcl()
                : view.readAttributes().permissions();
        assertEquals(before, after);
        if (view != null) assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(f.target, NOFOLLOW));
        else for (var entry : Files.getFileAttributeView(f.target, java.nio.file.attribute.AclFileAttributeView.class, NOFOLLOW).getAcl())
            if (entry.type() == java.nio.file.attribute.AclEntryType.ALLOW) assertEquals(Files.getOwner(f.target, NOFOLLOW), entry.principal());
        preserved(f);
    }

    @Test void realProviderProvesHardLinkIdentityIncludingWindowsNullKeys() throws Exception {
        var f = fixture(); var stage = new AtomicReference<Path>();
        new JsonPublicReportWriter(f.source, f.output, f.manifest, (event, path) -> {
            if (event == Event.AFTER_STAGE) stage.set(path);
            if (event == Event.AFTER_PUBLISH) {
                assertTrue(Files.isSameFile(stage.get(), path));
                var a = Files.readAttributes(stage.get(), BasicFileAttributes.class, NOFOLLOW);
                var b = Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW);
                if (path.getFileSystem().provider().getClass().getName().equals("sun.nio.fs.WindowsFileSystemProvider")) {
                    assertNull(a.fileKey()); assertNull(b.fileKey()); assertEquals("NTFS", Files.getFileStore(path).type().toUpperCase(java.util.Locale.ROOT));
                } else { assertNotNull(a.fileKey()); assertEquals(a.fileKey(), b.fileKey()); }
            }
        }).write(projected(), target(f));
        assertFalse(Files.exists(stage.get(), NOFOLLOW)); preserved(f); assertNoTemporary(f);
    }

    // E02 matrix anchor: independent failures, actual sentinels after every attempt.
    @Test void exportFailuresPreserveRestrictedEvidence() throws Exception {
        for (var boundary : List.of(Event.BEFORE_STAGE, Event.AFTER_STAGE, Event.WRITE, Event.BEFORE_FORCE,
                Event.AFTER_FORCE, Event.STAGE_CLOSE, Event.BEFORE_PUBLISH)) {
            var f = fixture();
            var writer = faulting(f, boundary);
            var failure = assertThrows(ProvenancePersistenceException.class, () -> writer.write(projected(), target(f)));
            facts(failure, PublicationOutcome.NOT_PUBLISHED, boundary == Event.STAGE_CLOSE ? Code.CLEANUP_FAILED : Code.WRITE_FAILED);
            assertFalse(Files.exists(f.target, NOFOLLOW)); preserved(f); assertNoTemporary(f);
        }
    }

    @Test void lostAcknowledgmentKeepsKnownPublishedBytesWithoutReceipt() throws Exception {
        var f = fixture(); var error = assertThrows(ProvenancePersistenceException.class,
                () -> faulting(f, Event.AFTER_PUBLISH).write(projected(), target(f)));
        facts(error, PublicationOutcome.PUBLISHED, Code.WRITE_FAILED);
        assertArrayEquals(new JsonManifestCodec().encode(projected()), Files.readAllBytes(f.target));
        writer(f).write(projected(), target(f)); preserved(f); assertNoTemporary(f);
    }

    @Test void classificationFailureRetainsEvidenceWithUnknownOutcome() throws Exception {
        var f = fixture(); var writer = new JsonPublicReportWriter(f.source, f.output, f.manifest, (event, path) -> {
            if (event == Event.BEFORE_PUBLISH || event == Event.CLASSIFY) throw new IOException("Patient Alice " + path);
        });
        var error = assertThrows(ProvenancePersistenceException.class, () -> writer.write(projected(), target(f)));
        facts(error, PublicationOutcome.UNKNOWN, Code.WRITE_FAILED, Code.RECOVERY_REQUIRED, Code.CLEANUP_FAILED);
        assertFalse(Files.exists(f.target, NOFOLLOW)); assertEquals(1, temporaryArtifacts(f).size()); preserved(f);
    }

    @Test void publishedCleanupFailureNeverRollsBackFinal() throws Exception {
        var f = fixture(); var error = assertThrows(ProvenancePersistenceException.class,
                () -> faulting(f, Event.CLEANUP).write(projected(), target(f)));
        facts(error, PublicationOutcome.PUBLISHED, Code.CLEANUP_FAILED);
        assertArrayEquals(new JsonManifestCodec().encode(projected()), Files.readAllBytes(f.target));
        assertEquals(1, temporaryArtifacts(f).size()); preserved(f);
    }

    @Test void primaryAndCleanupFailuresAreBothRetained() throws Exception {
        var f = fixture(); var writer = new JsonPublicReportWriter(f.source, f.output, f.manifest, (event, path) -> {
            if (event == Event.WRITE || event == Event.CLEANUP) throw new IOException("PID-123 " + path);
        });
        var error = assertThrows(ProvenancePersistenceException.class, () -> writer.write(projected(), target(f)));
        facts(error, PublicationOutcome.NOT_PUBLISHED, Code.WRITE_FAILED, Code.CLEANUP_FAILED);
        assertFalse(Files.exists(f.target, NOFOLLOW)); assertEquals(1, temporaryArtifacts(f).size()); preserved(f);
    }

    @Test void substitutedStageAfterUnpinIsRetainedRatherThanDeleted() throws Exception {
        var f = fixture(); Path replacement = f.output.resolve("replacement.tmp"); Files.writeString(replacement, "substitute");
        if (replacement.getFileSystem().provider().getClass().getName().equals("sun.nio.fs.WindowsFileSystemProvider"))
            Files.setAttribute(replacement, "basic:creationTime", FileTime.fromMillis(1234567), NOFOLLOW);
        var saved = new AtomicReference<Path>();
        var writer = new JsonPublicReportWriter(f.source, f.output, f.manifest, (event, path) -> {
            if (event == Event.AFTER_UNPIN) {
                Files.delete(path); Files.move(replacement, path); saved.set(path);
            }
        });
        var error = assertThrows(ProvenancePersistenceException.class, () -> writer.write(projected(), target(f)));
        facts(error, PublicationOutcome.PUBLISHED, Code.CLEANUP_FAILED);
        assertEquals("substitute", Files.readString(saved.get()));
        assertArrayEquals(new JsonManifestCodec().encode(projected()), Files.readAllBytes(f.target)); preserved(f);
    }

    @Test void competingPublicationWithDifferentBytesNeverReplacesWinner() throws Exception {
        var f = fixture(); var writer = new JsonPublicReportWriter(f.source, f.output, f.manifest, (event, path) -> {
            if (event == Event.BEFORE_PUBLISH) Files.writeString(path, "competing winner");
        });
        var error = assertThrows(ProvenancePersistenceException.class, () -> writer.write(projected(), target(f)));
        facts(error, PublicationOutcome.NOT_PUBLISHED, Code.OUTPUT_CONFLICT);
        assertEquals("competing winner", Files.readString(f.target)); preserved(f); assertNoTemporary(f);
    }

    @Test void competingIdenticalPublicationIsIdempotent() throws Exception {
        var f = fixture(); var writer = new JsonPublicReportWriter(f.source, f.output, f.manifest, (event, path) -> {
            if (event == Event.BEFORE_PUBLISH) Files.write(path, new JsonManifestCodec().encode(projected()));
        });
        writer.write(projected(), target(f)); assertArrayEquals(new JsonManifestCodec().encode(projected()), Files.readAllBytes(f.target));
        preserved(f); assertNoTemporary(f);
    }

    @Test void finalSubstitutionCannotBeClassifiedAsSuccessfulPublication() throws Exception {
        var f = fixture(); var writer = new JsonPublicReportWriter(f.source, f.output, f.manifest, (event, path) -> {
            if (event == Event.BEFORE_PUBLISH) { Files.createSymbolicLink(path, f.source.resolve("image.dcm")); throw new IOException("secret"); }
        });
        var error = assertThrows(ProvenancePersistenceException.class, () -> writer.write(projected(), target(f)));
        facts(error, PublicationOutcome.UNKNOWN, Code.WRITE_FAILED, Code.RECOVERY_REQUIRED, Code.CLEANUP_FAILED);
        assertTrue(Files.isSymbolicLink(f.target)); assertEquals(1, temporaryArtifacts(f).size()); preserved(f);
    }

    @Test void unavailableQualificationFailsClosedWithoutFallback() throws Exception {
        var f = fixture(); var writer = new JsonPublicReportWriter(f.source, f.output, f.manifest, (event, path) -> {
            if (event == Event.QUALIFY) throw new UnsupportedOperationException("provider path " + path);
        });
        var error = assertThrows(ProvenancePersistenceException.class, () -> writer.write(projected(), target(f)));
        facts(error, PublicationOutcome.NOT_PUBLISHED, Code.PUBLICATION_UNAVAILABLE);
        assertFalse(Files.exists(f.target)); preserved(f); assertNoTemporary(f);
    }

    @Test void probeCleanupFailureIsVisibleAndNeverTouchesJournal() throws Exception {
        var f = fixture(); var writer = new JsonPublicReportWriter(f.source, f.output, f.manifest, (event, path) -> {
            if (event == Event.PROBE_CLEANUP) throw new IOException("probe private " + path);
        });
        var error = assertThrows(ProvenancePersistenceException.class, () -> writer.write(projected(), target(f)));
        facts(error, PublicationOutcome.NOT_PUBLISHED, Code.CLEANUP_FAILED);
        assertFalse(Files.exists(f.target)); assertFalse(temporaryArtifacts(f).isEmpty()); preserved(f);
    }

    @Test void directorySubstitutionBeforeStageFailsClosed() throws Exception {
        var f = fixture(); Path parent = Files.createDirectory(f.output.resolve("reports")); Path moved = f.output.resolve("saved-reports");
        var writer = new JsonPublicReportWriter(f.source, f.output, f.manifest, (event, path) -> {
            if (event == Event.BEFORE_STAGE) {
                Files.move(parent, moved); Files.createSymbolicLink(parent, f.manifest);
            }
        });
        var error = assertThrows(ProvenancePersistenceException.class,
                () -> writer.write(projected(), new OutputTarget(parent.resolve("public.json").toString())));
        facts(error, PublicationOutcome.UNKNOWN, Code.CONTAINMENT_UNPROVEN, Code.RECOVERY_REQUIRED);
        assertFalse(Files.exists(f.manifest.resolve("public.json"))); assertFalse(Files.exists(moved.resolve("public.json"))); preserved(f);
    }

    private JsonPublicReportWriter writer(Fixture f) { return new JsonPublicReportWriter(f.source, f.output, f.manifest); }
    private JsonPublicReportWriter faulting(Fixture f, Event boundary) {
        return new JsonPublicReportWriter(f.source, f.output, f.manifest, (event, path) -> {
            if (event == boundary) throw new IOException("Patient Alice PID-123 " + path);
        });
    }
    private static OutputTarget target(Fixture f) { return new OutputTarget(f.target.toString()); }
    private record Fixture(Path source, Path output, Path manifest, Path target, List<Path> sentinels, List<byte[]> bytes) { }
    private Fixture fixture() throws Exception {
        Path base = Files.createTempDirectory(temporary, "case-"); Path source = Files.createDirectory(base.resolve("source"));
        Path output = Files.createDirectory(base.resolve("output")); Path manifest = Files.createDirectory(output.resolve("manifest"));
        Path records = Files.createDirectory(manifest.resolve("records"));
        var sentinels = List.of(source.resolve("image.dcm"), output.resolve("image.nii"), manifest.resolve("plan.json"),
                records.resolve("00000000000000000001.json"), manifest.resolve(".lease"), manifest.resolve(".stage-journal.tmp"));
        var bytes = new ArrayList<byte[]>();
        for (int i = 0; i < sentinels.size(); i++) { byte[] b = ("restricted sentinel " + i + " Patient Alice").getBytes(StandardCharsets.UTF_8); bytes.add(b); Files.write(sentinels.get(i), b); }
        return new Fixture(source, output, manifest, output.resolve("public.json"), sentinels, bytes);
    }
    private static void preserved(Fixture f) throws Exception {
        for (int i = 0; i < f.sentinels.size(); i++) assertArrayEquals(f.bytes.get(i), Files.readAllBytes(f.sentinels.get(i)));
        try (var entries = Files.walk(f.manifest)) {
            assertEquals(6, entries.count()); // namespace, records and four restricted sentinel entries
        }
    }
    private static List<Path> temporaryArtifacts(Fixture f) throws Exception {
        try (var entries = Files.list(f.output)) { return entries.filter(p -> p.getFileName().toString().startsWith(".public-")).toList(); }
    }
    private static void assertNoTemporary(Fixture f) throws Exception { assertTrue(temporaryArtifacts(f).isEmpty()); }
    private static void neutral(ProvenancePersistenceException error) {
        assertEquals("Provenance persistence failed", error.getMessage()); assertNull(error.getCause());
        assertEquals(0, error.getStackTrace().length); assertEquals(0, error.getSuppressed().length);
        assertTrue(error.knownPublication().isEmpty());
        assertTrue(error.failures().stream().allMatch(f -> f.phase() == Phase.PERSISTENCE));
    }
    private static void facts(ProvenancePersistenceException error, PublicationOutcome outcome, Code... codes) {
        neutral(error); assertEquals(outcome, error.outcome());
        assertEquals(java.util.Set.of(codes), new java.util.HashSet<>(error.failures().stream().map(ManifestFailure::code).toList()));
    }
    private static PublicJobReport projected() {
        UUID job = new UUID(0, 7); var digest = new ContentDigest(12, HASH);
        var source = new SourceFileRecord(new RelativePath(RelativePath.Root.SOURCE, "Patient Alice/PID-123/1.2.840.77/Écho.dcm"),
                Optional.of(digest), FormatAssessment.fromDetection(DetectionResult.identified(DetectionOutcome.DICOM)), List.of());
        var operation = new ManifestOperation(ID, ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI, List.of(source.source()),
                new RelativePath(RelativePath.Root.OUTPUT, "Patient Alice/PID-123/Écho.nii"));
        var plan = new ProvenanceManifest(1, job, TIME, List.of(source), List.of(operation));
        var runtime = new OutputTarget("C:/Patient Alice/PID-123/runtime-secret.nii");
        var provenance = new ProvenanceRecord(HASH, "mrinormalizer-1.0", "5.33.0", TIME.plusSeconds(2), true, 5, 3,
                "Patient Alice geometry", "PID-123 pixels", List.of());
        var validation = new ConversionValidationReport(runtime, 2, 3, 4, ScalarType.INT16, 24, 1.5, 2.5, 3.5,
                new AffineMatrix4(new double[][] {{1,0,0,11},{0,1,0,-17},{0,0,1,23},{0,0,0,1}}),
                new IntensityTransform(true, -2, 7), true, false, false, false);
        var evidence = ManifestProjection.conversion(org.cbihi.mrinormalizer.application.result.DicomToNiftiResult.success(runtime, provenance, validation));
        var observed = new CheckpointRecord.Observation(State.WRITTEN_UNVERIFIED, Optional.of(TIME.plusSeconds(1)),
                Optional.of(TIME.plusSeconds(2)), Optional.of(digest), 1, Optional.empty(), Optional.of(evidence));
        var state = new ManifestState(plan, new ManifestReceipt(job, 1, HASH, HASH), ManifestState.JobState.RUNNING,
                TIME.plusSeconds(3), List.of(new ManifestState.OperationState(ID, observed, List.of())), List.of());
        return ManifestProjection.publicReport(state);
    }
    private static String golden() {
        String[] states = {"NOT_STARTED", "IN_PROGRESS", "COMPLETED", "IDENTICAL_EXISTING", "WRITTEN_UNVERIFIED", "SKIPPED_POLICY", "BLOCKED", "FAILED", "RECOVERY_REQUIRED"};
        var entries = new ArrayList<String>();
        for (String kind : List.of("COPY", "CONVERT_DICOM_TO_NIFTI")) for (String state : states)
            entries.add("{\"kind\":\"" + kind + "\",\"state\":\"" + state + "\",\"count\":" + (kind.equals("CONVERT_DICOM_TO_NIFTI") && state.equals("WRITTEN_UNVERIFIED") ? 1 : 0) + "}");
        return "{\"schema\":\"org.cbihi.mrinormalizer.public-job-report\",\"schemaVersion\":1,\"state\":\"RUNNING\",\"sourceCount\":1,\"operationCounts\":["
                + String.join(",", entries) + "],\"failureCounts\":[]}\n";
    }
}
