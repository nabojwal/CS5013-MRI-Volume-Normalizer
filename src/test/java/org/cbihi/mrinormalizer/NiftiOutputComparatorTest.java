package org.cbihi.mrinormalizer;

import org.cbihi.mrinormalizer.application.dataset.nifti.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.application.validation.*;
import org.cbihi.mrinormalizer.domain.model.*;
import org.cbihi.mrinormalizer.application.conversion.NiftiAffineMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NiftiOutputComparatorTest {
    private static ImageVolume volume(long[] values) {
        return new ImageVolume(new VolumeGeometry(2,2,1,1,1,1,
                new double[]{10,-5,3},new double[]{1,0,0},new double[]{0,1,0},new double[]{0,0,1},
                CoordinateSystem.DICOM_PATIENT_LPS),new ImmutableVoxelData(2,2,1,ScalarType.UINT16,values),
                IntensityTransform.identityNotDeclared());
    }
    private static ImageVolume ras(long[] values) {
        return new ImageVolume(new VolumeGeometry(2,2,1,1,1,1,
                new double[]{-10,5,3},new double[]{-1,0,0},new double[]{0,-1,0},new double[]{0,0,1},
                CoordinateSystem.NIFTI_RAS),new ImmutableVoxelData(2,2,1,ScalarType.UINT16,values),
                IntensityTransform.identityNotDeclared());
    }
    private NiftiComparisonResult check(long[] original,long[] reopened) {
        var expected=volume(original);
        var report=org.cbihi.mrinormalizer.application.validation.ConversionValidationReport.losslessIntegerPreserving(
                new OutputTarget("stage.nii"),expected,NiftiAffineMapper.toNiftiRas(expected.geometry()));
        var read=new NiftiReadResult(new RelativePath(RelativePath.Root.SOURCE,"stage.nii"),
                Optional.of(ras(reopened)),List.of(),List.of(NiftiReadFinding.SFORM_SELECTED));
        return new NiftiOutputComparator().compare(expected,report,read,UUID.randomUUID(),new ContentDigest(42,"a".repeat(64)));
    }
    @Test void T01_allVoxelsSingletonAndNegativeRas() {
        var result=check(new long[]{0,1,65535,42},new long[]{0,1,65535,42});
        assertTrue(result.failures().isEmpty());
        assertEquals(4,result.verified().orElseThrow().comparedVoxelCount());
        assertEquals(8,result.verified().orElseThrow().comparedCornerCount());
    }
    @Test void T02_nonfirstVoxelMismatchFails() {
        var result=check(new long[]{0,1,65535,42},new long[]{0,1,65535,43});
        assertTrue(result.verified().isEmpty());
        assertTrue(result.failures().contains(NiftiComparisonFailure.VOXELS_DIFFER));
    }
    @Test void T03_failedF7OverridesPlausibleReport() {
        var expected=volume(new long[]{0,1,2,3});
        var report=org.cbihi.mrinormalizer.application.validation.ConversionValidationReport.losslessIntegerPreserving(
                new OutputTarget("stage.nii"),expected,NiftiAffineMapper.toNiftiRas(expected.geometry()));
        var read=new NiftiReadResult(new RelativePath(RelativePath.Root.SOURCE,"stage.nii"),
                Optional.empty(),List.of(NiftiReadFailure.GZIP_INTEGRITY_FAILED),List.of());
        var out=new NiftiOutputComparator().compare(expected,report,read,UUID.randomUUID(),new ContentDigest(2,"b".repeat(64)));
        assertTrue(out.failures().contains(NiftiComparisonFailure.REOPEN_FAILED));
    }

    // The following fixtures construct two INDEPENDENT volumes; the comparison is not a
    // mock of the M7 report or a writer-generated self-comparison.
    private static ImageVolume image(int w,int h,int d,ScalarType type,long[] voxels,
                                     VolumeGeometry geometry,IntensityTransform scaling) {
        return new ImageVolume(geometry,new ImmutableVoxelData(w,h,d,type,voxels),scaling);
    }
    private static VolumeGeometry geometry(int w,int h,int d,double sx,double sy,double sz,
                                           double[] origin,double[] x,double[] y,double[] z,CoordinateSystem cs) {
        return new VolumeGeometry(w,h,d,sy,sx,sz,origin,x,y,z,cs);
    }
    private static final double[] X={1,0,0},Y={0,1,0},Z={0,0,1};
    private static ImageVolume lps(int w,int h,int d,ScalarType t,long[] values,IntensityTransform scale){
        return image(w,h,d,t,values,geometry(w,h,d,1,1,1,new double[]{10,-5,3},X,Y,Z,
                CoordinateSystem.DICOM_PATIENT_LPS),scale);
    }
    private static ImageVolume ras(int w,int h,int d,ScalarType t,long[] values,IntensityTransform scale){
        return image(w,h,d,t,values,geometry(w,h,d,1,1,1,new double[]{-10,5,3},
                new double[]{-1,0,0},new double[]{0,-1,0},Z,
                CoordinateSystem.NIFTI_RAS),scale);
    }
    private static NiftiComparisonResult compare(ImageVolume source, ImageVolume actual){
        return compare(source,actual,report(source),List.of(NiftiReadFinding.SFORM_SELECTED));
    }
    private static ConversionValidationReport report(ImageVolume source){
        return ConversionValidationReport.losslessIntegerPreserving(new OutputTarget("stage.nii"),
                source,NiftiAffineMapper.toNiftiRas(source.geometry()));
    }
    private static NiftiComparisonResult compare(ImageVolume source,ImageVolume actual,
                    ConversionValidationReport report,List<NiftiReadFinding> findings){
        var read=new NiftiReadResult(new RelativePath(RelativePath.Root.SOURCE,"stage.nii"),
                Optional.of(actual),List.of(),findings);
        return new NiftiOutputComparator().compare(source,report,read,UUID.fromString("00000000-0000-4000-8000-000000000002"),
                new ContentDigest(42,"a".repeat(64)));
    }
    private static void failWith(NiftiComparisonFailure failure,NiftiComparisonResult result){
        assertTrue(result.verified().isEmpty(),"Unexpected positive comparison: "+failure);
        assertTrue(result.failures().contains(failure),"Expected "+failure+" in "+result.failures());
    }
    @Test void T01_fourScalarTypesCompareExactly(){
        var cases=new Object[][]{
                {ScalarType.UINT8,new long[]{0,1,127,255}},
                {ScalarType.INT8,new long[]{-128,-1,0,127}},
                {ScalarType.UINT16,new long[]{0,1,32768,65535}},
                {ScalarType.INT16,new long[]{-32768,-1,0,32767}}
        };
        for(var testcase:cases){
            var type=(ScalarType)testcase[0];var values=(long[])testcase[1];
            var outcome=compare(lps(2,2,1,type,values,IntensityTransform.identityNotDeclared()),
                    ras(2,2,1,type,values,IntensityTransform.identityNotDeclared()));
            assertTrue(outcome.failures().isEmpty(),type+": "+outcome.failures());
            assertEquals(4,outcome.verified().orElseThrow().comparedVoxelCount());
        }
    }
    @Test void T02_allCoordinatesAreComparedInCanonicalOrder(){
        long[] values={0,1,2,3,4,5,6,7,8,9,10,11};
        var source=lps(2,3,2,ScalarType.INT16,values,IntensityTransform.identityNotDeclared());
        var target=ras(2,3,2,ScalarType.INT16,values,IntensityTransform.identityNotDeclared());
        assertEquals(12,compare(source,target).verified().orElseThrow().comparedVoxelCount());
        var orderChanged=values.clone();orderChanged[1]=values[7];orderChanged[7]=values[1];
        failWith(NiftiComparisonFailure.VOXELS_DIFFER,compare(source,
                ras(2,3,2,ScalarType.INT16,orderChanged,IntensityTransform.identityNotDeclared())));
    }
    @Test void T03_singletonAxisGeometryIsMeaningful(){
        long[] values={42};
        var result=compare(lps(1,1,1,ScalarType.INT16,values,IntensityTransform.identityNotDeclared()),
                ras(1,1,1,ScalarType.INT16,values,IntensityTransform.identityNotDeclared()));
        assertTrue(result.failures().isEmpty());
        assertEquals(8,result.verified().orElseThrow().comparedCornerCount());
    }
    @Test void T04_anyOneMutatedVoxelFails(){
        long[] values={0,1,2,3,4,5,6,7,8,9,10,11};
        var expected=lps(2,3,2,ScalarType.UINT16,values,IntensityTransform.identityNotDeclared());
        for(int at:new int[]{0,5,11}){
            var changed=values.clone();changed[at]++;
            failWith(NiftiComparisonFailure.VOXELS_DIFFER,compare(expected,
                    ras(2,3,2,ScalarType.UINT16,changed,IntensityTransform.identityNotDeclared())));
        }
    }
    @Test void T05_dimensionRearrangementRejected(){
        long[] values={0,1,2,3,4,5};
        failWith(NiftiComparisonFailure.DIMENSIONS_DIFFER,compare(
                lps(2,3,1,ScalarType.UINT8,values,IntensityTransform.identityNotDeclared()),
                ras(3,2,1,ScalarType.UINT8,values,IntensityTransform.identityNotDeclared())));
    }
    @Test void T06_scalarTypeMismatchFails(){
        long[] values={0,1,2,3};
        var expected=lps(2,2,1,ScalarType.INT16,values,IntensityTransform.identityNotDeclared());
        for(var actualType:List.of(ScalarType.UINT16,ScalarType.INT8,ScalarType.UINT8))
            failWith(NiftiComparisonFailure.SCALAR_TYPE_DIFFERS,compare(expected,
                    ras(2,2,1,actualType,values,IntensityTransform.identityNotDeclared())));
    }
    @Test void T07_reportIsNotIndependentVerification(){
        var expected=lps(1,1,1,ScalarType.UINT16,new long[]{7},IntensityTransform.identityNotDeclared());
        var original=report(expected);
        var inconsistent=new ConversionValidationReport(original.output(),1,1,1,ScalarType.UINT16,1,
                original.rowSpacingMm(),original.columnSpacingMm(),original.sliceSpacingMm(),
                original.niftiRasVoxelToWorld(),original.intensityTransform(),false,false,false,false);
        failWith(NiftiComparisonFailure.REPORT_INCONSISTENT,compare(expected,
                ras(1,1,1,ScalarType.UINT16,new long[]{7},IntensityTransform.identityNotDeclared()),inconsistent,
                List.of(NiftiReadFinding.SFORM_SELECTED)));
    }
    @Test void T08_lpsToRasAppliedOnce(){
        var expected=lps(2,2,1,ScalarType.UINT16,new long[]{0,1,2,3},IntensityTransform.identityNotDeclared());
        assertTrue(compare(expected,ras(2,2,1,ScalarType.UINT16,new long[]{0,1,2,3},
                IntensityTransform.identityNotDeclared())).failures().isEmpty());
        var wrong=image(2,2,1,ScalarType.UINT16,new long[]{0,1,2,3},
                geometry(2,2,1,1,1,1,new double[]{10,-5,3},X,Y,Z,CoordinateSystem.NIFTI_RAS),
                IntensityTransform.identityNotDeclared());
        failWith(NiftiComparisonFailure.RAS_AFFINE_DIFFERS,compare(expected,wrong));
    }
    private static ImageVolume oblique(boolean shear,boolean quantized,boolean reversed,double origin){
        double a=Math.sqrt(0.5);
        double[] cx={a,a,0},cy=shear?new double[]{0,1,0}:new double[]{-a,a,0},cz={0,0,1};
        if(reversed)cz=new double[]{0,0,-1};
        var g=geometry(3,2,2,0.7,2,3,new double[]{origin,2.3,8.5},cx,cy,cz,
                CoordinateSystem.NIFTI_RAS);
        if(!quantized)return image(3,2,2,ScalarType.INT16,new long[12],g,IntensityTransform.identityNotDeclared());
        var affine=g.voxelToWorldAffine();double[][] cols=new double[3][3];double[] spacing=new double[3];
        for(int c=0;c<3;c++){
            for(int r=0;r<3;r++)cols[c][r]=(double)(float)affine.get(r,c);
            spacing[c]=Math.hypot(Math.hypot(cols[c][0],cols[c][1]),cols[c][2]);
            for(int r=0;r<3;r++)cols[c][r]/=spacing[c];
        }
        var quant=geometry(3,2,2,spacing[0],spacing[1],spacing[2],
                new double[]{(double)(float)origin,(double)(float)2.3,(double)(float)8.5},
                cols[0],cols[1],cols[2],CoordinateSystem.NIFTI_RAS);
        return image(3,2,2,ScalarType.INT16,new long[12],quant,IntensityTransform.identityNotDeclared());
    }
    @Test void T09_obliqueVoxelCentresMatch(){
        // Expected source is LPS; invert both world axes to express the same RAS affine.
        var target=oblique(false,false,false,11);
        var g=target.geometry();
        var expected=image(3,2,2,ScalarType.INT16,new long[12],geometry(3,2,2,
                g.columnSpacingMm(),g.rowSpacingMm(),g.sliceSpacing(),
                new double[]{-11,-2.3,8.5},
                new double[]{-g.columnIndexDirection()[0],-g.columnIndexDirection()[1],g.columnIndexDirection()[2]},
                new double[]{-g.rowIndexDirection()[0],-g.rowIndexDirection()[1],g.rowIndexDirection()[2]},
                new double[]{-g.sliceDirection()[0],-g.sliceDirection()[1],g.sliceDirection()[2]},
                CoordinateSystem.DICOM_PATIENT_LPS),IntensityTransform.identityNotDeclared());
        var outcome=compare(expected,oblique(false,true,false,11));
        assertTrue(outcome.failures().isEmpty(),outcome.failures().toString());
        assertEquals(8,outcome.verified().orElseThrow().comparedCornerCount());
    }
    @Test void T10_shearIsNotDiscarded(){
        var target=oblique(true,false,false,11);var g=target.geometry();
        var expected=image(3,2,2,ScalarType.INT16,new long[12],geometry(3,2,2,
                g.columnSpacingMm(),g.rowSpacingMm(),g.sliceSpacing(),
                new double[]{-11,-2.3,8.5},
                new double[]{-g.columnIndexDirection()[0],-g.columnIndexDirection()[1],g.columnIndexDirection()[2]},
                new double[]{-g.rowIndexDirection()[0],-g.rowIndexDirection()[1],g.rowIndexDirection()[2]},
                new double[]{-g.sliceDirection()[0],-g.sliceDirection()[1],g.sliceDirection()[2]},
                CoordinateSystem.DICOM_PATIENT_LPS),IntensityTransform.identityNotDeclared());
        assertTrue(compare(expected,oblique(true,true,false,11)).failures().isEmpty());
        failWith(NiftiComparisonFailure.RAS_AFFINE_DIFFERS,compare(expected,oblique(false,true,false,11)));
    }
    @Test void T10_shearedNiftiHeaderOmitsUnrepresentableQform() throws Exception {
        var g=geometry(2,2,1,1,1,1,new double[]{0,0,0},
                new double[]{1,0,0},new double[]{0.25,1,0},Z,
                CoordinateSystem.DICOM_PATIENT_LPS);
        var expected=image(2,2,1,ScalarType.INT16,new long[]{1,2,3,4},g,
                IntensityTransform.identityNotDeclared());
        var target=java.nio.file.Files.createTempFile("f8-shear-",".nii");
        java.nio.file.Files.delete(target);
        new org.cbihi.mrinormalizer.infrastructure.nifti.Nifti1VolumeWriter().write(expected,
                NiftiAffineMapper.toNiftiRas(g),new OutputTarget(target.toString()));
        var header=java.nio.ByteBuffer.wrap(java.nio.file.Files.readAllBytes(target))
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        assertEquals(0,header.getShort(252)); // qform_code: cannot encode shear losslessly
        assertTrue(header.getShort(254)>0);   // sform_code: independently preserved
    }
    @Test void T11_handednessCannotFlip(){
        var expected=lps(2,2,2,ScalarType.INT16,new long[8],IntensityTransform.identityNotDeclared());
        var actual=image(2,2,2,ScalarType.INT16,new long[8],geometry(2,2,2,1,1,1,
                new double[]{-10,5,3},new double[]{-1,0,0},new double[]{0,-1,0},
                new double[]{0,0,-1},CoordinateSystem.NIFTI_RAS),IntensityTransform.identityNotDeclared());
        failWith(NiftiComparisonFailure.HANDEDNESS_DIFFERS,compare(expected,actual));
    }
    @Test void T12_sformQuantizationBoundAccepted(){
        // Independent binary32 reference values, NOT a tolerance chosen from the observed error.
        assertEquals(0.699999988079071, (double)(float)0.7, 1e-15);
        assertEquals(300.001708984375,(double)(float)300.0017,1e-12);
        var origin=new double[]{300.0017,-5,3};
        var src=image(2,2,1,ScalarType.INT16,new long[4],
                geometry(2,2,1,0.7,1,1,origin,X,Y,Z,CoordinateSystem.DICOM_PATIENT_LPS),
                IntensityTransform.identityNotDeclared());
        var dst=image(2,2,1,ScalarType.INT16,new long[4],geometry(2,2,1,
                (double)(float)0.7,1,1,new double[]{-(double)(float)300.0017,5,3},
                new double[]{-1,0,0},new double[]{0,-1,0},Z,CoordinateSystem.NIFTI_RAS),
                IntensityTransform.identityNotDeclared());
        assertTrue(compare(src,dst).failures().isEmpty());
    }
    @Test void T13_largeOriginCornerBound(){
        for(double origin:new double[]{16777217.0,100000003.0}){
            var expected=image(2,2,2,ScalarType.INT16,new long[8],
                    geometry(2,2,2,1,1,1,new double[]{origin,0,0},X,Y,Z,
                    CoordinateSystem.DICOM_PATIENT_LPS),IntensityTransform.identityNotDeclared());
            var actual=image(2,2,2,ScalarType.INT16,new long[8],
                    geometry(2,2,2,1,1,1,new double[]{-(double)(float)origin,0,0},
                    new double[]{-1,0,0},new double[]{0,-1,0},Z,
                    CoordinateSystem.NIFTI_RAS),IntensityTransform.identityNotDeclared());
            assertTrue(compare(expected,actual).failures().isEmpty(),"origin="+origin);
        }
    }
    @Test void T14_justBeyondGeometryToleranceRejected(){
        var expected=lps(2,2,2,ScalarType.INT16,new long[8],IntensityTransform.identityNotDeclared());
        var target=image(2,2,2,ScalarType.INT16,new long[8],
                geometry(2,2,2,1,1,1,new double[]{-10-2e-9,5,3},
                new double[]{-1,0,0},new double[]{0,-1,0},Z,CoordinateSystem.NIFTI_RAS),
                IntensityTransform.identityNotDeclared());
        failWith(NiftiComparisonFailure.RAS_AFFINE_DIFFERS,compare(expected,target));
    }
    @Test void T15_transformDisagreementRejected(){
        var expected=lps(1,1,1,ScalarType.UINT16,new long[]{1},IntensityTransform.identityNotDeclared());
        var actual=ras(1,1,1,ScalarType.UINT16,new long[]{1},IntensityTransform.identityNotDeclared());
        failWith(NiftiComparisonFailure.REOPEN_FAILED,compare(expected,actual,report(expected),
                List.of(NiftiReadFinding.QFORM_SELECTED)));
        for(var broken:List.of(NiftiReadFailure.TRANSFORM_DISAGREEMENT,
                NiftiReadFailure.GEOMETRY_UNAVAILABLE,NiftiReadFailure.INVALID_HEADER)){
            var read=new NiftiReadResult(new RelativePath(RelativePath.Root.SOURCE,"stage.nii"),
                    Optional.empty(),List.of(broken),List.of());
            var verified=new NiftiOutputComparator().compare(expected,report(expected),read,
                    UUID.randomUUID(),new ContentDigest(42,"a".repeat(64)));
            failWith(NiftiComparisonFailure.REOPEN_FAILED,verified);
        }
    }
    @Test void T16_declaredScalingIsComparedAsMetadata(){
        var declared=new IntensityTransform(true,0.7,300.0017);
        var expected=lps(2,2,1,ScalarType.UINT16,new long[]{0,1,50,65535},declared);
        var actual=ras(2,2,1,ScalarType.UINT16,new long[]{0,1,50,65535},
                new IntensityTransform(true,(double)(float)0.7,(double)(float)300.0017));
        assertTrue(compare(expected,actual).failures().isEmpty());
        failWith(NiftiComparisonFailure.SCALING_DIFFERS,compare(expected,
                ras(2,2,1,ScalarType.UINT16,new long[]{0,1,50,65535},
                new IntensityTransform(true,0.7,300.0017))));
    }
    @Test void T17_undeclaredScalingCannotBecomeDeclared(){
        var expected=lps(2,2,1,ScalarType.UINT16,new long[]{1,2,3,4},IntensityTransform.identityNotDeclared());
        failWith(NiftiComparisonFailure.SCALING_DIFFERS,compare(expected,
                ras(2,2,1,ScalarType.UINT16,new long[]{1,2,3,4},new IntensityTransform(true,1,0))));
    }
    @Test void T18_scalingInvalidOrAppliedFails(){
        var expected=lps(2,2,1,ScalarType.UINT16,new long[]{1,2,3,4},
                new IntensityTransform(true,1e-50,1e50));
        failWith(NiftiComparisonFailure.INVALID_EXPECTED_FLOAT32,compare(expected,
                ras(2,2,1,ScalarType.UINT16,new long[]{1,2,3,4},new IntensityTransform(true,1,1))));
        for(var invalid:List.of(new IntensityTransform(true,1e50,1),
                new IntensityTransform(true,1e-50,1),new IntensityTransform(true,1,1e50))){
            var source=lps(2,2,1,ScalarType.UINT16,new long[]{1,2,3,4},invalid);
            failWith(NiftiComparisonFailure.INVALID_EXPECTED_FLOAT32,compare(source,
                    ras(2,2,1,ScalarType.UINT16,new long[]{1,2,3,4},new IntensityTransform(true,1,1))));
        }
        var valid=lps(2,2,1,ScalarType.UINT16,new long[]{1,2,3,4},new IntensityTransform(true,2,5));
        failWith(NiftiComparisonFailure.VOXELS_DIFFER,compare(valid,
                ras(2,2,1,ScalarType.UINT16,new long[]{7,9,11,13},new IntensityTransform(true,2,5))));
    }
}
