package org.cbihi.mrinormalizer.architecture;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class PackageStructureTest {

    @Test
    void requiredArchitecturalPackagesShouldExist() throws ClassNotFoundException {
        Class<?>[] contractTypes = {
                Class.forName("org.cbihi.mrinormalizer.domain.model.Volume"),
                Class.forName("org.cbihi.mrinormalizer.domain.model.InputSource"),
                Class.forName("org.cbihi.mrinormalizer.application.request.ProcessingRequest"),
                Class.forName("org.cbihi.mrinormalizer.application.result.ProcessingResult"),
                Class.forName("org.cbihi.mrinormalizer.application.service.ProcessingApplication"),
                Class.forName("org.cbihi.mrinormalizer.presentation.PresentationBoundary"),
                Class.forName("org.cbihi.mrinormalizer.infrastructure.package-info"),
                Class.forName("org.cbihi.mrinormalizer.presentation.swing.package-info")
        };

        for (Class<?> contractType : contractTypes) {
            assertNotNull(contractType.getPackage());
            assertTrue(contractType.getPackageName().startsWith("org.cbihi.mrinormalizer"));
        }
    }
}
