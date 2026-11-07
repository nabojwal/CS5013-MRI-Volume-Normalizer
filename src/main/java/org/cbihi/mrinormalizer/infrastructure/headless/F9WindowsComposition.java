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
    public enum InitializationStage { DESTINATION, PRIVATE_STAGE, MANIFEST, ADAPTERS }
    /** Public-safe diagnosis before executeOne; paths, UIDs and raw native messages are omitted. */
    public static final class InitializationFailure extends RuntimeException {
        private final InitializationStage stage;
        private final boolean recovery;
        InitializationFailure(InitializationStage stage, String detail, boolean recovery) {
            super("F9 initialization at " + stage + ": " + detail + (recovery ? "; recovery required" : "; no imaging output published"));
            this.stage = stage; this.recovery = recovery;
        }
        public InitializationStage stage() { return stage; }
        public boolean recoveryRequired() { return recovery; }
    }
    /** Derive F2 inventory cardinality from the actual source folder, without slice-count constants. */
    private static int inventoryCount(Path source) throws java.io.IOException {
        long count = 0;
        try (DirectoryStream<Path> children = Files.newDirectoryStream(source)) {
            for (Path child : children) {
                if (Thread.currentThread().isInterrupted())
                    throw new java.io.IOException("Inventory interrupted");
                count++;
                if (count > Integer.MAX_VALUE)
                    throw new java.io.IOException("Inventory cannot be represented safely");
            }
        }
        if (count == 0) throw new java.io.IOException("Empty DICOM source directory");
        return Math.toIntExact(count);
    }
    public static F9HeadlessConversionService open(Path sourceRoot, Path outputRoot, UUID jobId) {
        Objects.requireNonNull(sourceRoot);
        Objects.requireNonNull(outputRoot);
        Objects.requireNonNull(jobId);
        var trace = new JsonManifestStore.InitializationTrace();
        InitializationStage phase = InitializationStage.DESTINATION;
        try {
            var source = sourceRoot.toRealPath();
            var output = DestinationQualification.requireLocal(outputRoot);
            if (source.equals(output) || source.startsWith(output) || output.startsWith(source)
                    || !Files.isDirectory(source) || !Files.isDirectory(output))
                throw new IllegalArgumentException("Invalid headless root configuration");
            DestinationQualification.requirePathCapacity(output, 128);
            phase = InitializationStage.PRIVATE_STAGE;
            var stage = output.resolve(".pai-f9-private-stage");
            if (Files.exists(stage, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isDirectory(stage, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(stage))
                    throw new IllegalArgumentException("Invalid private stage directory");
                DestinationQualification.verifyPrivateDirectory(stage);
            } else Files.createDirectory(stage, DestinationQualification.privateAttributes(output, true));
            // Keep F1 publication/privacy qualification; only local-volume admission is shared.
            phase = InitializationStage.MANIFEST;
            var store = DestinationQualification.openForwardManifest(source, output, jobId, trace);
            try {
                phase = InitializationStage.ADAPTERS;
                var probe = new FormatDetectionService(List.of(new DicomFormatProbe(),new NiftiFormatProbe()));
                var scanner = new NioDatasetScanner(source, output, new InventoryLimits(inventoryCount(source), 3, 32), probe);
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
            } catch (RuntimeException exception) {
                try { store.close(); }
                catch (RuntimeException close) { throw new InitializationFailure(phase, "ADAPTER_INITIALIZATION_AND_RELEASE_FAILED", true); }
                throw new InitializationFailure(phase, exception.getClass().getSimpleName(), false);
            }
        } catch (InitializationFailure classified) { throw classified;
        } catch (org.cbihi.mrinormalizer.application.provenance.manifest.ProvenancePersistenceException failure) {
            boolean uncertain = failure.outcome() != org.cbihi.mrinormalizer.application.provenance.manifest.ProvenancePersistenceException.PublicationOutcome.NOT_PUBLISHED
                    || failure.failures().stream().anyMatch(f -> f.code().name().equals("CLEANUP_FAILED") || f.code().name().equals("RECOVERY_REQUIRED"));
            throw new InitializationFailure(phase, trace.summary() + " " + failure.failures().stream().map(f -> f.code().name()).toList().toString(), uncertain);
        } catch (java.io.IOException | SecurityException | IllegalArgumentException exception) {
            String detail = exception.getMessage();
            if (detail == null || !detail.matches("[A-Z][A-Z0-9_]{1,95}"))
                detail = exception instanceof java.nio.file.AccessDeniedException ? "ACCESS_DENIED" : exception.getClass().getSimpleName();
            throw new InitializationFailure(phase, detail, exception.getSuppressed().length != 0 || detail.contains("UNCERTAIN"));
        } catch (RuntimeException exception) {
            throw new InitializationFailure(phase, exception.getClass().getSimpleName(), true);
        }
    }
}
