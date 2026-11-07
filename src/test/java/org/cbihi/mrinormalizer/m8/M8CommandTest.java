package org.cbihi.mrinormalizer.m8;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;
import org.cbihi.mrinormalizer.bootstrap.M8Command;

final class M8CommandTest {
    private int run(String... args){return M8Command.run(args,new PrintStream(new ByteArrayOutputStream()),
            new PrintStream(new ByteArrayOutputStream()));}
    @Test void m8_30_missingFlagsAreUsageRefusal(){assertEquals(2,run());}
    @Test void m8_30_duplicateAndUnknownFlagsAreRefused(){
        assertEquals(2,run("--confirm-mr","--confirm-mr"));
        assertEquals(2,run("--debug","1"));
    }
    @Test void m8_30_missingRequiredAcquisitionDoesNotDefault(){
        assertEquals(2,run("--source-root",".","--input","a.nii","--output-root",".",
                "--confirm-mr","--confirm-biped","--scanning-sequence","UNKNOWN",
                "--sequence-variant","NONE"));
    }
}
