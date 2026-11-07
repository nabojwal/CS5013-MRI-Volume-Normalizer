package org.cbihi.mrinormalizer.application.service;

import java.util.*;
import org.cbihi.mrinormalizer.application.validation.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;

/** One operation; even published bytes are not successful without live F1 final ACK and clean release. */
public record VerifiedConversionResult(Status status, Publication publication,
        Optional<ManifestReceipt> lastAcknowledgedHead,
        Optional<PostWriteVerificationEvidence> verification,Optional<ContentDigest> finalDigest,
        List<CheckpointRecord.ConversionFault> faults,List<NiftiComparisonFailure> comparisonFailures,
        List<ManifestFailure> workflowFailures, Optional<ProvenancePersistenceException> persistenceFailure) {
    public enum Status { VERIFIED_PUBLISHED, FAILED, RECOVERY_REQUIRED }
    public enum Publication { NOT_PUBLISHED, PUBLISHED_UNACKNOWLEDGED, VERIFIED_AND_ACKNOWLEDGED, UNKNOWN }
    public VerifiedConversionResult {
        if (status==null||publication==null||lastAcknowledgedHead==null||verification==null||finalDigest==null
                ||faults==null||comparisonFailures==null||workflowFailures==null||persistenceFailure==null)
            throw new IllegalArgumentException("Invalid verified conversion result");
        var f=EnumSet.noneOf(CheckpointRecord.ConversionFault.class);
        for(var x:faults) if(x==null||!f.add(x)) throw new IllegalArgumentException("Duplicate conversion fault");
        faults=List.copyOf(f);
        var c=EnumSet.noneOf(NiftiComparisonFailure.class);
        for(var x:comparisonFailures) if(x==null||!c.add(x)) throw new IllegalArgumentException("Duplicate comparison failure");
        comparisonFailures=List.copyOf(c);
        var w=new HashSet<ManifestFailure>();
        for(var x:workflowFailures) if(x==null||!w.add(x)) throw new IllegalArgumentException("Duplicate workflow failure");
        var ordered=new ArrayList<>(w);
        ordered.sort(Comparator.comparingInt((ManifestFailure x)->x.phase().ordinal()).thenComparingInt(x->x.code().ordinal()));
        workflowFailures=List.copyOf(ordered);
        boolean success=status==Status.VERIFIED_PUBLISHED;
        if(success!=(publication==Publication.VERIFIED_AND_ACKNOWLEDGED && verification.isPresent()
                && finalDigest.isPresent() && finalDigest.orElseThrow().equals(verification.orElseThrow().stagedDigest())
                && faults.isEmpty()&&comparisonFailures.isEmpty()&&workflowFailures.isEmpty()&&persistenceFailure.isEmpty()))
            throw new IllegalArgumentException("Verified publication coherence failure");
        if(status==Status.FAILED && (publication!=Publication.NOT_PUBLISHED || finalDigest.isPresent()
                || faults.isEmpty()&&comparisonFailures.isEmpty()&&workflowFailures.isEmpty()&&persistenceFailure.isEmpty()))
            throw new IllegalArgumentException("FAILED requires certain nonpublication and a cause");
    }
}
