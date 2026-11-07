package org.cbihi.mrinormalizer.application.conversion;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadFinding;
import org.cbihi.mrinormalizer.application.m8.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.ContentDigest;
import org.cbihi.mrinormalizer.domain.model.*;

import static org.cbihi.mrinormalizer.application.m8.M8Fault.Code.*;
import static org.cbihi.mrinormalizer.application.m8.M8Fault.Phase.*;

/** Explicit RAS-to-LPS image-plane planning, including B02 serialized-stack separability. */
public final class M8GeometryPolicy {
    private final M8Limits limits;
    private final Supplier<UUID> uidSource;
    public M8GeometryPolicy(M8Limits limits, Supplier<UUID> uidSource) {
        this.limits = java.util.Objects.requireNonNull(limits);
        this.uidSource = java.util.Objects.requireNonNull(uidSource);
    }
    public M8Plan plan(M8Request request, ImageVolume volume, List<NiftiReadFinding> findings,
                       ContentDigest source, Instant generatedAt) {
        if (request==null || volume==null || findings==null || source==null || generatedAt==null)
            throw M8Fault.fail(PLAN, INVALID_INPUT);
        if (!request.metadata().complete()) throw M8Fault.fail(PLAN,METADATA_REQUIRED);
        VolumeGeometry g=volume.geometry();
        int w=g.width(), h=g.height(), d=g.depth();
        if (w>limits.maxDimension() || h>limits.maxDimension() || d>limits.maxDimension())
            throw M8Fault.fail(PLAN,RESOURCE_LIMIT);
        long n, aggregate;
        try {
            n=Math.multiplyExact(Math.multiplyExact((long)w,h),d);
            aggregate=Math.addExact(Math.addExact(Math.multiplyExact(16L,n),Math.multiplyExact(4L,Math.multiplyExact((long)w,h))),65L<<20);
            if (n > limits.nifti().maxVoxels() || n>Integer.MAX_VALUE-8L
                    || aggregate>limits.maxWorkingBytes() || Runtime.getRuntime().maxMemory() < aggregate+(256L<<20)
                    || Math.addExact(Math.multiplyExact(2L,n),Math.multiplyExact(d,16384L))>limits.maxOutputBytes())
                throw M8Fault.fail(PLAN,RESOURCE_LIMIT);
        } catch (ArithmeticException e) { throw M8Fault.fail(PLAN,RESOURCE_LIMIT); }
        long lower, upper;
        switch(volume.voxels().scalarType()) {
            case UINT8 -> { lower=0;upper=255; }
            case INT8 -> { lower=-128;upper=127; }
            case UINT16 -> { lower=0;upper=65535; }
            case INT16 -> { lower=-32768;upper=32767; }
            default -> throw M8Fault.fail(PLAN,UNSUPPORTED_INPUT);
        }
        for (int k=0;k<d;k++) for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
            long raw=volume.voxels().rawValueAt(x,y,k);
            if(raw<lower || raw>upper)throw M8Fault.fail(PLAN,INVALID_INPUT);
        }
        AffineMatrix4 a=g.voxelToWorldAffine();
        double[] u=flip(column(a,0)), v=flip(column(a,1)), step=flip(column(a,2)), origin=flip(column(a,3));
        double sx=norm(u), sy=norm(v), ss=norm(step);
        if (!good(sx) || !good(sy) || !good(ss)) throw M8Fault.fail(PLAN,GEOMETRY_UNREPRESENTABLE);
        double[] e1=scale(u,1/sx), originalE2=scale(v,1/sy);
        double uv=dot(e1,originalE2);
        if(!Double.isFinite(uv)||Math.abs(uv)>1e-6)throw M8Fault.fail(PLAN,GEOMETRY_UNREPRESENTABLE);
        double[] e2=normalize(sub(originalE2,scale(e1,uv)));
        double[] normal=normalize(cross(e1,e2));
        double projected=dot(step,normal);
        double lateral=norm(sub(step,scale(normal,projected)))/ss;
        if (!Double.isFinite(lateral)||lateral>1e-6 || projected==0 || !Double.isFinite(projected))
            throw M8Fault.fail(PLAN,GEOMETRY_UNREPRESENTABLE);
        int sigma=projected>0?1:-1;
        double s=Math.abs(projected);
        List<String> orient=new ArrayList<>();
        for(double z:e1)orient.add(ds(z,GEOMETRY_UNREPRESENTABLE));
        for(double z:e2)orient.add(ds(z,GEOMETRY_UNREPRESENTABLE));
        List<String> spacing=List.of(ds(sy,GEOMETRY_UNREPRESENTABLE),ds(sx,GEOMETRY_UNREPRESENTABLE));
        Optional<String> sliceSpace=d>1?Optional.of(ds(s,GEOMETRY_UNREPRESENTABLE)):Optional.empty();
        double slope=volume.intensityTransform().slope(), intercept=volume.intensityTransform().intercept();
        double intensityMin=slope*lower+intercept, intensityMax=slope*upper+intercept;
        if (!Double.isFinite(intensityMin)||!Double.isFinite(intensityMax))throw M8Fault.fail(PLAN,SCALING_UNREPRESENTABLE);
        String slopeDS=ds(slope,SCALING_UNREPRESENTABLE), interceptDS=ds(intercept,SCALING_UNREPRESENTABLE);
        if(Double.parseDouble(slopeDS)==0||Math.signum(Double.parseDouble(slopeDS))!=Math.signum(slope))
            throw M8Fault.fail(PLAN,SCALING_UNREPRESENTABLE);
        for(double r:new double[]{lower,upper}){
            double actual=Math.fma(Double.parseDouble(slopeDS),r,Double.parseDouble(interceptDS));
            double expected=Math.fma(slope,r,intercept);
            if (!Double.isFinite(actual)||Math.abs(actual-expected)>intensityTolerance(expected))
                throw M8Fault.fail(PLAN,SCALING_UNREPRESENTABLE);
        }
        // Compare serialized mapping to the original affine, including virtual singleton-axis steps.
        double[] pe1=parseTriplet(orient,0),pe2=parseTriplet(orient,3);
        if(Math.abs(norm(pe1)-1)>1e-6||Math.abs(norm(pe2)-1)>1e-6||Math.abs(dot(pe1,pe2))>1e-6)
            throw M8Fault.fail(PLAN,GEOMETRY_UNREPRESENTABLE);
        double[] pn=normalize(cross(pe1,pe2));
        if(dot(pn,normal)<=0)throw M8Fault.fail(PLAN,GEOMETRY_UNREPRESENTABLE);
        Set<String> usedUids=new HashSet<>();
        String study=uid(usedUids), series=uid(usedUids), frame=uid(usedUids);
        List<M8Plan.Slice> slices=new ArrayList<>();
        List<List<String>> positionStrings=new ArrayList<>();
        for(int k=0;k<d;k++){
            double[] sourcePos=add(origin,scale(step,k));
            List<String> pos=List.of(ds(sourcePos[0],GEOMETRY_UNREPRESENTABLE),
                    ds(sourcePos[1],GEOMETRY_UNREPRESENTABLE),ds(sourcePos[2],GEOMETRY_UNREPRESENTABLE));
            positionStrings.add(pos);
            double[] p=parseTriplet(pos,0);
            for(int y:new int[]{0,Math.max(1,h-1)})for(int x:new int[]{0,Math.max(1,w-1)}){
                double[] expected=add(sourcePos,add(scale(u,x),scale(v,y)));
                double[] reconstructed=add(p,add(scale(pe1,Double.parseDouble(spacing.get(1))*x),
                        scale(pe2,Double.parseDouble(spacing.get(0))*y)));
                if(norm(sub(expected,reconstructed))>0.001 || !Double.isFinite(norm(sub(expected,reconstructed))))
                    throw M8Fault.fail(PLAN,GEOMETRY_UNREPRESENTABLE);
            }
            slices.add(new M8Plan.Slice(k,k+1,String.format(java.util.Locale.ROOT,"image-%06d.dcm",k+1),uid(usedUids),pos));
        }
        if(d>1)validateStack(positionStrings,orient,sliceSpace.orElseThrow(),step,normal);
        return new M8Plan(request.jobId(),source,volume.voxels().scalarType(),w,h,d,a,
                volume.intensityTransform(),findings,request.metadata(),generatedAt,
                study,series,frame,orient,spacing,sliceSpace,slopeDS,interceptDS,sigma,slices);
    }
    private String uid(Set<String> seen) {
        UUID id=uidSource.get();
        if(id==null||id.version()!=4)throw M8Fault.fail(PLAN,UID_INVALID);
        String value="2.25."+new java.math.BigInteger(1,bytes(id)).toString();
        if (value.length()>64||!seen.add(value))throw M8Fault.fail(PLAN,UID_INVALID);
        return value;
    }
    private static byte[] bytes(UUID id){ java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(16);b.putLong(id.getMostSignificantBits());b.putLong(id.getLeastSignificantBits());return b.array(); }
    private static double intensityTolerance(double expected){return 1e-6+1e-9*Math.max(1,Math.abs(expected));}
    private static String ds(double value,M8Fault.Code code){
        if(!Double.isFinite(value))throw M8Fault.fail(PLAN,code);
        if(value==0)return "0";
        BigDecimal b=BigDecimal.valueOf(value);
        String best=null;double error=Double.POSITIVE_INFINITY;
        for(int p=15;p>=1;p--){
            BigDecimal rounded=b.round(new MathContext(p,RoundingMode.HALF_EVEN)).stripTrailingZeros();
            for(String candidate:new String[]{rounded.toPlainString(),rounded.toString().replace("E+","E")}){
                if(candidate.length()>16)continue;
                double v;
                try {v=Double.parseDouble(candidate);}catch(NumberFormatException e){continue;}
                if(!Double.isFinite(v))continue;
                double diff=Math.abs(v-value);
                if(best==null||diff<error ||(diff==error &&(candidate.length()<best.length()
                        || (candidate.length()==best.length() && candidate.compareTo(best)<0)))){
                    best=candidate;error=diff;
                }
            }
        }
        if(best==null)throw M8Fault.fail(PLAN,code);
        return best;
    }
    /** Same decimal-safe serialized stack predicate used by planner; verifier checks actual DICOM values. */
    private static void validateStack(List<List<String>> ipp,List<String> orient,String between,
                                     double[] sourceStep,double[] sourceNormal){
        double spacing=Math.abs(dot(sourceStep,sourceNormal));
        double serializedS=Double.parseDouble(between);
        double tau=1e-6*Math.min(serializedS,spacing);
        if(!good(serializedS)||!good(spacing)||!good(tau)
                ||Math.abs(serializedS-spacing)>1e-6*spacing)throw M8Fault.fail(PLAN,GEOMETRY_UNREPRESENTABLE);
        double[] nd=normalize(cross(parseTriplet(orient,0),parseTriplet(orient,3)));
        if(dot(nd,sourceNormal)<=0)throw M8Fault.fail(PLAN,GEOMETRY_UNREPRESENTABLE);
        int sign=dot(sourceStep,sourceNormal)>0?1:-1;
        for(int k=1;k<ipp.size();k++){
            double[] delta=new double[3];
            for(int i=0;i<3;i++){
                try{
                    delta[i]=new BigDecimal(ipp.get(k).get(i)).subtract(new BigDecimal(ipp.get(k-1).get(i))).doubleValue();
                }catch(NumberFormatException e){throw M8Fault.fail(PLAN,GEOMETRY_UNREPRESENTABLE);}
            }
            double q=dot(delta,nd);
            if(!Double.isFinite(q)||q==0||sign*q<=0||Math.abs(Math.abs(q)-serializedS)>tau
                    ||norm(sub(delta,scale(nd,q)))>tau)throw M8Fault.fail(PLAN,GEOMETRY_UNREPRESENTABLE);
            double[] left=parseTriplet(ipp.get(k-1),0),right=parseTriplet(ipp.get(k),0);
            double dq=dot(sub(right,left),nd);
            if(!Double.isFinite(dq)||dq==0||sign*dq<=0||Math.abs(Math.abs(dq)-serializedS)>tau)
                throw M8Fault.fail(PLAN,GEOMETRY_UNREPRESENTABLE);
        }
    }
    private static double[] column(AffineMatrix4 a,int index){return new double[]{a.get(0,index),a.get(1,index),a.get(2,index)};}
    private static double[] flip(double[] v){return new double[]{-v[0],-v[1],v[2]};}
    private static double[] parseTriplet(List<String> ss,int offset){return new double[]{Double.parseDouble(ss.get(offset)),Double.parseDouble(ss.get(offset+1)),Double.parseDouble(ss.get(offset+2))};}
    private static double[] add(double[] a,double[] b){return new double[]{a[0]+b[0],a[1]+b[1],a[2]+b[2]};}
    private static double[] sub(double[] a,double[] b){return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    private static double[] scale(double[] a,double v){return new double[]{a[0]*v,a[1]*v,a[2]*v};}
    private static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private static double norm(double[] a){return Math.hypot(Math.hypot(a[0],a[1]),a[2]);}
    private static double[] cross(double[] a,double[] b){return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    private static double[] normalize(double[] a){double n=norm(a);if(!good(n))throw M8Fault.fail(PLAN,GEOMETRY_UNREPRESENTABLE);return scale(a,1/n);}
    private static boolean good(double x){return Double.isFinite(x)&&x>0;}
}
