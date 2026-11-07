package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.*;
import org.cbihi.mrinormalizer.application.dataset.model.*;
import org.cbihi.mrinormalizer.domain.model.*;

class ProvenanceV2ConversionTest {
    private static final String HASH="a".repeat(64),OP="b".repeat(64);
    private static final UUID JOB=UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final Instant TIME=Instant.parse("2026-10-22T00:00:00Z");
    private static ProvenanceManifest plan(int v) {
        var src=new RelativePath(RelativePath.Root.SOURCE,"source.dcm");
        var assessment=new FormatAssessment(DetectionResult.identified(DetectionOutcome.DICOM),
                ImagingFormat.DICOM,FormatVariant.DICOM_UNSPECIFIED,ValidityStatus.VALID,
                SupportStatus.SUPPORTED,ConversionReadiness.READY,List.of());
        return new ProvenanceManifest(v,JOB,TIME,
                List.of(new SourceFileRecord(src,Optional.of(new ContentDigest(1,HASH)),assessment,List.of())),
                List.of(new ManifestOperation(OP,ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI,
                        List.of(src),new RelativePath(RelativePath.Root.OUTPUT,"a.nii"))));
    }
    private static CheckpointRecord next(ManifestReplay replay,int v,State state,Observation observation) {
        var head=replay.current().receipt();
        return new CheckpointRecord(v,JOB,head.sequence()+1,head.headSha256(),TIME,Kind.OPERATION_OBSERVED,
                Optional.of(OP),Optional.of(observation),Optional.empty(),List.of());
    }
    @Test void T34_versionedPlanAndObservationCompatibility() {
        assertEquals(1,plan(1).schemaVersion());assertEquals(2,plan(2).schemaVersion());
        var old=new Observation(State.IN_PROGRESS,Optional.of(TIME),Optional.empty(),Optional.empty(),1,Optional.empty(),Optional.empty());
        assertTrue(old.postWriteVerification().isEmpty());assertTrue(old.postWriteFaults().isEmpty());
        assertThrows(IllegalArgumentException.class,()->new CheckpointRecord(1,JOB,1,HASH,TIME,Kind.OPERATION_OBSERVED,
                Optional.of(OP),Optional.of(new Observation(State.STAGED_VERIFIED,Optional.of(TIME),Optional.of(TIME),
                        Optional.of(new ContentDigest(1,HASH)),1,Optional.empty(),Optional.empty())),Optional.empty(),List.of()));
    }
    @Test void T35_v2RunningAndDigestIntentRequired() {
        var replay=new ManifestReplay(plan(2),HASH);
        var head=replay.current().receipt();
        var running=new CheckpointRecord(2,JOB,1,head.headSha256(),TIME,Kind.JOB_OBSERVED,
                Optional.empty(),Optional.empty(),Optional.of(ManifestState.JobState.RUNNING),List.of());
        replay.accept(running,"c".repeat(64));
        var inProgress=new Observation(State.IN_PROGRESS,Optional.of(TIME),Optional.empty(),Optional.empty(),1,
                Optional.empty(),Optional.empty());
        replay.accept(next(replay,2,State.IN_PROGRESS,inProgress),"d".repeat(64));
        assertThrows(IllegalArgumentException.class,()->replay.accept(next(replay,2,State.STAGED_VERIFIED,
                new Observation(State.STAGED_VERIFIED,Optional.of(TIME),Optional.of(TIME),
                        Optional.of(new ContentDigest(1,HASH)),1,Optional.empty(),Optional.empty(),
                        Optional.of(new ConversionVerification(UUID.randomUUID(),new ContentDigest(1,HASH),1,8,0,false,"F8-NIFTI1-F32-1")),List.of())),"e".repeat(64)));
    }
    @Test void T38_publicV2MustNotBeExported() {
        var replay=new ManifestReplay(plan(2),HASH);
        assertThrows(IllegalArgumentException.class,()->ManifestProjection.publicReport(replay.current()));
    }

