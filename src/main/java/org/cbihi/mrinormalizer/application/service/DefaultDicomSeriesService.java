package org.cbihi.mrinormalizer.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.cbihi.mrinormalizer.application.provenance.ProvenanceRecord;
import org.cbihi.mrinormalizer.application.request.DicomSeriesRequest;
import org.cbihi.mrinormalizer.application.result.DicomProcessingResult;
import org.cbihi.mrinormalizer.domain.error.DicomProcessingError;
import org.cbihi.mrinormalizer.domain.error.DicomProcessingException;
import org.cbihi.mrinormalizer.domain.model.CoordinateSystem;
import org.cbihi.mrinormalizer.domain.model.DicomInstance;
import org.cbihi.mrinormalizer.domain.model.GeometryValidationPolicy;
import org.cbihi.mrinormalizer.domain.model.ImmutableVoxelData;
import org.cbihi.mrinormalizer.domain.model.NativeVolume;
import org.cbihi.mrinormalizer.domain.model.SliceGeometry;
import org.cbihi.mrinormalizer.domain.model.VolumeGeometry;
import org.cbihi.mrinormalizer.domain.port.DicomInstanceReader;

/** Synchronous M6 use case: explicit series selection through native volume reconstruction. */
public final class DefaultDicomSeriesService implements DicomSeriesService {

    private static final String SOFTWARE_VERSION = "0.1.0-SNAPSHOT";
    private static final String DCM4CHE_VERSION = "5.33.0";

    private final DicomInstanceReader reader;
    private final GeometryValidationPolicy policy;

    public DefaultDicomSeriesService(DicomInstanceReader reader, GeometryValidationPolicy policy) {
        this.reader = java.util.Objects.requireNonNull(reader, "reader");
        this.policy = java.util.Objects.requireNonNull(policy, "policy");
    }

    @Override
    public DicomProcessingResult process(DicomSeriesRequest request) {
        if (request == null || request.selectedSeriesInstanceUid() == null
                || request.selectedSeriesInstanceUid().isBlank()) {
            return failure(DicomProcessingError.SERIES_NOT_SELECTED, request, 0);
        }
        if (request.inputs() == null || request.inputs().isEmpty()) {
            return failure(DicomProcessingError.EMPTY_INPUT, request, 0);
        }
        try {
            List<DicomInstance> allInstances = new ArrayList<>();
            for (var input : request.inputs()) {
                allInstances.add(reader.read(input));
            }
            List<DicomInstance> selected = allInstances.stream()
                    .filter(instance -> request.selectedSeriesInstanceUid().equals(instance.seriesInstanceUid()))
                    .toList();
            if (selected.isEmpty()) {
                return failure(DicomProcessingError.SERIES_NOT_FOUND, request, 0);
            }
            validateCompatibility(selected);
            NativeVolume volume = validateAndReconstruct(selected);
            return DicomProcessingResult.success(volume, provenance(request, true, selected.size(), List.of(), volume));
        } catch (DicomProcessingException exception) {
            return failure(exception.error(), request, 0);
        } catch (RuntimeException exception) {
            return failure(DicomProcessingError.DICOM_PARSE_FAILED, request, 0);
        }
    }

    private void validateCompatibility(List<DicomInstance> instances) {
        DicomInstance reference = instances.getFirst();
        Set<String> sopInstanceUids = new HashSet<>();
        for (DicomInstance instance : instances) {
            if (!sopInstanceUids.add(instance.sopInstanceUid())) {
                fail(DicomProcessingError.DUPLICATE_SOP_INSTANCE);
            }
            if (!reference.studyInstanceUid().equals(instance.studyInstanceUid())) {
                fail(DicomProcessingError.MIXED_STUDY);
            }
            if (!reference.seriesInstanceUid().equals(instance.seriesInstanceUid())) {
                fail(DicomProcessingError.MIXED_SERIES);
            }
            if (!reference.modality().equals(instance.modality()) || !reference.sopClassUid().equals(instance.sopClassUid())
                    || reference.rows() != instance.rows() || reference.columns() != instance.columns()
                    || !reference.pixelEncoding().equals(instance.pixelEncoding())
                    || !reference.transferSyntaxUid().equals(instance.transferSyntaxUid())
                    || !sameRescale(reference, instance)) {
                fail(DicomProcessingError.INCOMPATIBLE_INSTANCE);
            }
            if (!near(reference.rowSpacing(), instance.rowSpacing(), policy.pixelSpacingToleranceMm())
                    || !near(reference.columnSpacing(), instance.columnSpacing(), policy.pixelSpacingToleranceMm())) {
                fail(DicomProcessingError.INCOMPATIBLE_INSTANCE);
            }
        }
    }

