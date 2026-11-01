package org.cbihi.mrinormalizer.application.m8;

import java.util.List;
import java.util.Objects;

/** No unsafe provider, path, patient, or exception text in public fault facts. */
public record M8Fault(Phase phase, Code code) {
    public M8Fault { Objects.requireNonNull(phase); Objects.requireNonNull(code); }
    public enum Phase { REQUEST, SOURCE, PLAN, WRITE, VERIFY, JOURNAL, PUBLISH, CLEANUP, RELEASE }
    public enum Code { INVALID_REQUEST, METADATA_REQUIRED, INVALID_INPUT,
        UNSUPPORTED_INPUT, RESOURCE_LIMIT, GEOMETRY_UNREPRESENTABLE,
        SCALING_UNREPRESENTABLE, UID_INVALID, CONTAINMENT_UNPROVEN,
        PROVIDER_UNSUPPORTED, ACCESS_DENIED, SOURCE_CHANGED, OUTPUT_CONFLICT,
        READ_FAILED, WRITE_FAILED, VERIFICATION_FAILED, STAGE_CHANGED,
        FINAL_CHANGED, ACK_UNCERTAIN, PUBLICATION_UNCERTAIN, CLEANUP_FAILED,
        RELEASE_FAILED, INTERRUPTED, INTERNAL_ERROR }

    public static final class Failure extends RuntimeException {
        private final List<M8Fault> faults;
        private final boolean uncertain;
        public Failure(List<M8Fault> faults, boolean effectsUncertain) {
            super("M8 operation refused or failed", null, false, false);
            if (faults == null || faults.isEmpty() || faults.stream().anyMatch(java.util.Objects::isNull))
                throw new IllegalArgumentException("Failures require controlled fault facts");
            this.faults = List.copyOf(faults);
            this.uncertain = effectsUncertain;
        }
        public List<M8Fault> faults() { return faults; }
        public boolean effectsUncertain() { return uncertain; }
    }
    public static Failure fail(Phase phase, Code code) {
        return new Failure(List.of(new M8Fault(phase,code)),false);
    }
}
