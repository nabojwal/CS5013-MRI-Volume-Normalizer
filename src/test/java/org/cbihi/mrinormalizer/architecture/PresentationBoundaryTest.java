package org.cbihi.mrinormalizer.architecture;

import java.lang.reflect.Method;

import org.cbihi.mrinormalizer.application.result.ProcessingResult;
import org.cbihi.mrinormalizer.presentation.PresentationBoundary;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

class PresentationBoundaryTest {

    @Test
    void presentationBoundaryShouldAcceptApplicationResults() throws NoSuchMethodException {
        Method present = PresentationBoundary.class.getDeclaredMethod("present", ProcessingResult.class);

        assertEquals(void.class, present.getReturnType());
        assertEquals(ProcessingResult.class, present.getParameterTypes()[0]);
        assertFalse(present.getParameterTypes()[0].getPackageName()
                .startsWith("org.cbihi.mrinormalizer.infrastructure"));
    }
}
