package org.cbihi.mrinormalizer.application.dataset.nifti;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;
import org.cbihi.mrinormalizer.domain.model.ImageVolume;

/** Structural value contract; construction does not prove that I/O occurred. */
public record NiftiReadResult(RelativePath source, Optional<ImageVolume> volume,
        List<NiftiReadFailure> failures, List<NiftiReadFinding> findings) {
    public NiftiReadResult {
        if (source == null || volume == null || failures == null || findings == null
                || failures.stream().anyMatch(e -> e == null) || findings.stream().anyMatch(e -> e == null)) {
            throw new IllegalArgumentException("Reading result components are required");
        }
        var fs = EnumSet.noneOf(NiftiReadFailure.class); fs.addAll(failures);
        var os = EnumSet.noneOf(NiftiReadFinding.class); os.addAll(findings);
        failures = List.copyOf(fs); findings = List.copyOf(os);
        if (volume.isPresent() != failures.isEmpty()
                || volume.isPresent() && ((os.contains(NiftiReadFinding.SFORM_SELECTED) ? 1 : 0)
                    + (os.contains(NiftiReadFinding.QFORM_SELECTED) ? 1 : 0) != 1)) {
            throw new IllegalArgumentException("Incoherent reading result");
        }
    }
}
