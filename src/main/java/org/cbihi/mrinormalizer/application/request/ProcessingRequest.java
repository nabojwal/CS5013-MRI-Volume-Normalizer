package org.cbihi.mrinormalizer.application.request;

import org.cbihi.mrinormalizer.domain.model.InputSource;
import org.cbihi.mrinormalizer.domain.model.OutputTarget;

public record ProcessingRequest(
        InputSource input,
        OutputTarget output
) {
}
