package org.cbihi.mrinormalizer.gui;

import org.cbihi.mrinormalizer.presentation.gui.*;
import org.cbihi.mrinormalizer.application.result.F9HeadlessResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

final class GuiI1WorkflowE1Test {
    @TempDir Path root;
    private GuiI1Scanner.ScanResult scan(Path input,String... names) throws Exception {
        List<GuiI1Scanner.Candidate> candidates=new ArrayList<>();
        for(String name:names) {
            Files.write(input.resolve(name),new byte[]{1,2,3});
            candidates.add(new GuiI1Scanner.Candidate(name,null,input,null,List.of(name)));
        }
        return new GuiI1Scanner.ScanResult(GuiI1Scanner.Direction.NIFTI_TO_DICOM,
                GuiI1Scanner.InputKind.NIFTI_DIRECTORY,input,candidates);
    }
    private static final class Backend implements GuiI1Execution.Backend {
        int calls;int rejectAt=-1;
        @Override public int ebo(String[] args) {
            calls++;
            if(calls==rejectAt)return 3;
            try {
                Path out=Path.of(args[3]);
                Path pub=Files.createDirectory(out.resolve("m8-12345678-1234-1234-1234-123456789012"));
                Files.write(pub.resolve("image-000001.dcm"),new byte[]{3,1,4});
            } catch(Exception e) {throw new IllegalStateException(e);}
            return 0;
        }
        @Override public int fbo(String[] args){throw new AssertionError();}
        @Override public F9HeadlessResult f9(Path input,Path output,String uid,List<String> files){throw new AssertionError();}
    }
    @Test void namesAreSafeAndCollisionsDetected() {
        assertEquals("scan_t1",GuiI1OutputOrganizer.stem("scan_t1.nii.gz"));
        assertThrows(IllegalArgumentException.class,()->GuiI1OutputOrganizer.names(List.of("scan.nii","SCAN.nii.gz")));
        assertThrows(IllegalArgumentException.class,()->GuiI1OutputOrganizer.stem("CON.nii"));
        assertThrows(IllegalArgumentException.class,()->GuiI1OutputOrganizer.stem("bad:name.nii"));
    }
    @Test void verifiedBatchHasNamedDicomAndSingleEvidenceRootAndBlocksReplay() throws Exception {
        Path input=Files.createDirectory(root.resolve("input"));Path output=Files.createDirectory(root.resolve("output"));
        var scan=scan(input,"flair.nii.gz","t1.nii");var backend=new Backend();
        List<String> activity=new ArrayList<>();
        var first=new GuiI1Execution(backend).execute(scan,List.of(0,1),output,activity::add);
        assertEquals(GuiI1Execution.State.VERIFIED,first.state());
        assertEquals(2,backend.calls);
        assertTrue(Files.exists(output.resolve("flair/image-000001.dcm")));
        assertTrue(Files.exists(output.resolve("t1/image-000001.dcm")));
        assertTrue(Files.isDirectory(output.resolve(".pai-internal/jobs")));
        assertTrue(activity.stream().anyMatch(s->s.contains("Processing 2 of 2: t1.nii")));
        var again=new GuiI1Execution(backend).execute(scan,List.of(0,1),output);
        assertEquals(GuiI1Execution.State.REJECTED,again.state());assertEquals(2,backend.calls);
    }
    @Test void partiallyCompletedBatchDoesNotMarkWholeRequestVerified() throws Exception {
        Path input=Files.createDirectory(root.resolve("input"));Path output=Files.createDirectory(root.resolve("output"));
        var scan=scan(input,"flair.nii","t1.nii");var backend=new Backend();backend.rejectAt=2;
        var result=new GuiI1Execution(backend).execute(scan,List.of(0,1),output);
        assertEquals(GuiI1Execution.State.PARTIAL,result.state());
        assertTrue(Files.exists(output.resolve("flair/image-000001.dcm")));
        assertFalse(Files.exists(output.resolve("t1")));
        String fp=GuiI1CompletionLedger.fingerprint(scan,List.of(0,1),output);
        assertFalse(GuiI1CompletionLedger.exists(output,fp));
    }
    @Test void reservedExistingOutputDoesNotDispatch() throws Exception {
        Path input=Files.createDirectory(root.resolve("input"));Path output=Files.createDirectory(root.resolve("output"));
        var scan=scan(input,"scan.nii");Files.createDirectory(output.resolve("scan"));
        var backend=new Backend();
        assertEquals(GuiI1Execution.State.REJECTED,
                new GuiI1Execution(backend).execute(scan,List.of(0),output).state());
        assertEquals(0,backend.calls);
    }
}
