package org.cbihi.mrinormalizer.application.provenance.manifest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/** Fixed-message typed persistence failure, with no cause, suppressed payload or writable stack. */
public final class ProvenancePersistenceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum PublicationOutcome { NOT_PUBLISHED, PUBLISHED, UNKNOWN }

    private final List<ManifestFailure> failures;
    private final PublicationOutcome outcome;
    private final Optional<ManifestReceipt> knownPublication;

    public ProvenancePersistenceException(List<ManifestFailure> failures, PublicationOutcome outcome,
            Optional<ManifestReceipt> knownPublication) {
        super("Provenance persistence failed", null, false, false);
        if (failures == null || outcome == null || knownPublication == null) {
            throw new IllegalArgumentException("Persistence failure components must be non-null");
        }
        if (failures.isEmpty() || failures.size() > 64) {
            throw new IllegalArgumentException("Persistence failures must be nonempty and bounded");
        }
        var seen = new HashSet<ManifestFailure>();
        var ordered = new ArrayList<ManifestFailure>(failures.size());
        for (var failure : failures) {
            if (failure == null || failure.phase() != ManifestFailure.Phase.PERSISTENCE || !seen.add(failure)) {
                throw new IllegalArgumentException("Persistence failures must be unique persistence facts");
            }
            ordered.add(failure);
        }
        ordered.sort(Comparator.comparingInt((ManifestFailure f) -> f.phase().ordinal()).thenComparingInt(f -> f.code().ordinal()));
        if (knownPublication.isPresent() && outcome != PublicationOutcome.PUBLISHED) {
            throw new IllegalArgumentException("Known receipt requires published outcome");
        }
        this.failures = List.copyOf(ordered);
        this.outcome = outcome;
        this.knownPublication = knownPublication;
    }

    public List<ManifestFailure> failures() { return failures; }

    public PublicationOutcome outcome() { return outcome; }

    public Optional<ManifestReceipt> knownPublication() { return knownPublication; }
}
