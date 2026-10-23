package org.cbihi.mrinormalizer.application.service;
import java.util.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.domain.model.InputSource;
public record VerifiedConversionRequest(String operationId,List<InputSource> selectedInputs,
        String selectedSeriesInstanceUid, RelativePath destination,ManifestReceipt acknowledgedRunningHead) {
    public VerifiedConversionRequest {
        if (operationId==null||!operationId.matches("[0-9a-f]{64}")||selectedInputs==null||selectedInputs.isEmpty()
                ||selectedInputs.stream().anyMatch(Objects::isNull)||selectedSeriesInstanceUid==null
                ||selectedSeriesInstanceUid.isBlank()||destination==null||destination.root()!=RelativePath.Root.OUTPUT
                ||acknowledgedRunningHead==null)
            throw new IllegalArgumentException("Invalid verified conversion request");
        selectedInputs=List.copyOf(selectedInputs);
    }
}
