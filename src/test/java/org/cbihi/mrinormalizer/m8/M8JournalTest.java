package org.cbihi.mrinormalizer.m8;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.cbihi.mrinormalizer.application.m8.*;
import org.cbihi.mrinormalizer.application.port.out.M8Workspace;
import org.cbihi.mrinormalizer.domain.model.*;
import org.cbihi.mrinormalizer.infrastructure.provenance.M8JournalCodec;

final class M8JournalTest {
    private static M8Workspace.Checkpoint sample() {
        ImageVolume im=M8GeometryTest.image(2,2,1,new double[]{0,0,0},new double[]{1,0,0},
                new double[]{0,1,0},new double[]{0,0,1},ScalarType.UINT8,
                IntensityTransform.identityNotDeclared());
        var plan=M8GeometryTest.plan(im);
        return new M8Workspace.Checkpoint(1,"0".repeat(64),M8Workspace.Event.INTENT,Instant.EPOCH,plan,List.of(),List.of());
    }
    @Test void m8_23_canonicalRoundTripAndExactContent(){
        var codec=new M8JournalCodec(M8Limits.defaults());var cp=sample();
        byte[] encoded=codec.encode(cp);
        assertArrayEquals(encoded,codec.encode(codec.decode(encoded)));
        assertTrue(new String(encoded,StandardCharsets.UTF_8).endsWith("\n"));
    }
    @Test void m8_23_rejectsTruncationAndTrailingContent(){
        var codec=new M8JournalCodec(M8Limits.defaults());byte[] b=codec.encode(sample());
        assertThrows(IllegalArgumentException.class,()->codec.decode(java.util.Arrays.copyOf(b,b.length-1)));
        var trailing=(new String(b,StandardCharsets.UTF_8)+"\n").getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,()->codec.decode(trailing));
    }
    @Test void m8_23_refusesDuplicateKeyAndUnknownSchema(){
        var codec=new M8JournalCodec(M8Limits.defaults());
        String s=new String(codec.encode(sample()),StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,()->codec.decode(s.replace("\"schema\":", "\"schema\":\"PAI-M8-JOURNAL-1\",\"schema\":").getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class,()->codec.decode(s.replace("PAI-M8-JOURNAL-1","PAI-M9-JOURNAL-1").getBytes(StandardCharsets.UTF_8)));
    }
}