    private NativeVolume validateAndReconstruct(List<DicomInstance> instances) {
        // SOP UIDs are unique after compatibility validation; request order is irrelevant.
        DicomInstance reference = instances.stream()
                .min(Comparator.comparing(DicomInstance::sopInstanceUid)).orElseThrow();
        var referenceGeometry = reference.geometry();
        validateOrientation(referenceGeometry);
        for (DicomInstance instance : instances) {
            var geometry = instance.geometry();
            validateOrientation(geometry);
            if (!nearVector(referenceGeometry.columnIndexDirection(), geometry.columnIndexDirection(),
                    policy.directionCosineTolerance()) || !nearVector(referenceGeometry.rowIndexDirection(),
                    geometry.rowIndexDirection(), policy.directionCosineTolerance())) {
                fail(DicomProcessingError.INCOMPATIBLE_INSTANCE);
            }
        }
        double[] referenceNormal = normalizedCross(referenceGeometry.columnIndexDirection(),
                referenceGeometry.rowIndexDirection());
        for (DicomInstance instance : instances) {
            double[] delta = subtract(instance.geometry().position(), referenceGeometry.position());
            double alongNormal = dot(delta, referenceNormal);
            double[] inPlane = subtract(delta, scale(referenceNormal, alongNormal));
            if (length(inPlane) > policy.positionToleranceMm()) {
                fail(DicomProcessingError.INVALID_POSITION);
            }
        }
        List<DicomInstance> ordered = new ArrayList<>(instances);
        ordered.sort(Comparator.comparingDouble(instance -> dot(instance.geometry().position(), referenceNormal)));
        List<Double> spacing = new ArrayList<>();
        for (int index = 1; index < ordered.size(); index++) {
            double difference = dot(ordered.get(index).geometry().position(), referenceNormal)
                    - dot(ordered.get(index - 1).geometry().position(), referenceNormal);
            if (difference <= policy.duplicatePositionToleranceMm()) {
                fail(DicomProcessingError.DUPLICATE_SLICE);
            }
            spacing.add(difference);
        }
        if (spacing.size() > 1) {
            double expected = spacing.getFirst();
            for (double actual : spacing.subList(1, spacing.size())) {
                if (!near(expected, actual, policy.sliceSpacingToleranceMm())) {
                    fail(DicomProcessingError.IRREGULAR_SPACING);
                }
            }
        }
        // Keep the existing single-slice policy. For stacks, validate and publish
        // exactly the grid defined by the first physical gap and first position.
        double sliceSpacing = spacing.isEmpty() ? 0.0d : spacing.getFirst();
        double firstProjection = dot(ordered.getFirst().geometry().position(), referenceNormal);
        for (int index = 0; index < ordered.size(); index++) {
            double actual = dot(ordered.get(index).geometry().position(), referenceNormal);
            double expected = firstProjection + index * sliceSpacing;
            if (!near(actual, expected, policy.positionToleranceMm())) {
                fail(DicomProcessingError.IRREGULAR_SPACING);
            }
        }
        return reconstruct(ordered, referenceGeometry, referenceNormal, sliceSpacing);
    }

    private void validateOrientation(SliceGeometry geometry) {
        double[] column = geometry.columnIndexDirection();
        double[] row = geometry.rowIndexDirection();
        if (!near(length(column), 1.0d, policy.directionCosineTolerance())
                || !near(length(row), 1.0d, policy.directionCosineTolerance())
                || Math.abs(dot(column, row)) > policy.directionCosineTolerance()) {
            fail(DicomProcessingError.INVALID_ORIENTATION);
        }
    }

