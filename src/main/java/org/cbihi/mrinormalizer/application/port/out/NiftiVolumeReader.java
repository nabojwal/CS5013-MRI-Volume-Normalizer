package org.cbihi.mrinormalizer.application.port.out;

import org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadResult;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;

/** Reads a logical source under the adapter's configured root. */
public interface NiftiVolumeReader {
    NiftiReadResult read(RelativePath source);
}
