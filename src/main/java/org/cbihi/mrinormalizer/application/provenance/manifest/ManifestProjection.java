package org.cbihi.mrinormalizer.application.provenance.manifest;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.cbihi.mrinormalizer.application.provenance.ProvenanceRecord;
import org.cbihi.mrinormalizer.application.provenance.manifest.PublicJobReport.FailureCount;
import org.cbihi.mrinormalizer.application.provenance.manifest.PublicJobReport.FailureNamespace;
import org.cbihi.mrinormalizer.application.provenance.manifest.PublicJobReport.OperationCount;
import org.cbihi.mrinormalizer.application.result.DicomProcessingResult;
import org.cbihi.mrinormalizer.application.result.DicomToNiftiResult;
import org.cbihi.mrinormalizer.domain.error.DicomProcessingError;
import org.cbihi.mrinormalizer.domain.error.DicomToNiftiError;
import org.cbihi.mrinormalizer.domain.model.DetectionDiagnostic;

/** Pure supplied-value projections. No execution, persistence, encoding or post-write reopening. */
public final class ManifestProjection {
    private ManifestProjection() { }

    public static ProcessingEvidence reconstruction(DicomProcessingResult result) {
        if (result == null) throw invalid();
        var errors = checkedErrors(result.errors());
        if (!result.successful() && errors.isEmpty()) throw invalid();
        return new ProcessingEvidence(ProcessingEvidence.Scope.RECONSTRUCTION, result.successful(),
                summary(result.provenance(), errors), errors, List.of(), Optional.empty());
    }

    public static ProcessingEvidence conversion(DicomToNiftiResult result) {
        if (result == null) throw invalid();
        var reconstructionErrors = checkedErrors(result.errors());
        var conversionErrors = EnumSet.noneOf(DicomToNiftiError.class);
        for (var error : result.conversionErrors()) if (!conversionErrors.add(error)) throw invalid();
        if (!result.successful() && reconstructionErrors.isEmpty() && conversionErrors.isEmpty()) throw invalid();
        var sourceSummary = summary(result.provenance(), reconstructionErrors);
        Optional<ProcessingEvidence.ConversionFacts> facts = Optional.empty();
        if (result.successful()) {
            var report = result.validationReport();
            // The existing result constructor already enforces report/target
            // consistency; keep it explicit without retaining the runtime target.
            if (report == null || !result.output().equals(report.output())) throw invalid();
            facts = Optional.of(new ProcessingEvidence.ConversionFacts(report.width(), report.height(), report.depth(),
                    report.scalarType(), report.voxelCount(), report.rowSpacingMm(), report.columnSpacingMm(), report.sliceSpacingMm(),
                    report.niftiRasVoxelToWorld(), report.intensityTransform(), report.storedVoxelValuesPreserved(),
                    report.resampled(), report.interpolated(), report.voxelOrderChanged()));
        }
        return new ProcessingEvidence(ProcessingEvidence.Scope.CONVERSION, result.successful(), sourceSummary,
                reconstructionErrors, List.copyOf(conversionErrors), facts);
    }

