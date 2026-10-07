package org.cbihi.mrinormalizer.infrastructure.dicom;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.cbihi.mrinormalizer.application.dataset.dicom.DicomMetadataLimits;
import org.cbihi.mrinormalizer.application.dataset.model.*;
import org.cbihi.mrinormalizer.application.service.*;
import org.cbihi.mrinormalizer.infrastructure.detection.DicomFormatProbe;
import org.cbihi.mrinormalizer.infrastructure.detection.NiftiFormatProbe;
import org.cbihi.mrinormalizer.infrastructure.filesystem.NioDatasetScanner;
import org.cbihi.mrinormalizer.domain.model.DetectionOutcome;
import org.dcm4che3.data.UID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DicomMetadataIntegrationTest {
    @TempDir Path temporary;
    // T29
    @Test void acceptedInventoryToCatalogIntegrationPreservesAssessments() throws Exception {
        var input = Files.createDirectory(temporary.resolve("input")); var output = Files.createDirectory(temporary.resolve("output"));
        Files.createDirectory(input.resolve("nested"));
        byte[] bytes = new Dcm4cheMetadataReaderTest.Fixture(UID.ExplicitVRLittleEndian).part10();
        Files.write(input.resolve("nested/a.dcm"), bytes); Files.write(input.resolve("z.dcm"), bytes);
        Files.write(input.resolve("unknown"), new byte[] {1, 2, 3, 4});
        var detector = new FormatDetectionService(List.of(new DicomFormatProbe(), new NiftiFormatProbe()));
        var inventory = new NioDatasetScanner(input, output, new InventoryLimits(100, 8, 10), detector).scan();
        assertTrue(inventory.complete());
        var before = inventory.entries().stream().map(InventoryEntry::assessment).toList();
        var catalog = new DicomMetadataInspectionService(new Dcm4cheMetadataReader(input,
                new DicomMetadataLimits(100_000, 8))).inspect(inventory);
        assertTrue(catalog.complete()); assertEquals(List.of("nested/a.dcm", "z.dcm"), catalog.inspections().stream().map(i -> i.source().path()).toList());
        assertEquals(before, inventory.entries().stream().map(InventoryEntry::assessment).toList());
        for (var entry : inventory.entries()) if (entry.assessment().initialDetection().outcome() == DetectionOutcome.DICOM) {
            assertEquals(ConversionReadiness.REQUIRES_VALIDATION, entry.assessment().readiness());
        }
        assertArrayEquals(bytes, Files.readAllBytes(input.resolve("nested/a.dcm")));
        try (var files = Files.list(output)) { assertEquals(0, files.count()); }
    }
}
