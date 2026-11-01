package org.cbihi.mrinormalizer.application.m8;

/** Caller attests MR provenance and BIPED coordinate applicability; no invented acquisition values. */
public record M8Metadata(boolean mrOriginConfirmed, boolean bipedCoordinatesConfirmed,
                         ScanningSequence scanningSequence, SequenceVariant sequenceVariant) {
    public enum ScanningSequence { SE, IR, GR, EP, RM }
    public enum SequenceVariant { SK, MTC, SS, TRSS, SP, MP, OSP, NONE }

    public boolean complete() {
        return mrOriginConfirmed && bipedCoordinatesConfirmed
                && scanningSequence != null && sequenceVariant != null;
    }
}
