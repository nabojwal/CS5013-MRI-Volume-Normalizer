package org.cbihi.mrinormalizer.domain.model;

public record DicomInstance(
        String sopInstanceUid,
        String studyInstanceUid,
        String seriesInstanceUid,
        String modality,
        String sopClassUid,
        String transferSyntaxUid,
        int rows,
        int columns,
        double rowSpacing,
        double columnSpacing,
        SliceGeometry geometry,
        PixelEncoding pixelEncoding,
        RescaleTransform rescaleTransform,
        VoxelData pixels,
        String frameOfReferenceUid,
        String anatomicalOrientationType
) {

    public DicomInstance {
        requireText(sopInstanceUid);
        requireText(studyInstanceUid);
        requireText(seriesInstanceUid);
        requireText(modality);
        requireText(sopClassUid);
        requireText(transferSyntaxUid);
        if (rows <= 0 || columns <= 0 || !Double.isFinite(rowSpacing) || !Double.isFinite(columnSpacing)
                || rowSpacing <= 0 || columnSpacing <= 0 || geometry == null || pixelEncoding == null
                || rescaleTransform == null || pixels == null || pixels.width() != columns
                || pixels.height() != rows || pixels.depth() != 1) {
            throw new IllegalArgumentException("DICOM instance metadata is invalid");
        }
    }

    private static void requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("required DICOM text metadata is missing");
        }
    }
}
