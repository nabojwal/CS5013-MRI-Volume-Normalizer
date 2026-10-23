package org.cbihi.mrinormalizer.architecture;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.cbihi.mrinormalizer.application.port.out.*;
import org.cbihi.mrinormalizer.application.service.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;

class VerifiedConversionBoundaryTest {
    @Test void T41_protectedF1PortUnchanged() throws Exception {
        assertEquals(1,ManifestStore.class.getMethods().length>=4?1:0);
        assertEquals(ManifestReceipt.class,ManifestStore.class.getMethod("append",CheckpointRecord.class,ManifestReceipt.class).getReturnType());
        assertEquals(3,VerifiedNiftiOutputAccess.class.getDeclaredMethods().length);
    }
    @Test void T42_v2PublicProjectionRejected() {
        assertThrows(IllegalArgumentException.class,()->new PublicJobReport(2,ManifestState.JobState.RUNNING,0,
                java.util.List.of(),java.util.List.of()));
    }

    @Test void T41_newApisRespectFrozenArchitecture() throws Exception {
        var constructor=VerifiedConversionEvidenceService.class.getDeclaredConstructors();
        assertEquals(1,constructor.length);
        var parameterTypes=constructor[0].getParameterTypes();
        assertArrayEquals(new Class<?>[]{
                org.cbihi.mrinormalizer.application.service.DicomSeriesService.class,
                NiftiVolumeWriter.class,NiftiVolumeReader.class,VerifiedNiftiOutputAccess.class,
                ManifestStore.class,org.cbihi.mrinormalizer.application.validation.NiftiOutputComparator.class,
                java.time.Clock.class},parameterTypes);
        for(var field:VerifiedConversionEvidenceService.class.getDeclaredFields())
            assertFalse(field.getType().getName().startsWith("org.cbihi.mrinormalizer.infrastructure."),
                    "Application service must depend on ports, not concrete provider "+field.getName());
        assertEquals(4,ManifestStore.class.getDeclaredMethods().length);
        assertEquals(3,VerifiedNiftiOutputAccess.class.getDeclaredMethods().length);
        assertEquals(7,VerifiedNiftiOutputAccess.PreparedOutput.class.getDeclaredMethods().length);
        assertEquals(7,CheckpointRecord.ConversionVerification.class.getRecordComponents().length);
    }
    @Test void T42_publicEvidenceOmitsIdentifiers() {
        var versionOne=new ProvenanceManifest(1,new java.util.UUID(0,1),
                java.time.Instant.parse("2026-10-04T00:00:00Z"),java.util.List.of(),java.util.List.of());
        var versionTwo=new ProvenanceManifest(2,versionOne.jobId(),versionOne.createdAt(),
                java.util.List.of(),java.util.List.of());
        var publicV1=ManifestProjection.publicReport(new ManifestReplay(versionOne,"a".repeat(64)).current());
        assertEquals(1,publicV1.schemaVersion());
        assertEquals(18,publicV1.operationCounts().size());
        assertEquals(java.util.List.of("schemaVersion","state","sourceCount","operationCounts","failureCounts"),
                java.util.Arrays.stream(PublicJobReport.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toList());
        assertThrows(IllegalArgumentException.class,()->ManifestProjection.publicReport(
                new ManifestReplay(versionTwo,"a".repeat(64)).current()));
        assertThrows(IllegalArgumentException.class,()->new PublicJobReport.FailureCount(
                PublicJobReport.FailureNamespace.PERSISTENCE,"patient name or DICOM UID",1));
        var failure=new VerifiedNiftiOutputAccess.AccessFailure(java.util.List.of(
                VerifiedNiftiOutputAccess.AccessFault.SOURCE_CONTAINMENT_UNPROVEN));
        assertNull(failure.getCause());
        assertEquals(0,failure.getSuppressed().length);
        assertEquals(0,failure.getStackTrace().length);
        assertFalse(failure.getMessage().contains("patient"));
    }
}
