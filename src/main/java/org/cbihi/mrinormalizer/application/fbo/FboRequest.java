package org.cbihi.mrinormalizer.application.fbo;

import java.nio.file.Path;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;

/** Explicit ordered prepared-directory selections, never inferred from patient metadata. */
public record FboRequest(Path inputParent, Path outputParent, List<SeriesSelection> selections) {
    public record SeriesSelection(String childDirectory, String seriesInstanceUid) {
        public SeriesSelection {
            if (childDirectory == null || childDirectory.isBlank()
                    || childDirectory.contains("/") || childDirectory.contains("\\")
                    || !childDirectory.equals(childDirectory.trim())
                    || seriesInstanceUid == null || seriesInstanceUid.length() > 64
                    || !seriesInstanceUid.matches("[0-9]+(?:\\.[0-9]+)*"))
                throw new IllegalArgumentException("Invalid FBO series selection");
            new RelativePath(RelativePath.Root.SOURCE, childDirectory);
            for (String component : seriesInstanceUid.split("\\.")) {
                if (component.length() > 1 && component.charAt(0) == '0')
                    throw new IllegalArgumentException("Noncanonical series UID");
            }
        }
    }
    public FboRequest {
        Objects.requireNonNull(inputParent, "inputParent");
        Objects.requireNonNull(outputParent, "outputParent");
        if (selections == null || selections.isEmpty() || selections.size() > 16)
            throw new IllegalArgumentException("FBO supports 1-16 selections");
        Set<String> seen = new HashSet<>();
        for (var s : selections) {
            if (s == null || !seen.add(Normalizer.normalize(s.childDirectory(), Normalizer.Form.NFC)
                    .toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Duplicate or invalid FBO selection");
        }
        selections = List.copyOf(selections);
    }
}
