package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.cbihi.mrinormalizer.application.dataset.dicom.*;
import org.cbihi.mrinormalizer.application.dataset.model.*;
import org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadResult;
import org.cbihi.mrinormalizer.application.port.out.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.application.request.F9HeadlessRequest;
import org.cbihi.mrinormalizer.application.result.F9HeadlessResult.*;
import org.cbihi.mrinormalizer.application.service.*;
import org.cbihi.mrinormalizer.application.validation.NiftiOutputComparator;
import org.cbihi.mrinormalizer.domain.model.*;

class F9HeadlessConversionServiceTest {
    @TempDir Path root;
    private static final RelativePath ONE = new RelativePath(RelativePath.Root.SOURCE,"one.dcm");
    private static final RelativePath TWO = new RelativePath(RelativePath.Root.SOURCE,"two.dcm");
    private static final RelativePath OUT = new RelativePath(RelativePath.Root.OUTPUT,"selected.nii");
    private static final String UID="1.2.3";
    private final AtomicInteger creates = new AtomicInteger();

    private static DicomMetadataInspection metadata(RelativePath file,String uid,String sop) {
        return new DicomMetadataInspection(file,Optional.of(new DicomDiscoveryMetadata("1.2",uid,sop,
                "MR","1.2.840.10008.5.1.4.1.1.4","1.2.840.10008.1.2",Optional.empty(),
                Optional.of(2),Optional.of(2),Optional.empty(),Optional.empty(),Optional.empty(),
                List.of(),List.of())),Optional.empty());
    }
    private static DatasetInventory inventory(List<RelativePath> members,DetectionResult assessment) {
        return new DatasetInventory(members.stream().map(m->new InventoryEntry(m,
                FormatAssessment.fromDetection(assessment))).toList(),List.of());
    }
    private F9HeadlessRequest req(List<RelativePath> files) {
        return new F9HeadlessRequest(UUID.randomUUID(), UID, files, OUT);
    }
    private final class Fixture implements AutoCloseable {
        final DatasetScanner scanner;
        final DicomMetadataReader reader;
        final ManifestStore store;
        final AtomicInteger preflightCalls = new AtomicInteger();
        final java.util.concurrent.atomic.AtomicReference<ProvenanceManifest> captured = new java.util.concurrent.atomic.AtomicReference<>();
        final VerifiedConversionEvidenceService verified;
        final F9HeadlessConversionService service;
        Fixture(DatasetInventory inv, DicomMetadataReader metadataReader) {
            scanner=()->inv;
            reader=metadataReader;
            store=new ManifestStore() {
                public ManifestReceipt create(ProvenanceManifest plan) {
                    creates.incrementAndGet();
                    captured.set(plan);
                    throw new ProvenancePersistenceException(List.of(new ManifestFailure(
                            ManifestFailure.Phase.PERSISTENCE,ManifestFailure.Code.PUBLICATION_UNAVAILABLE)),
                            ProvenancePersistenceException.PublicationOutcome.NOT_PUBLISHED,Optional.empty());
                }
                public ManifestReceipt append(CheckpointRecord r,ManifestReceipt head){throw new AssertionError("No journal intent");}
                public Optional<ManifestState> replay(Optional<ManifestReceipt> head){throw new AssertionError("No replay");}
                public void close() {}
            };
            VerifiedNiftiOutputAccess access=new VerifiedNiftiOutputAccess() {
                public SourceBinding validateSelectedSources(List<InputSource> a,List<SourceFileRecord> b){throw new AssertionError("No F8 execution");}
                public PreparedOutput prepare(String id,RelativePath out,ManifestReceipt head){throw new AssertionError("No F8 execution");}
                public void close() {}
            };
            verified=new VerifiedConversionEvidenceService(r->{throw new AssertionError("No M6 execution");},
                    (volume,affine,target)->{throw new AssertionError("No M7 execution");},
                    ref->{throw new AssertionError("No F7 execution");},access,store,
                    new NiftiOutputComparator(), Clock.systemUTC());
            DicomSeriesService preflight=r->{
                preflightCalls.incrementAndGet();
                var geometry=new VolumeGeometry(2,2,1,1,1,1,new double[]{0,0,0},
                        new double[]{1,0,0},new double[]{0,1,0},new double[]{0,0,1},
                        CoordinateSystem.DICOM_PATIENT_LPS);
                var volume=new ImageVolume(geometry,
                        new ImmutableVoxelData(2,2,1,ScalarType.UINT16,new long[]{1,2,3,4}),
                        IntensityTransform.identityNotDeclared());
                return org.cbihi.mrinormalizer.application.result.DicomProcessingResult.success(volume,null);
            };
            service=new F9HeadlessConversionService(root,scanner,reader,
                    new DicomSeriesDiscoveryService(),preflight,store,verified,Clock.systemUTC());
        }
        public void close(){service.close();}
    }

