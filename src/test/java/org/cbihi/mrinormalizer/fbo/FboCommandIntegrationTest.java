package org.cbihi.mrinormalizer.fbo;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.cbihi.mrinormalizer.bootstrap.FboCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Native integration: two actual F9 executions, separate real F1 journals, no mocks. */
class FboCommandIntegrationTest {
    @TempDir Path temp;
    @Test void t25_parserRejectsUnknownAndIncompleteFlags() {
        assertEquals(2, FboCommand.run(new String[0],System.out,System.err));
        assertEquals(2, FboCommand.run(new String[]{"--batch-all","true"},System.out,System.err));
        assertEquals(2, FboCommand.run(new String[]{"--input-root","x","--output-root","y","--series","folder"},
                System.out,System.err));
    }
    @Test void t21_jobRootCollisionFailsWithoutOverwriteOrF9Open() throws Exception {
        Path root=Files.createDirectories(temp.resolve("collision-batch"));
        UUID job=UUID.randomUUID();
        Path existing=Files.createDirectory(root.resolve("job-001-"+job));
        Path sentinel=Files.writeString(existing.resolve("sentinel.txt"),"preexisting bytes");
        var observed=new org.cbihi.mrinormalizer.application.fbo.FboBatchController.DirectoryObservation(
                null,java.nio.file.attribute.FileTime.fromMillis(1),java.nio.file.attribute.FileTime.fromMillis(1));
        var file=new org.cbihi.mrinormalizer.application.fbo.FboBatchController.PreparedFile(
                "image.dcm",3,observed.creationTime(),observed.lastModifiedTime(),null,"a".repeat(64));
        var prepared=new org.cbihi.mrinormalizer.application.fbo.FboBatchController.PreparedSeries(
                1,temp,"1.2.3",observed,List.of(file));
        var result=new org.cbihi.mrinormalizer.infrastructure.fbo.F9ForwardBatchRunner().runOne(prepared,job,root);
        assertEquals(org.cbihi.mrinormalizer.application.fbo.FboResult.EntryState.RECOVERY_REQUIRED,result.state());
        assertEquals("preexisting bytes",Files.readString(sentinel));
    }
    @Test @EnabledOnOs(OS.WINDOWS)
    void t21_windowsRunnerRejectsMissingParentBeforeRootCreation() throws Exception {
        Path absent=temp.resolve("missing-output");UUID job=UUID.randomUUID();
        var t=java.nio.file.attribute.FileTime.fromMillis(1);
        var selected=new org.cbihi.mrinormalizer.application.fbo.FboBatchController.PreparedSeries(1,temp,"1.2.3",
            new org.cbihi.mrinormalizer.application.fbo.FboBatchController.DirectoryObservation(null,t,t),
            List.of(new org.cbihi.mrinormalizer.application.fbo.FboBatchController.PreparedFile("image.dcm",3,t,t,null,"a".repeat(64))));
        var result=new org.cbihi.mrinormalizer.infrastructure.fbo.F9ForwardBatchRunner().runOne(selected,job,absent);
        assertEquals(org.cbihi.mrinormalizer.application.fbo.FboResult.EntryState.RECOVERY_REQUIRED,result.state());
        assertFalse(Files.exists(absent));
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void t13_t14_t15_twoRealF9SeriesPublishIndependentlyAndRepeatIsImmutable() throws Exception {
        String system=System.getenv("SystemDrive");
        assertNotNull(system,"Qualified native Windows system drive required");
        // Test-only source/output directories must be on the qualified system drive.
        Path writable=Path.of(System.getProperty("fbo.test.systemDriveTemp",
                System.getenv().getOrDefault("LOCALAPPDATA", System.getProperty("java.io.tmpdir")))).toRealPath();
        assertEquals(system+"\\",writable.getRoot().toString(),
                "Supply a writable native system-drive test directory");
        Path root=Files.createTempDirectory(writable,"pai-fbo-native-");
        Path input=Files.createDirectory(root.resolve("input"));
        // Use a short independent output parent: nested FBO/F9 journal staging adds
        // 203 characters beneath this parent on Windows. The source fixture stays
        // isolated under root; the output is a separate sibling, not inside it.
        Path output=Files.createTempDirectory(writable,"o-");
        int firstStagePathLength=output.toAbsolutePath().normalize().toString().length()+203;
        assertTrue(firstStagePathLength<=240,
                "Native Windows F1 stage-path headroom unavailable; use -Dfbo.test.systemDriveTemp=C:\\FBO");
        Path a=Files.createDirectory(input.resolve("series-A"));
        Path b=Files.createDirectory(input.resolve("series-B"));
        // Distinct Study/Series and SOP identities, identical validated geometry.
        dicom(a.resolve("slice-1.dcm"),"1.2.3", "1.2.3.1",0);
        dicom(a.resolve("slice-2.dcm"),"1.2.3", "1.2.3.2",2);
        dicom(b.resolve("slice-1.dcm"),"1.2.5", "1.2.5.1",0);
        dicom(b.resolve("slice-2.dcm"),"1.2.5", "1.2.5.2",2);
        String[] command={"--input-root",input.toString(),"--output-root",output.toString(),
                "--series","series-A","1.2.3","--series","series-B","1.2.5"};
        ByteArrayOutputStream stdout=new ByteArrayOutputStream(),stderr=new ByteArrayOutputStream();
        int first=FboCommand.run(command,new PrintStream(stdout),new PrintStream(stderr));
        assertEquals(0,first, ()->"FBO failure: "+stderr);
        assertTrue(stdout.toString().contains("FBO_STATUS=SUCCESS"));
        Map<String,String> oldHashes=new TreeMap<>();
        try(var stream=Files.walk(output)) {
            for(Path f:stream.filter(Files::isRegularFile).toList()) {
                oldHashes.put(output.relativize(f).toString(), HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f))));
            }
        }
        assertEquals(2,oldHashes.keySet().stream().filter(k->k.endsWith("selected.nii")).count());
        assertEquals(1,oldHashes.keySet().stream().filter(k->k.endsWith("summary.json")).count());
        assertTrue(oldHashes.keySet().stream().anyMatch(k->k.contains(".pai-f9-manifest")));
        // Reopen every independently recorded F1 journal to verify a terminal receipt.
        try(var stream=Files.walk(output)) {
            List<Path> jobRoots=stream.filter(p->Files.isDirectory(p)
                    && p.getFileName().toString().matches("job-[0-9]{3}-[0-9a-f-]{36}")).toList();
            assertEquals(2,jobRoots.size());
            for(Path jobRoot:jobRoots) {
                String name=jobRoot.getFileName().toString();
                UUID jobId=UUID.fromString(name.substring(8));
                Path source=name.startsWith("job-001-")?a:b;
                try(var journal=new org.cbihi.mrinormalizer.infrastructure.filesystem.JsonManifestStore(
                        source,jobRoot,jobRoot.resolve(".pai-f9-manifest"),jobId)) {
                    var replay=journal.replay(Optional.empty()).orElseThrow();
                    assertEquals(org.cbihi.mrinormalizer.application.provenance.manifest.ManifestState.JobState.COMPLETED,
                            replay.state());
                    assertEquals(org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.State.VERIFIED_PUBLISHED,
                            replay.operations().getFirst().observation().state());
                }
            }
        }
        try(var stream=Files.walk(output)) {
            for(Path f:stream.filter(p->p.getFileName().toString().equals("selected.nii")).toList())
                verifyNiftiByIndependentHeaderRead(f);
        }
        stdout.reset();stderr.reset();
        assertEquals(0,FboCommand.run(command,new PrintStream(stdout),new PrintStream(stderr)),
                "Second batch must leave prior verified outputs unchanged");
        for(var entry:oldHashes.entrySet()) {
            assertEquals(entry.getValue(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(output.resolve(entry.getKey())))),entry.getKey());
        }
        // Intentionally retain controlled native evidence until owner explicitly cleans it.
    }
    private static void verifyNiftiByIndependentHeaderRead(Path nifti) throws Exception {
        byte[] bytes=Files.readAllBytes(nifti);
        assertEquals(352+2*2*2*2,bytes.length);
        ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(348,b.getInt(0));
        assertEquals(3,b.getShort(40));
        assertEquals(2,b.getShort(42));assertEquals(2,b.getShort(44));assertEquals(2,b.getShort(46));
        assertEquals(512,b.getShort(70),"Unsigned 16-bit scalar type");
        assertEquals(16,b.getShort(72),"Bits per stored voxel");
        assertEquals(352.0f,b.getFloat(108));
        assertEquals(1.5f,b.getFloat(112),1e-6f);
        assertEquals(10.0f,b.getFloat(116),1e-6f);
        assertEquals(1,b.getShort(254),"sform scanner anatomical");
        for(int z=0;z<2;z++)for(int y=0;y<2;y++)for(int x=0;x<2;x++) {
            int ordinal=z*4+y*2+x;
            assertEquals(y*2+x+1,Short.toUnsignedInt(b.getShort(352+ordinal*2)),
                    "Independent stored voxel comparison at "+ordinal);
        }
        for(int column=0;column<3;column++) {
            double norm=0;
            for(int row=0;row<3;row++) {
                double entry=b.getFloat(280+row*16+column*4);
                assertTrue(Double.isFinite(entry));
                norm+=entry*entry;
            }
            assertEquals(new double[]{0.5,0.75,2.0}[column],Math.sqrt(norm),1e-5,
                    "Independent RAS geometry spacing, axis "+column);
        }
        assertEquals('n',bytes[344]);assertEquals('+',bytes[345]);assertEquals('1',bytes[346]);
    }
    private static void dicom(Path target,String series,String sop,double z) throws Exception {
        var d=new org.dcm4che3.data.Attributes();
        d.setString(org.dcm4che3.data.Tag.SOPClassUID,org.dcm4che3.data.VR.UI,org.dcm4che3.data.UID.MRImageStorage);
        d.setString(org.dcm4che3.data.Tag.SOPInstanceUID,org.dcm4che3.data.VR.UI,sop);
        d.setString(org.dcm4che3.data.Tag.StudyInstanceUID,org.dcm4che3.data.VR.UI,"1.2");
        d.setString(org.dcm4che3.data.Tag.SeriesInstanceUID,org.dcm4che3.data.VR.UI,series);
        d.setString(org.dcm4che3.data.Tag.FrameOfReferenceUID,org.dcm4che3.data.VR.UI,"1.2.4");
        d.setString(org.dcm4che3.data.Tag.Modality,org.dcm4che3.data.VR.CS,"MR");
        d.setInt(org.dcm4che3.data.Tag.Rows,org.dcm4che3.data.VR.US,2);
        d.setInt(org.dcm4che3.data.Tag.Columns,org.dcm4che3.data.VR.US,2);
        d.setString(org.dcm4che3.data.Tag.PixelSpacing,org.dcm4che3.data.VR.DS,"0.75","0.5");
        d.setString(org.dcm4che3.data.Tag.SpacingBetweenSlices,org.dcm4che3.data.VR.DS,"2");
        d.setString(org.dcm4che3.data.Tag.ImageOrientationPatient,org.dcm4che3.data.VR.DS,"1","0","0","0","1","0");
        d.setString(org.dcm4che3.data.Tag.ImagePositionPatient,org.dcm4che3.data.VR.DS,"10","20",Double.toString(z));
        d.setString(org.dcm4che3.data.Tag.PhotometricInterpretation,org.dcm4che3.data.VR.CS,"MONOCHROME2");
        d.setInt(org.dcm4che3.data.Tag.SamplesPerPixel,org.dcm4che3.data.VR.US,1);
        d.setInt(org.dcm4che3.data.Tag.BitsAllocated,org.dcm4che3.data.VR.US,16);
        d.setInt(org.dcm4che3.data.Tag.BitsStored,org.dcm4che3.data.VR.US,16);
        d.setInt(org.dcm4che3.data.Tag.HighBit,org.dcm4che3.data.VR.US,15);
        d.setInt(org.dcm4che3.data.Tag.PixelRepresentation,org.dcm4che3.data.VR.US,0);
        d.setDouble(org.dcm4che3.data.Tag.RescaleSlope,org.dcm4che3.data.VR.DS,1.5);
        d.setDouble(org.dcm4che3.data.Tag.RescaleIntercept,org.dcm4che3.data.VR.DS,10.0);
        d.setBytes(org.dcm4che3.data.Tag.PixelData,org.dcm4che3.data.VR.OW,new byte[]{1,0,2,0,3,0,4,0});
        try(var buf=new ByteArrayOutputStream();
            var writer=new org.dcm4che3.io.DicomOutputStream(buf,org.dcm4che3.data.UID.ExplicitVRLittleEndian)) {
            writer.writeFileMetaInformation(d.createFileMetaInformation(org.dcm4che3.data.UID.ExplicitVRLittleEndian));
            writer.writeDataset(null,d);
            writer.close();
            Files.write(target,buf.toByteArray());
        }
    }
}
