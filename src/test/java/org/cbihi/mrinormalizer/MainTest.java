package org.cbihi.mrinormalizer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainTest {

    @Test
    void javaVersionShouldBe21() {
        assertEquals(
                "21",
                System.getProperty("java.version").substring(0, 2)
        );
    }
}