    private NativeVolume reconstruct(List<DicomInstance> ordered, SliceGeometry referenceGeometry,
                                     double[] referenceNormal, double sliceSpacing) {
        DicomInstance first = ordered.getFirst();
        int width = first.columns();
        int height = first.rows();
        long[] values = new long[Math.multiplyExact(Math.multiplyExact(width, height), ordered.size())];
        for (int z = 0; z < ordered.size(); z++) {
            var pixels = ordered.get(z).pixels();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    values[(z * height + y) * width + x] = pixels.rawValueAt(x, y, 0);
                }
            }
        }
        VolumeGeometry geometry = new VolumeGeometry(width, height, ordered.size(), first.rowSpacing(),
                first.columnSpacing(), sliceSpacing, first.geometry().position(), referenceGeometry.columnIndexDirection(),
                referenceGeometry.rowIndexDirection(), referenceNormal, CoordinateSystem.DICOM_PATIENT_LPS);
        return new NativeVolume(geometry, new ImmutableVoxelData(width, height, ordered.size(),
                first.pixelEncoding(), values), first.rescaleTransform());
    }

    private boolean sameRescale(DicomInstance first, DicomInstance second) {
        return first.rescaleTransform().declared() == second.rescaleTransform().declared()
                && Double.compare(first.rescaleTransform().slope(), second.rescaleTransform().slope()) == 0
                && Double.compare(first.rescaleTransform().intercept(), second.rescaleTransform().intercept()) == 0;
    }

    private DicomProcessingResult failure(DicomProcessingError error, DicomSeriesRequest request, int acceptedSlices) {
        return DicomProcessingResult.failure(error, provenance(request, false, acceptedSlices, List.of(error), null));
    }

    private ProvenanceRecord provenance(DicomSeriesRequest request, boolean success, int acceptedSlices,
                                        List<DicomProcessingError> errors, NativeVolume volume) {
        int inputCount = request == null || request.inputs() == null ? 0 : request.inputs().size();
        String geometry = volume == null ? "unavailable" : "dimensions=" + volume.geometry().width() + "x"
                + volume.geometry().height() + "x" + volume.geometry().depth() + ";coordinate=DICOM_PATIENT_LPS";
        String pixels = volume == null ? "unavailable" : "allocated=" + volume.voxels().encoding().bitsAllocated()
                + ";stored=" + volume.voxels().encoding().bitsStored() + ";representation="
                + volume.voxels().encoding().valueType();
        return new ProvenanceRecord(fingerprint(request), SOFTWARE_VERSION, DCM4CHE_VERSION, Instant.now(), success,
                inputCount, acceptedSlices, geometry, pixels, errors);
    }

    private String fingerprint(DicomSeriesRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (request != null && request.inputs() != null) {
                for (var input : request.inputs()) {
                    String reference = input == null ? "<null>" : String.valueOf(input.reference());
                    digest.update(reference.getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) 0);
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean near(double first, double second, double tolerance) {
        return Math.abs(first - second) <= tolerance;
    }

    private boolean nearVector(double[] first, double[] second, double tolerance) {
        return near(first[0], second[0], tolerance) && near(first[1], second[1], tolerance)
                && near(first[2], second[2], tolerance);
    }

    private double[] subtract(double[] first, double[] second) {
        return new double[] {first[0] - second[0], first[1] - second[1], first[2] - second[2]};
    }

    private double[] scale(double[] vector, double multiplier) {
        return new double[] {vector[0] * multiplier, vector[1] * multiplier, vector[2] * multiplier};
    }

    private double length(double[] vector) {
        return Math.sqrt(dot(vector, vector));
    }

    private double[] normalizedCross(double[] first, double[] second) {
        double[] cross = new double[] {first[1] * second[2] - first[2] * second[1],
                first[2] * second[0] - first[0] * second[2], first[0] * second[1] - first[1] * second[0]};
        double magnitude = length(cross);
        if (!Double.isFinite(magnitude) || magnitude == 0.0d) {
            fail(DicomProcessingError.INVALID_ORIENTATION);
        }
        return scale(cross, 1.0d / magnitude);
    }

    private double dot(double[] first, double[] second) {
        return first[0] * second[0] + first[1] * second[1] + first[2] * second[2];
    }

    private void fail(DicomProcessingError error) {
        throw new DicomProcessingException(error, "DICOM series validation failed");
    }
}
