package org.cbihi.mrinormalizer.fbo;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.cbihi.mrinormalizer.application.fbo.*;
import org.cbihi.mrinormalizer.application.fbo.FboBatchController.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FboBatchControllerTest {
    @TempDir Path temp;
    private static final FileTime T = FileTime.fromMillis(1);
    private FboRequest request(int n) {
        List<FboRequest.SeriesSelection> selections = new ArrayList<>();
        for (int i = 1; i <= n; i++) selections.add(new FboRequest.SeriesSelection("series"+i,"1.2."+i));
        return new FboRequest(temp.resolve("source"),temp.resolve("output"),selections);
    }
    private PreparedBatch fixture(int n) throws IOException {
        Path source=Files.createDirectories(temp.resolve("source"));
        Path output=Files.createDirectories(temp.resolve("output"));
        List<PreparedSeries> series = new ArrayList<>();
        for(int i=1;i<=n;i++){
            Path directory=Files.createDirectories(source.resolve("series"+i));
            series.add(new PreparedSeries(i,directory,"1.2."+i,
                    new DirectoryObservation(null,T,T),List.of(new PreparedFile("slice.dcm",4,T,T,null,"a".repeat(64)))));
        }
        return new PreparedBatch(source,output,series);
    }
    private static ReportSink simpleReport() {
        return (r,root)->Files.writeString(root.resolve("fbo-"+r.batchId()+"-summary.json"),"synthetic test report");
    }
    private static Preflight fake(PreparedBatch b) {
        return new Preflight() {
            public PreparedBatch inspectAll(FboRequest r){return b;}
            public void assertUnchanged(PreparedSeries s) { }
        };
    }
    @Test void t09_t11_t12_newUuidAndSerialOrderedDispatch() throws Exception {
        PreparedBatch b=fixture(3);
        Set<UUID> ids=new HashSet<>(); List<PreparedSeries> observed=new ArrayList<>();
        FboResult result=new FboBatchController(fake(b),(prepared,id,root)->{
            assertTrue(ids.add(id)); assertFalse(Files.exists(root.resolve("job-%03d-%s".formatted(prepared.ordinal(),id))));
            assertEquals(observed.size()+1,prepared.ordinal()); observed.add(prepared);
            return FboResult.Entry.verified(prepared.ordinal(),id);
        },simpleReport()).execute(request(3));
        assertEquals(FboResult.Status.SUCCESS,result.status());
        assertEquals(3,result.verifiedCount()); assertEquals(3,observed.size());
        assertFalse(ids.contains(result.batchId()));
        assertTrue(Files.isRegularFile(result.reportPath().orElseThrow()));
    }
    @Test void t16_ordinaryDefiniteFailureContinues() throws Exception {
        PreparedBatch b=fixture(3); AtomicInteger called=new AtomicInteger();
        FboResult result=new FboBatchController(fake(b),(s,id,root)->{
            called.incrementAndGet();
            if(s.ordinal()==2)return FboResult.Entry.failed(2,id,FboResult.Reason.F9_FAILED);
            return FboResult.Entry.verified(s.ordinal(),id);
        },simpleReport()).execute(request(3));
        assertEquals(3,called.get()); assertEquals(FboResult.Status.PARTIAL_SUCCESS,result.status());
        assertEquals(2,result.verifiedCount()); assertEquals(1,result.failedCount());
    }
    @Test void t17_t18_t19_uncertainCasesHalt() throws Exception {
        for(int reason=0;reason<3;reason++){
            final int scenario=reason;
            PreparedBatch b=fixture(3); AtomicInteger called=new AtomicInteger();
            FboResult result=new FboBatchController(fake(b),(s,id,root)->{
                called.incrementAndGet();
                if (scenario==0) return FboResult.Entry.recovery(1,id,FboResult.Reason.F9_RECOVERY_REQUIRED);
                if (scenario==1) throw new IllegalStateException("private fault");
                return FboResult.Entry.recovery(1,id,FboResult.Reason.F9_CLOSE_UNCERTAIN);
            },simpleReport()).execute(request(3));
            assertEquals(FboResult.Status.RECOVERY_REQUIRED,result.status());
            assertEquals(1,called.get()); assertEquals(2,result.notAttemptedCount());
            // Remove only this test's controller-created root, leave source/output parent for reuse.
        }
    }
    @Test void t20_interruptBeforeNextJobRemainsTruthful() throws Exception {
        PreparedBatch b=fixture(3);
        try {
            FboResult result=new FboBatchController(fake(b),(s,id,root)->{
                Thread.currentThread().interrupt();
                return FboResult.Entry.verified(s.ordinal(),id);
            },simpleReport()).execute(request(3));
            assertEquals(FboResult.Status.INTERRUPTED,result.status());
            assertEquals(1,result.verifiedCount()); assertEquals(2,result.notAttemptedCount());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }
    @Test void t20_interruptDuringUncertainWorkIsNotReportedAsCancellationSuccess() throws Exception {
        PreparedBatch b=fixture(3);
        try {
            FboResult result=new FboBatchController(fake(b),(s,id,root)->{
                Thread.currentThread().interrupt();
                return FboResult.Entry.recovery(s.ordinal(),id,FboResult.Reason.INTERRUPTED);
            },simpleReport()).execute(request(3));
            assertEquals(FboResult.Status.RECOVERY_REQUIRED,result.status());
            assertEquals(2,result.notAttemptedCount());
            assertEquals(0,result.verifiedCount());
        } finally { Thread.interrupted(); }
    }
    @Test void t08_t10_mutatedSecondSeriesDoesNotReachRunner() throws Exception {
        Path input=Files.createDirectories(temp.resolve("source"));
        // The real preflight now refuses overly deep Windows output paths.
        // Keep the source fixture under @TempDir but put only this test's
        // output beneath the qualified short SystemDrive scratch directory.
        Path output;
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            String systemDrive=System.getenv("SystemDrive");
            assertNotNull(systemDrive,"Windows SystemDrive required for native drift test");
            Path scratch=Path.of(System.getProperty("fbo.test.systemDriveTemp",systemDrive+"\\FBO")).toRealPath();
            assertEquals(systemDrive+"\\",scratch.getRoot().toString(),
                    "Native drift fixture requires the Windows SystemDrive");
            output=Files.createTempDirectory(scratch,"o-");
            assertTrue(output.toString().length()+203<=240,
                    "Native F1 stage-path headroom unavailable: provide short -Dfbo.test.systemDriveTemp");
        } else {
            output=Files.createDirectories(temp.resolve("output"));
        }
        for(int i=1;i<=3;i++){
            Path child=Files.createDirectories(input.resolve("series"+i));
            Files.writeString(child.resolve("image.dcm"),"synthetic-preflight-byte-evidence");
        }
        var request=new FboRequest(input,output,request(3).selections());
        var preflight=new org.cbihi.mrinormalizer.infrastructure.fbo.FboDirectoryPreflight();
        AtomicInteger called=new AtomicInteger();
        FboResult result=new FboBatchController(preflight,(s,id,root)->{
            assertEquals(1,s.members().size());
            called.incrementAndGet();
            if(s.ordinal()==1)FilesWriteUnchecked(input.resolve("series2").resolve("added.dcm"));
            return FboResult.Entry.verified(s.ordinal(),id);
        },simpleReport()).execute(request);
        assertEquals(1,called.get(),"Second series must not dispatch after drift");
        assertEquals(FboResult.EntryState.FAILED,result.entries().get(1).state());
        assertEquals(FboResult.Reason.SOURCE_CHANGED_BEFORE_DISPATCH,result.entries().get(1).reason());
        assertEquals(FboResult.EntryState.NOT_ATTEMPTED,result.entries().get(2).state());
        try(var batches=Files.list(output)) {
            Path batch=batches.filter(Files::isDirectory).findFirst().orElseThrow();
            try(var jobs=Files.list(batch)) {
                assertFalse(jobs.anyMatch(p->p.getFileName().toString().startsWith("job-002")));
            }
        }
    }
    private static void FilesWriteUnchecked(Path path) {
        try { Files.writeString(path,"new member"); }catch(IOException e){throw new IllegalStateException(e);}
    }
    @Test void t24_reportFailureAfterVerifiedJobsSuppressesSuccess() throws Exception {
        PreparedBatch b=fixture(1);
        FboResult result=new FboBatchController(fake(b),(s,id,root)->FboResult.Entry.verified(1,id),
                (r,root)->{throw new IOException("injected report fsync error");}).execute(request(1));
        assertEquals(FboResult.Status.RECOVERY_REQUIRED,result.status());
        assertEquals(1,result.verifiedCount());assertTrue(result.reportPath().isEmpty());
    }
}