    @Test void T33_crashBoundaryStatesRemainTruthful() {
        var replay=new ManifestReplay(plan(2),HASH);
        var head=replay.current().receipt();
        replay.accept(new CheckpointRecord(2,JOB,1,head.headSha256(),TIME,Kind.JOB_OBSERVED,
                Optional.empty(),Optional.empty(),Optional.of(ManifestState.JobState.RUNNING),List.of()),"c".repeat(64));
        var running=new Observation(State.IN_PROGRESS,Optional.of(TIME),Optional.empty(),Optional.empty(),1,
                Optional.empty(),Optional.empty());
        replay.accept(next(replay,2,State.IN_PROGRESS,running),"d".repeat(64));
        assertEquals(State.IN_PROGRESS,replay.current().operations().getFirst().observation().state());
        assertThrows(IllegalArgumentException.class,()->next(replay,2,State.VERIFIED_PUBLISHED,
                new Observation(State.VERIFIED_PUBLISHED,Optional.of(TIME),Optional.of(TIME),
                        Optional.of(new ContentDigest(1,HASH)),1,Optional.empty(),Optional.empty())));
    }
    @Test void T34_stagedVerificationIsNotPublication() {
        var staged=new Observation(State.WRITTEN_UNVERIFIED,Optional.of(TIME),Optional.of(TIME),
                Optional.of(new ContentDigest(1,HASH)),1,Optional.empty(),
                Optional.of(ManifestProjection.conversion(
                        org.cbihi.mrinormalizer.application.result.DicomToNiftiResult.success(
                                new OutputTarget("stage.nii"),null,
                                new org.cbihi.mrinormalizer.application.validation.ConversionValidationReport(
                                        new OutputTarget("stage.nii"),1,1,1,ScalarType.INT16,1,
                                        1,1,1,new AffineMatrix4(new double[][]{
                                            {1,0,0,0},{0,1,0,0},{0,0,1,0},{0,0,0,1}}),
                                        IntensityTransform.identityNotDeclared(),true,false,false,false)))));
        assertTrue(staged.postWriteVerification().isEmpty());
        assertThrows(IllegalArgumentException.class,()->new Observation(State.STAGED_VERIFIED,
                staged.startedAt(),staged.finishedAt(),staged.outputDigest(),1,Optional.empty(),
                staged.processingEvidence()));
        assertThrows(IllegalArgumentException.class,()->new Observation(State.VERIFIED_PUBLISHED,
                staged.startedAt(),staged.finishedAt(),staged.outputDigest(),1,Optional.empty(),
                staged.processingEvidence()));
    }
    @Test void T36_v1GoldenBytesAreIdentical() throws Exception {
        var folder=java.nio.file.Files.createTempDirectory("f8-v1-golden-");
        var in=java.nio.file.Files.createDirectory(folder.resolve("source"));
        var out=java.nio.file.Files.createDirectory(folder.resolve("output"));
        var legacy=new ProvenanceManifest(1,new UUID(0,1),
                Instant.parse("2026-10-04T00:00:00Z"),List.of(),List.of());
        try(var store=new org.cbihi.mrinormalizer.infrastructure.filesystem.JsonManifestStore(
                in,out,out.resolve("manifest"),legacy.jobId())){
            store.create(legacy);
            var bytes=java.nio.file.Files.readAllBytes(out.resolve("manifest").resolve(legacy.jobId().toString()).resolve("plan.json"));
            String expected="{\"schema\":\"org.cbihi.mrinormalizer.provenance-plan\",\"schemaVersion\":1,"
                    +"\"jobId\":\"00000000-0000-0000-0000-000000000001\","
                    +"\"createdAt\":\"2026-10-04T00:00:00Z\",\"roots\":[\"SOURCE\",\"OUTPUT\"],"
                    +"\"sources\":[],\"operations\":[]}\n";
            assertArrayEquals(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),bytes);
        }
    }
    @Test void T37_versionMixingFailsClosed() {
        for(int version:new int[]{1,2}){
            var replay=new ManifestReplay(plan(version),HASH);
            var foreign=new CheckpointRecord(3-version,JOB,1,HASH,TIME,Kind.JOB_OBSERVED,
                    Optional.empty(),Optional.empty(),Optional.of(ManifestState.JobState.RUNNING),List.of());
            assertThrows(IllegalArgumentException.class,()->replay.accept(foreign,"c".repeat(64)));
            assertEquals(0,replay.current().receipt().sequence());
        }
        var old=new Observation(State.IN_PROGRESS,Optional.of(TIME),Optional.empty(),Optional.empty(),1,
                Optional.empty(),Optional.empty());
        var incompatible=new CheckpointRecord.ConversionVerification(UUID.randomUUID(),
                new ContentDigest(1,HASH),1,8,0,false,"F8-NIFTI1-F32-1");
        assertThrows(IllegalArgumentException.class,()->new CheckpointRecord(1,JOB,1,HASH,TIME,
                Kind.OPERATION_OBSERVED,Optional.of(OP),Optional.of(new Observation(
                        State.FAILED,Optional.of(TIME),Optional.of(TIME),Optional.empty(),1,
                        Optional.empty(),Optional.empty(),Optional.of(incompatible),
                        List.of(ConversionFault.OUTPUT_CONFLICT))),Optional.empty(),List.of()));
    }
    @Test void T38_jobCompletionRequiresAcknowledgedVerification() {
        var replay=new ManifestReplay(plan(2),HASH);
        var head=replay.current().receipt();
        replay.accept(new CheckpointRecord(2,JOB,1,head.headSha256(),TIME,Kind.JOB_OBSERVED,
                Optional.empty(),Optional.empty(),Optional.of(ManifestState.JobState.RUNNING),List.of()),"c".repeat(64));
        head=replay.current().receipt();
        var premature=new CheckpointRecord(2,JOB,2,head.headSha256(),TIME,Kind.JOB_OBSERVED,
                Optional.empty(),Optional.empty(),Optional.of(ManifestState.JobState.COMPLETED),List.of());
        assertThrows(IllegalArgumentException.class,()->replay.accept(premature,"d".repeat(64)));
        assertEquals(ManifestState.JobState.RUNNING,replay.current().state());
        assertThrows(IllegalArgumentException.class,()->ManifestProjection.publicReport(replay.current()));
    }
}
