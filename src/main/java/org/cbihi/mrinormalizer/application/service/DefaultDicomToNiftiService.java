package org.cbihi.mrinormalizer.application.service;

import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.Objects;

import org.cbihi.mrinormalizer.application.conversion.NiftiAffineMapper;
import org.cbihi.mrinormalizer.application.port.out.NiftiVolumeWriter;
import org.cbihi.mrinormalizer.application.provenance.ProvenanceRecord;
import org.cbihi.mrinormalizer.application.request.DicomSeriesRequest;
import org.cbihi.mrinormalizer.application.request.DicomToNiftiRequest;
import org.cbihi.mrinormalizer.application.result.DicomToNiftiResult;
import org.cbihi.mrinormalizer.domain.error.DicomToNiftiError;

/** Composes verified DICOM reconstruction, LPS-to-RAS mapping and NIfTI serialization. */
public final class DefaultDicomToNiftiService implements DicomToNiftiService {

    private final DicomSeriesService dicomSeriesService;
    private final NiftiVolumeWriter niftiWriter;

    public DefaultDicomToNiftiService(
            DicomSeriesService dicomSeriesService,
            NiftiVolumeWriter niftiWriter) {
        this.dicomSeriesService = Objects.requireNonNull(dicomSeriesService, "dicomSeriesService");
        this.niftiWriter = Objects.requireNonNull(niftiWriter, "niftiWriter");
    }

    @Override
    public DicomToNiftiResult convert(DicomToNiftiRequest request) {
        var reconstruction = request == null
                ? dicomSeriesService.process(null)
                : dicomSeriesService.process(new DicomSeriesRequest(
                        request.inputs(), request.selectedSeriesInstanceUid()));

        if (!reconstruction.successful()) {
            return DicomToNiftiResult.failure(
                    reconstruction.errors(),
                    reconstruction.provenance());
        }

        var volume = reconstruction.volume();
        var rasAffine = NiftiAffineMapper.toNiftiRas(volume.geometry());

        try {
            niftiWriter.write(volume, rasAffine, request.output());
        } catch (IllegalArgumentException exception) {
            return DicomToNiftiResult.outputFailure(
                    DicomToNiftiError.INVALID_OUTPUT,
                    failedOutputProvenance(reconstruction.provenance()));
        } catch (UncheckedIOException exception) {
            DicomToNiftiError error =
                    exception.getCause() instanceof FileAlreadyExistsException
                            ? DicomToNiftiError.OUTPUT_ALREADY_EXISTS
                            : DicomToNiftiError.OUTPUT_WRITE_FAILED;

            return DicomToNiftiResult.outputFailure(
                    error,
                    failedOutputProvenance(reconstruction.provenance()));
        }

        return DicomToNiftiResult.success(
                request.output(),
                reconstruction.provenance());
    }

    private ProvenanceRecord failedOutputProvenance(ProvenanceRecord provenance) {
        if (provenance == null) {
            return null;
        }

        return new ProvenanceRecord(
                provenance.inputFingerprint(),
                provenance.softwareVersion(),
                provenance.dcm4cheVersion(),
                provenance.completedAt(),
                false,
                provenance.inputCount(),
                provenance.acceptedSlices(),
                provenance.geometry(),
                provenance.pixels(),
                provenance.errors());
    }
}
