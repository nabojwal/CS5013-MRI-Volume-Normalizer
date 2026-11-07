package org.cbihi.mrinormalizer.ebo;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.cbihi.mrinormalizer.application.ebo.*;
import org.cbihi.mrinormalizer.infrastructure.ebo.EboReportWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** T25–T26: complete report schema/counts and non-overwriting failure behavior. */
public class EboReportWriterTest {
    @TempDir Path root;
    @Test void t25_reportReconcilesAndDeclaresTemplateAssumptions()throws Exception {
        var entries=List.of(new EboResult.Entry(1,"a.nii",UUID.randomUUID(),EboResult.EntryState.SUCCESS,
            "SUCCESS","COMPLETE_BYTES_VERIFIED",3,"m8-test",List.of()),
            new EboResult.Entry(2,"b.nii",null,EboResult.EntryState.NOT_ATTEMPTED,
                null,null,0,null,List.of()));
        var report=new EboResult(UUID.randomUUID(),"DIRECTORY",entries,EboResult.OverallStatus.INTERRUPTED,Optional.empty());
        Path p=new EboReportWriter().publish(report,root);
        String content=Files.readString(p);
        for(String token:List.of("PAI-EBO-REPORT-1.0","caller-selected non-clinical template assumptions",
            "\"scanningSequence\":\"SE\"","\"sequenceVariant\":\"NONE\"",
            "\"attemptedCount\":1","\"unattemptedCount\":1","NOT_ATTEMPTED","a.nii","b.nii"))
            assertTrue(content.contains(token),token);
    }
    @Test void t26_existingReportIsNotOverwritten()throws Exception {
        UUID id=UUID.randomUUID();Path target=root.resolve("ebo-"+id+"-summary.json");
        Files.writeString(target,"sentinel");
        var report=new EboResult(id,"SINGLE",List.of(),EboResult.OverallStatus.INVALID_REQUEST,Optional.empty());
        assertThrows(Exception.class,()->new EboReportWriter().publish(report,root));
        assertEquals("sentinel",Files.readString(target));
    }
}
