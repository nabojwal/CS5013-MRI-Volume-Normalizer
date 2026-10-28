package org.cbihi.mrinormalizer.application.request;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;

/** Exactly one caller-selected collection; no series or slab inference. */
public record F9HeadlessRequest(UUID jobId, String selectedSeriesInstanceUid,
        List<RelativePath> selectedMembers, RelativePath destination) {
    public F9HeadlessRequest {
        if (jobId == null || selectedSeriesInstanceUid == null || selectedSeriesInstanceUid.isBlank()
                || !selectedSeriesInstanceUid.matches("[0-9]+(?:\\.[0-9]+)*")
                || selectedSeriesInstanceUid.length() > 64 || selectedMembers == null
                || selectedMembers.isEmpty() || selectedMembers.size() > 128
                || selectedMembers.stream().anyMatch(p -> p == null || p.root() != RelativePath.Root.SOURCE)
                || new HashSet<>(selectedMembers).size() != selectedMembers.size()
                || destination == null || destination.root() != RelativePath.Root.OUTPUT
                || !destination.path().endsWith(".nii")) {
            throw new IllegalArgumentException("Invalid selected-slab request");
        }
        selectedMembers = List.copyOf(selectedMembers);
    }
}
