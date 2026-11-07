package org.cbihi.mrinormalizer.infrastructure.nifti;

import static org.cbihi.mrinormalizer.infrastructure.nifti.Nifti1VolumeReaderTest.*;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.*;
import java.nio.file.Path;
import org.cbihi.mrinormalizer.application.dataset.nifti.*;
import org.cbihi.mrinormalizer.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NiftiReadGeometryTest {
    @TempDir Path root;
    ByteBuffer basic() {return fixture(2,ByteOrder.LITTLE_ENDIAN,3,2,1);}
    NiftiReadResult run(ByteBuffer b) throws Exception {return read(root,b.array(),limits());}
    static void matrix(ByteBuffer b,double[][] a) {for(int r=0;r<3;r++)for(int c=0;c<4;c++)b.putFloat(280+r*16+c*4,(float)a[r][c]);}
    static void point(ImageVolume v,double x,double y,double z,double... expected) {assertArrayEquals(expected,v.geometry().voxelToWorldAffine().transformPoint(x,y,z),2e-5);}
    static void qOnly(ByteBuffer b) {b.putShort(254,(short)0);b.putShort(252,(short)1);}
    @Test void T06_T11_obliqueAndDepthOne() throws Exception {
        for(int n:new int[]{1,3}) {var b=fixture(2,ByteOrder.LITTLE_ENDIAN,n,2,1);matrix(b,new double[][]{{0,-2,0,10},{1,0,0,20},{0,0,-3,30}});
            var v=run(b).volume().orElseThrow();point(v,1,1,1,8,21,27);assertEquals(3,v.geometry().sliceSpacing());assertEquals(CoordinateSystem.NIFTI_RAS,v.geometry().coordinateSystem());}
        assertTrue(run(fixture(2,ByteOrder.LITTLE_ENDIAN,1,1,1)).volume().isPresent());
    }
    @Test void T12_qRotationNegativeQfac() throws Exception {
        var b=basic();qOnly(b);b.putFloat(76,-1);b.putFloat(264,(float)Math.sqrt(.5));b.putFloat(268,10);b.putFloat(272,20);b.putFloat(276,30);
        var v=run(b).volume().orElseThrow();point(v,1,1,1,8,21,27);
    }
    @Test void T13_unitEquivalence() throws Exception {
        for(int unit:new int[]{1,2,3}) {double factor=unit==1?1000:unit==2?1:.001;var b=basic();b.put(123,(byte)unit);
            b.putFloat(80,(float)(1/factor));b.putFloat(84,(float)(2/factor));b.putFloat(88,(float)(3/factor));
            matrix(b,new double[][]{{1/factor,0,0,10/factor},{0,2/factor,0,20/factor},{0,0,3/factor,30/factor}});
            var v=run(b).volume().orElseThrow();point(v,1,1,1,11,22,33);assertEquals(1,v.geometry().columnSpacingMm(),1e-6);}
    }
    @Test void T14_unitsAndSpacing() throws Exception {
        for(int unit:new int[]{0,4,7,66,130,58}) {var b=basic();b.put(123,(byte)unit);failure(unit==0||unit==4||unit==7?NiftiReadFailure.UNSUPPORTED_SPATIAL_UNITS:NiftiReadFailure.INVALID_HEADER,run(b));}
        for(int o:new int[]{80,84,88})for(float f:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY}) {var b=basic();b.putFloat(o,f);failure(NiftiReadFailure.INVALID_HEADER,run(b));}
        for(int time:new int[]{8,16,24,32,40,48}) {var b=basic();b.put(123,(byte)(time|2));assertTrue(run(b).volume().isPresent());}
    }
    @Test void T15_transformCodesAndAlternatives() throws Exception {
        for(int code:new int[]{0,-1,6,32767}) {var b=basic();b.putShort(254,(short)code);failure(NiftiReadFailure.GEOMETRY_UNAVAILABLE,run(b));
            b.putShort(252,(short)1);var r=run(b);assertTrue(r.findings().contains(NiftiReadFinding.QFORM_SELECTED));if(code!=0)assertTrue(r.findings().contains(NiftiReadFinding.INVALID_SFORM_IGNORED));}
        for(int code:new int[]{-1,6}) {var b=basic();b.putShort(252,(short)code);var r=run(b);assertTrue(r.findings().contains(NiftiReadFinding.INVALID_QFORM_IGNORED));}
        for(int code=1;code<=5;code++) {var b=basic();b.putShort(254,(short)code);assertTrue(run(b).volume().isPresent());}
    }
    @Test void T16_qfac() throws Exception {
        for(float f:new float[]{0,1,-1,2,Float.NaN,Float.POSITIVE_INFINITY}) {var b=basic();qOnly(b);b.putFloat(76,f);var r=run(b);
            if(f==0||f==1||f==-1) {assertTrue(r.volume().isPresent());assertEquals(f==0,r.findings().contains(NiftiReadFinding.QFAC_ZERO_AS_POSITIVE));}else failure(NiftiReadFailure.GEOMETRY_UNAVAILABLE,r);}
    }
    @Test void T17_quaternionBoundaries() throws Exception {
        for(float bval:new float[]{0,.999999f,1,1.0000004f,1.0000006f,Float.NaN}) {var b=basic();qOnly(b);b.putFloat(256,bval);var r=run(b);
            if(bval<=1.0000004f) {var v=r.volume().orElseThrow();assertEquals(bval>1,r.findings().contains(NiftiReadFinding.QUATERNION_ROUNDING_NORMALIZED));if(bval==1)point(v,1,1,1,1,-2,-3);if(bval==0)point(v,1,1,1,1,2,3);}
            else failure(NiftiReadFailure.GEOMETRY_UNAVAILABLE,r);}
    }
    @Test void T18_bothAgree() throws Exception {var b=basic();b.putShort(252,(short)1);var r=run(b);assertTrue(r.findings().contains(NiftiReadFinding.SFORM_SELECTED));point(r.volume().orElseThrow(),2,1,1,2,2,3);}
    @Test void T19_disagreementIncludingSingleton() throws Exception {
        for(int mode=0;mode<3;mode++) {var b=basic();b.putShort(252,(short)1);if(mode==0)b.putFloat(292,1);if(mode==1){b.putFloat(280,-1);b.putFloat(300,-2);}if(mode==2)b.putFloat(320,-3);failure(NiftiReadFailure.TRANSFORM_DISAGREEMENT,run(b));}
    }
    @Test void T20_originIndependentWorldTolerance() throws Exception {
        for(float shift:new float[]{.00009f,.00012f}) {var b=basic();b.putShort(252,(short)1);b.putFloat(292,shift);var r=run(b);if(shift<.0001)assertTrue(r.volume().isPresent());else failure(NiftiReadFailure.TRANSFORM_DISAGREEMENT,r);}
        var b=basic();b.putShort(252,(short)1);b.putFloat(268,1_000_000);b.putFloat(292,1_000_001);failure(NiftiReadFailure.TRANSFORM_DISAGREEMENT,run(b));
    }
    @Test void T21_shearHandedness() throws Exception {
        var b=basic();b.putFloat(84,(float)Math.sqrt(5));matrix(b,new double[][]{{1,1,0,4},{0,2,0,5},{0,0,-3,6}});var v=run(b).volume().orElseThrow();point(v,2,1,1,7,7,3);
    }
    @Test void T22_conditioningFloor() throws Exception {
        for(float e:new float[]{0,5e-9f,2e-8f}) {var b=basic();b.putFloat(84,1);matrix(b,new double[][]{{1,1,0,0},{0,e,0,0},{0,0,3,0}});var r=run(b);if(e>1e-8)assertTrue(r.volume().isPresent());else failure(NiftiReadFailure.GEOMETRY_UNAVAILABLE,r);}
    }
    @Test void T23_spacingToleranceFallback() throws Exception {
        for(float delta:new float[]{0,.000010f,.000012f,.001f}) {var b=basic();b.putFloat(280,1+delta);var r=run(b);if(delta<.000011)assertTrue(r.volume().isPresent());else {failure(NiftiReadFailure.GEOMETRY_UNAVAILABLE,r);b.putShort(252,(short)1);r=run(b);assertTrue(r.findings().contains(NiftiReadFinding.INVALID_SFORM_IGNORED));assertTrue(r.findings().contains(NiftiReadFinding.QFORM_SELECTED));}}
    }
    @Test void T17_nonfiniteQuaternionAndOffsetFields() throws Exception {
        for(int o=256;o<=276;o+=4) {var b=basic();qOnly(b);b.putFloat(o,Float.NaN);failure(NiftiReadFailure.GEOMETRY_UNAVAILABLE,run(b));}
    }
    @Test void T23_adjacentFloatSpacingBoundary() throws Exception {
        // For a one-mm axis the approved threshold lies between these neighboring floats.
        float inside=1.0000109672546387f, outside=Math.nextUp(inside);
        var b=basic();b.putFloat(280,inside);assertTrue(run(b).volume().isPresent());
        b.putFloat(280,outside);failure(NiftiReadFailure.GEOMETRY_UNAVAILABLE,run(b));
    }
    @Test void T20_adjacentFloatWorldBoundary() throws Exception {
        // Box (2,1,1), spacings (1,2,3): L=sqrt(17). Pure x translation.
        double threshold=.0001+.000001*Math.sqrt(17);
        float below=(float)threshold;if(below>threshold)below=Math.nextDown(below);
        var b=basic();b.putShort(252,(short)1);b.putFloat(292,below);assertTrue(run(b).volume().isPresent());
        b.putFloat(292,Math.nextUp(below));failure(NiftiReadFailure.TRANSFORM_DISAGREEMENT,run(b));
    }

    @Test void T15_nonfiniteSformFallsBack() throws Exception {
        for(float v:new float[]{Float.NaN,Float.POSITIVE_INFINITY}) {var b=basic();b.putFloat(292,v);failure(NiftiReadFailure.GEOMETRY_UNAVAILABLE,run(b));b.putShort(252,(short)1);var r=run(b);assertTrue(r.findings().contains(NiftiReadFinding.QFORM_SELECTED));assertTrue(r.findings().contains(NiftiReadFinding.INVALID_SFORM_IGNORED));}
    }

    @Test void T11_trueObliqueSformAnalytic() throws Exception {
        var b=basic();matrix(b,new double[][]{{.6,-1.6,0,10},{.8,1.2,0,20},{0,0,3,30}});
        var r=run(b);assertTrue(r.findings().contains(NiftiReadFinding.SFORM_SELECTED));point(r.volume().orElseThrow(),1,1,1,9,22,33);
    }
    @Test void T06_obliqueSingletonSlice() throws Exception {
        for(int width:new int[]{1,3}) {var b=fixture(2,ByteOrder.LITTLE_ENDIAN,width,width==1?1:2,1);
            matrix(b,new double[][]{{.8,0,1.8,10},{0,2,0,20},{-.6,0,2.4,30}});
            var v=run(b).volume().orElseThrow();point(v,1,1,1,12.6,22,31.8);assertEquals(3,v.geometry().sliceSpacing(),1e-6);}
    }
    @Test void T12_halfTurnsIndependentAxes() throws Exception {
        double[][] expected={{1,-2,-3},{-1,2,-3},{-1,-2,3}};
        for(int axis=0;axis<3;axis++) {var b=basic();qOnly(b);b.putFloat(256+axis*4,1);point(run(b).volume().orElseThrow(),1,1,1,expected[axis]);}
    }
    @Test void T13_qformUnitOffsets() throws Exception {
        for(int unit:new int[]{1,2,3}) {double factor=unit==1?1000:unit==2?1:.001;var b=basic();qOnly(b);b.put(123,(byte)unit);
            for(int i=0;i<3;i++){b.putFloat(80+i*4,(float)((i+1)/factor));b.putFloat(268+i*4,(float)((10+10*i)/factor));}
            point(run(b).volume().orElseThrow(),1,1,1,11,22,33);}
    }
    @Test void T20_fieldExtentScalesWorldTolerance() throws Exception {
        for(float shift:new float[]{.00019f,.00021f}) {var b=fixture(2,ByteOrder.LITTLE_ENDIAN,100,1,1);b.putShort(252,(short)1);b.putFloat(292,shift);
            var r=run(b);if(shift<.0002)assertTrue(r.volume().isPresent());else failure(NiftiReadFailure.TRANSFORM_DISAGREEMENT,r);}
    }

}
