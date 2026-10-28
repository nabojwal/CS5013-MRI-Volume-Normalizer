package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.cbihi.mrinormalizer.application.request.F9HeadlessRequest;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;
import org.cbihi.mrinormalizer.presentation.cli.F9HeadlessCli;

/** CLI/configuration negatives and frozen request boundaries; real NTFS execution is a separate Windows gate. */
class F9HeadlessIntegrationTest {
    @TempDir Path temp;
    @Test void S08_cliRequiresAllFiveExplicitFlags() {
        assertEquals(4,F9HeadlessCli.run(new String[]{}));
        assertEquals(4,F9HeadlessCli.run(new String[]{"--source-root",temp.toString()}));
        assertEquals(4,F9HeadlessCli.run(new String[]{"--source-root","x","--source-root","y",
                "--series-uid","1.2","--destination","a.nii","--job-id",java.util.UUID.randomUUID().toString()}));
    }
    @Test void S09_cliRejectsNestedOrEqualRoots() throws Exception {
        Path source=Files.createDirectory(temp.resolve("source"));
        Path nested=Files.createDirectory(source.resolve("out"));
        String job=java.util.UUID.randomUUID().toString();
        assertEquals(4,F9HeadlessCli.run(args(source,source,job)));
        assertEquals(4,F9HeadlessCli.run(args(source,nested,job)));
    }
    @Test void S10_cliRejectsNonRegularSelectedInputAndNoSilentExclusion() throws Exception {
        Path source=Files.createDirectory(temp.resolve("source"));
        Path out=Files.createDirectory(temp.resolve("output"));
        Files.createDirectory(source.resolve("other-series"));
        assertEquals(4,F9HeadlessCli.run(args(source,out,java.util.UUID.randomUUID().toString())));
    }
    @Test void S11_outputMustBeOutputRelativePlainNifti() {
        assertThrows(IllegalArgumentException.class,()->new F9HeadlessRequest(
                java.util.UUID.randomUUID(),"1.2",java.util.List.of(new RelativePath(RelativePath.Root.SOURCE,"a.dcm")),
                new RelativePath(RelativePath.Root.OUTPUT,"a.nii.gz")));
    }
    @Test void S12_realAdaptersSyntheticTwoSliceRoundTrip() throws Exception {
        Path source=Files.createDirectory(temp.resolve("synthetic-source"));
        Path output=Files.createDirectory(temp.resolve("synthetic-output"));
        dicom(source.resolve("slice-1.dcm"),"1.2.3.1",0.0);
        dicom(source.resolve("slice-2.dcm"),"1.2.3.2",2.0);
        String job=java.util.UUID.randomUUID().toString();
        int exit=F9HeadlessCli.run(args(source,output,job));
        assertEquals(0,exit);
        try (var journal=new org.cbihi.mrinormalizer.infrastructure.filesystem.JsonManifestStore(
                source,output,output.resolve(".pai-f9-manifest"),java.util.UUID.fromString(job))) {
            var replay=journal.replay(java.util.Optional.empty()).orElseThrow();
            assertEquals(org.cbihi.mrinormalizer.application.provenance.manifest.ManifestState.JobState.COMPLETED,
                    replay.state());
            assertEquals(org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.State.VERIFIED_PUBLISHED,
                    replay.operations().getFirst().observation().state());
        }
        Path converted=output.resolve("selected.nii");
        assertTrue(Files.isRegularFile(converted));
        assertEquals(352+2L*2*2*2,Files.size(converted));
        // Full value/affine/scaling comparison is the existing F8 comparator's responsibility;
        // this independent file-size check does not replace external reference validation.
    }
    @Test void S13_realAdaptersGappedSeriesDoesNotPublish() throws Exception {
        Path source=Files.createDirectory(temp.resolve("gapped-source"));
        Path output=Files.createDirectory(temp.resolve("gapped-output"));
        dicom(source.resolve("slice-1.dcm"),"1.2.3.1",0.0);
        dicom(source.resolve("slice-2.dcm"),"1.2.3.2",2.0);
        dicom(source.resolve("slice-3.dcm"),"1.2.3.3",8.0);
        String job=java.util.UUID.randomUUID().toString();
        int exit=F9HeadlessCli.run(args(source,output,job));
        assertEquals(2,exit);
        assertFalse(Files.exists(output.resolve("selected.nii")));
        try (var journal=new org.cbihi.mrinormalizer.infrastructure.filesystem.JsonManifestStore(
                source,output,output.resolve(".pai-f9-manifest"),java.util.UUID.fromString(job))) {
            assertTrue(journal.replay(java.util.Optional.empty()).isEmpty());
        }
    }
    @Test void S17_cleanupFailureSuppressesSuccessAndEmitsExactlyOneStatus() throws Exception {
        var method=F9HeadlessCli.class.getDeclaredMethod("executeAndReport",java.util.function.Supplier.class,
                AutoCloseable.class);
        method.setAccessible(true);
        var original=System.out;
        var bytes=new java.io.ByteArrayOutputStream();
        var closed=new java.util.concurrent.atomic.AtomicBoolean();
        var head=new org.cbihi.mrinormalizer.application.provenance.manifest.ManifestReceipt(
                java.util.UUID.randomUUID(),3,"a".repeat(64),"b".repeat(64));
        int exit;
        try(var out=new java.io.PrintStream(bytes,true,java.nio.charset.StandardCharsets.UTF_8)) {
            System.setOut(out);
            exit=(Integer)method.invoke(null,
                    (java.util.function.Supplier<org.cbihi.mrinormalizer.application.result.F9HeadlessResult>)
                        () -> org.cbihi.mrinormalizer.application.result.F9HeadlessResult.success(head),
                    (AutoCloseable) () -> {
                        closed.set(true);
                        throw new IllegalArgumentException("private fixture detail");
                    });
        } finally { System.setOut(original); }
        String text=bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(closed.get());
        assertEquals(3,exit);
        assertEquals(1L,text.lines().filter(line->line.startsWith("F9_STATUS=")).count());
        assertTrue(text.contains("F9_STATUS=RECOVERY_REQUIRED"));
        assertTrue(text.contains("F9_REASON=PUBLICATION_UNCERTAIN"));
        assertFalse(text.contains("F9_STATUS=VERIFIED_PUBLISHED"));
        assertFalse(text.contains("CONFIGURATION_ERROR"));
        assertFalse(text.contains("private fixture detail"));
    }

