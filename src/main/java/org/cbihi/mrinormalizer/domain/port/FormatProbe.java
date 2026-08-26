package org.cbihi.mrinormalizer.domain.port;

import org.cbihi.mrinormalizer.domain.model.DetectionResult;
import org.cbihi.mrinormalizer.domain.model.InputSource;

public interface FormatProbe {

    DetectionResult probe(InputSource input);
}
