package org.cbihi.mrinormalizer.infrastructure.headless;

import java.nio.file.*;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.cbihi.mrinormalizer.application.dataset.dicom.DicomMetadataLimits;
import org.cbihi.mrinormalizer.application.dataset.model.InventoryLimits;
import org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadLimits;
import org.cbihi.mrinormalizer.application.service.*;
import org.cbihi.mrinormalizer.application.validation.NiftiOutputComparator;
import org.cbihi.mrinormalizer.infrastructure.detection.*;
import org.cbihi.mrinormalizer.infrastructure.dicom.*;
import org.cbihi.mrinormalizer.infrastructure.filesystem.*;
import org.cbihi.mrinormalizer.infrastructure.nifti.*;
import org.cbihi.mrinormalizer.domain.model.GeometryValidationPolicy;

/** Explicit composition of accepted adapters; adds no alternative writer or verifier. */
public final class F9WindowsComposition {
    private F9WindowsComposition() {}
    public static F9HeadlessConversionService open(Path sourceRoot, Path outputRoot, UUID jobId) {
        Objects.requireNonNull(sourceRoot);
        Objects.requireNonNull(outputRoot);
        Objects.requireNonNull(jobId);
        try {
            var source = sourceRoot.toRealPath();
            var output = outputRoot.toRealPath();
            if (source.equals(output) || source.startsWith(output) || output.startsWith(source)
                    || !Files.isDirectory(source) || !Files.isDirectory(output))
                throw new IllegalArgumentException("Invalid headless root configuration");
            var stage = output.resolve(".pai-f9-private-stage");
            if (Files.exists(stage, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isDirectory(stage, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(stage))
                    throw new IllegalArgumentException("Invalid private stage directory");
            } else Files.createDirectory(stage);
            // Store qualification is deliberately inherited, including Windows system-drive restrictions.
            var store = new JsonManifestStore(source, output, output.resolve(".pai-f9-manifest"), jobId);
            try {
                var probe = new FormatDetectionService(List.of(new DicomFormatProbe(),new NiftiFormatProbe()));
                var scanner = new NioDatasetScanner(source, output, new InventoryLimits(256, 3, 32), probe);
                var metadata = new Dcm4cheMetadataReader(source,new DicomMetadataLimits(1048576, 16));
                var reader = new Nifti1VolumeReader(output,
                        new NiftiReadLimits(512L<<20,512L<<20,75_000_000L,65536,1024L<<20,16384));
                var access = new NioVerifiedNiftiOutputAccess(source,output,stage,store,
                        64L<<20,512L<<20,512L<<20);
                var verified = new VerifiedConversionEvidenceService(
                        new DefaultDicomSeriesService(new Dcm4cheInstanceReader(), GeometryValidationPolicy.defaults()),
                        new Nifti1VolumeWriter(),reader,access,store,new NiftiOutputComparator(),Clock.systemUTC());
                return new F9HeadlessConversionService(source,scanner,metadata,
                        new DicomSeriesDiscoveryService(),
                        new DefaultDicomSeriesService(new Dcm4cheInstanceReader(),GeometryValidationPolicy.defaults()),
                        store,verified,Clock.systemUTC());
            } catch (RuntimeException exception) { store.close(); throw exception; }
        } catch (java.io.IOException | SecurityException exception) {
            throw new IllegalArgumentException("Invalid headless root configuration");
        }
    }
}
