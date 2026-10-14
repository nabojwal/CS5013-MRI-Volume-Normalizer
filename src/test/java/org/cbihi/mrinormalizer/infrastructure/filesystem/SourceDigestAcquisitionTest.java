package org.cbihi.mrinormalizer.infrastructure.filesystem;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.cbihi.mrinormalizer.domain.model.*;
import org.cbihi.mrinormalizer.application.dataset.copy.*;
import org.cbihi.mrinormalizer.application.dataset.dicom.*;
import org.cbihi.mrinormalizer.application.dataset.model.*;
import org.cbihi.mrinormalizer.application.dataset.organization.*;
import org.cbihi.mrinormalizer.application.port.out.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.application.service.*;
import org.cbihi.mrinormalizer.infrastructure.detection.*;
import org.cbihi.mrinormalizer.infrastructure.dicom.Dcm4cheMetadataReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Native SOURCE observations use accepted M5/F2/F3/F4, never a replacement recognizer. */
public class SourceDigestAcquisitionTest {
    @TempDir Path temporary;
    public static final Instant TIME = Instant.parse("2026-10-10T00:00:00Z");
    public static final CopyLimits LIMITS = new CopyLimits(8_000_000, 300_000_000, 8192, 20);
    public static final InventoryLimits INVENTORY_LIMITS = new InventoryLimits(1000, 20, 20);
    public static final DicomMetadataLimits METADATA_LIMITS = new DicomMetadataLimits(100000, 8);
    public static FormatDetectionService detector() {
        return new FormatDetectionService(List.of(new DicomFormatProbe(), new NiftiFormatProbe()));
    }
    public static byte[] nifti(int size) {
        var bytes = new byte[Math.max(352, size)]; var b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0, 348); bytes[344] = 'n'; bytes[345] = '+'; bytes[346] = '1';
        for (int i = 352; i < bytes.length; i++) bytes[i] = (byte)(i * 37);
        return bytes;
    }
    public static ContentDigest digest(byte[] bytes) throws Exception {
        return new ContentDigest(bytes.length, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }
    public static byte[] dicom() throws Exception {
        var attrs = new org.dcm4che3.data.Attributes();
        attrs.setString(org.dcm4che3.data.Tag.StudyInstanceUID, org.dcm4che3.data.VR.UI, "1.2.3");
        attrs.setString(org.dcm4che3.data.Tag.SeriesInstanceUID, org.dcm4che3.data.VR.UI, "1.2.4");
        attrs.setString(org.dcm4che3.data.Tag.SOPInstanceUID, org.dcm4che3.data.VR.UI, "1.2.5");
        attrs.setString(org.dcm4che3.data.Tag.SOPClassUID, org.dcm4che3.data.VR.UI, org.dcm4che3.data.UID.MRImageStorage);
        attrs.setString(org.dcm4che3.data.Tag.Modality, org.dcm4che3.data.VR.CS, "MR");
        attrs.setString(org.dcm4che3.data.Tag.ImageType, org.dcm4che3.data.VR.CS, "ORIGINAL", "PRIMARY");
        attrs.setString(org.dcm4che3.data.Tag.FrameOfReferenceUID, org.dcm4che3.data.VR.UI, "1.2.6");
        attrs.setInt(org.dcm4che3.data.Tag.Rows, org.dcm4che3.data.VR.US, 2); attrs.setInt(org.dcm4che3.data.Tag.Columns, org.dcm4che3.data.VR.US, 2);
        attrs.setBytes(org.dcm4che3.data.Tag.PixelData, org.dcm4che3.data.VR.OW, new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        var bytes = new ByteArrayOutputStream();
        try (var out = new org.dcm4che3.io.DicomOutputStream(bytes, org.dcm4che3.data.UID.ExplicitVRLittleEndian)) {
            out.writeDataset(attrs.createFileMetaInformation(org.dcm4che3.data.UID.ExplicitVRLittleEndian), attrs);
        }
        return bytes.toByteArray();
    }
    public static Path privateDirectory(Path path) throws Exception {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
            return Files.createDirectory(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        var owner = Files.getOwner(path.getParent());
        var acl = List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build());
        return Files.createDirectory(path, new FileAttribute<List<AclEntry>>() {
            public String name() { return "acl:acl"; } public List<AclEntry> value() { return acl; }
        });
    }
    public static void privateFile(Path path, byte[] bytes) throws Exception {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        } else {
            var owner = Files.getOwner(path.getParent()); var acl = List.of(AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW).setPrincipal(owner).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build());
            Files.createFile(path, new FileAttribute<List<AclEntry>>() {
                public String name() { return "acl:acl"; } public List<AclEntry> value() { return acl; }
            });
        }
        Files.write(path, bytes);
    }
    public static final class Fixture {
        public final Path source, output, namespace;
        public DatasetInventory inventory;
        public DicomSeriesDiscovery discovery;
        public Fixture(Path base) throws Exception {
            source = privateDirectory(base.resolve("source")); output = privateDirectory(base.resolve("output"));
            namespace = output.resolve("journal"); privateFile(source.resolve("synthetic.nii"), nifti(4096)); observe();
        }
        public void observe() {
            inventory = new NioDatasetScanner(source, output, INVENTORY_LIMITS, detector()).scan();
            discovery = inventory.complete() ? new DicomSeriesDiscoveryService().discover(
                    new DicomMetadataInspectionService(new Dcm4cheMetadataReader(source, METADATA_LIMITS)).inspect(inventory))
                    : new DicomSeriesDiscovery(List.of(), List.of());
        }
        public NioVerifiedCopyAccess access() {
            return new NioVerifiedCopyAccess(source, output, namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector());
        }
        public NioVerifiedCopyAccess access(CopyLimits limits) {
            return new NioVerifiedCopyAccess(source, output, namespace, limits, INVENTORY_LIMITS, METADATA_LIMITS, detector());
        }
        public OrganizationPlan plan(SourceDigestBatch batch) {
            return new OrganizationPlanningService().plan(inventory, discovery, batch.suppliedDigests());
        }
        public JsonManifestStore store(UUID job) { return new JsonManifestStore(source, output, namespace, job); }
        public CopyExecutionResult execute(NioVerifiedCopyAccess access, SourceDigestBatch batch, UUID job) {
            try (var service = new CopyExecutionService(access, store(job), Clock.fixed(TIME, ZoneOffset.UTC))) {
                return service.execute(plan(batch), batch, job);
            }
        }
        public Set<String> outputs() throws Exception {
            try (var stream = Files.walk(output)) { return new HashSet<>(stream.filter(p -> !p.equals(output))
                    .map(p -> output.relativize(p).toString().replace('\\', '/')).toList()); }
        }
    }
    public static void code(List<CopyFailure> failures, CopyFailure.Code code) {
        assertTrue(failures.stream().anyMatch(f -> f.code() == code), "Missing controlled failure " + code);
    }
    // T09: independent JCA oracle over fixture bytes and gzip wrapper, not production's streaming implementation.
    @Test void originalStoredNiftiAndGzipBytesAreHashedExactly() throws Exception {
        var f = new Fixture(temporary); var compressed = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(compressed)) { gzip.write(nifti(100000)); }
        privateFile(f.source.resolve("synthetic.nii.gz"), compressed.toByteArray()); f.observe();
        try (var access = f.access()) {
            var batch = new SourceDigestService(access).acquire(f.inventory, f.discovery);
            assertEquals(SourceDigestBatch.Status.COMPLETE, batch.status());
            for (var fact : batch.digests()) assertEquals(digest(Files.readAllBytes(f.source.resolve(fact.source().path()))), fact.digest());
            assertNotEquals(digest(nifti(100000)), batch.digests().stream().filter(d -> d.source().path().endsWith("gz")).findFirst().orElseThrow().digest());
            assertTrue(f.outputs().isEmpty());
        }
    }
    // T10/T20: changed facts cannot be authorized by an equal, manually constructed batch.
    @Test void wrongAssessmentAndCopiedBatchRejectWholeExecution() throws Exception {
        var f = new Fixture(temporary);
        try (var access = f.access()) {
            var b = access.acquire(f.inventory, f.discovery); var p = f.plan(b);
            var copy = new SourceDigestBatch(b.status(), b.inventory(), b.discovery(), b.digests(), b.failures());
            assertFalse(access.revalidate(p, copy).isEmpty()); assertTrue(f.outputs().isEmpty());
            assertThrows(IllegalStateException.class, () -> access.prepare(p.copies().getFirst()));
        }
    }
    // T11/T12: root aliases and discovered links never grant mutation permission.
    @Test void overlapsAndDiscoveredSymbolicLinksFailClosed() throws Exception {
        var f = new Fixture(temporary);
        assertThrows(VerifiedCopyAccess.AccessFailure.class, () -> new NioVerifiedCopyAccess(f.source, f.source,
                f.source.resolve("journal"), LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector()));
        Files.createSymbolicLink(f.source.resolve("link.nii"), f.source.resolve("synthetic.nii")); f.observe();
        try (var access = f.access()) { assertEquals(SourceDigestBatch.Status.BLOCKED, access.acquire(f.inventory, f.discovery).status()); }
        assertTrue(f.outputs().isEmpty());
    }
    // T13/T14/T16/T22: changed bytes, membership and skipped-source facts all invalidate acquired evidence.
    @Test void postPlanChangesAndNewSkippedEntriesBlockWithoutReplanning() throws Exception {
        var f = new Fixture(temporary);
        try (var access = f.access()) {
            var b = access.acquire(f.inventory, f.discovery); var p = f.plan(b);
            privateFile(f.source.resolve("new-text"), new byte[] {1, 2, 3});
            code(access.revalidate(p, b), CopyFailure.Code.DISCOVERY_BINDING_UNPROVEN);
            assertTrue(f.outputs().isEmpty());
        }
    }
    @Test void sameSizeTimestampRestoredByteChangeIsCaughtByFreshHash() throws Exception {
        var f = new Fixture(temporary); var file = f.source.resolve("synthetic.nii");
        try (var access = f.access()) {
            var b = access.acquire(f.inventory, f.discovery); var p = f.plan(b); var time = Files.getLastModifiedTime(file);
            var bytes = Files.readAllBytes(file); bytes[400] ^= 1; Files.write(file, bytes); Files.setLastModifiedTime(file, time);
            code(access.revalidate(p, b), CopyFailure.Code.SOURCE_CHANGED); assertTrue(f.outputs().isEmpty());
        }
    }
    // T15/T17: read budget includes prior phases; bounds do not authorize a digest subset.
    @Test void perFileAndSessionReadBoundsStopBeforeMutation() throws Exception {
        var f = new Fixture(temporary);
        for (var limits : List.of(new CopyLimits(4095, 1000000, 128, 1), new CopyLimits(100000, 4095, 128, 1))) {
            try (var access = f.access(limits)) {
                var b = access.acquire(f.inventory, f.discovery); assertEquals(SourceDigestBatch.Status.BLOCKED, b.status());
                code(b.failures(), CopyFailure.Code.RESOURCE_LIMIT); assertThrows(IllegalStateException.class, b::suppliedDigests);
            }
        }
        assertTrue(f.outputs().isEmpty());
    }
    // T18: exclude atime, preserve all deliberately writable SOURCE properties and original bytes.
    @Test void sourceIsDeliberatelyUnmodifiedAndNoOutputIsCreatedByReadPhases() throws Exception {
        var f = new Fixture(temporary); var file = f.source.resolve("synthetic.nii");
        var before = Files.readAttributes(file, BasicFileAttributes.class); var bytes = Files.readAllBytes(file);
        var owner = Files.getOwner(file); var permissions = Files.getFileAttributeView(file, PosixFileAttributeView.class);
        var mode = permissions == null ? null : permissions.readAttributes().permissions();
        try (var access = f.access()) {
            var b = access.acquire(f.inventory, f.discovery); var p = f.plan(b); assertTrue(access.revalidate(p, b).isEmpty());
            try (var prepared = access.prepare(p.copies().getFirst())) { assertEquals(b.digests().getFirst().digest(), prepared.verifiedSourceDigest()); }
        }
        var after = Files.readAttributes(file, BasicFileAttributes.class);
        assertEquals(before.size(), after.size()); assertEquals(before.lastModifiedTime(), after.lastModifiedTime());
        assertEquals(before.creationTime(), after.creationTime()); assertEquals(owner, Files.getOwner(file));
        if (mode != null) assertEquals(mode, Files.getPosixFilePermissions(file));
        assertArrayEquals(bytes, Files.readAllBytes(file)); assertTrue(f.outputs().isEmpty());
    }
    // T19: empty and conclusive non-MRI sources do not acquire invented imaging digests.
    @Test void allSkippedAndEmptyHaveEmptyCompleteMapAndNoJobSuccess() throws Exception {
        var f = new Fixture(temporary); Files.delete(f.source.resolve("synthetic.nii"));
        for (boolean withText : new boolean[] {false, true}) {
            if (withText) privateFile(f.source.resolve("text"), new byte[] {1, 2, 3}); f.observe();
            var access = f.access(); var b = access.acquire(f.inventory, f.discovery);
            assertEquals(SourceDigestBatch.Status.COMPLETE, b.status()); assertTrue(b.suppliedDigests().isEmpty());
            var result = f.execute(access, b, UUID.randomUUID());
            assertEquals(CopyExecutionResult.Status.NOTHING_TO_COPY, result.status()); assertTrue(result.operations().isEmpty());
            assertNotEquals(ManifestState.JobState.COMPLETED, result.journal().map(ManifestState::state).orElse(null));
        }
    }
    // T09: original stored DICOM including pixel bytes, large bounded streams, and fixed independent empty SHA vector.
    @Test void dicomAndLargeStoredBytesMatchIndependentDigestsWithoutPixelDecoding() throws Exception {
        var f = new Fixture(temporary); var dcm = dicom(); privateFile(f.source.resolve("synthetic.dcm"), dcm);
        privateFile(f.source.resolve("large.nii"), nifti(2_000_000)); f.observe();
        try (var access = f.access()) {
            var b = access.acquire(f.inventory, f.discovery); assertEquals(SourceDigestBatch.Status.COMPLETE, b.status());
            assertEquals(digest(dcm), b.suppliedDigests().get(new RelativePath(RelativePath.Root.SOURCE, "synthetic.dcm")));
            assertEquals(digest(nifti(2_000_000)), b.suppliedDigests().get(new RelativePath(RelativePath.Root.SOURCE, "large.nii")));
        }
        var empty = temporary.resolve("empty-vector"); privateFile(empty, new byte[0]);
        try (var access = f.access(); var channel = java.nio.channels.FileChannel.open(empty, StandardOpenOption.READ)) {
            var method = NioVerifiedCopyAccess.class.getDeclaredMethod("hash", java.nio.channels.SeekableByteChannel.class,
                    CopyFailure.Stage.class, RelativePath.class, NioVerifiedCopyAccess.Event.class, Path.class); method.setAccessible(true);
            var zero = (ContentDigest)method.invoke(access, channel, CopyFailure.Stage.DIGEST_ACQUISITION, null, null, empty);
            assertEquals(new ContentDigest(0, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"), zero);
        }
        var retainedDiscovery = f.discovery; privateFile(f.source.resolve("empty-input"), new byte[0]); f.observe(); f.discovery = retainedDiscovery;
        try (var access = f.access()) { assertEquals(SourceDigestBatch.Status.BLOCKED, access.acquire(f.inventory, f.discovery).status()); }
    }
    // T10: all 14 metadata components are compared, including optional presence and ordered lists.
    @Test void everyMetadataFieldAndFindingChangeBlocksDespiteOtherwiseValidStoredBytes() throws Exception {
        var f = new Fixture(temporary); privateFile(f.source.resolve("synthetic.dcm"), dicom()); f.observe();
        var member = f.discovery.candidates().getFirst().members().getFirst(); var original = member.metadata();
        var components = DicomDiscoveryMetadata.class.getRecordComponents();
        var constructor = DicomDiscoveryMetadata.class.getConstructor(Arrays.stream(components).map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new));
        for (int index = 0; index < components.length; index++) {
            var values = new Object[components.length]; for (int i = 0; i < values.length; i++) values[i] = components[i].getAccessor().invoke(original);
            values[index] = switch (index) {
                case 0, 1, 2, 4, 5 -> "1.9.99";
                case 3 -> "CT";
                case 6 -> Optional.empty();
                case 7, 8 -> Optional.of(99);
                case 9, 10, 11 -> Optional.of(7);
                case 12 -> List.of(3, 2);
                case 13 -> List.of("DERIVED", "SECONDARY");
                default -> throw new AssertionError();
            };
            var changed = constructor.newInstance(values); assertNotEquals(original, changed, components[index].getName());
            var fake = new DicomSeriesDiscoveryService().discover(new DicomMetadataCatalog(List.of(
                    new DicomMetadataInspection(member.source(), Optional.of(changed), Optional.empty()))));
            try (var access = f.access()) {
                var b = access.acquire(f.inventory, fake); assertEquals(SourceDigestBatch.Status.BLOCKED, b.status(), components[index].getName());
                code(b.failures(), CopyFailure.Code.DISCOVERY_BINDING_UNPROVEN); assertTrue(f.outputs().isEmpty());
            }
        }
        var candidate = f.discovery.candidates().getFirst(); var fake = new DicomSeriesDiscovery(List.of(new DicomSeriesCandidate(candidate.key(), candidate.members(),
                List.of(new DicomSeriesScreeningFinding(DicomSeriesScreeningFinding.Code.AMBIGUOUS_SERIES, DicomSeriesScreeningFinding.Reason.MIXED_MATRIX)))), List.of());
        try (var access = f.access()) { code(access.acquire(f.inventory, fake).failures(), CopyFailure.Code.DISCOVERY_BINDING_UNPROVEN); }
    }
    @Test void recognitionExtensionAndAssessmentFactsAreComparedInFull() throws Exception {
        var f = new Fixture(temporary); var entry = f.inventory.entries().getFirst(); var original = entry.assessment();
        var changed = FormatAssessment.fromDetection(original.initialDetection().withExtensionMismatch(true));
        var fake = new DatasetInventory(List.of(new InventoryEntry(entry.source(), changed)), List.of());
        try (var access = f.access()) { code(access.acquire(fake, f.discovery).failures(), CopyFailure.Code.DISCOVERY_BINDING_UNPROVEN); assertTrue(f.outputs().isEmpty()); }
        var nonmatch = new DatasetInventory(List.of(new InventoryEntry(entry.source(), FormatAssessment.fromDetection(
                DetectionResult.unknown(DetectionDiagnostic.UNSUPPORTED_FORMAT)))), List.of());
        try (var access = f.access()) { assertEquals(SourceDigestBatch.Status.BLOCKED, access.acquire(nonmatch, f.discovery).status()); assertTrue(f.outputs().isEmpty()); }
    }
    @Test void everyCoherentAssessmentComponentChangeIsRejectedByFullReobservation() throws Exception {
        var f = new Fixture(temporary); var entry = f.inventory.entries().getFirst(); var a = entry.assessment();
        var alternatives = List.of(
            new FormatAssessment(a.initialDetection(), a.format(), a.variant(), ValidityStatus.VALID, a.support(), a.readiness(), a.reasons()),
            new FormatAssessment(a.initialDetection(), a.format(), a.variant(), a.validity(), SupportStatus.UNSUPPORTED, ConversionReadiness.BLOCKED, List.of(AssessmentReason.UNSUPPORTED_PROFILE)),
            new FormatAssessment(a.initialDetection(), a.format(), FormatVariant.NIFTI_1_SINGLE_FILE, a.validity(), SupportStatus.SUPPORTED, a.readiness(), a.reasons()));
        for (var changed : alternatives) try (var access = f.access()) {
            var supplied = new DatasetInventory(List.of(new InventoryEntry(entry.source(), changed)), List.of());
            var blocked = access.acquire(supplied, f.discovery); assertEquals(SourceDigestBatch.Status.BLOCKED, blocked.status());
            code(blocked.failures(), CopyFailure.Code.DISCOVERY_BINDING_UNPROVEN); assertThrows(IllegalStateException.class, blocked::suppliedDigests); assertTrue(f.outputs().isEmpty());
        }
    }
    // T11/T12: configured aliases may resolve safely; discovered directory aliases remain fail-closed.
    @Test void safeConfiguredAliasResolvesButDiscoveredDirectoryAliasDoesNot() throws Exception {
        var f = new Fixture(temporary); var alias = temporary.resolve("configured-alias"); Files.createSymbolicLink(alias, f.source);
        try (var access = new NioVerifiedCopyAccess(alias, f.output, f.namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector())) {
            assertEquals(SourceDigestBatch.Status.COMPLETE, access.acquire(f.inventory, f.discovery).status());
        }
        Files.createSymbolicLink(f.source.resolve("discovered-directory"), temporary); f.observe();
        try (var access = f.access()) { assertEquals(SourceDigestBatch.Status.BLOCKED, access.acquire(f.inventory, f.discovery).status()); assertTrue(f.outputs().isEmpty()); }
    }
    // T13/T14: change an observable identity fact; Windows null keys and tunneled birth times are not identities.
    @Test void replacedSourceKeyIsDetectedEvenWithIdenticalBytesAndTimestamp() throws Exception {
        var f = new Fixture(temporary); var file = f.source.resolve("synthetic.nii");
        try (var access = f.access()) {
            var b = access.acquire(f.inventory, f.discovery); var p = f.plan(b); var original = Files.readAttributes(file, BasicFileAttributes.class);
            var time = original.lastModifiedTime();
            var replacement = f.source.resolve("replacement"); privateFile(replacement, nifti(4096)); Files.setLastModifiedTime(replacement, time);
            Files.move(file, temporary.resolve("retained-original")); Files.move(replacement, file);
            if (original.fileKey() == null) {
                // NTFS tunnels the old creation time onto a reused name. Supply a real observable change.
                var created = FileTime.from(original.creationTime().toInstant().plusSeconds(2));
                Files.getFileAttributeView(file, BasicFileAttributeView.class).setTimes(null, null, created);
                assertNotEquals(original.creationTime(), Files.readAttributes(file, BasicFileAttributes.class).creationTime());
            } else assertNotEquals(original.fileKey(), Files.readAttributes(file, BasicFileAttributes.class).fileKey());
            assertFalse(Files.isSameFile(file, temporary.resolve("retained-original")));
            assertEquals(time, Files.getLastModifiedTime(file)); assertArrayEquals(nifti(4096), Files.readAllBytes(file));
            code(access.revalidate(p, b), CopyFailure.Code.SOURCE_CHANGED); assertTrue(f.outputs().isEmpty());
        }
    }
    // T15/T16/T17: earlier digest facts survive the first failing source; exhausted failure retention gets one terminal fact.
    @Test void partialFactsAreRetainedButNeverHandedToF5AndFailureCapIsCallerBound() throws Exception {
        var f = new Fixture(temporary); privateFile(f.source.resolve("z.nii"), nifti(4096)); f.observe();
        var faults = List.of(new CopyFailure(CopyFailure.Stage.DIGEST_ACQUISITION, CopyFailure.Code.READ_FAILED, Optional.empty(), Optional.empty()),
                new CopyFailure(CopyFailure.Stage.DIGEST_ACQUISITION, CopyFailure.Code.HASH_FAILED, Optional.empty(), Optional.empty()));
        try (var access = new NioVerifiedCopyAccess(f.source, f.output, f.namespace, new CopyLimits(100000, 1000000, 128, 1),
                INVENTORY_LIMITS, METADATA_LIMITS, detector(), (event, path) -> {
                    if (event == NioVerifiedCopyAccess.Event.SOURCE_READ && path.getFileName().toString().equals("z.nii")) throw new VerifiedCopyAccess.AccessFailure(faults);
                })) {
            var b = access.acquire(f.inventory, f.discovery); assertEquals(SourceDigestBatch.Status.BLOCKED, b.status());
            assertEquals(1, b.digests().size()); assertEquals(2, b.failures().size()); code(b.failures(), CopyFailure.Code.READ_FAILED); code(b.failures(), CopyFailure.Code.RESOURCE_LIMIT);
            assertThrows(IllegalStateException.class, b::suppliedDigests); assertTrue(f.outputs().isEmpty());
        }
    }
    @Test void closedAndFailedSessionsCannotAcquireAgain() throws Exception {
        var f = new Fixture(temporary); var access = f.access(); access.close();
        assertThrows(IllegalStateException.class, () -> access.acquire(f.inventory, f.discovery));
        try (var limited = f.access(new CopyLimits(1, 1, 1, 1))) {
            assertEquals(SourceDigestBatch.Status.BLOCKED, limited.acquire(f.inventory, f.discovery).status());
            assertThrows(IllegalStateException.class, () -> limited.acquire(f.inventory, f.discovery));
        }
    }

    @Test void sourceChangesDuringReadAndGrowthInvalidateWholeBatch() throws Exception {
        for (String mode : List.of("growth", "bytes", "skipped")) {
            var f = new Fixture(privateDirectory(temporary.resolve(mode))); var changed = new java.util.concurrent.atomic.AtomicBoolean();
            try (var access = new NioVerifiedCopyAccess(f.source, f.output, f.namespace, new CopyLimits(4096, 1000000, 31, 20), INVENTORY_LIMITS, METADATA_LIMITS, detector(),
                    (event, path) -> { if (event == NioVerifiedCopyAccess.Event.SOURCE_READ && changed.compareAndSet(false, true)) {
                        if (mode.equals("skipped")) Files.write(f.source.resolve("policy-text"), new byte[] {1});
                        else if (mode.equals("growth")) Files.write(path, new byte[] {1}, StandardOpenOption.APPEND);
                        else { var bytes = nifti(4096); bytes[400] ^= 1; Files.write(path, bytes); }
                    }})) {
                var b = access.acquire(f.inventory, f.discovery); assertEquals(SourceDigestBatch.Status.BLOCKED, b.status()); assertThrows(IllegalStateException.class, b::suppliedDigests);
                assertTrue(f.outputs().isEmpty()); assertTrue(changed.get());
            }
        }
    }


    @Test void originalIncompleteAndF3UnassignedBlockersRemainOriginalAndCannotHandoff() throws Exception {
        var f = new Fixture(temporary); privateFile(f.source.resolve("synthetic.dcm"), dicom()); f.observe();
        var original = new InventoryFailure(InventoryFailure.Code.TRAVERSAL_FAILED, Optional.empty());
        var incomplete = new DatasetInventory(f.inventory.entries(), List.of(original));
        try (var access = f.access()) {
            var blocked = access.acquire(incomplete, f.discovery); assertEquals(SourceDigestBatch.Status.BLOCKED, blocked.status());
            assertEquals(List.of(original), blocked.inventory().failures()); assertTrue(blocked.failures().isEmpty()); assertTrue(blocked.digests().isEmpty());
        }
        var dcm = new RelativePath(RelativePath.Root.SOURCE, "synthetic.dcm");
        var failure = new DicomMetadataInspection(dcm, Optional.empty(), Optional.of(DicomMetadataInspection.FailureCode.METADATA_READ_FAILED));
        var discovery = new DicomSeriesDiscovery(List.of(), List.of(failure));
        try (var access = f.access()) {
            var blocked = access.acquire(f.inventory, discovery); assertEquals(SourceDigestBatch.Status.BLOCKED, blocked.status());
            assertEquals(List.of(failure), blocked.discovery().unassigned()); assertTrue(blocked.failures().isEmpty()); assertThrows(IllegalStateException.class, blocked::suppliedDigests);
        }
        assertTrue(f.outputs().isEmpty());
    }
    @Test void canonicalOverlapAndUnsafeManifestNamespaceRejectWithoutMutation() throws Exception {
        var f = new Fixture(temporary);
        for (Path namespace : List.of(f.output, f.output.resolve("layout-v1"), f.output.resolve("layout-v1/nifti"), f.source.resolve("journal"), f.output.resolve(".f6-copy.lease"))) {
            assertThrows(VerifiedCopyAccess.AccessFailure.class, () -> new NioVerifiedCopyAccess(f.source, f.output, namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector()));
        }
        assertThrows(VerifiedCopyAccess.AccessFailure.class, () -> new NioVerifiedCopyAccess(f.source, f.source, f.source.resolve("journal"), LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector()));
        var inside = privateDirectory(f.source.resolve("nested-output"));
        assertThrows(VerifiedCopyAccess.AccessFailure.class, () -> new NioVerifiedCopyAccess(f.source, inside, inside.resolve("journal"), LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector()));
        assertTrue(f.outputs().isEmpty());
    }


    @Test void sequentialCohortDoesNotAccumulateOpenSourceDescriptors() throws Exception {
        var f = new Fixture(temporary); for (int i = 0; i < 60; i++) privateFile(f.source.resolve("member-" + i + ".nii"), nifti(352)); f.observe();
        try (var probe = new DescriptorProbe(); var access = f.access()) {
            // One persistent observer avoids counting newly spawned measurement-process handles as SOURCE leaks.
            probe.count(); probe.count();
            long before = probe.count();
            var retained = new ArrayList<java.nio.channels.FileChannel>();
            try {
                for (int i = 0; i < 12; i++) retained.add(java.nio.channels.FileChannel.open(f.source.resolve("synthetic.nii"), StandardOpenOption.READ));
                assertTrue(probe.count() >= before + 12, "Native probe must detect real retained file handles");
            } finally { for (var channel : retained) channel.close(); }
            before = probe.count(); var b = access.acquire(f.inventory, f.discovery); assertEquals(61, b.digests().size());
            long acquired = probe.count();
            System.out.println("F6 descriptor baseline=" + before + " acquired=" + acquired);
            assertTrue(acquired <= before + 3, "Descriptor baseline=" + before + " acquired=" + acquired);
            var p = f.plan(b); assertTrue(access.revalidate(p, b).isEmpty());
            long revalidated = probe.count();
            System.out.println("F6 descriptor revalidated=" + revalidated);
            assertTrue(revalidated <= before + 3, "Descriptor baseline=" + before + " revalidated=" + revalidated);
            try (var prepared = access.prepare(p.copies().getFirst())) {
                assertEquals(p.copies().getFirst().expectedDigest(), prepared.verifiedSourceDigest());
                assertThrows(IllegalStateException.class, () -> access.prepare(p.copies().getLast()));
                long preparedCount = probe.count();
                System.out.println("F6 descriptor prepared=" + preparedCount);
                assertTrue(preparedCount <= before + 5, "Descriptor baseline=" + before + " prepared=" + preparedCount);
            }
            long closedCount = probe.count();
            System.out.println("F6 descriptor closed=" + closedCount);
            assertTrue(closedCount <= before + 3, "Descriptor baseline=" + before + " closed=" + closedCount); assertTrue(f.outputs().isEmpty());
        }
    }
    private static final class DescriptorProbe implements AutoCloseable {
        private final Process process;
        private final BufferedReader reader;
        private final BufferedWriter writer;
        DescriptorProbe() throws Exception {
            if (System.getProperty("os.name").equals("Linux")) { process = null; reader = null; writer = null; return; }
            process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                    "$p=Get-Process -Id " + ProcessHandle.current().pid()
                    + "; while (($line=[Console]::ReadLine()) -ne $null) { $p.Refresh(); [Console]::WriteLine($p.HandleCount) }")
                    .redirectErrorStream(true).start();
            reader = process.inputReader(java.nio.charset.StandardCharsets.UTF_8);
            writer = process.outputWriter(java.nio.charset.StandardCharsets.UTF_8);
        }
        long count() throws Exception {
            if (process == null) try (var paths = Files.list(Path.of("/proc/self/fd"))) { return paths.count(); }
            writer.write("count\n"); writer.flush();
            long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
            while (!reader.ready() && process.isAlive() && System.nanoTime() < end) Thread.sleep(10);
            assertTrue(reader.ready(), "Native handle observer did not respond");
            return Long.parseLong(reader.readLine().trim());
        }
        @Override public void close() throws Exception {
            if (process == null) return;
            try {
                writer.close(); assertTrue(process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)); assertEquals(0, process.exitValue());
            } finally { reader.close(); if (process.isAlive()) process.destroyForcibly(); }
        }
    }


    @Test void checkedByteCountOverflowCannotWrapOrTruncateEvidence() throws Exception {
        var f = new Fixture(temporary);
        try (var access = f.access()) {
            var method = NioVerifiedCopyAccess.class.getDeclaredMethod("addCount", long.class, int.class, CopyFailure.Stage.class, RelativePath.class); method.setAccessible(true);
            var error = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> method.invoke(access, Long.MAX_VALUE, 1, CopyFailure.Stage.DIGEST_ACQUISITION, null));
            assertInstanceOf(VerifiedCopyAccess.AccessFailure.class, error.getCause()); code(((VerifiedCopyAccess.AccessFailure)error.getCause()).failures(), CopyFailure.Code.RESOURCE_LIMIT);
            assertTrue(f.outputs().isEmpty());
        }
    }

    @Test void changedExistingSkippedSourceCannotAuthorizeRevalidatedCopy() throws Exception {
        var f = new Fixture(temporary); privateFile(f.source.resolve("policy-text"), new byte[] {1, 2, 3}); f.observe();
        try (var access = f.access()) {
            var batch = access.acquire(f.inventory, f.discovery); var plan = f.plan(batch);
            Files.write(f.source.resolve("policy-text"), new byte[] {1, 2, 3, 4});
            assertEquals(f.inventory, new NioDatasetScanner(f.source, f.output, INVENTORY_LIMITS, detector()).scan());
            code(access.revalidate(plan, batch), CopyFailure.Code.SOURCE_CHANGED); assertTrue(f.outputs().isEmpty());
        }
    }
}
