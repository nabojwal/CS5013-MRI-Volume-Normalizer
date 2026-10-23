package org.cbihi.mrinormalizer.application.validation;

import java.util.*;
import org.cbihi.mrinormalizer.application.conversion.NiftiAffineMapper;
import org.cbihi.mrinormalizer.application.dataset.nifti.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.ContentDigest;
import org.cbihi.mrinormalizer.domain.model.*;

/** Full-fidelity integer and quantized NIfTI-1 RAS comparison, never a publication capability. */
public final class NiftiOutputComparator {
    public NiftiComparisonResult compare(ImageVolume expectedM6, ConversionValidationReport m7Report,
            NiftiReadResult reopened, UUID sessionId, ContentDigest stagedDigest) {
        var fs = EnumSet.noneOf(NiftiComparisonFailure.class);
        if (expectedM6 == null || m7Report == null || reopened == null || sessionId == null || stagedDigest == null)
            return fail(NiftiComparisonFailure.REPORT_INCONSISTENT);
        if (!reopened.failures().isEmpty() || reopened.volume().isEmpty()) {
            fs.add(NiftiComparisonFailure.REOPEN_FAILED);
            if (reopened.failures().contains(NiftiReadFailure.INTERRUPTED)) fs.add(NiftiComparisonFailure.READ_INTERRUPTED);
            if (reopened.failures().contains(NiftiReadFailure.RESOURCE_LIMIT)) fs.add(NiftiComparisonFailure.RESOURCE_LIMIT);
            return new NiftiComparisonResult(Optional.empty(), List.copyOf(fs));
        }
        if (!reopened.findings().contains(NiftiReadFinding.SFORM_SELECTED)) fs.add(NiftiComparisonFailure.REOPEN_FAILED);
        var actual = reopened.volume().orElseThrow();
        var e = expectedM6.geometry(); var a = actual.geometry();
        var expectedV = expectedM6.voxels(); var actualV = actual.voxels();
        if (e.width() != a.width() || e.height() != a.height() || e.depth() != a.depth())
            fs.add(NiftiComparisonFailure.DIMENSIONS_DIFFER);
        long voxelCount = 0;
        try {
            voxelCount = Math.multiplyExact(Math.multiplyExact((long)e.width(), e.height()), e.depth());
            if (m7Report.width() != e.width() || m7Report.height() != e.height() || m7Report.depth() != e.depth()
                    || m7Report.voxelCount() != voxelCount || m7Report.scalarType() != expectedV.scalarType()
                    || !m7Report.intensityTransform().equals(expectedM6.intensityTransform())
                    || Double.compare(m7Report.rowSpacingMm(), e.rowSpacingMm()) != 0
                    || Double.compare(m7Report.columnSpacingMm(), e.columnSpacingMm()) != 0
                    || Double.compare(m7Report.sliceSpacingMm(), e.sliceSpacing()) != 0
                    || !m7Report.storedVoxelValuesPreserved() || m7Report.interpolated()
                    || m7Report.resampled() || m7Report.voxelOrderChanged())
                fs.add(NiftiComparisonFailure.REPORT_INCONSISTENT);
        } catch (ArithmeticException ex) { fs.add(NiftiComparisonFailure.DIMENSIONS_DIFFER); }
        if (actualV.scalarType() != expectedV.scalarType()) fs.add(NiftiComparisonFailure.SCALAR_TYPE_DIFFERS);
        if (e.width() == a.width() && e.height() == a.height() && e.depth() == a.depth()) {
            boolean mismatch = false;
            for (int z=0; z<e.depth(); z++) for (int y=0;y<e.height();y++) for(int x=0;x<e.width();x++)
                mismatch |= expectedV.rawValueAt(x,y,z) != actualV.rawValueAt(x,y,z);
            if (mismatch) fs.add(NiftiComparisonFailure.VOXELS_DIFFER);
        }
        double deviation = 0;
        try {
            var E = NiftiAffineMapper.toNiftiRas(e);
            if (!E.equals(m7Report.niftiRasVoxelToWorld())) fs.add(NiftiComparisonFailure.REPORT_INCONSISTENT);
            var A = a.voxelToWorldAffine();
            double[][] q = new double[3][4];
            for (int r=0;r<3;r++) for(int c=0;c<4;c++) {
                double original = E.get(r,c);
                float f = (float)original;
                if (!Float.isFinite(f)) fs.add(NiftiComparisonFailure.INVALID_EXPECTED_FLOAT32);
                q[r][c] = (double)f;
                double tolerance = 1e-9 + 8*Math.ulp(q[r][c]);
                if (Math.abs(A.get(r,c)-q[r][c]) > tolerance) fs.add(NiftiComparisonFailure.RAS_AFFINE_DIFFERS);
            }
            if (!fs.contains(NiftiComparisonFailure.INVALID_EXPECTED_FLOAT32)) {
                for (int c=0;c<3;c++) {
                    double norm = Math.hypot(Math.hypot(q[0][c],q[1][c]),q[2][c]);
                    double observed = c==0?a.columnSpacingMm():c==1?a.rowSpacingMm():a.sliceSpacing();
                    if (!Double.isFinite(norm) || norm<=0 || Math.abs(observed-norm)>1e-9+16*Math.ulp(norm))
                        fs.add(NiftiComparisonFailure.SPACING_DIFFERS);
                }
                double de=det(E.copyRows()),dq=det(q),da=det(A.copyRows());
                if (!Double.isFinite(de) || !Double.isFinite(dq) || !Double.isFinite(da)
                        || de==0 || dq==0 || da==0 || Math.signum(de)!=Math.signum(dq)
                        || Math.signum(dq)!=Math.signum(da)) fs.add(NiftiComparisonFailure.HANDEDNESS_DIFFERS);
                for(int mask=0;mask<8;mask++) {
                    double[] v = {((mask&1)==0?0:e.width()-1), ((mask&2)==0?0:e.height()-1), ((mask&4)==0?0:e.depth()-1)};
                    double[] diffs = new double[3]; double[] quant = new double[3]; double[] raw = new double[3];
                    double maxBound=0;
                    for(int k=0;k<3;k++) {
                        double Aq = world(A.copyRows(),k,v), Q=world(q,k,v), Ex=world(E.copyRows(),k,v);
                        double weighted=0;
                        for(int c=0;c<3;c++) weighted += Math.abs(q[k][c]*v[c]);
                        weighted+=Math.abs(q[k][3]);
                        double bound = 1e-8+16*Math.ulp(Q)+1e-12*weighted;
                        maxBound=Math.max(maxBound,bound);
                        if(Math.abs(Aq-Q)>bound) fs.add(NiftiComparisonFailure.RAS_AFFINE_DIFFERS);
                        diffs[k]=Aq-Ex; quant[k]=Q-Ex;raw[k]=Ex;
                    }
                    double pointDelta=length(diffs);
                    deviation=Math.max(deviation,pointDelta);
                    if(pointDelta>length(quant)+Math.sqrt(3)*maxBound+8*Math.ulp(pointDelta))
                        fs.add(NiftiComparisonFailure.RAS_AFFINE_DIFFERS);
                }
            }
        } catch (IllegalArgumentException|ArithmeticException ex) {
            fs.add(NiftiComparisonFailure.INVALID_EXPECTED_FLOAT32);
        }
        var t = expectedM6.intensityTransform(); var u=actual.intensityTransform();
        if(t.declared()!=u.declared()) fs.add(NiftiComparisonFailure.SCALING_DIFFERS);
        if (!t.declared()) {
            if(u.declared() || u.slope()!=1.0 || u.intercept()!=0.0) fs.add(NiftiComparisonFailure.SCALING_DIFFERS);
        } else {
            float sq=(float)t.slope(),iq=(float)t.intercept();
            if(!Float.isFinite(sq) || !Float.isFinite(iq) || sq==0) fs.add(NiftiComparisonFailure.INVALID_EXPECTED_FLOAT32);
            else if(Double.doubleToLongBits((double)sq)!=Double.doubleToLongBits(u.slope())
                    || Double.doubleToLongBits(iq==0?0.0d:(double)iq)!=Double.doubleToLongBits(u.intercept()==0?0.0d:u.intercept()))
                fs.add(NiftiComparisonFailure.SCALING_DIFFERS);
        }
        if (!fs.isEmpty()) return new NiftiComparisonResult(Optional.empty(),List.copyOf(fs));
        return new NiftiComparisonResult(Optional.of(new PostWriteVerificationEvidence(
                sessionId, stagedDigest, voxelCount, 8, deviation,
                reopened.findings().contains(NiftiReadFinding.WEAK_SOURCE_IDENTITY), "F8-NIFTI1-F32-1")),List.of());
    }
    private static NiftiComparisonResult fail(NiftiComparisonFailure f) {
        return new NiftiComparisonResult(Optional.empty(), List.of(f));
    }
    private static double world(double[][] a,int r,double[] v) {
        return ((a[r][0]*v[0]+a[r][1]*v[1])+a[r][2]*v[2])+a[r][3];
    }
    private static double length(double[] v) {
        return Math.hypot(Math.hypot(v[0],v[1]),v[2]);
    }
    private static double det(double[][] a){
        return a[0][0]*(a[1][1]*a[2][2]-a[1][2]*a[2][1])
            -a[0][1]*(a[1][0]*a[2][2]-a[1][2]*a[2][0])
            +a[0][2]*(a[1][0]*a[2][1]-a[1][1]*a[2][0]);
    }
}
