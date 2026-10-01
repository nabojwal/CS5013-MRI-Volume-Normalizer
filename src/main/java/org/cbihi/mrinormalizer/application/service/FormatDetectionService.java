package org.cbihi.mrinormalizer.application.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.cbihi.mrinormalizer.domain.model.DetectionDiagnostic;
import org.cbihi.mrinormalizer.domain.model.DetectionOutcome;
import org.cbihi.mrinormalizer.domain.model.DetectionResult;
import org.cbihi.mrinormalizer.domain.model.InputSource;
import org.cbihi.mrinormalizer.domain.port.FormatProbe;

public final class FormatDetectionService {

    private final List<FormatProbe> probes;

    public FormatDetectionService(List<FormatProbe> probes) {
        this.probes = List.copyOf(probes);
    }

    public DetectionResult detect(InputSource input) {
        final Path path;
        try {
            path = Path.of(input.reference());
        } catch (RuntimeException exception) {
            return DetectionResult.unknown(DetectionDiagnostic.INVALID_INPUT_REFERENCE);
        }
        if (!Files.exists(path)) {
            return DetectionResult.unknown(DetectionDiagnostic.INPUT_NOT_FOUND);
        }
        if (Files.isDirectory(path)) {
            return DetectionResult.unknown(DetectionDiagnostic.INPUT_IS_DIRECTORY);
        }
        if (!Files.isReadable(path)) {
            return DetectionResult.unknown(DetectionDiagnostic.INPUT_NOT_READABLE);
        }
        try {
            if (Files.size(path) == 0) {
                return DetectionResult.unknown(DetectionDiagnostic.EMPTY_INPUT);
            }
        } catch (java.io.IOException exception) {
            return DetectionResult.unknown(DetectionDiagnostic.IO_ERROR);
        }

        DetectionResult fallback = DetectionResult.unknown(DetectionDiagnostic.UNSUPPORTED_FORMAT);
        for (FormatProbe probe : probes) {
            DetectionResult result = probe.probe(input);
            if (result.outcome() != DetectionOutcome.UNKNOWN) {
                return applyExtensionHint(result, path);
            }
            // Inconclusive probes must not hide a later positive match. Retain useful
            // diagnostics if every remaining probe merely reports a non-match.
            if (result.diagnostic() != DetectionDiagnostic.UNSUPPORTED_FORMAT) {
                fallback = result;
            }
        }
        return applyExtensionHint(fallback, path);
    }

    private DetectionResult applyExtensionHint(DetectionResult result, Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        boolean mismatch = switch (result.outcome()) {
            case DICOM -> !name.endsWith(".dcm");
            case NIFTI -> !(name.endsWith(".nii") || name.endsWith(".hdr"));
            case NIFTI_GZ -> !name.endsWith(".nii.gz");
            default -> false;
        };
        return result.withExtensionMismatch(mismatch);
    }
}
