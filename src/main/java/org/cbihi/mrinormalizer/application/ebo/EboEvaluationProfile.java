package org.cbihi.mrinormalizer.application.ebo;

import org.cbihi.mrinormalizer.application.m8.M8Metadata;

/** Explicit caller-selected non-clinical template; never recovered acquisition facts. */
public final class EboEvaluationProfile {
    public static final String NAME="evaluation";
    public static final String DISCLOSURE="caller-selected non-clinical template assumptions, not recovered acquisition facts";
    private EboEvaluationProfile() {}
    public static M8Metadata metadata() {
        return new M8Metadata(true,true,M8Metadata.ScanningSequence.SE,M8Metadata.SequenceVariant.NONE);
    }
}
