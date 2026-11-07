package org.cbihi.mrinormalizer.gui;

import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.cbihi.mrinormalizer.presentation.gui.*;
import org.cbihi.mrinormalizer.infrastructure.headless.F9WindowsComposition;
import org.dcm4che3.data.*;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class GuiI1DicomPreparationTest {
    @TempDir Path temp;
    static void dicom(Path path, String series, int slice) throws Exception {
        var a = new Attributes();
        a.setString(Tag.SOPClassUID, VR.UI, UID.MRImageStorage);
        a.setString(Tag.SOPInstanceUID, VR.UI, series + "." + (slice + 1));
        a.setString(Tag.StudyInstanceUID, VR.UI, "1.2");
        a.setString(Tag.SeriesInstanceUID, VR.UI, series);
        a.setString(Tag.FrameOfReferenceUID, VR.UI, "1.2.4");
        a.setString(Tag.Modality, VR.CS, "MR");
        a.setInt(Tag.Rows, VR.US, 2); a.setInt(Tag.Columns, VR.US, 2);
        a.setString(Tag.PixelSpacing, VR.DS, "0.75", "0.5");
        a.setString(Tag.SpacingBetweenSlices, VR.DS, "2");
        a.setString(Tag.ImageOrientationPatient, VR.DS, "1", "0", "0", "0", "1", "0");
        a.setString(Tag.ImagePositionPatient, VR.DS, "10", "20", "" + (2*slice));
        a.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
        a.setInt(Tag.SamplesPerPixel, VR.US, 1); a.setInt(Tag.BitsAllocated, VR.US, 16);
        a.setInt(Tag.BitsStored, VR.US, 16); a.setInt(Tag.HighBit, VR.US, 15); a.setInt(Tag.PixelRepresentation, VR.US, 0);
        a.setDouble(Tag.RescaleSlope, VR.DS, 1.5); a.setDouble(Tag.RescaleIntercept, VR.DS, 10);
        var data = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        for (int i=0;i<4;i++) data.putShort((short)(slice*4+i+1));
        a.setBytes(Tag.PixelData, VR.OW, data.array());
        try (var out = new DicomOutputStream(path.toFile())) {
            out.writeDataset(a.createFileMetaInformation(UID.ExplicitVRLittleEndian), a);
        }
    }
    private Path output() throws Exception {
        String configured = System.getProperty("d1.test.destination", "");
        Path parent = configured.isBlank() ? temp : Path.of(configured);
        return Files.createTempDirectory(parent, "D1 D2 output ");
    }
    private GuiI1Scanner.ScanResult scan(Path input) throws Exception {
        return new GuiI1Scanner().scan(GuiI1Scanner.Direction.DICOM_TO_NIFTI, input);
    }
    private static void checkNifti(Path p, int depth) throws Exception {
        byte[] raw = Files.readAllBytes(p);
        assertEquals(352 + 8*depth, raw.length);
        ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(348, b.getInt(0)); assertEquals(3,b.getShort(40));
        assertEquals(2,b.getShort(42)); assertEquals(2,b.getShort(44)); assertEquals(depth,b.getShort(46));
        assertEquals(512,b.getShort(70)); // UINT16
        assertEquals(1.5,b.getFloat(112)); assertEquals(10,b.getFloat(116));
        assertEquals(-0.5,b.getFloat(280)); assertEquals(-10,b.getFloat(292));
        assertEquals(-0.75,b.getFloat(300)); assertEquals(-20,b.getFloat(308));
        assertEquals(2,b.getFloat(320));
        for (int n=0;n<4*depth;n++) assertEquals(n+1,Short.toUnsignedInt(b.getShort(352+2*n)));
    }
    @Test void nestedShuffled155InstancesUseRealF9AndPreservePixelsGeometryScalingAndOriginals() throws Exception {
        Path input = Files.createDirectory(temp.resolve("MRI scan"));
        Path a = Files.createDirectories(input.resolve("study/first")), b = Files.createDirectories(input.resolve("study/second"));
        for(int i=0;i<155;i++) dicom((i%2==0?a:b).resolve("slice-"+(155-i)+".bin"),"1.2.3",i);
        var before = GuiI1DicomPreparation.capture(input);
        var scan = scan(input); assertEquals(155,scan.candidates().getFirst().instanceCount());
        Path output = output();
        var result = new GuiI1Execution().execute(scan,List.of(0),output);
        assertEquals(GuiI1Execution.State.VERIFIED,result.state(),result.summary());
        checkNifti(output.resolve("MRI scan.nii"),155);
        assertEquals(before,GuiI1DicomPreparation.capture(input));
        byte[] first = Files.readAllBytes(output.resolve("MRI scan.nii"));
        assertEquals(GuiI1Execution.State.REJECTED,new GuiI1Execution().execute(scan,List.of(0),output).state());
        assertArrayEquals(first,Files.readAllBytes(output.resolve("MRI scan.nii")));
    }
    @Test void singleConventionalFileDoesNotConsumeSiblingSlices() throws Exception {
        Path input=Files.createDirectory(temp.resolve("files")); Path one=input.resolve("chosen.dcm");
        dicom(one,"1.2.3",0); dicom(input.resolve("unselected.dcm"),"1.2.3",1);
        var picked=scan(one);assertEquals(1,picked.candidates().getFirst().instanceCount());
        Path out=output();var result=new GuiI1Execution().execute(picked,List.of(0),out);
        assertEquals(GuiI1Execution.State.VERIFIED,result.state(),result.summary());
        checkNifti(out.resolve("chosen.nii"),1);
    }
    @Test void twoMixedSeriesUseActualFboSequentiallyWithoutManualUidOrFolders() throws Exception {
        Path input = Files.createDirectory(temp.resolve("mixed"));
        for(int i=0;i<2;i++) {dicom(input.resolve("a"+i),"1.2.3",i);dicom(input.resolve("b"+i),"1.2.5",i);}
        var scanned = scan(input); Path output = output();
        var result = new GuiI1Execution().execute(scanned,List.of(0,1),output);
        assertEquals(GuiI1Execution.State.VERIFIED,result.state(),result.summary());
        List<Path> outputs;
        try(var walk=Files.walk(output)) {outputs=walk.filter(p->p.toString().endsWith(".nii")).toList();}
        assertEquals(2,outputs.size()); for(Path file:outputs)checkNifti(file,2);
    }
    @Test void mutationOrAddedMemberAfterInspectionIsRejectedBeforeBackend() throws Exception {
        Path input=Files.createDirectory(temp.resolve("source"));dicom(input.resolve("a"),"1.2.3",0);
        var scanned=scan(input);dicom(input.resolve("b"),"1.2.3",1);
        var result=new GuiI1Execution().execute(scanned,List.of(0),output());
        assertEquals(GuiI1Execution.State.REJECTED,result.state());
        assertTrue(result.summary().contains("changed"));
    }
    @Test void sameSizeMutationWithRestoredTimestampStillRefusesDispatch() throws Exception {
        Path input=Files.createDirectory(temp.resolve("source"));Path file=input.resolve("a");dicom(file,"1.2.3",0);
        var scanned=scan(input);var timestamp=Files.getLastModifiedTime(file);
        byte[] changed=Files.readAllBytes(file);changed[changed.length-1]^=1;Files.write(file,changed);Files.setLastModifiedTime(file,timestamp);
        var result=new GuiI1Execution().execute(scanned,List.of(0),output());
        assertEquals(GuiI1Execution.State.REJECTED,result.state());
    }
    @Test void irregularPhysicalSpacingDoesNotPublishEvenWithPreparedCopies() throws Exception {
        Path input=Files.createDirectory(temp.resolve("gapped"));
        dicom(input.resolve("a"),"1.2.3",0);dicom(input.resolve("b"),"1.2.3",1);dicom(input.resolve("c"),"1.2.3",4);
        Path out=output();var result=new GuiI1Execution().execute(scan(input),List.of(0),out);
        assertEquals(GuiI1Execution.State.FAILED,result.state());assertFalse(Files.exists(out.resolve("gapped.nii")));
    }
    @Test void guiAdmissionReturnsRejectionForMissingDestination() throws Exception {
        Path input=Files.createDirectory(temp.resolve("source"));dicom(input.resolve("a"),"1.2.3",0);
        Path missing=temp.resolve("missing");
        var result=new GuiI1Execution().executeForGui(scan(input),List.of(0),missing,event->{});
        assertEquals(GuiI1Execution.State.REJECTED,result.state());assertTrue(result.summary().contains("Destination admission"));
        assertFalse(Files.exists(missing));
    }
    @Test void corruptReplayRecordDoesNotAuthorizeAnotherRun() throws Exception {
        Path out=output();String id="a".repeat(64);GuiI1CompletionLedger.mark(out,id);
        Path record=out.resolve(".pai-internal/gui-verified").resolve(id+".record");
        Files.writeString(record,"not a verified receipt");
        assertThrows(java.io.IOException.class,()->GuiI1CompletionLedger.exists(out,id));
    }
    @Test void privateEvidenceCollisionPreservesForeignEntryAndDoesNotDispatch() throws Exception {
        Path input=Files.createDirectory(temp.resolve("source"));dicom(input.resolve("a"),"1.2.3",0);
        Files.writeString(input.resolve("unselected.txt"),"non-image"); // requires a prepared selection
        Path out=output();Files.writeString(out.resolve(".pai-d2-evidence"),"preserve");
        var result=new GuiI1Execution().execute(scan(input),List.of(0),out);
        assertEquals(GuiI1Execution.State.REJECTED,result.state());
        assertEquals("preserve",Files.readString(out.resolve(".pai-d2-evidence")));
        assertFalse(Files.exists(out.resolve("source.nii")));
    }
    @Test void preparedBytesHaveProvenanceAndCopiesAreRemovedWithoutDeletingEvidence() throws Exception {
        Path input=Files.createDirectory(temp.resolve("source"));dicom(input.resolve("z"),"1.2.3",0);
        var scanned=scan(input); var p=GuiI1DicomPreparation.prepare(scanned,List.of(0),output());
        Path copy=p.candidates().getFirst().directory().resolve("image-000001.dcm");
        assertArrayEquals(Files.readAllBytes(input.resolve("z")),Files.readAllBytes(copy));
        assertTrue(Files.readString(p.evidence()).contains("sha256"));
        p.close();assertFalse(Files.exists(copy));assertTrue(Files.isRegularFile(p.evidence()));
    }
    @Test void replacedPreparedCopyIsRetainedAndCleanupReportsUncertainty() throws Exception {
        Path input=Files.createDirectory(temp.resolve("source"));dicom(input.resolve("a"),"1.2.3",0);
        var p=GuiI1DicomPreparation.prepare(scan(input),List.of(0),output());
        Path file=p.candidates().getFirst().directory().resolve("image-000001.dcm");
        Files.writeString(file,"unexpected change");
        assertThrows(java.io.IOException.class,p::close);
        assertEquals("unexpected change",Files.readString(file));
    }
    @Test void hardLinkAliasesAreRejected() throws Exception {
        Path input=Files.createDirectory(temp.resolve("source"));dicom(input.resolve("a"),"1.2.3",0);
        Files.createLink(input.resolve("alias"),input.resolve("a"));
        assertThrows(java.io.IOException.class,()->scan(input));
    }
    @Test void duplicateSopsAndMultiframeDoNotBecomeExecutable() throws Exception {
        Path input=Files.createDirectory(temp.resolve("source"));dicom(input.resolve("a"),"1.2.3",0);
        Files.copy(input.resolve("a"),input.resolve("b"));
        var r=scan(input);assertFalse(r.candidates().getFirst().eligible());
        assertThrows(IllegalArgumentException.class,()->GuiI1Execution.plan(r,List.of(0)));
        // A separate metadata fixture isolates the unsupported multiframe decision.
        var reader=new GuiI1DicomDiscovery(path->new GuiI1DicomDiscovery.Metadata("1.2","1.2.3","1.2.3.1","MR",2));
        assertTrue(reader.discover(input).series().stream().noneMatch(GuiI1DicomDiscovery.Series::eligible));
    }
    @Test void invalidPrivateStageProducesInitializationDiagnosisRatherThanGenericRecovery() throws Exception {
        Path input=Files.createDirectory(temp.resolve("source"));dicom(input.resolve("a"),"1.2.3",0);
        Path output=output();Files.writeString(output.resolve(".pai-f9-private-stage"),"preserve");
        var result=new GuiI1Execution().execute(scan(input),List.of(0),output);
        assertEquals(GuiI1Execution.State.FAILED,result.state());assertTrue(result.summary().contains("PRIVATE_STAGE"));
        assertEquals("preserve",Files.readString(output.resolve(".pai-f9-private-stage")));
        assertFalse(Files.exists(output.resolve("source.nii")));
    }
}
