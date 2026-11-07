package org.cbihi.mrinormalizer.infrastructure.filesystem;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.cbihi.mrinormalizer.infrastructure.provenance.M8JournalCodec;
import org.cbihi.mrinormalizer.application.conversion.M8GeometryPolicy;
import org.cbihi.mrinormalizer.application.m8.*;
import org.cbihi.mrinormalizer.application.port.out.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;
import org.cbihi.mrinormalizer.application.service.M8ConversionService;
import org.cbihi.mrinormalizer.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Actual job-owned filesystem and journal fault cases; no dcm4che required by these tests. */
final class M8RecoveryTest {
    @TempDir Path root;
    private static M8Request request(){return new M8Request(UUID.randomUUID(),
        new RelativePath(RelativePath.Root.SOURCE,"a.nii"),
        new M8Metadata(true,true,M8Metadata.ScanningSequence.SE,M8Metadata.SequenceVariant.NONE));}
    private static byte[] nifti(){
        ByteBuffer b=ByteBuffer.allocate(360).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0,348);b.putShort(40,(short)3);
        b.putShort(42,(short)2);b.putShort(44,(short)2);b.putShort(46,(short)2);
        b.putShort(70,(short)2);b.putShort(72,(short)8);
        b.putFloat(76,1);b.putFloat(80,1);b.putFloat(84,1);b.putFloat(88,1);
        b.putFloat(108,352);b.put(123,(byte)2);b.putShort(254,(short)1);
        b.putFloat(280,1);b.putFloat(300,1);b.putFloat(320,1);
        b.put(344,(byte)'n');b.put(345,(byte)'+');b.put(346,(byte)'1');
        for(int i=0;i<8;i++)b.put(352+i,(byte)(i+1));
        return b.array();
    }
    private static Path[] roots(Path parent)throws Exception{
        Path in=Files.createDirectory(parent.resolve("source"));
        Path out=Files.createDirectory(parent.resolve("output"));
        Files.write(in.resolve("a.nii"),nifti());
        return new Path[]{in,out};
    }
    private static ImageVolume volume(){
        VolumeGeometry g=new VolumeGeometry(2,2,2,1,1,1,new double[]{0,0,0},
            new double[]{1,0,0},new double[]{0,1,0},new double[]{0,0,1},
            CoordinateSystem.NIFTI_RAS);
        return new ImageVolume(g,new ImmutableVoxelData(2,2,2,ScalarType.UINT8,
            new long[]{1,2,3,4,5,6,7,8}),IntensityTransform.identityNotDeclared());
    }
    private static M8Plan plan(M8Request request,M8Workspace.Session session){
        return new M8GeometryPolicy(M8Limits.defaults(),UUID::randomUUID).plan(
            request,volume(),List.of(),session.sourceDigest(),Instant.EPOCH);
    }
    private static M8ConversionService service(NioM8Workspace workspace){
        DicomMrSliceWriter writer=(src,plan,z,stream)->{
            try{stream.write(new byte[]{(byte)(z+1),42});}
            catch(IOException ioe){throw M8Fault.fail(M8Fault.Phase.WRITE,M8Fault.Code.WRITE_FAILED);}
        };
        DicomMrSliceVerifier verifier=(src,plan,z,stream)->{
            try{byte[] b=stream.readAllBytes();
                if(b.length!=2||b[0]!=(byte)(z+1))throw new IOException("Modified synthetic slice");}
            catch(IOException ioe){throw M8Fault.fail(M8Fault.Phase.VERIFY,M8Fault.Code.VERIFICATION_FAILED);}
            return new DicomMrSliceVerifier.Check(4,4,0,0);
        };
        return new M8ConversionService(workspace,writer,verifier,
            new M8GeometryPolicy(M8Limits.defaults(),UUID::randomUUID),Clock.systemUTC());
    }
    @Test void m8_20_unexpectedSnapshotRuntimeCannotBeMisreportedAsRejected()throws Exception{
        Path[] dirs=roots(root);M8Request request=request();
        var workspace=new NioM8Workspace(dirs[0],dirs[1],M8Limits.defaults(),Clock.systemUTC(),
            (phase,z)->{if(phase.equals("snapshot-force"))throw new IllegalStateException("unexpected");});
        M8Result result=service(workspace).execute(request);
        assertEquals(M8Result.Status.RECOVERY_REQUIRED,result.status(),
            "Effects uncertainty must survive workspace.open failure before session return");
        assertEquals(M8Fault.Code.INTERNAL_ERROR,result.faults().getFirst().code());
        assertFalse(Files.exists(dirs[1].resolve("m8-"+request.jobId())));
    }
    @Test void m8_20_privateDirectoryFailureIsClassifiedAsWriteActivity()throws Exception{
        Path[] dirs=roots(root);M8Request request=request();
        var workspace=new NioM8Workspace(dirs[0],dirs[1],M8Limits.defaults(),Clock.systemUTC(),
            (phase,z)->{if(phase.equals("create-private"))throw new IOException("injected create failure");});
        M8Result result=service(workspace).execute(request);
        assertEquals(M8Result.Status.REJECTED,result.status());
        assertEquals(new M8Fault(M8Fault.Phase.WRITE,M8Fault.Code.WRITE_FAILED),
            result.faults().getFirst());
        assertFalse(Files.exists(dirs[1].resolve(".pai-m8-"+request.jobId())));
    }
    @Test void m8_20_snapshotActivityFaultsHavePrecisePhasesAndNoFalseRecovery()throws Exception{
        record Case(String boundary,M8Fault.Phase phase,M8Fault.Code code,
                    M8Result.Status status) {}
        var cases=List.of(
            new Case("snapshot-open",M8Fault.Phase.SOURCE,M8Fault.Code.READ_FAILED,M8Result.Status.REJECTED),
            new Case("snapshot-source-read",M8Fault.Phase.SOURCE,M8Fault.Code.READ_FAILED,M8Result.Status.REJECTED),
            new Case("snapshot-destination-open",M8Fault.Phase.WRITE,M8Fault.Code.WRITE_FAILED,M8Result.Status.REJECTED),
            new Case("snapshot-write",M8Fault.Phase.WRITE,M8Fault.Code.WRITE_FAILED,M8Result.Status.REJECTED),
            new Case("snapshot-force",M8Fault.Phase.WRITE,M8Fault.Code.WRITE_FAILED,M8Result.Status.REJECTED),
            new Case("snapshot-source-reread",M8Fault.Phase.SOURCE,M8Fault.Code.READ_FAILED,M8Result.Status.REJECTED),
            new Case("snapshot-destination-close",M8Fault.Phase.WRITE,M8Fault.Code.WRITE_FAILED,M8Result.Status.REJECTED),
            new Case("snapshot-source-close",M8Fault.Phase.RELEASE,M8Fault.Code.RELEASE_FAILED,M8Result.Status.RECOVERY_REQUIRED)
        );
        int index=0;
        for(Case c:cases){
            Path parent=Files.createDirectory(root.resolve("snapshot-boundary-"+(index++)));
            Path[] dirs=roots(parent);M8Request request=request();
            byte[] initial=Files.readAllBytes(dirs[0].resolve("a.nii"));
            var workspace=new NioM8Workspace(dirs[0],dirs[1],M8Limits.defaults(),Clock.systemUTC(),
                (phase,z)->{if(phase.equals(c.boundary()))throw new IOException("injected bounded failure");});
            M8Result result=service(workspace).execute(request);
            assertEquals(c.status(),result.status(),c.boundary());
            assertEquals(new M8Fault(c.phase(),c.code()),result.faults().getFirst(),c.boundary());
            assertEquals(M8Result.Publication.NONE,result.publication(),c.boundary());
            assertFalse(Files.exists(dirs[1].resolve("m8-"+request.jobId())),c.boundary());
            assertFalse(Files.exists(dirs[1].resolve(".pai-m8-"+request.jobId())),c.boundary());
            assertArrayEquals(initial,Files.readAllBytes(dirs[0].resolve("a.nii")),c.boundary());
        }
        assertEquals(8,index);
    }
    @Test void m8_20_primaryCopyFailureAndActualSourceCloseFailureRequireRecovery()throws Exception{
        Path[] dirs=roots(root);M8Request request=request();
        byte[] original=Files.readAllBytes(dirs[0].resolve("a.nii"));
        java.util.List<String> visited=new java.util.ArrayList<>();
        var workspace=new NioM8Workspace(dirs[0],dirs[1],M8Limits.defaults(),Clock.systemUTC(),
            (phase,z)->{
                visited.add(phase);
                if(phase.equals("snapshot-write")||phase.equals("snapshot-source-close"))
                    throw new IOException("controlled fault at "+phase);
            });
        M8Result result=service(workspace).execute(request);
        assertTrue(visited.contains("snapshot-write"));
        assertTrue(visited.contains("snapshot-source-close"),
            "The release hook must run while unwinding the primary I/O failure");
        assertEquals(M8Result.Status.RECOVERY_REQUIRED,result.status(),
            "An actual release-path failure cannot be downgraded to REJECTED");
        assertEquals(new M8Fault(M8Fault.Phase.WRITE,M8Fault.Code.WRITE_FAILED),
            result.faults().getFirst(),"Keep original primary write fault");
        assertTrue(result.faults().contains(new M8Fault(M8Fault.Phase.RELEASE,
            M8Fault.Code.RELEASE_FAILED)),"Preserve secondary close failure");
        assertEquals(M8Result.Publication.NONE,result.publication());
        assertFalse(Files.exists(dirs[1].resolve("m8-"+request.jobId())));
        assertFalse(Files.exists(dirs[1].resolve(".pai-m8-"+request.jobId())));
        assertArrayEquals(original,Files.readAllBytes(dirs[0].resolve("a.nii")));
    }
    @Test void m8_20_primaryReadFailureAndActualDestinationCloseFailureRequireRecovery()throws Exception{
        Path[] dirs=roots(root);M8Request request=request();
        java.util.List<String> visited=new java.util.ArrayList<>();
        var workspace=new NioM8Workspace(dirs[0],dirs[1],M8Limits.defaults(),Clock.systemUTC(),
            (phase,z)->{
                visited.add(phase);
                if(phase.equals("snapshot-source-read")||phase.equals("snapshot-destination-close"))
                    throw new IOException("controlled fault at "+phase);
            });
        M8Result result=service(workspace).execute(request);
        assertTrue(visited.contains("snapshot-source-read"));
        assertTrue(visited.contains("snapshot-destination-close"),
            "Writer close must run during earlier read fault");
        assertEquals(M8Result.Status.RECOVERY_REQUIRED,result.status());
        assertEquals(new M8Fault(M8Fault.Phase.SOURCE,M8Fault.Code.READ_FAILED),
            result.faults().getFirst());
        assertTrue(result.faults().contains(new M8Fault(M8Fault.Phase.WRITE,
            M8Fault.Code.WRITE_FAILED)),"Preserve secondary writing-stream close failure");
        assertEquals(M8Result.Publication.NONE,result.publication());
        assertFalse(Files.exists(dirs[1].resolve("m8-"+request.jobId())));
    }
    @Test void m8_20_closeFailureSurvivesSuppressionDisabledOnControlledPrimary()throws Exception{
        Path[] dirs=roots(root);M8Request request=request();
        var workspace=new NioM8Workspace(dirs[0],dirs[1],M8Limits.defaults(),Clock.systemUTC(),
            (phase,z)->{
                if(phase.equals("snapshot-write"))
                    throw M8Fault.fail(M8Fault.Phase.WRITE,M8Fault.Code.RESOURCE_LIMIT);
                if(phase.equals("snapshot-source-close"))
                    throw new IOException("controlled release failure after physical close");
            });
        M8Result result=service(workspace).execute(request);
        assertEquals(M8Result.Status.RECOVERY_REQUIRED,result.status());
        assertEquals(new M8Fault(M8Fault.Phase.WRITE,M8Fault.Code.RESOURCE_LIMIT),
            result.faults().getFirst(),"Preserve typed primary failure");
        assertTrue(result.faults().contains(new M8Fault(M8Fault.Phase.RELEASE,
            M8Fault.Code.RELEASE_FAILED)),"Secondary release fact must survive disabled suppression");
        assertEquals(M8Result.Publication.NONE,result.publication());
        assertFalse(Files.exists(dirs[1].resolve("m8-"+request.jobId())));
    }
    @Test void m8_24_trailingTornCheckpointInvalidatesJournalReplay()throws Exception{
        Path[] dirs=roots(root);M8Request request=request();
        var workspace=new NioM8Workspace(dirs[0],dirs[1],M8Limits.defaults(),Clock.systemUTC());
        try(var session=workspace.open(request)){
            M8Plan p=plan(request,session);
            var receipt=session.acknowledge(M8Workspace.Event.INTENT,p,List.of(),List.of());
            Path privateRoot=dirs[1].resolve(".pai-m8-"+request.jobId());
            Files.writeString(privateRoot.resolve("checkpoint-000002.json"),"{\"torn\":true}");
            assertThrows(M8Fault.Failure.class,()->session.replay(receipt));
            assertFalse(Files.exists(dirs[1].resolve("m8-"+request.jobId())));
        }
    }
    @Test void m8_20_createdButUnverifiedStageIsRemovedBeforeFailed()throws Exception{
        Path[] dirs=roots(root);M8Request request=request();
        var workspace=new NioM8Workspace(dirs[0],dirs[1],M8Limits.defaults(),Clock.systemUTC());
        try(var session=workspace.open(request)){
            M8Plan p=plan(request,session);
            session.acknowledge(M8Workspace.Event.INTENT,p,List.of(),List.of());
            try(var bytes=session.createStage(0)){bytes.write(17);}
            Path stage=dirs[1].resolve(".pai-m8-"+request.jobId()).resolve("image-000001.dcm");
            assertTrue(Files.exists(stage));
            session.cleanupOwnedData();
            assertFalse(Files.exists(stage),"Created stage must not be ignored just because not digested");
            var receipt=session.acknowledge(M8Workspace.Event.FAILED,p,List.of(),List.of(
                new M8Fault(M8Fault.Phase.WRITE,M8Fault.Code.WRITE_FAILED)));
            assertEquals(M8Workspace.Event.FAILED,receipt.event());
        }
    }
    @Test void m8_20_actualInjectedStageWriteFaultDoesNotLeaveUntrackedFile()throws Exception{
        Path[] dirs=roots(root);M8Request request=request();
        var workspace=new NioM8Workspace(dirs[0],dirs[1],M8Limits.defaults(),Clock.systemUTC(),
            (phase,z)->{if(phase.equals("stage-write"))throw new IOException("injected write fault");});
        try(var session=workspace.open(request)){
            M8Plan p=plan(request,session);
            session.acknowledge(M8Workspace.Event.INTENT,p,List.of(),List.of());
            assertThrows(IOException.class,()->{try(var stream=session.createStage(0)){stream.write(42);}});
            session.cleanupOwnedData();
            assertFalse(Files.exists(dirs[1].resolve(".pai-m8-"+request.jobId())
                    .resolve("image-000001.dcm")));
        }
    }
    @Test void m8_16_snapshotFailureDoesNotDeleteOtherEntries()throws Exception{
        Path[] dirs=roots(root);M8Request request=request();
        var workspace=new NioM8Workspace(dirs[0],dirs[1],M8Limits.defaults(),Clock.systemUTC(),
            (phase,z)->{if(phase.equals("snapshot-force"))throw new IOException("injected force fault");});
        assertThrows(M8Fault.Failure.class,()->workspace.open(request));
        assertArrayEquals(nifti(),Files.readAllBytes(dirs[0].resolve("a.nii")));
        assertFalse(Files.exists(dirs[1].resolve(".pai-m8-"+request.jobId())));
    }
    @Test void m8_26_linkFailureProducesRecoveryRequiredAndNoMarker()throws Exception{
        Path[] dirs=roots(root);M8Request request=request();
        var workspace=new NioM8Workspace(dirs[0],dirs[1],M8Limits.defaults(),Clock.systemUTC(),
            (phase,z)->{if(phase.equals("link")&&z==1)throw new IOException("injected second link fault");});
        var result=service(workspace).execute(request);
        assertEquals(M8Result.Status.RECOVERY_REQUIRED,result.status());
        assertNotEquals(M8Result.Publication.COMPLETE_BYTES_VERIFIED,result.publication());
        Path published=dirs[1].resolve("m8-"+request.jobId());
        assertTrue(Files.exists(published.resolve("image-000001.dcm")));
        assertFalse(Files.exists(published.resolve("image-000002.dcm")));
        assertFalse(Files.exists(published.resolve("PAI-M8-COMPLETE.json")));
    }
    @Test void m8_26_markerCorruptionAtFinalReadbackCannotReportSuccess()throws Exception{
        Path[] dirs=roots(root);M8Request request=request();
        var workspace=new NioM8Workspace(dirs[0],dirs[1],M8Limits.defaults(),Clock.systemUTC(),
            (phase,z)->{
                if(phase.equals("final-readback")){
                    Path marker=dirs[1].resolve("m8-"+request.jobId()).resolve("PAI-M8-COMPLETE.json");
                    if(Files.isRegularFile(marker)){
                        try(var ch=Files.newByteChannel(marker,StandardOpenOption.WRITE)){
                            ch.write(ByteBuffer.wrap(new byte[]{'X'}));
                        }
                    }
                }
            });
        var result=service(workspace).execute(request);
        assertEquals(M8Result.Status.RECOVERY_REQUIRED,result.status());
        assertFalse(result.status()==M8Result.Status.SUCCESS);
        assertTrue(Files.isRegularFile(dirs[1].resolve("m8-"+request.jobId())
            .resolve("PAI-M8-COMPLETE.json")));
    }
    @Test void m8_24_journalReceiptAloneCannotConstructSuccessfulResult(){
        var receipt=new M8Workspace.Receipt(UUID.randomUUID(),5,"0".repeat(64),M8Workspace.Event.COMPLETED);
        assertThrows(IllegalArgumentException.class,()->new M8Result(receipt.jobId(),M8Result.Status.SUCCESS,
            M8Result.Publication.PARTIAL_OR_UNKNOWN,Optional.empty(),Optional.empty(),Optional.of(receipt),
            List.of(),List.of(),List.of(),List.of()));
    }
    @Test void m8_24_acknowledgmentBoundaryFaultsNeverReportSuccess()throws Exception{
        int cases=0;
        for(int sequence=1;sequence<=5;sequence++)
            for(String point:new String[]{"checkpoint-create","checkpoint-force","checkpoint-readback"}){
                Path parent=Files.createDirectory(root.resolve("ack-"+(cases++)));
                Path[] dirs=roots(parent);M8Request request=request();
                final int failAt=sequence;final String failPoint=point;
                var workspace=new NioM8Workspace(dirs[0],dirs[1],M8Limits.defaults(),Clock.systemUTC(),
                    (phase,z)->{if(phase.equals(failPoint)&&z==failAt)
                        throw new IOException("injected acknowledgment fault");});
                M8Result result=service(workspace).execute(request);
                assertEquals(M8Result.Status.RECOVERY_REQUIRED,result.status(),
                    "Acknowledgment ambiguity requires recovery at checkpoint "+sequence+" / "+point);
                assertEquals(new M8Fault(M8Fault.Phase.JOURNAL,M8Fault.Code.ACK_UNCERTAIN),
                    result.faults().getFirst(),"Preserve primary acknowledgment fault");
                Path privateRoot=dirs[1].resolve(".pai-m8-"+request.jobId());
                Path publicRoot=dirs[1].resolve("m8-"+request.jobId());
                assertFalse(Files.exists(publicRoot.resolve("PAI-M8-COMPLETE.json")),
                    "Acknowledgment uncertainty cannot create success marker");
                assertTrue(Files.exists(privateRoot),"Uncertain journal retained for review");
                assertEquals(sequence>=4,Files.isDirectory(publicRoot),
                    "Public-series visibility must be consistent with the fault boundary");
                if(sequence>=4){
                    assertTrue(Files.isRegularFile(publicRoot.resolve("image-000001.dcm")));
                    assertTrue(Files.isRegularFile(publicRoot.resolve("image-000002.dcm")));
                    assertEquals(M8Result.Publication.COMPLETE_BYTES_VERIFIED,result.publication(),
                        "Complete bytes may be known despite uncertain acknowledgment");
                }else{
                    assertEquals(M8Result.Publication.NONE,result.publication(),
                        "No public link exists before successful PUBLISHING acknowledgment");
                }
            }
        assertEquals(15,cases);
    }
    /** A separate, read-only, bounded post-crash J/M/I inspection. No workspace
     * method, resume action or production marker builder is used here. */
    private record Survivor(boolean journal,boolean marker,boolean inventory) {
        boolean artifactComplete(){return journal&&marker&&inventory;}
    }
    private static String sha256(byte[] bytes)throws Exception{
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static boolean legalSurvivorEvent(M8Workspace.Event previous,M8Workspace.Event current){
        return switch(current){
            case INTENT -> previous==null;
            case STAGED_VERIFIED -> previous==M8Workspace.Event.INTENT;
            case PUBLISHING -> previous==M8Workspace.Event.STAGED_VERIFIED;
            case PUBLISHED -> previous==M8Workspace.Event.PUBLISHING;
            case COMPLETED -> previous==M8Workspace.Event.PUBLISHED;
            case FAILED -> previous==M8Workspace.Event.INTENT || previous==M8Workspace.Event.STAGED_VERIFIED;
            case RECOVERY_REQUIRED -> previous!=null && previous!=M8Workspace.Event.RECOVERY_REQUIRED;
        };
    }
    private static byte[] survivorInventoryBytes(List<M8Workspace.SliceEvidence> evidence){
        var b=new StringBuilder("[");
        for(var x:evidence){
            if(b.length()>1)b.append(',');
            b.append("{\"sourceZ\":").append(x.sourceZ())
             .append(",\"filename\":\"").append(x.filename()).append('"')
             .append(",\"sopUid\":\"").append(x.sopUid()).append('"')
             .append(",\"size\":").append(x.digest().sizeBytes())
             .append(",\"sha256\":\"").append(x.digest().sha256()).append('"')
             .append(",\"comparedVoxels\":").append(x.check().comparedVoxels())
             .append(",\"comparedCorners\":").append(x.check().comparedCorners())
             .append(",\"maxWorldError\":\"").append(Double.toHexString(x.check().maxWorldErrorMm())).append('"')
             .append(",\"maxIntensityError\":\"").append(Double.toHexString(x.check().maxIntensityError())).append("\"}");
        }
        return b.append("]\n").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    private static Survivor inspectSurvivor(Path privateRoot,Path publicRoot,UUID job)throws Exception{
        boolean journal=false,marker=false,inventory=false;
        if(!Files.isDirectory(privateRoot,LinkOption.NOFOLLOW_LINKS))return new Survivor(false,false,false);
        var codec=new M8JournalCodec(M8Limits.defaults());
        List<Path> paths;
        try(var ls=Files.list(privateRoot)){
            paths=ls.filter(p->p.getFileName().toString().startsWith("checkpoint-"))
                .sorted().toList();
        }
        if(paths.isEmpty()||paths.size()>8)return new Survivor(false,false,false);
        String previous="0".repeat(64);
        M8Workspace.Event preceding=null;
        M8Plan frozenPlan=null;
        List<M8Workspace.SliceEvidence> slices=null;
        M8Workspace.Checkpoint last=null;
        long totalBytes=0;
        for(int i=0;i<paths.size();i++){
            Path path=paths.get(i);
            if(!path.getFileName().toString().equals(String.format(java.util.Locale.ROOT,"checkpoint-%06d.json",i+1))
                ||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))return new Survivor(false,false,false);
            long bytesLength=Files.size(path);
            if(bytesLength<=0||bytesLength>8L*(1<<20)||bytesLength>64L*(1<<20)-totalBytes)
                return new Survivor(false,false,false);
            totalBytes+=bytesLength;
            byte[] bytes=Files.readAllBytes(path);
            if(bytes.length!=bytesLength)return new Survivor(false,false,false);
            M8Workspace.Checkpoint cp;
            try{cp=codec.decode(bytes);}catch(RuntimeException e){return new Survivor(false,false,false);}
            if(cp.sequence()!=i+1||!cp.previousSha256().equals(previous)
                ||!cp.plan().jobId().equals(job)||!legalSurvivorEvent(preceding,cp.event()))
                return new Survivor(false,false,false);
            if(frozenPlan==null)frozenPlan=cp.plan();
            else if(!frozenPlan.equals(cp.plan()))return new Survivor(false,false,false);
            if(cp.event()==M8Workspace.Event.STAGED_VERIFIED){
                slices=cp.slices();
                if(slices.size()!=frozenPlan.depth())return new Survivor(false,false,false);
                for(int z=0;z<slices.size();z++){
                    var x=slices.get(z);var planned=frozenPlan.slices().get(z);
                    if(x.sourceZ()!=z||!x.filename().equals(planned.filename())
                        ||!x.sopUid().equals(planned.sopUid()))return new Survivor(false,false,false);
                }
            }
            if(cp.event()==M8Workspace.Event.PUBLISHING||cp.event()==M8Workspace.Event.PUBLISHED
                ||cp.event()==M8Workspace.Event.COMPLETED){
                if(slices==null||!cp.slices().equals(slices))return new Survivor(false,false,false);
            }
            previous=sha256(bytes);preceding=cp.event();last=cp;
        }
        journal=last!=null&&last.event()==M8Workspace.Event.COMPLETED;
        if(!journal||slices==null)return new Survivor(false,false,false);
        Path completion=publicRoot.resolve("PAI-M8-COMPLETE.json");
        String expected="{\"schema\":\"PAI-M8-COMPLETE-1\",\"profile\":\"M8-MR-1\",\"jobId\":\""
            +job+"\",\"completedSequence\":"+last.sequence()+",\"completedSha256\":\""
            +previous+"\",\"inventorySha256\":\""+sha256(survivorInventoryBytes(slices))+"\"}\n";
        byte[] expectedBytes=expected.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if(Files.isRegularFile(completion,LinkOption.NOFOLLOW_LINKS)
            && Files.size(completion)==expectedBytes.length && expectedBytes.length<=8192){
            marker=java.util.Arrays.equals(Files.readAllBytes(completion),expectedBytes);
        }
        if(Files.isDirectory(publicRoot,LinkOption.NOFOLLOW_LINKS)){
            try(var ls=Files.list(publicRoot)){
                List<Path> entries=ls.toList();
                if(entries.size()==slices.size()+1 && entries.contains(completion)){
                    inventory=true;
                    for(var x:slices){
                        Path file=publicRoot.resolve(x.filename());
                        if(!entries.contains(file)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)
                            ||Files.size(file)!=x.digest().sizeBytes()){
                            inventory=false;break;
                        }
                        // Synthetic crash fixture uses a bounded two-byte slice;
                        // an independent bytes/hash comparison proves its inventory.
                        if(x.digest().sizeBytes()>8192||!sha256(Files.readAllBytes(file)).equals(x.digest().sha256())){
                            inventory=false;break;
                        }
                    }
                }
            }
        }
        return new Survivor(journal,marker,inventory);
    }
    /** A separately launched JVM dies after the first public link. Never reuse user images. */
    public static final class CrashChild {
        public static void main(String[] args)throws Exception{
            Path[] dirs={Path.of(args[0]),Path.of(args[1])};
            M8Request request=new M8Request(UUID.fromString(args[2]),
                new RelativePath(RelativePath.Root.SOURCE,"a.nii"),
                new M8Metadata(true,true,M8Metadata.ScanningSequence.SE,M8Metadata.SequenceVariant.NONE));
            String phaseToKill=args[3];int sliceToKill=Integer.parseInt(args[4]);
            var workspace=new NioM8Workspace(dirs[0],dirs[1],M8Limits.defaults(),Clock.systemUTC(),
                (phase,z)->{if(phase.equals(phaseToKill)&&z==sliceToKill)Runtime.getRuntime().halt(73);});
            service(workspace).execute(request);
            System.exit(74); // reaching normal return means the kill-point was missed
        }
    }
    @Test void m8_29_childJvmCrashAtFivePublicationBoundariesNeverReportsExecutionSuccess()throws Exception{
        String[][] crashes={
            {"checkpoint-readback","2","false"}, // STAGED_VERIFIED receipt not acknowledged
            {"link","1","true"},                 // first public link only
            {"final-readback","-1","true"},       // all public slice links, PUBLISHED not acknowledged
            {"marker-create","-1","true"},        // bare COMPLETED without marker
            {"marker-readback","-1","true"}       // marker exists, release not established
        };
        String cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        String javaExec=Path.of(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        for(int i=0;i<crashes.length;i++){
            Path parent=Files.createDirectory(root.resolve("crash-"+i));
            Path[] dirs=roots(parent);UUID job=UUID.randomUUID();
            Process child=new ProcessBuilder(javaExec,"-Xmx2g","-cp",cp,CrashChild.class.getName(),
                dirs[0].toString(),dirs[1].toString(),job.toString(),crashes[i][0],crashes[i][1])
                    .redirectErrorStream(true).start();
            boolean ended=child.waitFor(30,java.util.concurrent.TimeUnit.SECONDS);
            if(!ended){child.destroyForcibly();fail("Child JVM crash fixture timed out");}
            assertEquals(73,child.exitValue(),"Kill-point did not execute at "+crashes[i][0]);
            Path published=dirs[1].resolve("m8-"+job);
            boolean expectedPublic=Boolean.parseBoolean(crashes[i][2]);
            assertEquals(expectedPublic,Files.exists(published),"Public visibility at "+crashes[i][0]);
            if(expectedPublic){
                assertTrue(Files.isRegularFile(published.resolve("image-000001.dcm")));
            }
            Survivor survivor=inspectSurvivor(dirs[1].resolve(".pai-m8-"+job),published,job);
            boolean markerPresent=Files.isRegularFile(published.resolve("PAI-M8-COMPLETE.json"));
            // Crash probes evaluate the surviving journal separately from marker and
            // inventory visibility. A terminal journal without a marker is not success.
            if(crashes[i][0].equals("marker-create")||crashes[i][0].equals("marker-readback"))
                assertTrue(survivor.journal(),"COMPLETED journal should survive the controlled halt");
            if(crashes[i][0].equals("marker-create"))
                assertFalse(markerPresent,"Bare terminal receipt is not artifact completion");
            if(crashes[i][0].equals("marker-readback"))
                assertTrue(markerPresent,"Present marker cannot prove release after crash");
            if(crashes[i][0].equals("marker-create")){
                assertFalse(survivor.marker());
                assertFalse(survivor.inventory());
            }
            if(crashes[i][0].equals("marker-readback")){
                assertTrue(survivor.marker(),"Marker bytes must be checked against terminal receipt");
                assertTrue(survivor.inventory(),"All public bytes and membership must match the journal");
            }
            if(!crashes[i][0].equals("marker-readback")){
                assertFalse(Files.exists(published.resolve("PAI-M8-COMPLETE.json")),
                    "No completion marker may precede the marker-link boundary");
            }else{
                // Even when J/M/I are complete, this terminated execution has not
                // established a successful final resource release.
                assertTrue(Files.exists(published.resolve("PAI-M8-COMPLETE.json")));
            }
            if(crashes[i][0].equals("marker-readback")){
                assertTrue(survivor.artifactComplete());
            }else{
                assertFalse(survivor.artifactComplete(),"Pre-marker crash must not be completed");
            }
        }
    }

}
