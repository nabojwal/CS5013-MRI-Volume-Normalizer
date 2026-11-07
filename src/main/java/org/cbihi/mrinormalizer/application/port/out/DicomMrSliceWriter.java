package org.cbihi.mrinormalizer.application.port.out;

import java.io.OutputStream;
import org.cbihi.mrinormalizer.application.m8.M8Plan;
import org.cbihi.mrinormalizer.domain.model.ImageVolume;

/** Borrowed stream; writer must not close the owner-provided target. */
public interface DicomMrSliceWriter {
    void write(ImageVolume source, M8Plan plan, int sourceZ, OutputStream target);
}
