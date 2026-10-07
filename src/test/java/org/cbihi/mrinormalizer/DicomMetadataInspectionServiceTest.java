package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.*;
import static org.cbihi.mrinormalizer.DicomMetadataModelTest.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.cbihi.mrinormalizer.application.dataset.dicom.*;
import org.cbihi.mrinormalizer.application.dataset.dicom.DicomMetadataInspection.FailureCode;
import org.cbihi.mrinormalizer.application.dataset.model.*;
import org.cbihi.mrinormalizer.application.port.out.DicomMetadataReader;
import org.cbihi.mrinormalizer.application.service.DicomMetadataInspectionService;
import org.cbihi.mrinormalizer.domain.model.*;
import org.junit.jupiter.api.Test;

class DicomMetadataInspectionServiceTest {
    // T05
    @Test void inspectionRequiresCompleteInventory() {
        var calls = new AtomicInteger();
        var service = new DicomMetadataInspectionService(s -> { calls.incrementAndGet(); return success(s.path()); });
        assertThrows(IllegalArgumentException.class, () -> new DicomMetadataInspectionService(null));
        assertThrows(IllegalArgumentException.class, () -> service.inspect(null));
        assertThrows(IllegalArgumentException.class, () -> service.inspect(new DatasetInventory(List.of(), List.of(
                new InventoryFailure(InventoryFailure.Code.TRAVERSAL_FAILED, Optional.empty())))));
        assertThrows(IllegalArgumentException.class, () -> service.inspect(new DatasetInventory(List.of(entry("a",
                DetectionResult.unknown(DetectionDiagnostic.IO_ERROR))), List.of())));
        assertEquals(0, calls.get());
    }

    // T06
    @Test void onlyPositiveDicomEntriesAreInspectedOnce() {
        var selected = entry("z", DetectionResult.identified(DetectionOutcome.DICOM));
        var before = selected.assessment();
        var inventory = new DatasetInventory(List.of(selected, entry("a", DetectionResult.identified(DetectionOutcome.DICOM)),
                entry("n", DetectionResult.identified(DetectionOutcome.NIFTI)),
                entry("g", DetectionResult.identified(DetectionOutcome.NIFTI_GZ)),
                entry("u", DetectionResult.unknown(DetectionDiagnostic.UNSUPPORTED_FORMAT)),
                entry("c", DetectionResult.corrupt(DetectionDiagnostic.INVALID_DICOM))), List.of());
        var calls = new ArrayList<String>();
        var result = new DicomMetadataInspectionService(s -> { calls.add(s.path()); return success(s.path()); }).inspect(inventory);
        assertEquals(List.of("a", "z"), calls);
        assertEquals(calls, result.inspections().stream().map(i -> i.source().path()).toList());
        assertSame(before, selected.assessment()); assertNotEquals(ConversionReadiness.READY, before.readiness());
        assertTrue(result.complete());
    }

    // T07
    @Test void readerFailuresPreserveRequestedSourceCoverage() {
        List<DicomMetadataReader> broken = List.of(s -> null, s -> { throw new IllegalStateException("patient-provider-sentinel"); }, s -> success("other"));
        for (var reader : broken) {
            var calls = new AtomicInteger();
            var inventory = new DatasetInventory(List.of(entry("a", DetectionResult.identified(DetectionOutcome.DICOM)),
                    entry("b", DetectionResult.identified(DetectionOutcome.DICOM))), List.of());
            var result = new DicomMetadataInspectionService(s -> { calls.incrementAndGet(); return reader.inspect(s); }).inspect(inventory);
            assertEquals(2, calls.get()); assertEquals(List.of("a", "b"), result.inspections().stream().map(i -> i.source().path()).toList());
            assertFalse(result.complete());
            for (var i : result.inspections()) {
                assertEquals(Optional.of(FailureCode.METADATA_READ_FAILED), i.failure());
                assertFalse(i.toString().contains("sentinel")); assertTrue(i.metadata().isEmpty());
            }
        }
    }

    private static InventoryEntry entry(String p, DetectionResult detection) {
        return new InventoryEntry(source(p), FormatAssessment.fromDetection(detection));
    }
}
