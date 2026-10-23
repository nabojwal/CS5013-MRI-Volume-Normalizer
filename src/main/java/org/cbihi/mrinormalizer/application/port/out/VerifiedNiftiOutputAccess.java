package org.cbihi.mrinormalizer.application.port.out;

import java.util.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.domain.model.*;

/** Session-owned staged NIfTI publication port. A digest or caller-created receipt is not a capability. */
public interface VerifiedNiftiOutputAccess extends AutoCloseable {
    SourceBinding validateSelectedSources(List<InputSource> selectedInputs,
            List<SourceFileRecord> plannedSourceRecords);
    PreparedOutput prepare(String operationId, RelativePath finalDestination,
            ManifestReceipt acknowledgedIntent);
    @Override void close();

    record SourceBinding(List<RelativePath> sources, int matchedExpectedSourceCount) {
        public SourceBinding {
            if (sources == null || sources.isEmpty() || matchedExpectedSourceCount != sources.size()
                    || sources.stream().anyMatch(x -> x == null || x.root() != RelativePath.Root.SOURCE)
                    || new HashSet<>(sources).size()!=sources.size())
                throw new IllegalArgumentException("Invalid source binding");
            sources=List.copyOf(sources);
        }
    }
    enum AccessFault {
        SOURCE_PATH_INVALID, SOURCE_CONTAINMENT_UNPROVEN, SOURCE_CHANGED,
        SOURCE_DIGEST_MISMATCH, RESOURCE_LIMIT, STAGE_CREATE_FAILED,
        STAGE_CLOSE_FAILED, STAGE_HASH_FAILED, STAGE_CHANGED,
        WEAK_IDENTITY_UNQUALIFIED, OUTPUT_CONFLICT,
        PUBLICATION_UNAVAILABLE, PUBLICATION_UNKNOWN,
        FINAL_DIGEST_MISMATCH, CLEANUP_FAILED, RELEASE_FAILED, INTERRUPTED
    }
    /** Controlled failure, no provider message/cause/stack or identifying paths. */
    final class AccessFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final List<AccessFault> faults;
        public AccessFailure(List<AccessFault> faults) {
            super("Verified NIfTI output access failed", null, false, false);
            if (faults == null || faults.isEmpty() || faults.size() > AccessFault.values().length)
                throw new IllegalArgumentException("Invalid access faults");
            var unique = EnumSet.noneOf(AccessFault.class);
            for (var f: faults) if (f == null || !unique.add(f))
                throw new IllegalArgumentException("Invalid access faults");
            this.faults=List.copyOf(unique);
        }
        public List<AccessFault> faults(){return faults;}
    }
    enum Publication { NOT_PUBLISHED, PUBLISHED_BYTES_CHECKED, UNKNOWN }
    record PublicationResult(Publication publication, Optional<ContentDigest> finalDigest,
            List<AccessFault> faults) {
        public PublicationResult {
            if (publication==null || finalDigest==null || faults==null)
                throw new IllegalArgumentException("Invalid publication result");
            var s=EnumSet.noneOf(AccessFault.class);
            for (var f:faults) if(f==null||!s.add(f)) throw new IllegalArgumentException("Invalid publication faults");
            faults=List.copyOf(s);
            if ((publication==Publication.PUBLISHED_BYTES_CHECKED)!=(finalDigest.isPresent())
                || publication==Publication.PUBLISHED_BYTES_CHECKED&&!faults.isEmpty())
                throw new IllegalArgumentException("Invalid publication identity");
        }
    }
    interface PreparedOutput extends AutoCloseable {
        UUID sessionId();
        OutputTarget stageTarget();
        RelativePath stageReadReference();
        ContentDigest digestClosedStage();
        PublicationResult publishVerified(ManifestReceipt acknowledgedStageVerification);
        void discardUnpublished();
        @Override void close();
    }
}