    @Test void S18_executionIllegalArgumentExceptionStillClosesAndNeverReportsConfigFailure() throws Exception {
        var method=F9HeadlessCli.class.getDeclaredMethod("executeAndReport",java.util.function.Supplier.class,
                AutoCloseable.class);
        method.setAccessible(true);
        var original=System.out;
        var bytes=new java.io.ByteArrayOutputStream();
        var closed=new java.util.concurrent.atomic.AtomicBoolean();
        int exit;
        try(var out=new java.io.PrintStream(bytes,true,java.nio.charset.StandardCharsets.UTF_8)) {
            System.setOut(out);
            exit=(Integer)method.invoke(null,
                    (java.util.function.Supplier<org.cbihi.mrinormalizer.application.result.F9HeadlessResult>)
                        () -> { throw new IllegalArgumentException("runtime fixture detail"); },
                    (AutoCloseable) () -> closed.set(true));
        } finally { System.setOut(original); }
        String text=bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(closed.get());
        assertEquals(3,exit);
        assertEquals(1L,text.lines().filter(line->line.startsWith("F9_STATUS=")).count());
        assertTrue(text.contains("F9_STATUS=RECOVERY_REQUIRED"));
        assertFalse(text.contains("CONFIGURATION_ERROR"));
        assertFalse(text.contains("runtime fixture detail"));
    }