    public static PublicJobReport publicReport(ManifestState current) {
        if (current == null) throw invalid();
        var kinds = ManifestOperation.Kind.values();
        var states = CheckpointRecord.State.values();
        long[][] operations = new long[kinds.length][states.length];
        long[] detection = new long[DetectionDiagnostic.values().length];
        long[] reconstruction = new long[DicomProcessingError.values().length];
        long[] conversion = new long[DicomToNiftiError.values().length];
        long[][] workflow = new long[ManifestFailure.Phase.values().length][ManifestFailure.Code.values().length];
        try {
            for (var source : current.plan().sources()) {
                var diagnostic = source.assessment().initialDetection().diagnostic();
                if (diagnostic != DetectionDiagnostic.NONE) detection[diagnostic.ordinal()] = Math.addExact(detection[diagnostic.ordinal()], 1);
                addWorkflow(source.failures(), workflow);
            }
            // Both accepted immutable lists are canonical in operation-ID order.
            // Join by that exact key; neither kind nor identity is inferred from evidence.
            for (int index = 0; index < current.operations().size(); index++) {
                var observed = current.operations().get(index);
                var planned = current.plan().operations().get(index);
                if (!observed.operationId().equals(planned.operationId())) throw invalid();
                int kind = planned.kind().ordinal();
                int state = observed.observation().state().ordinal();
                operations[kind][state] = Math.addExact(operations[kind][state], 1);
                var evidence = observed.observation().processingEvidence();
                if (evidence.isPresent()) {
                    for (var error : evidence.orElseThrow().reconstructionErrors()) reconstruction[error.ordinal()] = Math.addExact(reconstruction[error.ordinal()], 1);
                    for (var error : evidence.orElseThrow().conversionErrors()) conversion[error.ordinal()] = Math.addExact(conversion[error.ordinal()], 1);
                }
                addWorkflow(observed.failures(), workflow);
            }
            addWorkflow(current.jobFailures(), workflow);
        } catch (ArithmeticException overflow) {
            throw invalid();
        }
        var operationCounts = new ArrayList<OperationCount>(kinds.length * states.length);
        for (var kind : kinds) for (var state : states) operationCounts.add(new OperationCount(kind, state, operations[kind.ordinal()][state.ordinal()]));
        var failureCounts = new ArrayList<FailureCount>();
        addCounts(failureCounts, FailureNamespace.DETECTION, DetectionDiagnostic.values(), detection);
        addCounts(failureCounts, FailureNamespace.RECONSTRUCTION, DicomProcessingError.values(), reconstruction);
        addCounts(failureCounts, FailureNamespace.CONVERSION, DicomToNiftiError.values(), conversion);
        for (var phase : ManifestFailure.Phase.values()) {
            var namespace = switch (phase) {
                case HASHING -> FailureNamespace.HASHING;
                case EXECUTION -> FailureNamespace.EXECUTION;
                case POST_WRITE_VALIDATION -> FailureNamespace.POST_WRITE_VALIDATION;
                case PERSISTENCE -> FailureNamespace.PERSISTENCE;
            };
            addCounts(failureCounts, namespace, ManifestFailure.Code.values(), workflow[phase.ordinal()]);
        }
        // Plan construction guarantees distinct SOURCE keys, independently of
        // supplied equal digests and the number of operation references.
        return new PublicJobReport(1, current.state(), current.plan().sources().size(), operationCounts, failureCounts);
    }

    private static List<DicomProcessingError> checkedErrors(List<DicomProcessingError> errors) {
        var unique = EnumSet.noneOf(DicomProcessingError.class);
        for (var error : errors) if (!unique.add(error)) throw invalid();
        return List.copyOf(unique);
    }

    private static Optional<ProcessingEvidence.SourceSummary> summary(ProvenanceRecord provenance, List<DicomProcessingError> errors) {
        if (provenance == null) return Optional.empty();
        if (!checkedErrors(provenance.errors()).equals(errors)) throw invalid();
        return Optional.of(new ProcessingEvidence.SourceSummary(Optional.ofNullable(provenance.inputFingerprint()),
                provenance.softwareVersion(), provenance.dcm4cheVersion(), provenance.completedAt(), provenance.successful(),
                provenance.inputCount(), provenance.acceptedSlices()));
    }

    private static void addWorkflow(List<ManifestFailure> failures, long[][] counts) {
        for (var failure : failures) {
            int phase = failure.phase().ordinal(); int code = failure.code().ordinal();
            counts[phase][code] = Math.addExact(counts[phase][code], 1);
        }
    }

    private static <E extends Enum<E>> void addCounts(List<FailureCount> result, FailureNamespace namespace, E[] codes, long[] counts) {
        for (var code : codes) if (counts[code.ordinal()] > 0) result.add(new FailureCount(namespace, code.name(), counts[code.ordinal()]));
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid manifest projection");
    }
}
