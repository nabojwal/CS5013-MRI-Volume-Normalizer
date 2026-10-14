package org.cbihi.mrinormalizer.application.service;

import org.cbihi.mrinormalizer.application.dataset.copy.SourceDigestBatch;
import org.cbihi.mrinormalizer.application.dataset.dicom.DicomSeriesDiscovery;
import org.cbihi.mrinormalizer.application.dataset.model.DatasetInventory;
import org.cbihi.mrinormalizer.application.port.out.VerifiedCopyAccess;

/** Borrows the adapter for pre-planning evidence; never owns/closes an execution session. */
public final class SourceDigestService {
    private final VerifiedCopyAccess access;
    public SourceDigestService(VerifiedCopyAccess access) {
        if (access == null) throw new IllegalArgumentException("Invalid COPY acquisition contract"); this.access = access;
    }
    public SourceDigestBatch acquire(DatasetInventory inventory, DicomSeriesDiscovery discovery) {
        if (inventory == null || discovery == null) throw new IllegalArgumentException("Invalid COPY acquisition contract");
        return access.acquire(inventory, discovery);
    }
}