    private static void dicom(Path target,String sop,double z) throws Exception {
        var dataset=new org.dcm4che3.data.Attributes();
        dataset.setString(org.dcm4che3.data.Tag.SOPClassUID,org.dcm4che3.data.VR.UI,org.dcm4che3.data.UID.MRImageStorage);
        dataset.setString(org.dcm4che3.data.Tag.SOPInstanceUID,org.dcm4che3.data.VR.UI,sop);
        dataset.setString(org.dcm4che3.data.Tag.StudyInstanceUID,org.dcm4che3.data.VR.UI,"1.2");
        dataset.setString(org.dcm4che3.data.Tag.SeriesInstanceUID,org.dcm4che3.data.VR.UI,"1.2.3");
        dataset.setString(org.dcm4che3.data.Tag.FrameOfReferenceUID,org.dcm4che3.data.VR.UI,"1.2.4");
        dataset.setString(org.dcm4che3.data.Tag.Modality,org.dcm4che3.data.VR.CS,"MR");
        dataset.setInt(org.dcm4che3.data.Tag.Rows,org.dcm4che3.data.VR.US,2);
        dataset.setInt(org.dcm4che3.data.Tag.Columns,org.dcm4che3.data.VR.US,2);
        dataset.setString(org.dcm4che3.data.Tag.PixelSpacing,org.dcm4che3.data.VR.DS,"0.75","0.5");
        dataset.setString(org.dcm4che3.data.Tag.SpacingBetweenSlices,org.dcm4che3.data.VR.DS,"2");
        dataset.setString(org.dcm4che3.data.Tag.ImageOrientationPatient,org.dcm4che3.data.VR.DS,"1","0","0","0","1","0");
        dataset.setString(org.dcm4che3.data.Tag.ImagePositionPatient,org.dcm4che3.data.VR.DS,"10","20",Double.toString(z));
        dataset.setString(org.dcm4che3.data.Tag.PhotometricInterpretation,org.dcm4che3.data.VR.CS,"MONOCHROME2");
        dataset.setInt(org.dcm4che3.data.Tag.SamplesPerPixel,org.dcm4che3.data.VR.US,1);
        dataset.setInt(org.dcm4che3.data.Tag.BitsAllocated,org.dcm4che3.data.VR.US,16);
        dataset.setInt(org.dcm4che3.data.Tag.BitsStored,org.dcm4che3.data.VR.US,16);
        dataset.setInt(org.dcm4che3.data.Tag.HighBit,org.dcm4che3.data.VR.US,15);
        dataset.setInt(org.dcm4che3.data.Tag.PixelRepresentation,org.dcm4che3.data.VR.US,0);
        dataset.setDouble(org.dcm4che3.data.Tag.RescaleSlope,org.dcm4che3.data.VR.DS,1.5);
        dataset.setDouble(org.dcm4che3.data.Tag.RescaleIntercept,org.dcm4che3.data.VR.DS,10.0);
        dataset.setBytes(org.dcm4che3.data.Tag.PixelData,org.dcm4che3.data.VR.OW,
                new byte[]{1,0,2,0,3,0,4,0});
        try(var bytes=new java.io.ByteArrayOutputStream();
            var writer=new org.dcm4che3.io.DicomOutputStream(bytes,org.dcm4che3.data.UID.ExplicitVRLittleEndian)) {
            writer.writeFileMetaInformation(dataset.createFileMetaInformation(org.dcm4che3.data.UID.ExplicitVRLittleEndian));
            writer.writeDataset(null,dataset);
            writer.close();
            Files.write(target,bytes.toByteArray());
        }
    }
    private static String[] args(Path s,Path o,String job) {
        return new String[]{"--source-root",s.toString(),"--output-root",o.toString(),
                "--series-uid","1.2.3","--destination","selected.nii","--job-id",job};
    }
}
