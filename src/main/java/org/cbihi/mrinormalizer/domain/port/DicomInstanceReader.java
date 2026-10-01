package org.cbihi.mrinormalizer.domain.port;

import org.cbihi.mrinormalizer.domain.model.DicomInstance;
import org.cbihi.mrinormalizer.domain.model.InputSource;

public interface DicomInstanceReader {

    DicomInstance read(InputSource input);
}
