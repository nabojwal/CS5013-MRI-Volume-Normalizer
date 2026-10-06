package org.cbihi.mrinormalizer.application.provenance.manifest;

import java.util.List;

import org.cbihi.mrinormalizer.domain.error.DicomProcessingError;
import org.cbihi.mrinormalizer.domain.error.DicomToNiftiError;
import org.cbihi.mrinormalizer.domain.model.DetectionDiagnostic;

/** Closed aggregate public value. Restricted identifiers and technical facts have no field here. */
public record PublicJobReport(
        int schemaVersion,
        ManifestState.JobState state,
        long sourceCount,
        List<OperationCount> operationCounts,
        List<FailureCount> failureCounts
) {
    public record OperationCount(ManifestOperation.Kind kind, CheckpointRecord.State state, long count) {
        public OperationCount {
            if (kind == null || state == null || count < 0) throw invalid();
        }
    }

    public enum FailureNamespace {
        DETECTION, RECONSTRUCTION, CONVERSION, HASHING, EXECUTION, POST_WRITE_VALIDATION, PERSISTENCE
    }

    public record FailureCount(FailureNamespace namespace, String code, long count) {
        public FailureCount {
            if (namespace == null || code == null || count <= 0) throw invalid();
            codeOrdinal(namespace, code);
        }
    }

    public PublicJobReport {
        if (schemaVersion != 1 || state == null || sourceCount < 0 || sourceCount > 100000
                || operationCounts == null || failureCounts == null) throw invalid();
        var kinds = ManifestOperation.Kind.values();
        var states = CheckpointRecord.State.values();
        if (operationCounts.size() != kinds.length * states.length) throw invalid();
        long operations = 0;
        int index = 0;
        try {
            for (var kind : kinds) for (var observation : states) {
                var count = operationCounts.get(index++);
                if (count == null || count.kind() != kind || count.state() != observation) throw invalid();
                operations = Math.addExact(operations, count.count());
            }
            // The accepted restricted plan has at most 100000 operations.
            if (operations > 100000) throw invalid();
            long failures = 0;
            int previousNamespace = -1;
            int previousCode = -1;
            for (var count : failureCounts) {
                if (count == null) throw invalid();
                int namespace = count.namespace().ordinal();
                int code = codeOrdinal(count.namespace(), count.code());
                if (namespace < previousNamespace || namespace == previousNamespace && code <= previousCode) throw invalid();
                failures = Math.addExact(failures, count.count());
                previousNamespace = namespace;
                previousCode = code;
            }
        } catch (ArithmeticException overflow) {
            throw invalid();
        }
        operationCounts = List.copyOf(operationCounts);
        failureCounts = List.copyOf(failureCounts);
    }

    private static int codeOrdinal(FailureNamespace namespace, String code) {
        return switch (namespace) {
            case DETECTION -> {
                if (code.equals(DetectionDiagnostic.NONE.name())) throw invalid();
                yield enumOrdinal(DetectionDiagnostic.values(), code);
            }
            case RECONSTRUCTION -> enumOrdinal(DicomProcessingError.values(), code);
            case CONVERSION -> enumOrdinal(DicomToNiftiError.values(), code);
            case HASHING, EXECUTION, POST_WRITE_VALIDATION, PERSISTENCE -> {
                int ordinal = enumOrdinal(ManifestFailure.Code.values(), code);
                var phase = switch (namespace) {
                    case HASHING -> ManifestFailure.Phase.HASHING;
                    case EXECUTION -> ManifestFailure.Phase.EXECUTION;
                    case POST_WRITE_VALIDATION -> ManifestFailure.Phase.POST_WRITE_VALIDATION;
                    case PERSISTENCE -> ManifestFailure.Phase.PERSISTENCE;
                    default -> throw invalid();
                };
                try { new ManifestFailure(phase, ManifestFailure.Code.values()[ordinal]); }
                catch (IllegalArgumentException inapplicable) { throw invalid(); }
                yield ordinal;
            }
        };
    }

    private static <E extends Enum<E>> int enumOrdinal(E[] values, String code) {
        for (var value : values) if (value.name().equals(code)) return value.ordinal();
        throw invalid();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid public job report");
    }
}
