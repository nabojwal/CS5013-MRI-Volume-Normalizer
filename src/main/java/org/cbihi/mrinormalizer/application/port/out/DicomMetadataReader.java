package org.cbihi.mrinormalizer.application.port.out;

import org.cbihi.mrinormalizer.application.dataset.dicom.DicomMetadataInspection;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;

/** Restricted metadata-only external boundary. No execution identity is established. */
public interface DicomMetadataReader {
    DicomMetadataInspection inspect(RelativePath source);
}
