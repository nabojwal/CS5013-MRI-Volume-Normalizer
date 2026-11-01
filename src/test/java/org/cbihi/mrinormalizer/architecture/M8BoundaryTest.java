package org.cbihi.mrinormalizer.architecture;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class M8BoundaryTest {
    @Test void m8_31_applicationImplementationsDoNotImportDcm4cheOrPresentation()throws Exception {
        Path root=Path.of("src/main/java/org/cbihi/mrinormalizer/application");
        try(var paths=Files.walk(root)){
            for(Path p:paths.filter(f->f.toString().contains("M8")&&f.toString().endsWith(".java")).toList()){
                String java=Files.readString(p,StandardCharsets.UTF_8);
                assertFalse(java.contains("import org.dcm4che"),p.toString());
                assertFalse(java.contains("import org.cbihi.mrinormalizer.presentation"),p.toString());
            }
        }
    }
    @Test void m8_31_forwardOrchestrationNotCalledFromReverseService()throws Exception {
        String service=Files.readString(Path.of("src/main/java/org/cbihi/mrinormalizer/application/service/M8ConversionService.java"));
        assertFalse(service.contains("F9HeadlessConversionService"));
        assertFalse(service.contains("DicomToNiftiService"));
    }
}
