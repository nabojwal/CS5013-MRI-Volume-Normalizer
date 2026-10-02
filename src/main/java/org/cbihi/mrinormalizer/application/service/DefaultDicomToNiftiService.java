package org.cbihi.mrinormalizer.application.service;

import java.util.Objects;

import org.cbihi.mrinormalizer.application.conversion.NiftiAffineMapper;
import org.cbihi.mrinormalizer.application.port.out.NiftiVolumeWriter;
import org.cbihi.mrinormalizer.application.request.DicomSeriesRequest;
import org.cbihi.mrinormalizer.application.request.DicomToNiftiRequest;
import org.cbihi.mrinormalizer.application.result.DicomToNiftiResult;

/** Composes verified DICOM reconstruction, LPS-to-RAS mapping and NIfTI serialization. */
public final class DefaultDicomToNiftiService implements DicomToNiftiService {

    private final DicomSeriesService dicomSeriesService;
    private final NiftiVolumeWriter niftiWriter;

    public DefaultDicomToNiftiService(DicomSeriesService dicomSeriesService, NiftiVolumeWriter niftiWriter) {
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
            return DicomToNiftiResult.failure(reconstruction.errors(), reconstruction.provenance());
        }

        var volume = reconstruction.volume();
        var rasAffine = NiftiAffineMapper.toNiftiRas(volume.geometry());
        niftiWriter.write(volume, rasAffine, request.output());
        return DicomToNiftiResult.success(request.output(), reconstruction.provenance());
    }
}
