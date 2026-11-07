package org.cbihi.mrinormalizer.m8;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.cbihi.mrinormalizer.application.conversion.M8GeometryPolicy;
import org.cbihi.mrinormalizer.application.m8.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.domain.model.*;

final class M8GeometryTest {
    static ImageVolume image(int w,int h,int d,double[] origin,double[] column,double[] row,double[] step,
                             ScalarType scalar,IntensityTransform intensity) {
        VolumeGeometry geometry=new VolumeGeometry(w,h,d,2,1,3,origin,column,row,step,CoordinateSystem.NIFTI_RAS);
        long[] values=new long[w*h*d];for(int i=0;i<values.length;i++)values[i]=i;
        return new ImageVolume(geometry,new ImmutableVoxelData(w,h,d,scalar,values),intensity);
    }
    static M8Request request(){return new M8Request(UUID.randomUUID(),
        new RelativePath(RelativePath.Root.SOURCE,"test.nii"),new M8Metadata(true,true,
            M8Metadata.ScanningSequence.SE,M8Metadata.SequenceVariant.NONE));}
    static M8Plan plan(ImageVolume image){
        return new M8GeometryPolicy(M8Limits.defaults(),UUID::randomUUID).plan(request(),image,List.of(),
            new ContentDigest(352,"0".repeat(64)),Instant.parse("2026-10-28T00:00:00Z"));
    }
    @Test void m8_07_axisAlignedRasLpsAndPhysicalSliceOrder(){
        ImageVolume im=image(2,2,3,new double[]{5,6,7},new double[]{1,0,0},new double[]{0,1,0},
                new double[]{0,0,-1},ScalarType.UINT8,IntensityTransform.identityNotDeclared());
        M8Plan p=plan(im);
        assertEquals(List.of("-5","-6","7"),p.slices().get(0).positionDs());
        assertEquals(-1,p.sliceStepSign());
        assertEquals("1",p.pixelSpacingDs().get(1));
        assertEquals("2",p.pixelSpacingDs().get(0));
    }
    @Test void m8_08_rejectsInPlaneShear(){
        ImageVolume im=image(2,2,3,new double[]{0,0,0},new double[]{1,0,0},
                new double[]{0.1,1,0},new double[]{0,0,1},ScalarType.UINT8,
                IntensityTransform.identityNotDeclared());
        assertThrows(M8Fault.Failure.class,()->plan(im));
    }
    @Test void m8_08_rejectsStackTilt(){
        ImageVolume im=image(2,2,3,new double[]{0,0,0},new double[]{1,0,0},
                new double[]{0,1,0},new double[]{0.01,0,1},ScalarType.UINT8,
                IntensityTransform.identityNotDeclared());
        assertThrows(M8Fault.Failure.class,()->plan(im));
    }
    @Test void m8_09_dsNeverTruncatesAndLocaleIndependent(){
        Locale before=Locale.getDefault();try{
            Locale.setDefault(Locale.GERMANY);
            ImageVolume im=image(2,2,1,new double[]{3.141592653589793,0,0},
                new double[]{1,0,0},new double[]{0,1,0},new double[]{0,0,1},
                ScalarType.UINT8,IntensityTransform.identityNotDeclared());
            String v=plan(im).slices().getFirst().positionDs().getFirst();
            assertTrue(v.length()<=16);assertFalse(v.contains(","));
        }finally{Locale.setDefault(before);}
    }
    @Test void m8_09_B02_refusesCollapsedAndAcceptsRepresentableSpacing(){
        ImageVolume collapsed=tinyStack(1e15,0.0001);
        assertThrows(M8Fault.Failure.class,()->plan(collapsed));
        ImageVolume representable=tinyStack(0,0.0001);
        assertDoesNotThrow(()->plan(representable));
    }
    private static ImageVolume tinyStack(double zOrigin,double sliceSpacing){
        VolumeGeometry g=new VolumeGeometry(1,1,2,1,1,sliceSpacing,
            new double[]{0,0,zOrigin},new double[]{1,0,0},new double[]{0,1,0},
            new double[]{0,0,1},CoordinateSystem.NIFTI_RAS);
        return new ImageVolume(g,new ImmutableVoxelData(1,1,2,ScalarType.UINT8,new long[]{1,2}),
                IntensityTransform.identityNotDeclared());
    }
    @Test void m8_13_rejectsDuplicateInjectedUids(){
        UUID repeated=UUID.randomUUID();
        ImageVolume im=image(2,2,1,new double[]{0,0,0},new double[]{1,0,0},
            new double[]{0,1,0},new double[]{0,0,1},ScalarType.INT16,IntensityTransform.identityNotDeclared());
        var geo=new M8GeometryPolicy(M8Limits.defaults(),()->repeated);
        assertThrows(M8Fault.Failure.class,()->geo.plan(request(),im,List.of(),
            new ContentDigest(0,"0".repeat(64)),Instant.EPOCH));
    }
}
