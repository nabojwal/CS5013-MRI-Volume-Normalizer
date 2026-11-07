package org.cbihi.mrinormalizer.application.ebo;

import java.nio.file.Path;
import java.util.Objects;

/** One input file or a top-level directory; output root must already exist. */
public record EboRequest(Mode mode, Path input, Path outputRoot, String metadataProfile) {
    public enum Mode { SINGLE, DIRECTORY }
    public EboRequest {
        Objects.requireNonNull(mode,"mode"); Objects.requireNonNull(input,"input");
        Objects.requireNonNull(outputRoot,"outputRoot");
        if(!EboEvaluationProfile.NAME.equals(metadataProfile))
            throw new IllegalArgumentException("Unsupported metadata profile");
    }
}
