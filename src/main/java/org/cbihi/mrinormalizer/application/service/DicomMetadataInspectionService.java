package org.cbihi.mrinormalizer.application.service;

import java.util.ArrayList;
import java.util.Optional;
import org.cbihi.mrinormalizer.application.dataset.dicom.DicomMetadataCatalog;
import org.cbihi.mrinormalizer.application.dataset.dicom.DicomMetadataInspection;
import org.cbihi.mrinormalizer.application.dataset.dicom.DicomMetadataInspection.FailureCode;
import org.cbihi.mrinormalizer.application.dataset.model.DatasetInventory;
import org.cbihi.mrinormalizer.application.port.out.DicomMetadataReader;
import org.cbihi.mrinormalizer.domain.model.DetectionOutcome;
import org.cbihi.mrinormalizer.domain.model.ImagingFormat;

/** One metadata outcome per positively recognized source; no grouping or F0 mutation. */
public final class DicomMetadataInspectionService {
    private final DicomMetadataReader reader;
    public DicomMetadataInspectionService(DicomMetadataReader reader) {
        if (reader == null) throw new IllegalArgumentException("Metadata reader is required");
        this.reader = reader;
    }
    public DicomMetadataCatalog inspect(DatasetInventory inventory) {
        if (inventory == null || !inventory.complete()) throw new IllegalArgumentException("Complete inventory is required");
        var outcomes = new ArrayList<DicomMetadataInspection>();
        for (var entry : inventory.entries()) {
            var assessment = entry.assessment();
            if (assessment.initialDetection().outcome() != DetectionOutcome.DICOM || assessment.format() != ImagingFormat.DICOM) continue;
            DicomMetadataInspection outcome;
            try {
                outcome = reader.inspect(entry.source());
                if (outcome == null || !outcome.source().equals(entry.source())) throw new IllegalArgumentException("Invalid reader outcome");
            } catch (RuntimeException e) {
                outcome = new DicomMetadataInspection(entry.source(), Optional.empty(), Optional.of(FailureCode.METADATA_READ_FAILED));
            }
            outcomes.add(outcome);
        }
        return new DicomMetadataCatalog(outcomes);
    }
}
