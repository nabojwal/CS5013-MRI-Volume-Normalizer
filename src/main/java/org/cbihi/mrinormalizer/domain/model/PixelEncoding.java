package org.cbihi.mrinormalizer.domain.model;

import java.util.Objects;

/** Describes the DICOM source encoding of unscaled stored pixel samples. */
public record PixelEncoding(
        int bitsAllocated,
        int bitsStored,
        int highBit,
        PixelValueType valueType,
        int samplesPerPixel,
        String photometricInterpretation
) {
    public PixelEncoding {
        if (bitsAllocated != 8 && bitsAllocated != 16) {
            throw new IllegalArgumentException("only 8-bit and 16-bit native samples are supported");
        }
        if (bitsStored < 1 || bitsStored > bitsAllocated || highBit != bitsStored - 1) {
            throw new IllegalArgumentException("DICOM stored-bit layout is invalid");
        }
        Objects.requireNonNull(valueType, "valueType");
        if (samplesPerPixel != 1) {
            throw new IllegalArgumentException("only single-sample pixels are supported");
        }
        if (!"MONOCHROME2".equals(photometricInterpretation)) {
            throw new IllegalArgumentException("only MONOCHROME2 pixels are supported");
        }
    }
}
