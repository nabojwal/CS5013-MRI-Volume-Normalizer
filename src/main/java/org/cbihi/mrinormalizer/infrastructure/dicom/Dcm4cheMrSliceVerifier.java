package org.cbihi.mrinormalizer.infrastructure.dicom;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;
import java.util.Set;
import java.util.HashSet;
import org.cbihi.mrinormalizer.application.m8.*;
import org.cbihi.mrinormalizer.application.port.out.DicomMrSliceVerifier;
import org.cbihi.mrinormalizer.domain.model.*;
import org.dcm4che3.data.*;
import org.dcm4che3.io.DicomInputStream;

/** Independent Part-10 semantic oracle; reads bytes, never calls the writer or its builders. */
public final class Dcm4cheMrSliceVerifier implements DicomMrSliceVerifier {
    private final M8Limits limits;
    private UUID job;
    private int expectedZ;
    private List<String> previousIpp;
    private List<String> serializedOrientation;
    private String serializedSpacing;
    private final java.util.List<List<String>> allPositions=new java.util.ArrayList<>();

    public Dcm4cheMrSliceVerifier(M8Limits limits){this.limits=java.util.Objects.requireNonNull(limits);}

    @Override public synchronized Check verify(ImageVolume source,M8Plan plan,int sourceZ,InputStream input){
        if(source==null||plan==null||input==null||sourceZ!=expectedZ
                || (job!=null&&!job.equals(plan.jobId())))throw fail();
        if(!source.geometry().voxelToWorldAffine().equals(plan.sourceRasAffine())
            ||source.geometry().width()!=plan.width()||source.geometry().height()!=plan.height()
            ||source.geometry().depth()!=plan.depth()
            ||!source.intensityTransform().equals(plan.sourceIntensity()))throw fail();
        if(job==null)job=plan.jobId();
        long maxSlice=(long)plan.width()*plan.height()*2+16384L;
        if(maxSlice>limits.maxOutputBytes()||maxSlice>Integer.MAX_VALUE-16)throw fail();
        try {
            // Fail before handing bytes to dcm4che: its readDataset() may allocate
            // according to an attacker-controlled declared Pixel Data length.
            // Streaming ingestion is capped to the one planned slice plus bounded
            // metadata; the explicit-VR preflight rejects oversized/undefined VLs.
            byte[] wire=readBounded(input,maxSlice);
            preflightExplicitVr(wire,Math.multiplyExact((long)plan.width()*plan.height(),2L));
            try(DicomInputStream dis=new DicomInputStream(new ByteArrayInputStream(wire))) {
                Attributes meta=dis.readFileMetaInformation();
                if(meta==null || !UID.ExplicitVRLittleEndian.equals(dis.getTransferSyntax()))throw fail();
                Attributes ds=dis.readDataset();
                if(ds==null)throw fail();
                verifyAllowlistAndVR(ds);
                if(!UID.MRImageStorage.equals(ds.getString(Tag.SOPClassUID))
                        || !UID.MRImageStorage.equals(meta.getString(Tag.MediaStorageSOPClassUID))
                        || !UID.ExplicitVRLittleEndian.equals(meta.getString(Tag.TransferSyntaxUID))
                        || !plan.slices().get(sourceZ).sopUid().equals(ds.getString(Tag.SOPInstanceUID))
                        || !plan.slices().get(sourceZ).sopUid().equals(meta.getString(Tag.MediaStorageSOPInstanceUID))
                        || !plan.studyUid().equals(ds.getString(Tag.StudyInstanceUID))
                        || !plan.seriesUid().equals(ds.getString(Tag.SeriesInstanceUID))
                        || !plan.frameUid().equals(ds.getString(Tag.FrameOfReferenceUID))
                        || !"MR".equals(ds.getString(Tag.Modality)))throw fail();
                if(ds.getInt(Tag.Rows,-1)!=plan.height() || ds.getInt(Tag.Columns,-1)!=plan.width()
                        ||ds.getInt(Tag.SamplesPerPixel,-1)!=1||ds.getInt(Tag.BitsAllocated,-1)!=16
                        ||ds.getInt(Tag.BitsStored,-1)!=16||ds.getInt(Tag.HighBit,-1)!=15
                        ||!"MONOCHROME2".equals(ds.getString(Tag.PhotometricInterpretation))
                        ||ds.contains(Tag.NumberOfFrames)||ds.contains(Tag.PlanarConfiguration)
                        ||ds.getInt(Tag.InstanceNumber,-1)!=sourceZ+1)throw fail();
                int signed=(plan.sourceScalar()==ScalarType.INT8||plan.sourceScalar()==ScalarType.INT16)?1:0;
                if(ds.getInt(Tag.PixelRepresentation,-1)!=signed ||ds.getVR(Tag.PixelData)!=VR.OW)throw fail();
                if(!"BIPED".equals(ds.getString(Tag.AnatomicalOrientationType))
                        ||!"DERIVED".equals(ds.getStrings(Tag.ImageType)[0])
                        ||!Arrays.equals(ds.getStrings(Tag.ImageType),new String[]{"DERIVED","SECONDARY","OTHER"})
                        ||!plan.metadata().scanningSequence().name().equals(ds.getString(Tag.ScanningSequence))
                        ||!plan.metadata().sequenceVariant().name().equals(ds.getString(Tag.SequenceVariant))
                        ||!"US".equals(ds.getString(Tag.RescaleType)))throw fail();
                for(int tag:new int[]{Tag.PatientName,Tag.PatientID,Tag.PatientBirthDate,Tag.PatientSex,
                        Tag.StudyDate,Tag.StudyTime,Tag.StudyID,Tag.AccessionNumber,
                        Tag.ReferringPhysicianName,Tag.PatientPosition,Tag.Laterality,Tag.PositionReferenceIndicator,
                        Tag.ScanOptions,Tag.MRAcquisitionType,Tag.RepetitionTime,Tag.EchoTime,
                        Tag.EchoTrainLength,Tag.TriggerTime,Tag.ContrastBolusAgent,Tag.SliceThickness}) {
                    if(!ds.contains(tag)||ds.containsValue(tag))throw fail();
                }
                if(plan.metadata().scanningSequence()==M8Metadata.ScanningSequence.IR
                        &&(!ds.contains(Tag.InversionTime)||ds.containsValue(Tag.InversionTime)))throw fail();
                if(ds.contains(Tag.LossyImageCompression)||ds.contains(Tag.BurnedInAnnotation)
                        ||ds.contains(Tag.RecognizableVisualFeatures)||ds.contains(Tag.SpecificCharacterSet))throw fail();
                String[] orient=ds.getStrings(Tag.ImageOrientationPatient),ipp=ds.getStrings(Tag.ImagePositionPatient);
                String[] spacing=ds.getStrings(Tag.PixelSpacing);
                if(orient==null||orient.length!=6||ipp==null||ipp.length!=3||spacing==null||spacing.length!=2
                        ||!Arrays.equals(orient,plan.orientationDs().toArray(String[]::new))
                        ||!Arrays.equals(ipp,plan.slices().get(sourceZ).positionDs().toArray(String[]::new))
                        ||!Arrays.equals(spacing,plan.pixelSpacingDs().toArray(String[]::new)))throw fail();
                if(plan.depth()>1 && !plan.sliceSpacingDs().orElseThrow().equals(ds.getString(Tag.SpacingBetweenSlices)))throw fail();
                if(plan.depth()==1 && ds.contains(Tag.SpacingBetweenSlices))throw fail();
                double rs=Double.parseDouble(spacing[0]),cs=Double.parseDouble(spacing[1]);
                double[] e1=new double[3],e2=new double[3],p=new double[3];
                for(int i=0;i<3;i++){e1[i]=Double.parseDouble(orient[i]);e2[i]=Double.parseDouble(orient[i+3]);p[i]=Double.parseDouble(ipp[i]);}
                if(!(rs>0 && cs>0) || Math.abs(norm(e1)-1)>1e-6
                        ||Math.abs(norm(e2)-1)>1e-6
                        ||Math.abs(dot(e1,e2))>1e-6)throw fail();
                AffineMatrix4 a=plan.sourceRasAffine();
                double maxErr=0;
                for(int y:new int[]{0,Math.max(1,plan.height()-1)})for(int x:new int[]{0,Math.max(1,plan.width()-1)}){
                    double[] expected=flip(a.transformPoint(x,y,sourceZ));
                    double[] actual=add(p,add(scale(e1,cs*x),
                            scale(e2,rs*y)));
                    double err=norm(sub(expected,actual));
                    if(!Double.isFinite(err)||err>0.001)throw fail();maxErr=Math.max(maxErr,err);
                }
                List<String> now=List.of(ipp);
                allPositions.add(now);
                if(serializedOrientation==null)serializedOrientation=List.of(orient);
                if(plan.depth()>1){
                    String bs=ds.getString(Tag.SpacingBetweenSlices);
                    if(serializedSpacing==null)serializedSpacing=bs;
                    if(!serializedSpacing.equals(bs)||!serializedOrientation.equals(List.of(orient)))throw fail();
                    double[] n=normalize(cross(e1,e2));
                    if(sourceZ>0){
                        // Verify the source stack's ordered actually parsed positions, not just plan values.
                        double[] step=flip(column(a,2));
                        double[] u=flip(column(a,0));
                        double[] v=flip(column(a,1));
                        double[] sn=normalize(cross(u,v));
                        verifyAdjacent(previousIpp,now,List.of(orient),bs,step,sn);
                    }
                }
                previousIpp=now;
                double rawSlope=Double.parseDouble(ds.getString(Tag.RescaleSlope));
                double rawIntercept=Double.parseDouble(ds.getString(Tag.RescaleIntercept));
                if(!ds.getString(Tag.RescaleSlope).equals(plan.slopeDs())
                        ||!ds.getString(Tag.RescaleIntercept).equals(plan.interceptDs())||rawSlope==0)throw fail();
                byte[] pixel=ds.getBytes(Tag.PixelData);
                long count=(long)plan.width()*plan.height();
                if(pixel==null||pixel.length!=count*2)throw fail();
                double maxIntensity=0;
                for(int y=0;y<plan.height();y++)for(int x=0;x<plan.width();x++){
                    int i=(y*plan.width()+x)*2;
                    int unsigned=(pixel[i]&255)|((pixel[i+1]&255)<<8);
                    long read=signed==1?(short)unsigned:unsigned;
                    long expected=source.voxels().rawValueAt(x,y,sourceZ);
                    if(read!=expected)throw fail();
                    double actual=Math.fma(rawSlope,read,rawIntercept);
                    double original=Math.fma(plan.sourceIntensity().slope(),expected,plan.sourceIntensity().intercept());
                    double error=Math.abs(actual-original);
                    if(!Double.isFinite(error)||error>intensityTolerance(original))throw fail();
                    maxIntensity=Math.max(maxIntensity,error);
                }
                expectedZ++;
                return new Check(count,4,maxErr,maxIntensity);
            }
        }catch(IOException|RuntimeException ex){
            if(ex instanceof M8Fault.Failure f)throw f;
            throw fail();
        }
    }
    /** Independent parse-back numeric implementation; never calls planner helpers. */
    private static double[] column(AffineMatrix4 a,int k){return new double[]{a.get(0,k),a.get(1,k),a.get(2,k)};}
    private static double[] flip(double[] a){return new double[]{-a[0],-a[1],a[2]};}
    private static double[] add(double[] a,double[] b){return new double[]{a[0]+b[0],a[1]+b[1],a[2]+b[2]};}
    private static double[] sub(double[] a,double[] b){return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    private static double[] scale(double[] a,double x){return new double[]{a[0]*x,a[1]*x,a[2]*x};}
    private static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private static double norm(double[] a){return Math.hypot(Math.hypot(a[0],a[1]),a[2]);}
    private static double[] cross(double[] a,double[] b){return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    private static double[] normalize(double[] a){double d=norm(a);if(!(d>0)||!Double.isFinite(d))throw fail();return scale(a,1/d);}
    private static double intensityTolerance(double d){return 1e-6+1e-9*Math.max(1,Math.abs(d));}
    private static void verifyAdjacent(List<String> previous,List<String> current,List<String> dsOrient,
             String dsSpacing,double[] sourceStep,double[] sourceNormal){
        double spacing=Math.abs(dot(sourceStep,sourceNormal));
        double declared=Double.parseDouble(dsSpacing);
        double tau=1e-6*Math.min(spacing,declared);
        if(!(tau>0)||!Double.isFinite(tau)||Math.abs(declared-spacing)>1e-6*spacing)throw fail();
        double[] d1=new double[3],d2=new double[3];
        for(int i=0;i<3;i++){
            d1[i]=Double.parseDouble(dsOrient.get(i));
            d2[i]=Double.parseDouble(dsOrient.get(i+3));
        }
        double[] n=normalize(cross(d1,d2));
        if(dot(n,sourceNormal)<=0)throw fail();
        double sign=dot(sourceStep,sourceNormal);
        double[] precise=new double[3],rounded=new double[3];
        for(int i=0;i<3;i++){
            precise[i]=new BigDecimal(current.get(i)).subtract(new BigDecimal(previous.get(i))).doubleValue();
            rounded[i]=Double.parseDouble(current.get(i))-Double.parseDouble(previous.get(i));
        }
        for(double[] delta:new double[][]{precise,rounded}){
            double q=dot(delta,n);
            if(!Double.isFinite(q)||q==0||q*sign<=0||Math.abs(Math.abs(q)-declared)>tau
                    ||norm(sub(delta,scale(n,q)))>tau)throw fail();
        }
    }
    /** Borrowed-stream bounded read: a size refusal precedes any unbounded input allocation. */
    private static byte[] readBounded(InputStream source,long limit)throws IOException {
        if(limit<132||limit>Integer.MAX_VALUE-16)throw new IOException("DICOM limit");
        ByteArrayOutputStream output=new ByteArrayOutputStream(8192);
        byte[] block=new byte[8192];long used=0;int n;
        while((n=source.read(block))!=-1){
            if(n==0)continue;
            if(n>limit-used)throw new IOException("DICOM input exceeds allowed slice bound");
            output.write(block,0,n);used+=n;
        }
        return output.toByteArray();
    }
    /**
     * Independently inspect Part 10 explicit VR LE length headers before invoking
     * the dcm4che dataset parser. Undefined-length/encapsulated elements, duplicate
     * Pixel Data, truncated lengths and oversized declared Pixel Data are refused.
     * This is a bounded length guard, not an alternative DICOM semantic parser.
     */
    private static void preflightExplicitVr(byte[] wire,long expectedPixelBytes) {
        if(wire.length<144||wire[128]!='D'||wire[129]!='I'||wire[130]!='C'||wire[131]!='M')throw fail();
        ByteBuffer b=ByteBuffer.wrap(wire).order(ByteOrder.LITTLE_ENDIAN);
        int offset=132;int pixelElements=0;
        while(offset<wire.length){
            if(wire.length-offset<8)throw fail();
            int group=Short.toUnsignedInt(b.getShort(offset));
            int element=Short.toUnsignedInt(b.getShort(offset+2));
            int tag=(group<<16)|element;
            char a=(char)(wire[offset+4]&255), c=(char)(wire[offset+5]&255);
            if(a<'A'||a>'Z'||c<'A'||c>'Z')throw fail();
            String vr=""+a+c;
            boolean wide=switch(vr){
                case "OB","OD","OF","OL","OV","OW","SQ","UC","UN","UR","UT" -> true;
                default -> false;
            };
            long vl;
            int header=wide?12:8;
            if(wire.length-offset<header)throw fail();
            if(wide){
                if(wire[offset+6]!=0||wire[offset+7]!=0)throw fail();
                vl=Integer.toUnsignedLong(b.getInt(offset+8));
            }else vl=Short.toUnsignedLong(b.getShort(offset+6));
            if(vl==0xffffffffL || vl>wire.length-(long)offset-header)throw fail();
            if(tag==Tag.PixelData){
                pixelElements++;
                if(!"OW".equals(vr)||vl!=expectedPixelBytes)throw fail();
            }
            offset=Math.toIntExact(offset+header+vl);
        }
        if(offset!=wire.length||pixelElements!=1)throw fail();
    }
    private static final java.util.Map<Integer,VR> PROFILE_VR = buildProfile();
    private static java.util.Map<Integer,VR> buildProfile(){
        java.util.Map<Integer,VR> m=new java.util.HashMap<>();
        for(int tag:new int[]{Tag.PatientName,Tag.ReferringPhysicianName})m.put(tag,VR.PN);
        for(int tag:new int[]{Tag.PatientID,Tag.PositionReferenceIndicator,Tag.Manufacturer,
                    Tag.SeriesDescription,Tag.ManufacturerModelName,Tag.SoftwareVersions,
                    Tag.ContrastBolusAgent,Tag.RescaleType})m.put(tag,VR.LO);
        for(int tag:new int[]{Tag.PatientBirthDate,Tag.StudyDate,Tag.ContentDate,Tag.InstanceCreationDate})m.put(tag,VR.DA);
        for(int tag:new int[]{Tag.StudyTime,Tag.ContentTime,Tag.InstanceCreationTime})m.put(tag,VR.TM);
        for(int tag:new int[]{Tag.PatientSex,Tag.AnatomicalOrientationType,Tag.Modality,
                    Tag.PatientPosition,Tag.Laterality,Tag.ImageType,Tag.PhotometricInterpretation,
                    Tag.ScanningSequence,Tag.SequenceVariant,Tag.ScanOptions,Tag.MRAcquisitionType})m.put(tag,VR.CS);
        for(int tag:new int[]{Tag.SOPClassUID,Tag.SOPInstanceUID,Tag.StudyInstanceUID,
                    Tag.SeriesInstanceUID,Tag.FrameOfReferenceUID})m.put(tag,VR.UI);
        for(int tag:new int[]{Tag.StudyID,Tag.AccessionNumber,Tag.TimezoneOffsetFromUTC})m.put(tag,VR.SH);
        for(int tag:new int[]{Tag.SeriesNumber,Tag.InstanceNumber,Tag.EchoTrainLength})m.put(tag,VR.IS);
        for(int tag:new int[]{Tag.ImageOrientationPatient,Tag.ImagePositionPatient,Tag.PixelSpacing,
                    Tag.SpacingBetweenSlices,Tag.SliceThickness,Tag.RepetitionTime,Tag.EchoTime,
                    Tag.TriggerTime,Tag.InversionTime,Tag.RescaleSlope,Tag.RescaleIntercept})m.put(tag,VR.DS);
        for(int tag:new int[]{Tag.Rows,Tag.Columns,Tag.SamplesPerPixel,Tag.BitsAllocated,
                    Tag.BitsStored,Tag.HighBit,Tag.PixelRepresentation})m.put(tag,VR.US);
        m.put(Tag.DerivationDescription,VR.ST);m.put(Tag.PixelData,VR.OW);
        return java.util.Map.copyOf(m);
    }
    private static void requireVM(Attributes ds,int tag,int expected){
        String[] vals=ds.getStrings(tag);
        if(vals==null||vals.length!=expected)throw fail();
    }
    private static void verifyAllowlistAndVR(Attributes ds){
        for(int tag:ds.tags()){
            VR expected=PROFILE_VR.get(tag);
            if(expected==null||ds.getVR(tag)!=expected)throw fail();
        }
        for(int tag:PROFILE_VR.keySet()){
            if(tag==Tag.InversionTime||tag==Tag.SpacingBetweenSlices)continue;
            if(!ds.contains(tag))throw fail();
        }
        requireVM(ds,Tag.ImageType,3);
        requireVM(ds,Tag.ImageOrientationPatient,6);
        requireVM(ds,Tag.ImagePositionPatient,3);
        requireVM(ds,Tag.PixelSpacing,2);
        for(int tag:new int[]{Tag.SOPClassUID,Tag.SOPInstanceUID,Tag.StudyInstanceUID,
                Tag.SeriesInstanceUID,Tag.FrameOfReferenceUID,Tag.ScanningSequence,
                Tag.SequenceVariant,Tag.RescaleSlope,Tag.RescaleIntercept,Tag.RescaleType})requireVM(ds,tag,1);
    }
    private static M8Fault.Failure fail(){return M8Fault.fail(M8Fault.Phase.VERIFY,M8Fault.Code.VERIFICATION_FAILED);}
}
