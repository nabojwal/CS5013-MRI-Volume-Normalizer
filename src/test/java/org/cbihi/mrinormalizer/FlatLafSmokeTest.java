package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.formdev.flatlaf.FlatLightLaf;

class FlatLafSmokeTest {

    @Test
    void flatLafShouldInstall() {
        assertTrue(FlatLightLaf.setup());
    }
}