    @Test void S01_refusesMissingSelectedMemberBeforeMutation() {
        try(var fixture=new Fixture(inventory(List.of(ONE,TWO),DetectionResult.identified(DetectionOutcome.DICOM)),
                rel->metadata(rel,UID,"1.9"))){
            var result=fixture.service.executeOne(req(List.of(ONE)));
            assertEquals(Status.FAILED,result.status());
            assertEquals(List.of(Reason.SELECTED_MEMBERS_MISMATCH),result.reasons());
            assertEquals(0,creates.get());
        }
    }
    @Test void S02_refusesUnrecognizedInventoryBeforeMutation() {
        try(var fixture=new Fixture(inventory(List.of(ONE),DetectionResult.unknown(DetectionDiagnostic.IO_ERROR)),
                rel->metadata(rel,UID,"1.9"))){
            var result=fixture.service.executeOne(req(List.of(ONE)));
            assertEquals(List.of(Reason.INVENTORY_INCOMPLETE),result.reasons());
            assertEquals(0,creates.get());
        }
    }
    @Test void S03_rejectsMetadataFailureWithoutMutation() {
        try(var fixture=new Fixture(inventory(List.of(ONE),DetectionResult.identified(DetectionOutcome.DICOM)),
                rel->new DicomMetadataInspection(rel,Optional.empty(),
                        Optional.of(DicomMetadataInspection.FailureCode.INCOMPLETE_METADATA)))){
            assertEquals(List.of(Reason.METADATA_OR_SCREENING_FAILED),fixture.service.executeOne(req(List.of(ONE))).reasons());
            assertEquals(0,creates.get());
        }
    }
    @Test void S04_inconclusiveLargeDicomRecognitionPreservedNotUpgraded() throws Exception {
        Files.write(root.resolve("one.dcm"),new byte[]{1,2,3,4});
        try(var fixture=new Fixture(inventory(List.of(ONE),DetectionResult.unknown(DetectionDiagnostic.INPUT_TOO_LARGE)),
                rel->metadata(rel,UID,"1.9"))){
            var result=fixture.service.executeOne(req(List.of(ONE)));
            assertEquals(Status.FAILED,result.status());
            assertEquals(List.of(Reason.PLAN_OR_INTENT_NOT_ACKNOWLEDGED),result.reasons());
            assertEquals(1,creates.get()); // Reaches F1 after direct F3/F4 evidence; no F8 invocation.
            assertEquals(DetectionOutcome.UNKNOWN, fixture.captured.get().sources().getFirst()
                    .assessment().initialDetection().outcome());
            assertEquals(ConversionReadiness.READY, fixture.captured.get().sources().getFirst()
                    .assessment().readiness());
            assertEquals(1,fixture.preflightCalls.get());
        }
    }
    @Test void S05_rejectsWrongUidBeforeMutation() {
        try(var fixture=new Fixture(inventory(List.of(ONE),DetectionResult.identified(DetectionOutcome.DICOM)),
                rel->metadata(rel,"1.2.9","1.9"))){
            assertEquals(List.of(Reason.METADATA_OR_SCREENING_FAILED),fixture.service.executeOne(req(List.of(ONE))).reasons());
            assertEquals(0,creates.get());
        }
    }
    @Test void S06_requestRejectsDuplicatesAndUnsafeDestination() {
        assertThrows(IllegalArgumentException.class,()->req(List.of(ONE,ONE)));
        assertThrows(IllegalArgumentException.class,()->new F9HeadlessRequest(UUID.randomUUID(),UID,List.of(ONE),
                new RelativePath(RelativePath.Root.OUTPUT,"selected.nii.gz")));
    }
    @Test void S07_serviceSingleUse() {
        try(var fixture=new Fixture(inventory(List.of(ONE),DetectionResult.unknown(DetectionDiagnostic.IO_ERROR)),
                rel->metadata(rel,UID,"1.9"))){
            fixture.service.executeOne(req(List.of(ONE)));
            assertThrows(IllegalStateException.class,()->fixture.service.executeOne(req(List.of(ONE))));
        }
    }

