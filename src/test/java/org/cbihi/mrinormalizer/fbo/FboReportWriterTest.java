package org.cbihi.mrinormalizer.fbo;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.cbihi.mrinormalizer.application.fbo.FboResult;
import org.cbihi.mrinormalizer.infrastructure.fbo.FboReportWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FboReportWriterTest {
    @TempDir Path temp;
    @Test void t23_publicReportWhitelistsOnlySanitizedFields() throws Exception {
        UUID batch=UUID.randomUUID(),job=UUID.randomUUID();
        var result=new FboResult(batch,FboResult.Status.PARTIAL_SUCCESS,
                List.of(FboResult.Entry.verified(1,job)),Optional.empty());
        Path report=new FboReportWriter().publish(result,temp);
        String s=Files.readString(report);
        assertTrue(s.contains("PAI-FBO-REPORT-1.0"));
        assertTrue(s.contains("\"terminalStatus\":\"SUCCESS\""));
        assertTrue(s.contains("\"verifiedOutput\":true"));
        for(String restricted:List.of("seriesInstanceUid","selectedMembers","creationTime",
                "sha256","sourcePath","patient","metadataProfile","sourceName","private-stage"))
            assertFalse(s.contains(restricted),restricted);
    }
    @Test void t24_noReplaceReportAndPreserveOriginalBytes() throws Exception {
        UUID batch=UUID.randomUUID(),job=UUID.randomUUID();
        var result=new FboResult(batch,FboResult.Status.FAILED,
                List.of(FboResult.Entry.failed(1,job,FboResult.Reason.F9_FAILED)),Optional.empty());
        var writer=new FboReportWriter();
        Path first=writer.publish(result,temp);
        byte[] original=Files.readAllBytes(first);
        assertThrows(java.io.IOException.class,()->writer.publish(result,temp));
        assertArrayEquals(original,Files.readAllBytes(first));
    }
}
