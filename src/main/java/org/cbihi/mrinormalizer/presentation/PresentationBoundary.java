package org.cbihi.mrinormalizer.presentation;

import org.cbihi.mrinormalizer.application.result.ProcessingResult;

public interface PresentationBoundary {

    void present(ProcessingResult result);
}
