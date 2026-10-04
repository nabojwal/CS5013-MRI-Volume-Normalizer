package org.cbihi.mrinormalizer.application.port.out;

import java.util.Optional;

import org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestReceipt;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestState;
import org.cbihi.mrinormalizer.application.provenance.manifest.ProvenanceManifest;

/** Job-scoped restricted persistence protocol. This port has no implementation in S2A. */
public interface ManifestStore extends AutoCloseable {
    /** Publish the immutable execution-evidence plan; acknowledge known publication with a sequence-zero receipt. */
    ManifestReceipt create(ProvenanceManifest plan);

    /** Publish one factual record against the expected head; known publication returns its receipt. */
    ManifestReceipt append(CheckpointRecord record, ManifestReceipt expectedHead);

    /**
     * Replay the complete valid plan/journal. An empty argument allows normal restart without an external
     * acknowledged rollback floor. A present receipt additionally requires its unchanged presence in the valid
     * chain, including when a later valid head exists. No committed plan and no orphan records yields an empty
     * result only when no supplied receipt claims acknowledged evidence. Orphan records, acknowledged loss or
     * corrupt committed evidence must fail, never silently return an older usable prefix.
     */
    Optional<ManifestState> replay(Optional<ManifestReceipt> minimumExpectedHead);

    @Override
    void close();
}
