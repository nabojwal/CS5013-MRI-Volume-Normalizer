package org.cbihi.mrinormalizer.ebo;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.cbihi.mrinormalizer.application.ebo.*;
import org.cbihi.mrinormalizer.application.m8.*;
import org.cbihi.mrinormalizer.application.port.out.M8Workspace;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;
import org.cbihi.mrinormalizer.infrastructure.ebo.EboFileDiscovery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** T07–T13, T16–T18, T21–T24. Deterministic job seam; integration in EboCommandTest. */
public class EboBatchControllerTest {
    @TempDir Path temp;
    private EboRequest req(Path in,Path out){return new EboRequest(EboRequest.Mode.DIRECTORY,in,out,"evaluation");}
    private Path dir(String name)throws Exception{return Files.createDirectory(temp.resolve(name));}
    private static M8Result mock(UUID id,M8Result.Status state,M8Result.Publication pub){
        return new M8Result(id,state,pub,
            state==M8Result.Status.SUCCESS?Optional.of(new RelativePath(RelativePath.Root.OUTPUT,"m8-"+id)):Optional.empty(),
            Optional.empty(),state==M8Result.Status.SUCCESS?Optional.of(new M8Workspace.Receipt(id,1,"a",M8Workspace.Event.COMPLETED)):Optional.empty(),
            List.of(),List.of(),List.of(),state==M8Result.Status.SUCCESS?List.of():List.of(new M8Fault(M8Fault.Phase.SOURCE,M8Fault.Code.INVALID_INPUT)));
    }
    @Test void t07_t08_t09_t10_discoverOnlyCandidatesWithStableOrder()throws Exception {
        Path in=dir("in"),out=dir("out");
        for(String s:List.of("z.nii.gz","B.NII","a.nii","skip.txt"))Files.writeString(in.resolve(s),"x");
        var discover=new EboFileDiscovery();
        var x=discover.discover(req(in,out));var y=discover.discover(req(in,out));
        assertEquals(List.of("B.NII","a.nii","z.nii.gz"),x.stream().map(EboFileDiscovery.SourceFile::relativeName).toList());
        assertEquals(x,y);
    }
    @Test void t11_t12_preflightRejectsEmptyManyLinksAndOverlap()throws Exception {
        Path in=dir("in"),out=dir("out");var d=new EboFileDiscovery();
        assertThrows(IllegalArgumentException.class,()->d.discover(req(in,out)));
        assertThrows(IllegalArgumentException.class,()->d.discover(req(in,in)));
        for(int i=0;i<257;i++)Files.writeString(in.resolve("i"+i+".nii"),"x");
        assertThrows(IllegalArgumentException.class,()->d.discover(req(in,out)));
        for(int i=0;i<257;i++)Files.delete(in.resolve("i"+i+".nii"));
        Path file=Files.writeString(in.resolve("a.nii"),"a");
        try{Files.createSymbolicLink(in.resolve("b.nii"),file);}catch(UnsupportedOperationException | java.nio.file.FileSystemException e){return;}
        assertThrows(IllegalArgumentException.class,()->d.discover(req(in,out)));
    }
    @Test void t13_t16_t17_t18_t21_t22_safeFailuresContinueSequentially()throws Exception {
        Path in=dir("in"),out=dir("out");for(String name:List.of("a.nii","b.nii","c.nii"))Files.writeString(in.resolve(name),"unchanged");
        var calls=new ArrayList<String>();var ids=new HashSet<UUID>();int[] active={0},max={0};
        EboBatchController.JobRunner runner=(request,root,dest)->{
            assertEquals(1,++active[0]);max[0]=Math.max(max[0],active[0]);
            calls.add(request.source().path());ids.add(request.jobId());active[0]--;
            return mock(request.jobId(),calls.size()==2?M8Result.Status.REJECTED:M8Result.Status.SUCCESS,
                calls.size()==2?M8Result.Publication.NONE:M8Result.Publication.COMPLETE_BYTES_VERIFIED);
        };
        var c=new EboBatchController(r->new EboFileDiscovery().discover(r).stream().map(EboFileDiscovery.SourceFile::relativeName).toList(),runner,(r,o)->out.resolve("report.json"));
        var result=c.execute(req(in,out));
        assertEquals(List.of("a.nii","b.nii","c.nii"),calls);
        assertEquals(1,max[0]);assertEquals(3,ids.size());
        assertEquals(EboResult.OverallStatus.PARTIAL_SUCCESS,result.overallStatus());
        assertEquals(2,result.successCount());assertEquals(1,result.rejectedCount());
        for(String n:calls)assertEquals("unchanged",Files.readString(in.resolve(n)));
    }
    @Test void t18_repeatRunMaintainsExistingOutputFiles()throws Exception {
        Path in=dir("in"),out=dir("out");Files.writeString(in.resolve("a.nii"),"stable");
        var runner=(EboBatchController.JobRunner)(request,source,dest)->{
            try {
                Path folder=Files.createDirectory(dest.resolve("m8-"+request.jobId()));
                Files.writeString(folder.resolve("image-000001.dcm"),"first-payload");
            } catch(java.io.IOException e){throw new IllegalStateException(e);}
            return mock(request.jobId(),M8Result.Status.SUCCESS,M8Result.Publication.COMPLETE_BYTES_VERIFIED);
        };
        var c=new EboBatchController(r->new EboFileDiscovery().discover(r).stream().map(EboFileDiscovery.SourceFile::relativeName).toList(),runner,(r,o)->out.resolve("report-"+r.batchId()+".json"));
        var first=c.execute(req(in,out));Path firstPath=out.resolve(first.entries().getFirst().outputDirectory()).resolve("image-000001.dcm");
        byte[] baseline=Files.readAllBytes(firstPath);
        var second=c.execute(req(in,out));
        assertNotEquals(first.entries().getFirst().outputDirectory(),second.entries().getFirst().outputDirectory());
        assertArrayEquals(baseline,Files.readAllBytes(firstPath));
        assertEquals("stable",Files.readString(in.resolve("a.nii")));
    }
    @Test void t22_failedButUnpublishedJobDoesNotStopNextInput()throws Exception {
        Path in=dir("in"),out=dir("out");
        Files.writeString(in.resolve("a.nii"),"x");Files.writeString(in.resolve("b.nii"),"x");
        var calls=new ArrayList<String>();
        var c=new EboBatchController(r->new EboFileDiscovery().discover(r).stream().map(EboFileDiscovery.SourceFile::relativeName).toList(),(r,s,o)->{
            calls.add(r.source().path());
            return mock(r.jobId(),calls.size()==1?M8Result.Status.FAILED:M8Result.Status.SUCCESS,
                calls.size()==1?M8Result.Publication.NONE:M8Result.Publication.COMPLETE_BYTES_VERIFIED);
        },(r,o)->out.resolve("report.json"));
        var result=c.execute(req(in,out));
        assertEquals(2,result.attemptedCount());assertEquals(1,result.failedCount());
        assertEquals(1,result.successCount());assertEquals(EboResult.OverallStatus.PARTIAL_SUCCESS,result.overallStatus());
    }
    @Test void t24_priorInterruptionLeavesAllInputsUnattempted()throws Exception {
        Path in=dir("in"),out=dir("out");Files.writeString(in.resolve("a.nii"),"x");
        var c=new EboBatchController(r->new EboFileDiscovery().discover(r).stream().map(EboFileDiscovery.SourceFile::relativeName).toList(),(r,s,o)->{throw new AssertionError("Runner must not be called");},
                new org.cbihi.mrinormalizer.infrastructure.ebo.EboReportWriter()::publish);
        Thread.currentThread().interrupt();
        try {
            var result=c.execute(req(in,out));
            assertEquals(EboResult.OverallStatus.INTERRUPTED,result.overallStatus());
            assertEquals(1,result.unattemptedCount());
            assertTrue(Files.isRegularFile(result.reportPath().orElseThrow()));
            assertTrue(Thread.currentThread().isInterrupted(),"Original interrupt status preserved");
        } finally {Thread.interrupted();}
    }
    @Test void t23_t24_recoveryAndUnexpectedExceptionsHaltWithUnattemptedEntries()throws Exception {
        Path in=dir("in"),out=dir("out");
        for(String s:List.of("a.nii","b.nii","c.nii"))Files.writeString(in.resolve(s),"x");
        var c=new EboBatchController(r->new EboFileDiscovery().discover(r).stream().map(EboFileDiscovery.SourceFile::relativeName).toList(),(r,s,o)->mock(r.jobId(),M8Result.Status.RECOVERY_REQUIRED,M8Result.Publication.PARTIAL_OR_UNKNOWN),
            (r,o)->out.resolve("report.json"));
        var result=c.execute(req(in,out));
        assertEquals(EboResult.OverallStatus.RECOVERY_REQUIRED,result.overallStatus());
        assertEquals(2,result.unattemptedCount());
        assertEquals(EboResult.EntryState.NOT_ATTEMPTED,result.entries().get(2).state());
        var x=new EboBatchController(r->new EboFileDiscovery().discover(r).stream().map(EboFileDiscovery.SourceFile::relativeName).toList(),(r,s,o)->{throw new IllegalStateException("synthetic");},(r,o)->out.resolve("report.json"));
        var exceptional=x.execute(req(in,out));
        assertEquals(EboResult.OverallStatus.RECOVERY_REQUIRED,exceptional.overallStatus());
        assertEquals(2,exceptional.unattemptedCount());
    }
}
