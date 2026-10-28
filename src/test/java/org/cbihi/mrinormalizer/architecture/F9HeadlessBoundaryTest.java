package org.cbihi.mrinormalizer.architecture;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.cbihi.mrinormalizer.application.service.F9HeadlessConversionService;
import org.cbihi.mrinormalizer.application.request.F9HeadlessRequest;
import org.cbihi.mrinormalizer.application.result.F9HeadlessResult;
import org.cbihi.mrinormalizer.infrastructure.headless.F9WindowsComposition;
import org.cbihi.mrinormalizer.presentation.cli.F9HeadlessCli;

class F9HeadlessBoundaryTest {
    @Test void S12_f9UsesOnlyItsOwnedApplicationPortsAndExistingF8Service() {
        assertTrue(Modifier.isFinal(F9HeadlessConversionService.class.getModifiers()));
        assertTrue(F9HeadlessRequest.class.isRecord());
        assertTrue(F9HeadlessResult.class.isRecord());
        assertTrue(Modifier.isFinal(F9WindowsComposition.class.getModifiers()));
        assertTrue(Modifier.isFinal(F9HeadlessCli.class.getModifiers()));
    }
    @Test void S13_noAlternateConversionOrMetadataParserInService() throws Exception {
        var source=Files.readString(Path.of("src/main/java/org/cbihi/mrinormalizer/application/service/F9HeadlessConversionService.java"));
        assertTrue(source.contains("verified.executeOne("));
        assertFalse(source.contains("new DicomInputStream("));
        assertFalse(source.contains("new Nifti1VolumeWriter("));
        assertFalse(source.contains("DefaultDicomToNiftiService("));
        assertFalse(source.contains("Files.write("));
    }
}
