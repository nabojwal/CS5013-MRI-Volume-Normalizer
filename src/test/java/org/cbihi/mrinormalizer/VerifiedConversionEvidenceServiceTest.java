package org.cbihi.mrinormalizer;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.cbihi.mrinormalizer.application.service.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.application.port.out.*;
import org.cbihi.mrinormalizer.application.validation.*;

class VerifiedConversionEvidenceServiceTest {
    @Test void T33_failedResultMayCoexistWithCompetingDestination() {
        var result=new VerifiedConversionResult(VerifiedConversionResult.Status.FAILED,
                VerifiedConversionResult.Publication.NOT_PUBLISHED,Optional.empty(),Optional.empty(),Optional.empty(),
                List.of(CheckpointRecord.ConversionFault.OUTPUT_CONFLICT),List.of(),
                List.of(new ManifestFailure(ManifestFailure.Phase.EXECUTION,ManifestFailure.Code.OUTPUT_CONFLICT)),Optional.empty());
        assertEquals(VerifiedConversionResult.Status.FAILED,result.status());
        assertTrue(result.finalDigest().isEmpty());
    }
    @Test void T40_ambiguousPublicationCannotBeFailed() {
        assertThrows(IllegalArgumentException.class,()->new VerifiedConversionResult(
                VerifiedConversionResult.Status.FAILED,VerifiedConversionResult.Publication.UNKNOWN,
                Optional.empty(),Optional.empty(),Optional.empty(),
                List.of(CheckpointRecord.ConversionFault.PUBLICATION_UNKNOWN),List.of(),List.of(),Optional.empty()));
    }
    @Test void T39_faultPayloadIsControlledAndOrdered() {
        var f=new VerifiedNiftiOutputAccess.AccessFailure(List.of(
                VerifiedNiftiOutputAccess.AccessFault.CLEANUP_FAILED,VerifiedNiftiOutputAccess.AccessFault.OUTPUT_CONFLICT));
        assertEquals(List.of(VerifiedNiftiOutputAccess.AccessFault.OUTPUT_CONFLICT,
                VerifiedNiftiOutputAccess.AccessFault.CLEANUP_FAILED),f.faults());
        assertNull(f.getCause());assertEquals(0,f.getStackTrace().length);
        assertThrows(IllegalArgumentException.class,()->new VerifiedNiftiOutputAccess.AccessFailure(List.of(
                VerifiedNiftiOutputAccess.AccessFault.OUTPUT_CONFLICT,VerifiedNiftiOutputAccess.AccessFault.OUTPUT_CONFLICT)));
    }