    /** Real F1-v2 journal and F8 publication ports, with only M6 pixel reconstruction stubbed. */
    private final class JournalFixture implements AutoCloseable {
        final Path source, output, publishedFile;
        final UUID job = UUID.randomUUID();
        final org.cbihi.mrinormalizer.infrastructure.filesystem.JsonManifestStore backing;
        final ManifestStore intercepted;
        final F9HeadlessConversionService service;
        final AtomicInteger f8M6Calls = new AtomicInteger();
        final AtomicInteger terminalJobAttempts = new AtomicInteger();
        final AtomicInteger preflightCalls = new AtomicInteger();

        JournalFixture(boolean failF8Reconstruction, boolean loseCompletedAck) throws Exception {
            source = Files.createDirectory(root.resolve("real-source"));
            output = Files.createDirectory(root.resolve("real-output"));
            Files.createDirectory(output.resolve("private-stage"));
            Files.write(source.resolve("one.dcm"),new byte[]{1,2,3,4,5});
            publishedFile = output.resolve(OUT.path());
            backing = new org.cbihi.mrinormalizer.infrastructure.filesystem.JsonManifestStore(
                    source, output, output.resolve("manifest"), job);
            intercepted = new ManifestStore() {
                @Override public ManifestReceipt create(ProvenanceManifest plan) { return backing.create(plan); }
                @Override public ManifestReceipt append(CheckpointRecord record, ManifestReceipt head) {
                    if (record.kind() == CheckpointRecord.Kind.JOB_OBSERVED
                            && record.jobState().orElse(null) == ManifestState.JobState.COMPLETED) {
                        terminalJobAttempts.incrementAndGet();
                        if (loseCompletedAck) {
                            // Publication of the output has already occurred. F9 must never claim SUCCESS.
                            throw new ProvenancePersistenceException(List.of(new ManifestFailure(
                                    ManifestFailure.Phase.PERSISTENCE,ManifestFailure.Code.WRITE_FAILED)),
                                    ProvenancePersistenceException.PublicationOutcome.UNKNOWN,Optional.empty());
                        }
                    }
                    return backing.append(record, head);
                }
                @Override public Optional<ManifestState> replay(Optional<ManifestReceipt> floor) {
                    return backing.replay(floor);
                }
                @Override public void close() { backing.close(); }
            };
            var geom = new VolumeGeometry(2,2,1,1,1,1,new double[]{0,0,0},
                    new double[]{1,0,0},new double[]{0,1,0},new double[]{0,0,1},
                    CoordinateSystem.DICOM_PATIENT_LPS);
            var volume = new ImageVolume(geom,
                    new ImmutableVoxelData(2,2,1,ScalarType.UINT16,new long[]{1,2,65535,3}),
                    IntensityTransform.identityNotDeclared());
            DicomSeriesService preflight = call -> {
                preflightCalls.incrementAndGet();
                return org.cbihi.mrinormalizer.application.result.DicomProcessingResult.success(volume,null);
            };
            DicomSeriesService verifiedM6 = call -> {
                f8M6Calls.incrementAndGet();
                if (failF8Reconstruction) return org.cbihi.mrinormalizer.application.result.DicomProcessingResult.failure(
                        org.cbihi.mrinormalizer.domain.error.DicomProcessingError.IRREGULAR_SPACING,null);
                return org.cbihi.mrinormalizer.application.result.DicomProcessingResult.success(volume,null);
            };
            var reopened = new org.cbihi.mrinormalizer.infrastructure.nifti.Nifti1VolumeReader(output,
                    new org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadLimits(
                            1_000_000,1_000_000,100_000,352,2_000_000,10_000));
            var access = new org.cbihi.mrinormalizer.infrastructure.filesystem.NioVerifiedNiftiOutputAccess(
                    source,output,output.resolve("private-stage"),intercepted,1_000_000,1_000_000,1_000_000);
            var f8 = new VerifiedConversionEvidenceService(verifiedM6,
                    new org.cbihi.mrinormalizer.infrastructure.nifti.Nifti1VolumeWriter(),
                    reopened,access,intercepted,new NiftiOutputComparator(),Clock.systemUTC());
            service = new F9HeadlessConversionService(source,
                    () -> inventory(List.of(ONE),DetectionResult.unknown(
                            DetectionDiagnostic.INPUT_TOO_LARGE)),
                    rel -> metadata(rel,UID,"1.9"),new DicomSeriesDiscoveryService(),
                    preflight,intercepted,f8,Clock.systemUTC());
        }
        F9HeadlessRequest request() { return new F9HeadlessRequest(job,UID,List.of(ONE),OUT); }
        ManifestState journal() { return backing.replay(Optional.empty()).orElseThrow(); }
        @Override public void close() { service.close(); }
    }

