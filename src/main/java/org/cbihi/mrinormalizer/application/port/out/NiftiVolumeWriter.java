package org.cbihi.mrinormalizer.application.port.out;

import org.cbihi.mrinormalizer.domain.model.AffineMatrix4;
import org.cbihi.mrinormalizer.domain.model.ImageVolume;
import org.cbihi.mrinormalizer.domain.model.OutputTarget;

/** Application output boundary for serializing a generic image volume as NIfTI. */
public interface NiftiVolumeWriter {

    void write(ImageVolume volume, AffineMatrix4 voxelToWorldRas, OutputTarget target);
}
