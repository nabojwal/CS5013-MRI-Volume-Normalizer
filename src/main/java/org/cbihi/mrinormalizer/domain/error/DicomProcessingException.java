package org.cbihi.mrinormalizer.domain.error;

public final class DicomProcessingException extends RuntimeException {

    private final DicomProcessingError error;

    public DicomProcessingException(DicomProcessingError error, String message) {
        super(message);
        this.error = error;
    }

    public DicomProcessingException(DicomProcessingError error, String message, Throwable cause) {
        super(message, cause);
        this.error = error;
    }

    public DicomProcessingError error() {
        return error;
    }
}
