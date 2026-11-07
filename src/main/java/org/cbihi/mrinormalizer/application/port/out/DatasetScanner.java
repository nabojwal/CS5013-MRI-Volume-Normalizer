package org.cbihi.mrinormalizer.application.port.out;

import org.cbihi.mrinormalizer.application.dataset.model.DatasetInventory;

/** Read-only deterministic inventory of the adapter's configured dataset roots. */
public interface DatasetScanner {
    DatasetInventory scan();
}
