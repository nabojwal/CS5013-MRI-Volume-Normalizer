package org.cbihi.mrinormalizer.m8;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.jupiter.api.Test;
import org.cbihi.mrinormalizer.application.m8.*;
import org.cbihi.mrinormalizer.domain.model.*;
import org.cbihi.mrinormalizer.infrastructure.dicom.*;
import org.dcm4che3.data.*;
import org.dcm4che3.io.DicomInputStream;

final class M8DicomEncodingTest {
    @Test void m8_03_uint8WideningAndIndependentPixelComparison()throws Exception {
        ImageVolume im=M8GeometryTest.image(3,2,1,new double[]{10,20,30},
            new double[]{1,0,0},new double[]{0,1,0},new double[]{0,0,1},
            ScalarType.UINT8,new IntensityTransform(true,2,-12));
        var plan=M8GeometryTest.plan(im);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        new Dcm4cheMrSliceWriter().write(im,plan,0,bytes);
        try(var dis=new DicomInputStream(new ByteArrayInputStream(bytes.toByteArray()))){
            assertNotNull(dis.readFileMetaInformation());
            Attributes ds=dis.readDataset();
            assertEquals(16,ds.getInt(Tag.BitsAllocated,0));
            assertEquals(0,ds.getInt(Tag.PixelRepresentation,-1));
            assertEquals(12,ds.getBytes(Tag.PixelData).length);
            assertEquals("2",ds.getString(Tag.RescaleSlope));
        }
        var check=new Dcm4cheMrSliceVerifier(M8Limits.defaults()).verify(im,plan,0,
                new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(6,check.comparedVoxels());assertEquals(4,check.comparedCorners());
    }
    @Test void m8_21_independentVerifierRejectsChangedPixel()throws Exception {
        ImageVolume im=M8GeometryTest.image(2,2,1,new double[]{0,0,0},
            new double[]{1,0,0},new double[]{0,1,0},new double[]{0,0,1},
            ScalarType.INT16,IntensityTransform.identityNotDeclared());
        var plan=M8GeometryTest.plan(im);ByteArrayOutputStream out=new ByteArrayOutputStream();
        new Dcm4cheMrSliceWriter().write(im,plan,0,out);
        byte[] bytes=out.toByteArray();
        // Corrupt the last raw stored pixel without changing DICOM syntax.
        bytes[bytes.length-2]^=1;
        assertThrows(M8Fault.Failure.class,()->new Dcm4cheMrSliceVerifier(M8Limits.defaults()).verify(im,plan,0,
                new ByteArrayInputStream(bytes)));
    }
    private static byte[] modified(byte[] source, Consumer<Attributes> mutate)throws Exception{
        Attributes ds;
        try(var dis=new DicomInputStream(new ByteArrayInputStream(source))){
            assertNotNull(dis.readFileMetaInformation());ds=dis.readDataset();
        }
        mutate.accept(ds);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(var writer=new DicomOutputStream(out,UID.ExplicitVRLittleEndian)){
            writer.writeDataset(ds.createFileMetaInformation(UID.ExplicitVRLittleEndian),ds);
        }
        return out.toByteArray();
    }
    @Test void m8_21_mutationMatrixProvesIndependentTagAndPixelChecks()throws Exception{
        ImageVolume im=M8GeometryTest.image(3,2,1,new double[]{10,20,30},
            new double[]{1,0,0},new double[]{0,1,0},new double[]{0,0,1},
            ScalarType.UINT16,new IntensityTransform(true,-2,1024));
        M8Plan p=M8GeometryTest.plan(im);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        new Dcm4cheMrSliceWriter().write(im,p,0,out);
        byte[] original=out.toByteArray();
        // Mandatory clean writer -> verifier control: mutations must not pass simply
        // because the verifier already rejects the writer's unmodified output.
        var unmodified=new Dcm4cheMrSliceVerifier(M8Limits.defaults()).verify(
            im,p,0,new ByteArrayInputStream(original));
        assertEquals(6,unmodified.comparedVoxels());
        List<Consumer<Attributes>> mutations=List.of(
          ds->ds.setInt(Tag.Rows,VR.US,99),
          ds->ds.setInt(Tag.PixelRepresentation,VR.US,1),
          ds->ds.setString(Tag.ImagePositionPatient,VR.DS,"99","99","99"),
          ds->ds.setString(Tag.RescaleSlope,VR.DS,"1"),
          ds->ds.setString(Tag.SOPInstanceUID,VR.UI,"2.25.999"),
          ds->ds.setString(0x00181030,VR.LO,"UNKNOWN EXTRA TAG"),
          ds->ds.setString(Tag.PatientID,VR.LO,"FABRICATED"),
          ds->{
              try {
                  byte[] b=ds.getBytes(Tag.PixelData);
                  assertNotNull(b);
                  assertTrue(b.length>0);
                  b[0]^=1;
                  ds.setBytes(Tag.PixelData,VR.OW,b);
              } catch (IOException e) {
                  throw new UncheckedIOException(e);
              }
          }
        );
        for(Consumer<Attributes> change:mutations){
            byte[] corrupted=modified(original,change);
            assertThrows(M8Fault.Failure.class,()->new Dcm4cheMrSliceVerifier(M8Limits.defaults())
                .verify(im,p,0,new ByteArrayInputStream(corrupted)));
        }
    }
    @Test void m8_15_preflightRejectsOversizedOrUndefinedPixelValueLength()throws Exception{
        ImageVolume image=M8GeometryTest.image(2,2,1,new double[]{0,0,0},
            new double[]{1,0,0},new double[]{0,1,0},new double[]{0,0,1},
            ScalarType.UINT16,IntensityTransform.identityNotDeclared());
        M8Plan plan=M8GeometryTest.plan(image);
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        new Dcm4cheMrSliceWriter().write(image,plan,0,output);
        byte[] valid=output.toByteArray();
        assertEquals(4,new Dcm4cheMrSliceVerifier(M8Limits.defaults())
            .verify(image,plan,0,new ByteArrayInputStream(valid)).comparedVoxels());
        int pixel=-1;
        for(int i=132;i+12<valid.length;i++){
            if((valid[i]&255)==0xe0&&(valid[i+1]&255)==0x7f
                &&(valid[i+2]&255)==0x10&&(valid[i+3]&255)==0x00
                &&valid[i+4]=='O'&&valid[i+5]=='W'){
                pixel=i;break;
            }
        }
        assertTrue(pixel>=0,"Explicit-VR OW pixel length header must be present");
        for(byte[] invalidLength:new byte[][]{
            {(byte)0xff,(byte)0xff,(byte)0xff,(byte)0xff},
            {(byte)0xff,(byte)0xff,(byte)0xff,(byte)0x7f}
        }){
            byte[] corrupt=valid.clone();
            System.arraycopy(invalidLength,0,corrupt,pixel+8,4);
            assertThrows(M8Fault.Failure.class,()->new Dcm4cheMrSliceVerifier(M8Limits.defaults())
                .verify(image,plan,0,new ByteArrayInputStream(corrupt)));
        }
    }
    @Test void m8_03_fourStorageScalarsAreComparedWithoutUnsignedClipping()throws Exception{
        for(ScalarType scalar:ScalarType.values()){
            long min=switch(scalar){case UINT8,UINT16->0;case INT8->-128;case INT16->-32768;};
            long max=switch(scalar){case UINT8->255;case INT8->127;case UINT16->65535;case INT16->32767;};
            VolumeGeometry g=new VolumeGeometry(3,1,1,1,1,1,
                new double[]{0,0,0},new double[]{1,0,0},new double[]{0,1,0},new double[]{0,0,1},
                CoordinateSystem.NIFTI_RAS);
            ImageVolume im=new ImageVolume(g,new ImmutableVoxelData(3,1,1,scalar,
                new long[]{min,0,max}),IntensityTransform.identityNotDeclared());
            M8Plan plan=M8GeometryTest.plan(im);ByteArrayOutputStream out=new ByteArrayOutputStream();
            new Dcm4cheMrSliceWriter().write(im,plan,0,out);
            var check=new Dcm4cheMrSliceVerifier(M8Limits.defaults()).verify(im,plan,0,
                new ByteArrayInputStream(out.toByteArray()));
            assertEquals(3,check.comparedVoxels());
        }
    }
    @Test void m8_22_orderedParseBackAcceptsSignedStacksAndRejectsCollapsedIpp()throws Exception{
        for(int sign:new int[]{1,-1}){
            ImageVolume source=M8GeometryTest.image(2,2,3,new double[]{0,0,10},
                new double[]{1,0,0},new double[]{0,1,0},new double[]{0,0,sign},
                ScalarType.UINT8,IntensityTransform.identityNotDeclared());
            M8Plan plan=M8GeometryTest.plan(source);
            var writer=new Dcm4cheMrSliceWriter();
            var clean=new Dcm4cheMrSliceVerifier(M8Limits.defaults());
            List<byte[]> slices=new ArrayList<>();
            for(int z=0;z<plan.depth();z++){
                ByteArrayOutputStream target=new ByteArrayOutputStream();
                writer.write(source,plan,z,target);
                byte[] bytes=target.toByteArray();slices.add(bytes);
                assertEquals(4,clean.verify(source,plan,z,
                    new ByteArrayInputStream(bytes)).comparedVoxels(),"Valid signed stack "+sign);
            }
            // The mutated second instance retains valid DICOM encoding but
            // makes two source-adjacent IPPs identical: never accept it.
            var changed=modified(slices.get(1),ds->ds.setString(Tag.ImagePositionPatient,VR.DS,
                plan.slices().get(0).positionDs().toArray(String[]::new)));
            var reject=new Dcm4cheMrSliceVerifier(M8Limits.defaults());
            reject.verify(source,plan,0,new ByteArrayInputStream(slices.get(0)));
            assertThrows(M8Fault.Failure.class,()->reject.verify(source,plan,1,
                new ByteArrayInputStream(changed)));
        }
    }

    @Test void m8_22_b02ParseBackRejectsCollapsedIppAfterAllEarlierPositionChecks()throws Exception{
        // Deliberately forge a plan whose second planned IPP has collapsed.
        // Its position error is <0.001 mm, so the independent verifier's
        // earlier exact-plan and source-corner checks BOTH pass. Only B02's
        // ordered serialized separation guard can reject the second slice.
        for(int sign:new int[]{1,-1}){
            ImageVolume image=M8GeometryTest.image(2,2,3,new double[]{0,0,0},
                new double[]{1,0,0},new double[]{0,1,0},
                new double[]{0,0,sign*0.0001},
                ScalarType.UINT8,IntensityTransform.identityNotDeclared());
            M8Plan approved=M8GeometryTest.plan(image);
            double sourceStep=Math.abs(image.geometry().voxelToWorldAffine().get(2,2));
            assertTrue(sourceStep>0 && sourceStep<0.001,
                "Source-to-collapsed IPP error must pass the earlier corner tolerance");
            List<M8Plan.Slice> fakeSlices=new ArrayList<>(approved.slices());
            var second=approved.slices().get(1);
            fakeSlices.set(1,new M8Plan.Slice(second.sourceZ(),second.instanceNumber(),
                second.filename(),second.sopUid(),approved.slices().get(0).positionDs()));
            M8Plan forged=new M8Plan(approved.jobId(),approved.sourceDigest(),
                approved.sourceScalar(),approved.width(),approved.height(),approved.depth(),
                approved.sourceRasAffine(),approved.sourceIntensity(),approved.sourceFindings(),
                approved.metadata(),approved.generatedAt(),approved.studyUid(),approved.seriesUid(),
                approved.frameUid(),approved.orientationDs(),approved.pixelSpacingDs(),
                approved.sliceSpacingDs(),approved.slopeDs(),approved.interceptDs(),
                approved.sliceStepSign(),fakeSlices);
            var writer=new Dcm4cheMrSliceWriter();
            ByteArrayOutputStream firstOut=new ByteArrayOutputStream();
            ByteArrayOutputStream secondOut=new ByteArrayOutputStream();
            writer.write(image,forged,0,firstOut);
            writer.write(image,forged,1,secondOut);
            try(var parsed=new DicomInputStream(new ByteArrayInputStream(secondOut.toByteArray()))){
                assertNotNull(parsed.readFileMetaInformation());
                Attributes actual=parsed.readDataset();
                assertArrayEquals(fakeSlices.get(1).positionDs().toArray(String[]::new),
                    actual.getStrings(Tag.ImagePositionPatient),
                    "Parsed output must agree exactly with the forged planned IPP");
            }
            var verifier=new Dcm4cheMrSliceVerifier(M8Limits.defaults());
            assertEquals(4,verifier.verify(image,forged,0,
                new ByteArrayInputStream(firstOut.toByteArray())).comparedVoxels(),
                "The first slice establishes the clean ordering and plan controls");
            assertThrows(M8Fault.Failure.class,()->verifier.verify(image,forged,1,
                new ByteArrayInputStream(secondOut.toByteArray())));
        }
    }
    /** Isolate the independent verifier's B02 predicate from earlier planned
     * IPP-string equality: a mutated Part 10 file may be rejected too early. */
    private static void checkB02(java.util.List<String> previous,
            java.util.List<String> current,String spacing,double stepZ)throws Exception {
        var method=Dcm4cheMrSliceVerifier.class.getDeclaredMethod("verifyAdjacent",
            java.util.List.class,java.util.List.class,java.util.List.class,String.class,
            double[].class,double[].class);
        method.setAccessible(true);
        try{
            method.invoke(null,previous,current,
                java.util.List.of("1","0","0","0","1","0"),spacing,
                new double[]{0,0,stepZ},new double[]{0,0,1});
        }catch(java.lang.reflect.InvocationTargetException e){
            if(e.getCause() instanceof M8Fault.Failure failure)throw failure;
            throw new AssertionError("Unexpected verifier predicate error",e.getCause());
        }
    }
    @Test void m8_22_b02IndependentPredicateChecksSpacingSignCollapseAndLateralResidual()throws Exception{
        var zero=java.util.List.of("0","0","0");
        // Exact and near-boundary correct signed progressions must pass.
        checkB02(zero,java.util.List.of("0","0","1"),"1",1);
        checkB02(zero,java.util.List.of("0","0","-1"),"1",-1);
        checkB02(zero,java.util.List.of("0.0000005","0","1.0000005"),"1",1);
        // The following invocations are of the B02 guard itself: no planned
        // IPP comparison, tag allowlist, or voxel check can mask the result.
        java.util.List<java.util.List<String>> bad=java.util.List.of(
            zero,                                    // exact collapse
            java.util.List.of("0.0","0","0"),    // text-distinct numeric collapse
            java.util.List.of("0","0","-1"),     // reversed direction
            java.util.List.of("0","0","1.000002"), // 2e-6 > 1e-6 spacing error
            java.util.List.of("0.000002","0","1")); // lateral residual > bound
        for(var current:bad){
            assertThrows(M8Fault.Failure.class,()->checkB02(zero,current,"1",1));
        }
        assertThrows(M8Fault.Failure.class,
            ()->checkB02(zero,java.util.List.of("0","0","0.0000000001"),"0.0000000001",1));
        assertThrows(M8Fault.Failure.class,
            ()->checkB02(zero,java.util.List.of("0","0","1"),"1.000002",1));
        assertThrows(M8Fault.Failure.class,
            ()->checkB02(java.util.List.of("0","0","1E-320"),
                java.util.List.of("0","0","1.00001E-320"),"1E-320",1e-320));
    }

}
