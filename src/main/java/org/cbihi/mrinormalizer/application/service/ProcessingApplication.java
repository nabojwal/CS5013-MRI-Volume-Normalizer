package org.cbihi.mrinormalizer.application.service;

import org.cbihi.mrinormalizer.application.request.ProcessingRequest;
import org.cbihi.mrinormalizer.application.result.ProcessingResult;

public interface ProcessingApplication {

    ProcessingResult execute(ProcessingRequest request);
}
