package org.cbihi.mrinormalizer.application.port.out;

import java.io.InputStream;
import org.cbihi.mrinormalizer.application.m8.M8Plan;
import org.cbihi.mrinormalizer.domain.model.ImageVolume;

/** Independently parses emitted DICOM; never delegates to the writer. */
public interface DicomMrSliceVerifier {
    Check verify(ImageVolume source, M8Plan plan, int sourceZ, InputStream input);
    record Check(long comparedVoxels, int comparedCorners,
                 double maxWorldErrorMm, double maxIntensityError) {
        public Check {
            if (comparedVoxels < 0 || comparedCorners < 0 || !Double.isFinite(maxWorldErrorMm)
                    || maxWorldErrorMm < 0 || !Double.isFinite(maxIntensityError) || maxIntensityError < 0)
                throw new IllegalArgumentException("Invalid comparison evidence");
        }
    }
}
