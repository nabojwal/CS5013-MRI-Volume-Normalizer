package org.cbihi.mrinormalizer.application.port.out;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadResult;
import org.cbihi.mrinormalizer.application.m8.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.ContentDigest;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;

/** One-shot, owned workspace. Receipts are replayed/bound by the workspace before publication. */
public interface M8Workspace {
    Session open(M8Request request);
    enum Event { INTENT, STAGED_VERIFIED, PUBLISHING, PUBLISHED, COMPLETED, FAILED, RECOVERY_REQUIRED }
    record Receipt(UUID jobId, long sequence, String sha256, Event event) {}
    record SliceEvidence(int sourceZ, String filename, String sopUid,
                         ContentDigest digest, DicomMrSliceVerifier.Check check) {}
    record Checkpoint(long sequence, String previousSha256, Event event,
                      Instant utc, M8Plan plan, List<SliceEvidence> slices,
                      List<M8Fault> faults) {
        public Checkpoint { slices=List.copyOf(slices); faults=List.copyOf(faults); }
    }
    interface Session extends AutoCloseable {
        ContentDigest sourceDigest();
        NiftiReadResult readSnapshot();
        void verifySnapshotUnchanged();
        OutputStream createStage(int sourceZ);
        InputStream reopenStage(int sourceZ);
        ContentDigest digestClosedStage(int sourceZ);
        Receipt acknowledge(Event event, M8Plan plan, List<SliceEvidence> evidence, List<M8Fault> faults);
        Receipt replay(Receipt expected);
        void publish(Receipt publishingReceipt, List<SliceEvidence> evidence);
        List<SliceEvidence> verifyPublished(List<SliceEvidence> expected);
        void cleanupOwnedData();
        void sealCompletion(Receipt completedReceipt);
        M8Result.Publication publicationKnowledge();
        RelativePath outputDirectory();
        @Override void close();
    }
}
