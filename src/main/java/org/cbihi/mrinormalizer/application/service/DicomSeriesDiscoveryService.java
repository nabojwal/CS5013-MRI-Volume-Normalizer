package org.cbihi.mrinormalizer.application.service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.cbihi.mrinormalizer.application.dataset.dicom.DicomMetadataCatalog;
import org.cbihi.mrinormalizer.application.dataset.dicom.DicomMetadataInspection;
import org.cbihi.mrinormalizer.application.dataset.dicom.DicomSeriesCandidate;
import org.cbihi.mrinormalizer.application.dataset.dicom.DicomSeriesDiscovery;
import org.cbihi.mrinormalizer.application.dataset.dicom.DicomSeriesKey;
import org.cbihi.mrinormalizer.application.dataset.dicom.DicomSeriesMember;
import org.cbihi.mrinormalizer.application.dataset.dicom.DicomSeriesScreeningFinding;
import org.cbihi.mrinormalizer.application.dataset.dicom.DicomSeriesScreeningFinding.Code;
import org.cbihi.mrinormalizer.application.dataset.dicom.DicomSeriesScreeningFinding.Reason;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;

/**
 * Stateless in-memory F4 grouping and conservative observed-conflict screening.
 * Accepts incomplete F3 catalogs and preserves every supplied source exactly once.
 * No splitting, merging, deduplication, enrichment, I/O or M6 invocation occurs.
 * Missing observations never prove absence or compatibility. Future orchestration
 * must honor findings and still invoke definitive M6 validation before conversion.
 */
public final class DicomSeriesDiscoveryService {
    public DicomSeriesDiscoveryService() {}

    public DicomSeriesDiscovery discover(DicomMetadataCatalog catalog) {
        if (catalog == null) throw new IllegalArgumentException("Discovery requires a metadata catalog");
        var groups = new HashMap<DicomSeriesKey, List<DicomSeriesMember>>();
        var sopKeys = new HashMap<String, Set<DicomSeriesKey>>();
        var unassigned = new ArrayList<DicomMetadataInspection>();
        var inputSources = new HashSet<RelativePath>();
        for (var inspection : catalog.inspections()) {
            if (!inputSources.add(inspection.source())) coverageFailure();
            if (inspection.failure().isPresent()) {
                unassigned.add(inspection);
            } else {
                var metadata = inspection.metadata().orElseThrow();
                var key = new DicomSeriesKey(metadata.studyInstanceUid(), metadata.seriesInstanceUid());
                groups.computeIfAbsent(key, unused -> new ArrayList<>()).add(new DicomSeriesMember(inspection.source(), metadata));
                sopKeys.computeIfAbsent(metadata.sopInstanceUid(), unused -> new HashSet<>()).add(key);
            }
        }
        var candidates = new ArrayList<DicomSeriesCandidate>();
        for (var group : groups.entrySet()) {
            candidates.add(new DicomSeriesCandidate(group.getKey(), group.getValue(), screen(group.getValue(), sopKeys)));
        }
        var discovery = new DicomSeriesDiscovery(candidates, unassigned);
        var outputSources = new HashSet<RelativePath>();
        for (var candidate : discovery.candidates()) for (var member : candidate.members()) {
            if (!outputSources.add(member.source())) coverageFailure();
        }
        for (var inspection : discovery.unassigned()) if (!outputSources.add(inspection.source())) coverageFailure();
        if (!inputSources.equals(outputSources)) coverageFailure();
        return discovery;
    }

    private static List<DicomSeriesScreeningFinding> screen(List<DicomSeriesMember> members,
            Map<String, Set<DicomSeriesKey>> sopKeys) {
        var reasons = EnumSet.noneOf(Reason.class);
        var sops = new HashSet<String>(); var modalities = new HashSet<String>(); var sopClasses = new HashSet<String>();
        var frames = new HashSet<String>(); var matrices = new HashSet<Matrix>();
        var imageTypes = new HashSet<List<String>>();
        var acquisitions = new HashSet<Integer>(); var temporals = new HashSet<Integer>(); var echoes = new HashSet<Integer>();
        for (var member : members) {
            var metadata = member.metadata();
            if (!sops.add(metadata.sopInstanceUid())) reasons.add(Reason.DUPLICATE_SOP_INSTANCE);
            if (sopKeys.get(metadata.sopInstanceUid()).size() > 1) reasons.add(Reason.SOP_INSTANCE_REUSED_ACROSS_SERIES);
            modalities.add(metadata.modality()); sopClasses.add(metadata.sopClassUid());
            metadata.frameOfReferenceUid().ifPresent(frames::add);
            if (metadata.rows().isPresent() && metadata.columns().isPresent()) {
                matrices.add(new Matrix(metadata.rows().orElseThrow(), metadata.columns().orElseThrow()));
            }
            if (metadata.numberOfFrames().isPresent() && metadata.numberOfFrames().orElseThrow() > 1) reasons.add(Reason.MULTIFRAME_MEMBER);
            if (!metadata.imageType().isEmpty()) imageTypes.add(metadata.imageType());
            metadata.acquisitionNumber().ifPresent(acquisitions::add);
            metadata.temporalPositionIdentifier().ifPresent(temporals::add);
            echoes.addAll(metadata.echoNumbers());
        }
        if (modalities.size() > 1) reasons.add(Reason.MIXED_MODALITY);
        if (sopClasses.size() > 1) reasons.add(Reason.MIXED_SOP_CLASS);
        if (frames.size() > 1) reasons.add(Reason.MIXED_FRAME_OF_REFERENCE);
        if (matrices.size() > 1) reasons.add(Reason.MIXED_MATRIX);
        if (imageTypes.size() > 1) reasons.add(Reason.MIXED_IMAGE_TYPE);
        if (acquisitions.size() > 1) reasons.add(Reason.MULTIPLE_ACQUISITION_NUMBERS);
        if (temporals.size() > 1) reasons.add(Reason.MULTIPLE_TEMPORAL_POSITIONS);
        if (echoes.size() > 1) reasons.add(Reason.MULTIPLE_ECHO_NUMBERS);
        var findings = new ArrayList<DicomSeriesScreeningFinding>();
        for (var reason : reasons) {
            Code code = switch (reason) {
                case DUPLICATE_SOP_INSTANCE, SOP_INSTANCE_REUSED_ACROSS_SERIES, MIXED_MODALITY,
                        MIXED_SOP_CLASS, MIXED_FRAME_OF_REFERENCE, MIXED_MATRIX, MULTIFRAME_MEMBER, MIXED_IMAGE_TYPE -> Code.AMBIGUOUS_SERIES;
                case MULTIPLE_ACQUISITION_NUMBERS, MULTIPLE_TEMPORAL_POSITIONS, MULTIPLE_ECHO_NUMBERS -> Code.MULTIDIMENSIONAL_SERIES;
            };
            findings.add(new DicomSeriesScreeningFinding(code, reason));
        }
        return findings;
    }

    private record Matrix(int rows, int columns) {}
    private static void coverageFailure() { throw new IllegalStateException("Discovery source coverage invariant failed"); }
}
