package org.cbihi.mrinormalizer.m8;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.cbihi.mrinormalizer.application.m8.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;

final class M8ModelTest {
    private static M8Metadata metadata(){return new M8Metadata(true,true,
        M8Metadata.ScanningSequence.SE,M8Metadata.SequenceVariant.NONE);}
    @Test void m8_01_requestRejectsWrongSourceRootAndNonV4Uuid(){
        assertThrows(IllegalArgumentException.class,()->new M8Request(UUID.randomUUID(),
            new RelativePath(RelativePath.Root.OUTPUT,"x.nii"),metadata()));
        assertThrows(IllegalArgumentException.class,()->new M8Request(UUID.fromString("00000000-0000-1000-8000-000000000001"),
            new RelativePath(RelativePath.Root.SOURCE,"x.nii"),metadata()));
    }
    @Test void m8_01_metadataDoesNotInventUnknownTypeOneValues(){
        assertTrue(metadata().complete());
        assertFalse(new M8Metadata(false,true,M8Metadata.ScanningSequence.RM,M8Metadata.SequenceVariant.NONE).complete());
        assertFalse(new M8Metadata(true,true,null,M8Metadata.SequenceVariant.NONE).complete());
        assertFalse(new M8Metadata(true,true,M8Metadata.ScanningSequence.SE,null).complete());
    }
    @Test void m8_01_falseSuccessIsUnrepresentable(){
        assertThrows(IllegalArgumentException.class,()->new M8Result(UUID.randomUUID(),M8Result.Status.SUCCESS,
            M8Result.Publication.NONE,Optional.empty(),Optional.empty(),Optional.empty(),
            List.of(),List.of(),List.of(),List.of()));
    }
    @Test void m8_15_profilesCannotBeRaised(){
        M8Limits d=M8Limits.defaults();assertEquals(4096,d.maxDimension());
        assertThrows(IllegalArgumentException.class,()->new M8Limits(d.nifti(),4097,d.maxWorkingBytes(),
            d.maxOutputBytes(),d.maxCheckpointBytes(),d.maxJournalBytes()));
        assertThrows(IllegalArgumentException.class,()->new M8Limits(d.nifti(),d.maxDimension(),d.maxWorkingBytes(),
            129L<<20,d.maxCheckpointBytes(),d.maxJournalBytes()));
    }
}