    @Test void T35_realV2StoreM7F7RoundTripAndNoReplace() throws Exception {
        var sandbox=java.nio.file.Files.createTempDirectory("f8-single-series-test-");
        var source=java.nio.file.Files.createDirectory(sandbox.resolve("source"));
        var output=java.nio.file.Files.createDirectory(sandbox.resolve("output"));
        java.nio.file.Files.createDirectory(output.resolve("private-stage"));
        var dicom=source.resolve("fixture.dcm");
        java.nio.file.Files.write(dicom,new byte[]{1,2,3,4,5});
        var digest=new ContentDigest(5,java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(dicom))));
        var relative=new RelativePath(RelativePath.Root.SOURCE,"fixture.dcm");
        var destination=new RelativePath(RelativePath.Root.OUTPUT,"converted.nii");
        var assessment=new org.cbihi.mrinormalizer.application.dataset.model.FormatAssessment(
                org.cbihi.mrinormalizer.domain.model.DetectionResult.identified(
                        org.cbihi.mrinormalizer.domain.model.DetectionOutcome.DICOM),
                org.cbihi.mrinormalizer.domain.model.ImagingFormat.DICOM,
                org.cbihi.mrinormalizer.application.dataset.model.FormatVariant.DICOM_UNSPECIFIED,
                org.cbihi.mrinormalizer.application.dataset.model.ValidityStatus.VALID,
                org.cbihi.mrinormalizer.application.dataset.model.SupportStatus.SUPPORTED,
                org.cbihi.mrinormalizer.application.dataset.model.ConversionReadiness.READY,List.of());
        var sourceRecord=new SourceFileRecord(relative,Optional.of(digest),assessment,List.of());
        String op="c".repeat(64);
        var job=java.util.UUID.randomUUID();var time=java.time.Instant.now();
        var plan=new ProvenanceManifest(2,job,time,List.of(sourceRecord),List.of(
                new ManifestOperation(op,ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI,List.of(relative),destination)));
        var geom=new org.cbihi.mrinormalizer.domain.model.VolumeGeometry(2,2,1,1,1,1,
                new double[]{0,0,0},new double[]{1,0,0},new double[]{0,1,0},new double[]{0,0,1},
                org.cbihi.mrinormalizer.domain.model.CoordinateSystem.DICOM_PATIENT_LPS);
        var volume=new org.cbihi.mrinormalizer.domain.model.ImageVolume(geom,
                new org.cbihi.mrinormalizer.domain.model.ImmutableVoxelData(2,2,1,
                        org.cbihi.mrinormalizer.domain.model.ScalarType.UINT16,new long[]{1,2,65535,3}),
                org.cbihi.mrinormalizer.domain.model.IntensityTransform.identityNotDeclared());
        try(var journal=new org.cbihi.mrinormalizer.infrastructure.filesystem.JsonManifestStore(source,output,
                output.resolve("manifest"),job)) {
            var head=journal.create(plan);
            head=journal.append(new CheckpointRecord(2,job,1,head.headSha256(),time,
                    CheckpointRecord.Kind.JOB_OBSERVED,Optional.empty(),Optional.empty(),
                    Optional.of(ManifestState.JobState.RUNNING),List.of()),head);
            var reader=new org.cbihi.mrinormalizer.infrastructure.nifti.Nifti1VolumeReader(output,
                    new org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadLimits(
                            1_000_000,1_000_000,100_000,352,2_000_000,10_000));
            try(var access=new org.cbihi.mrinormalizer.infrastructure.filesystem.NioVerifiedNiftiOutputAccess(
                    source,output,output.resolve("private-stage"),journal,1_000_000,1_000_000,1_000_000);
                var service=new VerifiedConversionEvidenceService(
                    r->org.cbihi.mrinormalizer.application.result.DicomProcessingResult.success(volume,null),
                    new org.cbihi.mrinormalizer.infrastructure.nifti.Nifti1VolumeWriter(),reader,
                    access,journal,new NiftiOutputComparator(),java.time.Clock.systemUTC())) {
                var result=service.executeOne(new VerifiedConversionRequest(op,List.of(
                        new org.cbihi.mrinormalizer.domain.model.InputSource(dicom.toString())),
                        "1.2.3",destination,head));
                assertEquals(VerifiedConversionResult.Status.VERIFIED_PUBLISHED,result.status());
                assertTrue(java.nio.file.Files.isRegularFile(output.resolve("converted.nii")));
                assertEquals(result.finalDigest().orElseThrow(),result.verification().orElseThrow().stagedDigest());
                assertEquals(CheckpointRecord.State.VERIFIED_PUBLISHED,journal.replay(Optional.empty()).orElseThrow()
                        .operations().getFirst().observation().state());
            }
        }
    }

    private static final class Scenario implements AutoCloseable {
        final java.nio.file.Path root,source,output,stageDirectory,sourceFile,finalFile;
        final java.util.UUID job=UUID.randomUUID();
        final java.time.Instant time=java.time.Instant.now();
        final String op="c".repeat(64);
        final RelativePath destination=new RelativePath(RelativePath.Root.OUTPUT,"converted.nii");
        final org.cbihi.mrinormalizer.infrastructure.filesystem.JsonManifestStore real;
        final ManifestReceipt running;
        final org.cbihi.mrinormalizer.domain.model.ImageVolume volume;
        Scenario() throws Exception {
            root=java.nio.file.Files.createTempDirectory("f8-scenario-");
            source=java.nio.file.Files.createDirectory(root.resolve("source"));
            output=java.nio.file.Files.createDirectory(root.resolve("output"));
            stageDirectory=java.nio.file.Files.createDirectory(output.resolve("private-stage"));
            sourceFile=source.resolve("fixture.dcm");
            java.nio.file.Files.write(sourceFile,new byte[]{1,2,3,4,5});
            finalFile=output.resolve("converted.nii");
            var digest=new ContentDigest(5,java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(sourceFile))));
            var rel=new RelativePath(RelativePath.Root.SOURCE,"fixture.dcm");
            var assessment=new org.cbihi.mrinormalizer.application.dataset.model.FormatAssessment(
                    org.cbihi.mrinormalizer.domain.model.DetectionResult.identified(
                            org.cbihi.mrinormalizer.domain.model.DetectionOutcome.DICOM),
                    org.cbihi.mrinormalizer.domain.model.ImagingFormat.DICOM,
                    org.cbihi.mrinormalizer.application.dataset.model.FormatVariant.DICOM_UNSPECIFIED,
                    org.cbihi.mrinormalizer.application.dataset.model.ValidityStatus.VALID,
                    org.cbihi.mrinormalizer.application.dataset.model.SupportStatus.SUPPORTED,
                    org.cbihi.mrinormalizer.application.dataset.model.ConversionReadiness.READY,List.of());
            var plan=new ProvenanceManifest(2,job,time,
                    List.of(new SourceFileRecord(rel,Optional.of(digest),assessment,List.of())),
                    List.of(new ManifestOperation(op,ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI,
                            List.of(rel),destination)));
            var geom=new org.cbihi.mrinormalizer.domain.model.VolumeGeometry(2,2,1,1,1,1,
                    new double[]{0,0,0},new double[]{1,0,0},new double[]{0,1,0},new double[]{0,0,1},
                    org.cbihi.mrinormalizer.domain.model.CoordinateSystem.DICOM_PATIENT_LPS);
            volume=new org.cbihi.mrinormalizer.domain.model.ImageVolume(geom,
                    new org.cbihi.mrinormalizer.domain.model.ImmutableVoxelData(2,2,1,
                            org.cbihi.mrinormalizer.domain.model.ScalarType.UINT16,new long[]{1,2,65535,3}),
                    org.cbihi.mrinormalizer.domain.model.IntensityTransform.identityNotDeclared());
            real=new org.cbihi.mrinormalizer.infrastructure.filesystem.JsonManifestStore(source,output,
                    output.resolve("manifest"),job);
            var head=real.create(plan);
            running=real.append(new CheckpointRecord(2,job,1,head.headSha256(),time,
                    CheckpointRecord.Kind.JOB_OBSERVED,Optional.empty(),Optional.empty(),
                    Optional.of(ManifestState.JobState.RUNNING),List.of()),head);
        }
        VerifiedConversionRequest request(){
            return new VerifiedConversionRequest(op,List.of(new org.cbihi.mrinormalizer.domain.model.InputSource(
                    sourceFile.toString())),"1.2.3",destination,running);
        }
        org.cbihi.mrinormalizer.application.port.out.NiftiVolumeReader realReader(){
            return new org.cbihi.mrinormalizer.infrastructure.nifti.Nifti1VolumeReader(output,
                    new org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadLimits(
                            1_000_000,1_000_000,100_000,352,2_000_000,10_000));
        }
        VerifiedConversionResult execute(org.cbihi.mrinormalizer.application.port.out.NiftiVolumeReader reader,
                ManifestStore serviceStore, long sourceLimit,long stageLimit,
                org.cbihi.mrinormalizer.application.service.DicomSeriesService m6){
            try(var access=new org.cbihi.mrinormalizer.infrastructure.filesystem.NioVerifiedNiftiOutputAccess(
                    source,output,stageDirectory,real,sourceLimit,sourceLimit,stageLimit);
                var service=new VerifiedConversionEvidenceService(m6,
                        new org.cbihi.mrinormalizer.infrastructure.nifti.Nifti1VolumeWriter(),reader,
                        access,serviceStore,new NiftiOutputComparator(),java.time.Clock.systemUTC())){
                return service.executeOne(request());
            }
        }
        VerifiedConversionResult execute() {
            return execute(realReader(),real,1_000_000,1_000_000,
                    r->org.cbihi.mrinormalizer.application.result.DicomProcessingResult.success(volume,null));
        }
        @Override public void close(){real.close();}
    }
    private static void notPublished(Scenario fixture,VerifiedConversionResult result){
        assertNotEquals(VerifiedConversionResult.Status.VERIFIED_PUBLISHED,result.status());
        assertFalse(java.nio.file.Files.exists(fixture.finalFile));
    }
    @Test void T19_reportSuccessDoesNotVerifyOutput() throws Exception {
        try(var s=new Scenario()){
            var reader=(org.cbihi.mrinormalizer.application.port.out.NiftiVolumeReader) ref ->
                    new org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadResult(ref,Optional.empty(),
                        List.of(org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadFailure.GZIP_INTEGRITY_FAILED),List.of());
            var result=s.execute(reader,s.real,1_000_000,1_000_000,
                    r->org.cbihi.mrinormalizer.application.result.DicomProcessingResult.success(s.volume,null));
            notPublished(s,result);
            assertTrue(result.faults().contains(CheckpointRecord.ConversionFault.REOPEN_FAILED));
        }
    }
    @Test void T20_readerFailureAndWeakIdentityAreDistinguished() throws Exception {
        try(var s=new Scenario()){
            var delegated=s.realReader();
            var weak=(org.cbihi.mrinormalizer.application.port.out.NiftiVolumeReader) ref -> {
                var parsed=delegated.read(ref);
                var findings=new ArrayList<>(parsed.findings());
                findings.add(org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadFinding.WEAK_SOURCE_IDENTITY);
                return new org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadResult(
                        ref,parsed.volume(),parsed.failures(),findings);
            };
            var result=s.execute(weak,s.real,1_000_000,1_000_000,
                    r->org.cbihi.mrinormalizer.application.result.DicomProcessingResult.success(s.volume,null));
            assertEquals(VerifiedConversionResult.Status.VERIFIED_PUBLISHED,result.status());
            assertTrue(result.verification().orElseThrow().weakSourceIdentityObserved());
        }
        try(var s=new Scenario()){
            var interrupted=(org.cbihi.mrinormalizer.application.port.out.NiftiVolumeReader) ref ->
                    new org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadResult(ref,Optional.empty(),
                            List.of(org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadFailure.INTERRUPTED),List.of());
            var result=s.execute(interrupted,s.real,1_000_000,1_000_000,
                    r->org.cbihi.mrinormalizer.application.result.DicomProcessingResult.success(s.volume,null));
            notPublished(s,result);
            assertTrue(result.faults().contains(CheckpointRecord.ConversionFault.INTERRUPTED));
            assertTrue(Thread.currentThread().isInterrupted());
            Thread.interrupted(); // restore test-runner cleanliness; assertion above establishes propagation.
        }
    }
    @Test void T21_capturesSameM6VolumeUsedByM7() throws Exception {
        try(var s=new Scenario()){
            var calls=new int[1];
            var result=s.execute(s.realReader(),s.real,1_000_000,1_000_000, request->{
                calls[0]++;
                assertEquals(s.sourceFile.toString(),request.inputs().getFirst().reference());
                assertEquals("1.2.3",request.selectedSeriesInstanceUid());
                return org.cbihi.mrinormalizer.application.result.DicomProcessingResult.success(s.volume,null);
            });
            assertEquals(1,calls[0]);
            assertEquals(VerifiedConversionResult.Status.VERIFIED_PUBLISHED,result.status());
            assertEquals(4,result.verification().orElseThrow().comparedVoxelCount());
        }
    }
    @Test void T22_duplicateOrMismatchedCaptureRefused() throws Exception {
        try(var s=new Scenario()){
            var calls=new int[1];
            var result=s.execute(s.realReader(),s.real,1_000_000,1_000_000,request->{
                calls[0]++;
                return null; // zero successful M6 capture: no report can authorize publication.
            });
            assertEquals(1,calls[0]);
            notPublished(s,result);
            assertFalse(result.verification().isPresent());
        }
    }
    @Test void T24_planBindingRequired() throws Exception {
        try(var s=new Scenario()){
            var altered=java.nio.file.Files.write(s.sourceFile,new byte[]{1,2,3,4,6});
            var calls=new int[1];
            var result=s.execute(s.realReader(),s.real,1_000_000,1_000_000,r->{
                calls[0]++;
                return org.cbihi.mrinormalizer.application.result.DicomProcessingResult.success(s.volume,null);
            });
            notPublished(s,result);
            assertEquals(0,calls[0]);
            assertTrue(result.faults().contains(CheckpointRecord.ConversionFault.SOURCE_DIGEST_UNPROVEN));
        }
    }
    @Test void T29_resourceLimitsAreEnforced() throws Exception {
        try(var s=new Scenario()){
            var calls=new int[1];
            var r=s.execute(s.realReader(),s.real,4,1_000_000,q->{calls[0]++;
                return org.cbihi.mrinormalizer.application.result.DicomProcessingResult.success(s.volume,null);
            });
            notPublished(s,r);
            assertEquals(0,calls[0]);
            assertTrue(r.faults().contains(CheckpointRecord.ConversionFault.RESOURCE_LIMIT));
        }
        try(var s=new Scenario()){
            var r=s.execute(s.realReader(),s.real,1_000_000,100,
                    q->org.cbihi.mrinormalizer.application.result.DicomProcessingResult.success(s.volume,null));
            notPublished(s,r);
            assertTrue(r.faults().contains(CheckpointRecord.ConversionFault.RESOURCE_LIMIT));
        }
    }
    @Test void T31_existingOutputIsNotOverwritten() throws Exception {
        try(var s=new Scenario()){
            var sentinel=new byte[]{21,22,23,24};
            java.nio.file.Files.write(s.finalFile,sentinel);
            var calls=new int[1];
            var result=s.execute(s.realReader(),s.real,1_000_000,1_000_000,q->{calls[0]++;
                return org.cbihi.mrinormalizer.application.result.DicomProcessingResult.success(s.volume,null);
            });
            assertEquals(0,calls[0]);
            assertEquals(VerifiedConversionResult.Status.FAILED,result.status());
            assertEquals(VerifiedConversionResult.Publication.NOT_PUBLISHED,result.publication());
            assertTrue(result.faults().contains(CheckpointRecord.ConversionFault.OUTPUT_CONFLICT));
            assertArrayEquals(sentinel,java.nio.file.Files.readAllBytes(s.finalFile));
        }
    }
    @Test void T35_lostAcknowledgmentNeverClaimsSuccess() throws Exception {
        try(var s=new Scenario()){
            var faulty=new ManifestStore(){
                public ManifestReceipt create(ProvenanceManifest p){return s.real.create(p);}
                public ManifestReceipt append(CheckpointRecord record,ManifestReceipt head){
                    if(record.kind()==CheckpointRecord.Kind.OPERATION_OBSERVED &&
                            record.observation().orElseThrow().state()==CheckpointRecord.State.VERIFIED_PUBLISHED){
                        throw new ProvenancePersistenceException(List.of(new ManifestFailure(
                                ManifestFailure.Phase.PERSISTENCE,ManifestFailure.Code.RECOVERY_REQUIRED)),
                                ProvenancePersistenceException.PublicationOutcome.UNKNOWN,Optional.empty());
                    }
                    return s.real.append(record,head);
                }
                public Optional<ManifestState> replay(Optional<ManifestReceipt> receipt){return s.real.replay(receipt);}
                public void close(){} // borrowed real store is closed by Scenario.
            };
            var result=s.execute(s.realReader(),faulty,1_000_000,1_000_000,
                    q->org.cbihi.mrinormalizer.application.result.DicomProcessingResult.success(s.volume,null));
            assertEquals(VerifiedConversionResult.Status.RECOVERY_REQUIRED,result.status());
            assertEquals(VerifiedConversionResult.Publication.PUBLISHED_UNACKNOWLEDGED,result.publication());
            assertTrue(result.faults().contains(CheckpointRecord.ConversionFault.FINAL_ACK_FAILED));
            assertTrue(result.persistenceFailure().isPresent());
            assertEquals(ProvenancePersistenceException.PublicationOutcome.UNKNOWN,
                    result.persistenceFailure().orElseThrow().outcome());
            assertTrue(java.nio.file.Files.exists(s.finalFile));
            assertEquals(CheckpointRecord.State.STAGED_VERIFIED,
                    s.real.replay(Optional.empty()).orElseThrow().operations().getFirst().observation().state());
        }
    }

    private static ManifestStore injectedAppendFailure(Scenario s,
            CheckpointRecord.State state,ProvenancePersistenceException.PublicationOutcome outcome){
        return new ManifestStore(){
            public ManifestReceipt create(ProvenanceManifest p){return s.real.create(p);}
            public ManifestReceipt append(CheckpointRecord record,ManifestReceipt head){
                if(record.kind()==CheckpointRecord.Kind.OPERATION_OBSERVED
                        &&record.observation().orElseThrow().state()==state)
                    throw new ProvenancePersistenceException(List.of(new ManifestFailure(
                            ManifestFailure.Phase.PERSISTENCE,ManifestFailure.Code.RECOVERY_REQUIRED)),
                            outcome,Optional.empty());
                return s.real.append(record,head);
            }
            public Optional<ManifestState> replay(Optional<ManifestReceipt> head){return s.real.replay(head);}
            public void close(){} // scenario owns the real journal.
        };
    }
    @Test void T23_unacknowledgedIntentNeverStartsWrite() throws Exception {
        try(var s=new Scenario()){
            var calls=new int[1];
            var result=s.execute(s.realReader(),injectedAppendFailure(s,
                    CheckpointRecord.State.IN_PROGRESS,
                    ProvenancePersistenceException.PublicationOutcome.NOT_PUBLISHED),1_000_000,1_000_000,
                    r->{calls[0]++;return org.cbihi.mrinormalizer.application.result.DicomProcessingResult.success(s.volume,null);});
            assertEquals(0,calls[0]);
            notPublished(s,result);
            assertEquals(VerifiedConversionResult.Publication.NOT_PUBLISHED,result.publication());
            assertEquals(VerifiedConversionResult.Status.FAILED,result.status());
            assertEquals(0,s.real.replay(Optional.empty()).orElseThrow().operations().getFirst()
                    .observation().matchedExpectedSourceCount());
        }
    }
    @Test void T40_journalUncertaintyPreventsContinuation() throws Exception {
        try(var s=new Scenario()){
            var calls=new int[1];
            var result=s.execute(s.realReader(),injectedAppendFailure(s,
                    CheckpointRecord.State.IN_PROGRESS,
                    ProvenancePersistenceException.PublicationOutcome.UNKNOWN),1_000_000,1_000_000,
                    r->{calls[0]++;return org.cbihi.mrinormalizer.application.result.DicomProcessingResult.success(s.volume,null);});
            assertEquals(0,calls[0]);
            assertEquals(VerifiedConversionResult.Status.RECOVERY_REQUIRED,result.status());
            assertEquals(VerifiedConversionResult.Publication.UNKNOWN,result.publication());
            assertEquals(s.running,result.lastAcknowledgedHead().orElseThrow());
            assertEquals(ProvenancePersistenceException.PublicationOutcome.UNKNOWN,
                    result.persistenceFailure().orElseThrow().outcome());
            assertFalse(java.nio.file.Files.exists(s.finalFile));
        }
    }
    private static VerifiedConversionResult executeWithCleanupInjection(Scenario s,
            org.cbihi.mrinormalizer.application.port.out.NiftiVolumeReader reader,
            boolean injectCollision) throws Exception {
        return executeWithInjectedAccess(s,reader,injectCollision,false);
    }
    private static VerifiedConversionResult executeWithInjectedAccess(Scenario s,
            org.cbihi.mrinormalizer.application.port.out.NiftiVolumeReader reader,
            boolean injectCollision,boolean injectRelease) throws Exception {
        try(var nativeAccess=new org.cbihi.mrinormalizer.infrastructure.filesystem.NioVerifiedNiftiOutputAccess(
                s.source,s.output,s.stageDirectory,s.real,1_000_000,1_000_000,1_000_000)){
            VerifiedNiftiOutputAccess wrapper=new VerifiedNiftiOutputAccess(){
                public SourceBinding validateSelectedSources(List<org.cbihi.mrinormalizer.domain.model.InputSource> in,
                        List<SourceFileRecord> planned){return nativeAccess.validateSelectedSources(in,planned);}
                public PreparedOutput prepare(String op,RelativePath dest,ManifestReceipt head){
                    var inner=nativeAccess.prepare(op,dest,head);
                    return new PreparedOutput(){
                        public UUID sessionId(){return inner.sessionId();}
                        public org.cbihi.mrinormalizer.domain.model.OutputTarget stageTarget(){return inner.stageTarget();}
                        public RelativePath stageReadReference(){return inner.stageReadReference();}
                        public ContentDigest digestClosedStage(){return inner.digestClosedStage();}
                        public PublicationResult publishVerified(ManifestReceipt receipt){
                            if(injectCollision){
                                try {java.nio.file.Files.write(s.finalFile,new byte[]{61,62,63});}
                                catch(java.io.IOException e){throw new AccessFailure(List.of(AccessFault.PUBLICATION_UNAVAILABLE));}
                            }
                            return inner.publishVerified(receipt);
                        }
                        public void discardUnpublished(){
                            if(!injectCollision)throw new AccessFailure(List.of(AccessFault.CLEANUP_FAILED));
                            inner.discardUnpublished();
                        }
                        public void close(){
                            inner.close();
                            if(injectRelease)throw new AccessFailure(List.of(AccessFault.RELEASE_FAILED));
                        }
                    };
                }
                public void close(){nativeAccess.close();}
            };
            try(var service=new VerifiedConversionEvidenceService(
                    r->org.cbihi.mrinormalizer.application.result.DicomProcessingResult.success(s.volume,null),
                    new org.cbihi.mrinormalizer.infrastructure.nifti.Nifti1VolumeWriter(),
                    reader,wrapper,s.real,new NiftiOutputComparator(),java.time.Clock.systemUTC())){
                return service.executeOne(s.request());
            }
        }
    }
    @Test void T39_cleanupFailureDoesNotErasePrimary() throws Exception {
        try(var s=new Scenario()){
            var brokenReader=(org.cbihi.mrinormalizer.application.port.out.NiftiVolumeReader) ref ->
                    new org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadResult(ref,Optional.empty(),
                            List.of(org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadFailure.INVALID_HEADER),List.of());
            var result=executeWithCleanupInjection(s,brokenReader,false);
            assertEquals(VerifiedConversionResult.Status.RECOVERY_REQUIRED,result.status());
            assertTrue(result.faults().contains(CheckpointRecord.ConversionFault.REOPEN_FAILED));
            assertTrue(result.faults().contains(CheckpointRecord.ConversionFault.CLEANUP_FAILED));
            assertFalse(java.nio.file.Files.exists(s.finalFile));
        }
    }
    @Test void T31_competingPublicationCannotReplaceExisting() throws Exception {
        try(var s=new Scenario()){
            var result=executeWithCleanupInjection(s,s.realReader(),true);
            assertEquals(VerifiedConversionResult.Status.FAILED,result.status());
            assertEquals(VerifiedConversionResult.Publication.NOT_PUBLISHED,result.publication());
            assertTrue(result.faults().contains(CheckpointRecord.ConversionFault.OUTPUT_CONFLICT));
            assertArrayEquals(new byte[]{61,62,63},java.nio.file.Files.readAllBytes(s.finalFile));
        }
    }
    @Test void T29_readerResourceLimitIsEnforced() throws Exception {
        try(var s=new Scenario()){
            var limitReader=new org.cbihi.mrinormalizer.infrastructure.nifti.Nifti1VolumeReader(s.output,
                    new org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadLimits(
                            1_000_000,352,100_000,352,2_000_000,10_000));
            var result=s.execute(limitReader,s.real,1_000_000,1_000_000,
                    r->org.cbihi.mrinormalizer.application.result.DicomProcessingResult.success(s.volume,null));
            notPublished(s,result);
            assertTrue(result.faults().contains(CheckpointRecord.ConversionFault.RESOURCE_LIMIT));
        }
    }

    @Test void T27_corruptNativeStageIsNotPublished() throws Exception {
        try(var s=new Scenario()){
            var corrupt=(org.cbihi.mrinormalizer.application.port.out.NiftiVolumeReader) ref -> {
                // F7 sees actual corrupt bytes, not a fabricated positive NiftiReadResult.
                var stage=s.output.resolve(ref.path());
                try {java.nio.file.Files.write(stage,new byte[]{1,2,3});}
                catch(java.io.IOException e){
                    return new org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadResult(
                            ref,Optional.empty(),List.of(
                                org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadFailure.SOURCE_CHANGED),List.of());
                }
                return s.realReader().read(ref);
            };
            VerifiedConversionResult result=null;
            VerifiedNiftiOutputAccess.AccessFailure retainedStage=null;
            try {
                result=s.execute(corrupt,s.real,1_000_000,1_000_000,
                        r->org.cbihi.mrinormalizer.application.result.DicomProcessingResult.success(s.volume,null));
            } catch(VerifiedNiftiOutputAccess.AccessFailure controlled) {
                // A modified stage can legitimately refuse its own release/cleanup;
                // the controlled fault and retained stage are not a successful transaction.
                retainedStage=controlled;
            }
            assertFalse(java.nio.file.Files.exists(s.finalFile));
            if(retainedStage!=null)
                assertTrue(retainedStage.faults().contains(VerifiedNiftiOutputAccess.AccessFault.CLEANUP_FAILED));
            else {
                assertNotEquals(VerifiedConversionResult.Status.VERIFIED_PUBLISHED,result.status());
                assertTrue(result.faults().contains(CheckpointRecord.ConversionFault.REOPEN_FAILED)
                        ||result.faults().contains(CheckpointRecord.ConversionFault.STAGE_CHANGED));
            }
            assertNotEquals(CheckpointRecord.State.VERIFIED_PUBLISHED,
                    s.real.replay(Optional.empty()).orElseThrow().operations().getFirst().observation().state());
        }
    }
    @Test void T28_interruptOrCloseFailureFailsClosed() throws Exception {
        try(var s=new Scenario()){
            var closeFailure=(org.cbihi.mrinormalizer.application.port.out.NiftiVolumeReader) ref ->
                    new org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadResult(ref,Optional.empty(),
                            List.of(org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadFailure.CLOSE_FAILED),List.of());
            var result=s.execute(closeFailure,s.real,1_000_000,1_000_000,
                    r->org.cbihi.mrinormalizer.application.result.DicomProcessingResult.success(s.volume,null));
            notPublished(s,result);
            assertTrue(result.faults().contains(CheckpointRecord.ConversionFault.REOPEN_FAILED));
        }
        try(var s=new Scenario()){
            var result=executeWithInjectedAccess(s,s.realReader(),false,true);
            assertEquals(VerifiedConversionResult.Status.RECOVERY_REQUIRED,result.status());
            assertTrue(result.faults().contains(CheckpointRecord.ConversionFault.RELEASE_FAILED));
            assertTrue(java.nio.file.Files.exists(s.finalFile));
        }
    }

    @Test void T30_finalBytesMatchVerifiedStageAndHardlinkIdentity() throws Exception {
        try(var s=new Scenario();
            var nativeAccess=new org.cbihi.mrinormalizer.infrastructure.filesystem.NioVerifiedNiftiOutputAccess(
                s.source,s.output,s.stageDirectory,s.real,1_000_000,1_000_000,1_000_000)) {
            var count=new int[1];
            var hashes=new ArrayList<ContentDigest>();
            var sameFile=new boolean[1];
            VerifiedNiftiOutputAccess tracked=new VerifiedNiftiOutputAccess(){
                public SourceBinding validateSelectedSources(List<org.cbihi.mrinormalizer.domain.model.InputSource> in,
                        List<SourceFileRecord> src){return nativeAccess.validateSelectedSources(in,src);}
                public PreparedOutput prepare(String op,RelativePath dest,ManifestReceipt ack){
                    var owned=nativeAccess.prepare(op,dest,ack);
                    return new PreparedOutput(){
                        public UUID sessionId(){return owned.sessionId();}
                        public org.cbihi.mrinormalizer.domain.model.OutputTarget stageTarget(){return owned.stageTarget();}
                        public RelativePath stageReadReference(){return owned.stageReadReference();}
                        public ContentDigest digestClosedStage(){
                            var observed=owned.digestClosedStage();count[0]++;hashes.add(observed);
                            return observed;
                        }
                        public PublicationResult publishVerified(ManifestReceipt receipt){
                            assertEquals(2,count[0]);
                            assertEquals(hashes.getFirst(),hashes.getLast());
                            assertFalse(java.nio.file.Files.exists(s.finalFile));
                            var published=owned.publishVerified(receipt);
                            assertEquals(Publication.PUBLISHED_BYTES_CHECKED,published.publication());
                            assertEquals(hashes.getFirst(),published.finalDigest().orElseThrow());
                            try {
                                var path=java.nio.file.Path.of(stageTarget().reference());
                                sameFile[0]=java.nio.file.Files.isSameFile(path,s.finalFile);
                                var bytes=java.nio.file.Files.readAllBytes(s.finalFile);
                                var independent=new ContentDigest(bytes.length,java.util.HexFormat.of().formatHex(
                                        java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
                                assertEquals(independent,published.finalDigest().orElseThrow());
                            }catch(java.io.IOException|java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
                            return published;
                        }
                        public void discardUnpublished(){owned.discardUnpublished();}
                        public void close(){owned.close();}
                    };
                }
                public void close(){nativeAccess.close();}
            };
            try(var service=new VerifiedConversionEvidenceService(
                    r->org.cbihi.mrinormalizer.application.result.DicomProcessingResult.success(s.volume,null),
                    new org.cbihi.mrinormalizer.infrastructure.nifti.Nifti1VolumeWriter(),s.realReader(),
                    tracked,s.real,new NiftiOutputComparator(),java.time.Clock.systemUTC())){
                var result=service.executeOne(s.request());
                assertEquals(VerifiedConversionResult.Status.VERIFIED_PUBLISHED,result.status());
                assertEquals(2,count[0]);
                assertTrue(sameFile[0]);
                assertEquals(hashes.getFirst(),result.verification().orElseThrow().stagedDigest());
                assertEquals(hashes.getFirst(),result.finalDigest().orElseThrow());
                assertEquals(CheckpointRecord.State.VERIFIED_PUBLISHED,
                        s.real.replay(Optional.empty()).orElseThrow().operations().getFirst().observation().state());
            }
        }
    }

    @Test void T21_exactM6InstanceIsSuppliedToM7Writer() throws Exception {
        try(var s=new Scenario();
            var access=new org.cbihi.mrinormalizer.infrastructure.filesystem.NioVerifiedNiftiOutputAccess(
                    s.source,s.output,s.stageDirectory,s.real,1_000_000,1_000_000,1_000_000)) {
            var calls=new int[1];var writes=new int[1];
            org.cbihi.mrinormalizer.application.port.out.NiftiVolumeWriter observedWriter=(input,affine,target)->{
                writes[0]++;
                assertSame(s.volume,input); // identity, not merely pixel-wise equality
                new org.cbihi.mrinormalizer.infrastructure.nifti.Nifti1VolumeWriter().write(input,affine,target);
            };
            try(var service=new VerifiedConversionEvidenceService(r->{
                        calls[0]++;
                        return org.cbihi.mrinormalizer.application.result.DicomProcessingResult.success(s.volume,null);
                    },observedWriter,s.realReader(),access,s.real,new NiftiOutputComparator(),
                    java.time.Clock.systemUTC())) {
                var result=service.executeOne(s.request());
                assertEquals(1,calls[0]);
                assertEquals(1,writes[0]);
                assertEquals(VerifiedConversionResult.Status.VERIFIED_PUBLISHED,result.status());
                assertEquals(2,s.volume.voxels().rawValueAt(1,0,0));
            }
        }
    }

    @Test void T29_realF7EncodedVoxelAndWorkingLimitsRefusePublication() throws Exception {
        var limits=List.of(
                new org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadLimits(
                        352,1_000_000,100_000,352,2_000_000,10_000),
                new org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadLimits(
                        1_000_000,1_000_000,3,352,2_000_000,10_000),
                new org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadLimits(
                        1_000_000,1_000_000,100_000,352,128,10_000));
        for(var bound:limits){
            try(var s=new Scenario()){
                var reader=new org.cbihi.mrinormalizer.infrastructure.nifti.Nifti1VolumeReader(s.output,bound);
                var result=s.execute(reader,s.real,1_000_000,1_000_000,
                        q->org.cbihi.mrinormalizer.application.result.DicomProcessingResult.success(s.volume,null));
                notPublished(s,result);
                assertTrue(result.faults().contains(CheckpointRecord.ConversionFault.RESOURCE_LIMIT),
                        "F7 refused resource limit but F8 did not classify it: "+bound);
            }
        }
    }

    @Test void T35_finalAcknowledgmentOutcomesNeverInventSuccess() throws Exception {
        for(var outcome:List.of(ProvenancePersistenceException.PublicationOutcome.NOT_PUBLISHED,
                ProvenancePersistenceException.PublicationOutcome.PUBLISHED)){
            try(var s=new Scenario()){
                var interruptedStore=new ManifestStore(){
                    public ManifestReceipt create(ProvenanceManifest p){return s.real.create(p);}
                    public ManifestReceipt append(CheckpointRecord record,ManifestReceipt head){
                        if(record.kind()==CheckpointRecord.Kind.OPERATION_OBSERVED
                                &&record.observation().orElseThrow().state()==CheckpointRecord.State.VERIFIED_PUBLISHED){
                            Optional<ManifestReceipt> known=outcome==ProvenancePersistenceException.PublicationOutcome.PUBLISHED
                                    ?Optional.of(s.real.append(record,head)):Optional.empty();
                            throw new ProvenancePersistenceException(List.of(new ManifestFailure(
                                    ManifestFailure.Phase.PERSISTENCE,ManifestFailure.Code.RECOVERY_REQUIRED)),outcome,known);
                        }
                        return s.real.append(record,head);
                    }
                    public Optional<ManifestState> replay(Optional<ManifestReceipt> h){return s.real.replay(h);}
                    public void close(){}
                };
                var result=s.execute(s.realReader(),interruptedStore,1_000_000,1_000_000,
                        r->org.cbihi.mrinormalizer.application.result.DicomProcessingResult.success(s.volume,null));
                assertEquals(VerifiedConversionResult.Status.RECOVERY_REQUIRED,result.status());
                assertEquals(VerifiedConversionResult.Publication.PUBLISHED_UNACKNOWLEDGED,result.publication());
                assertTrue(result.faults().contains(CheckpointRecord.ConversionFault.FINAL_ACK_FAILED));
                assertEquals(outcome,result.persistenceFailure().orElseThrow().outcome());
                assertTrue(java.nio.file.Files.exists(s.finalFile));
                var lastState=s.real.replay(Optional.empty()).orElseThrow().operations().getFirst().observation().state();
                assertEquals(outcome==ProvenancePersistenceException.PublicationOutcome.PUBLISHED
                        ?CheckpointRecord.State.VERIFIED_PUBLISHED:CheckpointRecord.State.STAGED_VERIFIED,lastState);
            }
        }
    }
}