    @Test void S14_terminalCompletedIsReplayedAndAcknowledgedAfterVerifiedPublication() throws Exception {
        try(var fixture = new JournalFixture(false,false)) {
            var request=fixture.request();
            var result=fixture.service.executeOne(request);
            assertEquals(Status.VERIFIED_PUBLISHED,result.status());
            assertTrue(Files.isRegularFile(fixture.publishedFile));
            var replay=fixture.journal();
            assertEquals(ManifestState.JobState.COMPLETED,replay.state());
            assertEquals(CheckpointRecord.State.VERIFIED_PUBLISHED,
                    replay.operations().getFirst().observation().state());
            assertEquals(replay.receipt(),result.acknowledgedHead().orElseThrow());
            assertEquals(1,fixture.terminalJobAttempts.get());
            assertEquals(1,fixture.preflightCalls.get());
            assertEquals(1,fixture.f8M6Calls.get());
            assertThrows(IllegalStateException.class,()->fixture.service.executeOne(request));
        }
    }

    @Test void S15_failedJobCompletionAcknowledgmentAfterPublicationNeedsRecoveryNoRetry() throws Exception {
        try(var fixture = new JournalFixture(false,true)) {
            var request=fixture.request();
            var result=fixture.service.executeOne(request);
            assertEquals(Status.RECOVERY_REQUIRED,result.status());
            assertEquals(List.of(Reason.FINAL_JOB_ACK_UNCERTAIN),result.reasons());
            assertTrue(Files.isRegularFile(fixture.publishedFile));
            var replay=fixture.journal();
            assertEquals(ManifestState.JobState.RUNNING,replay.state());
            assertEquals(CheckpointRecord.State.VERIFIED_PUBLISHED,
                    replay.operations().getFirst().observation().state());
            assertEquals(1,fixture.terminalJobAttempts.get());
            assertEquals(1,fixture.f8M6Calls.get());
            assertThrows(IllegalStateException.class,()->fixture.service.executeOne(request));
            assertEquals(1,fixture.terminalJobAttempts.get());
        }
    }

    @Test void S16_knownNonpublicationAppendsAcknowledgedFailedJobNoRetry() throws Exception {
        try(var fixture = new JournalFixture(true,false)) {
            var request=fixture.request();
            var result=fixture.service.executeOne(request);
            assertEquals(Status.FAILED,result.status());
            assertEquals(List.of(Reason.CONVERSION_OR_VERIFICATION_FAILED),result.reasons());
            assertFalse(Files.exists(fixture.publishedFile));
            var replay=fixture.journal();
            assertEquals(ManifestState.JobState.FAILED,replay.state());
            assertEquals(CheckpointRecord.State.FAILED,
                    replay.operations().getFirst().observation().state());
            assertEquals(0,fixture.terminalJobAttempts.get());
            assertEquals(1,fixture.preflightCalls.get());
            assertEquals(1,fixture.f8M6Calls.get());
            assertThrows(IllegalStateException.class,()->fixture.service.executeOne(request));
            assertEquals(1,fixture.f8M6Calls.get());
        }
    }

}
