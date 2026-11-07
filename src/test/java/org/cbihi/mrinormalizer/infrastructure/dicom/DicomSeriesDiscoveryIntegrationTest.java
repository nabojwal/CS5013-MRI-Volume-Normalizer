package org.cbihi.mrinormalizer.infrastructure.dicom;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.cbihi.mrinormalizer.application.dataset.dicom.*;
import org.cbihi.mrinormalizer.application.dataset.model.*;
import org.cbihi.mrinormalizer.application.service.*;
import org.cbihi.mrinormalizer.domain.model.DetectionOutcome;
import org.cbihi.mrinormalizer.infrastructure.detection.DicomFormatProbe;
import org.cbihi.mrinormalizer.infrastructure.detection.NiftiFormatProbe;
import org.cbihi.mrinormalizer.infrastructure.filesystem.NioDatasetScanner;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DicomSeriesDiscoveryIntegrationTest {
    @TempDir Path temporary;

    // T22
    @Test void discoveryPreservesAssessmentsAndReadiness() throws Exception {
        var pipeline = pipeline();
        var before = pipeline.inventory.entries().stream().map(InventoryEntry::assessment).toList();
        var result = new DicomSeriesDiscoveryService().discover(pipeline.catalog);
        assertEquals(2, result.candidates().size());
        for (int i = 0; i < before.size(); i++) {
            var entry = pipeline.inventory.entries().get(i);
            assertSame(before.get(i), entry.assessment());
            if (entry.assessment().initialDetection().outcome() == DetectionOutcome.DICOM) {
                assertEquals(ConversionReadiness.REQUIRES_VALIDATION, entry.assessment().readiness());
            }
            assertNotEquals(ConversionReadiness.READY, entry.assessment().readiness());
        }
    }

    // T24
    @Test void realF3PipelinePreservesExactlyOnceCoverage() throws Exception {
        var pipeline = pipeline(); assertTrue(pipeline.inventory.complete()); assertFalse(pipeline.catalog.complete());
        assertEquals(4, pipeline.catalog.inspections().size());
        var originalFailure = pipeline.catalog.inspections().stream().filter(i -> i.failure().isPresent()).findFirst().orElseThrow();
        assertEquals(Optional.of(DicomMetadataInspection.FailureCode.INCOMPLETE_METADATA), originalFailure.failure());
        var result = new DicomSeriesDiscoveryService().discover(pipeline.catalog);
        assertEquals(2, result.candidates().size()); assertEquals(List.of(2, 1), result.candidates().stream().map(c -> c.members().size()).toList());
        assertEquals(List.of("1.2", "1.9"), result.candidates().stream().map(c -> c.key().studyInstanceUid()).toList());
        assertTrue(result.candidates().stream().allMatch(c -> c.findings().isEmpty()));
        assertEquals(1, result.unassigned().size()); assertSame(originalFailure, result.unassigned().getFirst());
        var sources = result.candidates().stream().flatMap(c -> c.members().stream()).map(DicomSeriesMember::source).toList();
        var all = new java.util.ArrayList<>(sources); all.addAll(result.unassigned().stream().map(DicomMetadataInspection::source).toList());
        assertEquals(4, all.size()); assertEquals(4, new HashSet<>(all).size());
        assertEquals(new HashSet<>(pipeline.catalog.inspections().stream().map(DicomMetadataInspection::source).toList()), new HashSet<>(all));
        for (var member : result.candidates().stream().flatMap(c -> c.members().stream()).toList()) {
            var original = pipeline.catalog.inspections().stream().filter(i -> i.source().equals(member.source())).findFirst().orElseThrow();
            assertSame(original.metadata().orElseThrow(), member.metadata());
        }
        for (var entry : pipeline.bytes.entrySet()) assertArrayEquals(entry.getValue(), Files.readAllBytes(entry.getKey()));
        try (var files = Files.walk(pipeline.input)) { assertEquals(pipeline.bytes.size(), files.filter(Files::isRegularFile).count()); }
        try (var files = Files.list(pipeline.output)) { assertEquals(0, files.count()); }
    }

    private Pipeline pipeline() throws Exception {
        Path input = Files.createDirectory(temporary.resolve("input")); Path output = Files.createDirectory(temporary.resolve("output"));
        Files.createDirectory(input.resolve("nested")); var bytes = new LinkedHashMap<Path, byte[]>();
        bytes.put(input.resolve("nested/a.nii"), fixture("1.2", "1.3", "1.4", false));
        bytes.put(input.resolve("b"), fixture("1.2", "1.3", "1.5", false));
        bytes.put(input.resolve("c.dcm"), fixture("1.9", "1.3", "1.6", false));
        bytes.put(input.resolve("incomplete.dcm"), fixture("1.2", "1.3", "1.7", true));
        bytes.put(input.resolve("unknown"), new byte[] {1, 2, 3, 4});
        for (var entry : bytes.entrySet()) Files.write(entry.getKey(), entry.getValue());
        var detector = new FormatDetectionService(List.of(new DicomFormatProbe(), new NiftiFormatProbe()));
        var inventory = new NioDatasetScanner(input, output, new InventoryLimits(100, 8, 10), detector).scan();
        assertTrue(inventory.complete());
        assertEquals(4, inventory.entries().stream().filter(e -> e.assessment().initialDetection().outcome() == DetectionOutcome.DICOM).count());
        var catalog = new DicomMetadataInspectionService(new Dcm4cheMetadataReader(input, new DicomMetadataLimits(100_000, 8))).inspect(inventory);
        return new Pipeline(input, output, inventory, catalog, bytes);
    }
    private static byte[] fixture(String study, String series, String sop, boolean missingStudy) throws Exception {
        // Reuse the accepted F3 synthetic byte fixture without changing its source.
        var fixture = new Dcm4cheMetadataReaderTest.Fixture(UID.ExplicitVRLittleEndian);
        fixture.text(Tag.StudyInstanceUID, "UI", study); fixture.text(Tag.SeriesInstanceUID, "UI", series);
        fixture.text(Tag.SOPInstanceUID, "UI", sop);
        if (missingStudy) fixture.elements.remove(Tag.StudyInstanceUID);
        return fixture.part10(fixture.syntax, sop, fixture.sop);
    }
    private record Pipeline(Path input, Path output, DatasetInventory inventory, DicomMetadataCatalog catalog, Map<Path, byte[]> bytes) {}
}
