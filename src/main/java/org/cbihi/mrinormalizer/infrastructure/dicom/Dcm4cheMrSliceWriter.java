package org.cbihi.mrinormalizer.infrastructure.dicom;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import org.cbihi.mrinormalizer.application.m8.*;
import org.cbihi.mrinormalizer.application.port.out.DicomMrSliceWriter;
import org.cbihi.mrinormalizer.domain.model.*;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;

/** Conventional MR Part 10, with explicitly declared Standard Extended rescale attributes. */
public final class Dcm4cheMrSliceWriter implements DicomMrSliceWriter {
    public Dcm4cheMrSliceWriter() {}
    @Override public void write(ImageVolume source,M8Plan plan,int sourceZ,OutputStream target){
        Objects.requireNonNull(source);Objects.requireNonNull(plan);Objects.requireNonNull(target);
        if(sourceZ<0||sourceZ>=plan.depth())throw new IllegalArgumentException("Slice out of range");
        M8Plan.Slice slice=plan.slices().get(sourceZ);
        Attributes ds=dataset(source,plan,slice);
        try {
            // dcm4che output may close its stream; prevent it from closing a borrowed workspace handle.
            OutputStream nonClosing=new FilterOutputStream(target) {
                @Override public void close() throws IOException { flush(); }
            };
            try(DicomOutputStream out=new DicomOutputStream(nonClosing,UID.ExplicitVRLittleEndian)) {
                out.writeDataset(ds.createFileMetaInformation(UID.ExplicitVRLittleEndian),ds);
            }
        }catch(IOException e){throw M8Fault.fail(M8Fault.Phase.WRITE,M8Fault.Code.WRITE_FAILED);}
    }
    private static void str(Attributes a,int tag,VR vr,String... val){a.setString(tag,vr,val);}
    private static void nul(Attributes a,int tag,VR vr){a.setNull(tag,vr);}
    private static Attributes dataset(ImageVolume image,M8Plan p,M8Plan.Slice slice){
        Attributes ds=new Attributes();
        // Patient, study, series, Frame of Reference, equipment and image modules.
        nul(ds,Tag.PatientName,VR.PN);nul(ds,Tag.PatientID,VR.LO);
        nul(ds,Tag.PatientBirthDate,VR.DA);nul(ds,Tag.PatientSex,VR.CS);
        str(ds,Tag.AnatomicalOrientationType,VR.CS,"BIPED");
        str(ds,Tag.StudyInstanceUID,VR.UI,p.studyUid());
        nul(ds,Tag.StudyDate,VR.DA);nul(ds,Tag.StudyTime,VR.TM);
        nul(ds,Tag.ReferringPhysicianName,VR.PN);nul(ds,Tag.StudyID,VR.SH);
        nul(ds,Tag.AccessionNumber,VR.SH);
        str(ds,Tag.Modality,VR.CS,"MR");str(ds,Tag.SeriesInstanceUID,VR.UI,p.seriesUid());
        str(ds,Tag.SeriesNumber,VR.IS,"1");
        nul(ds,Tag.PatientPosition,VR.CS);nul(ds,Tag.Laterality,VR.CS);
        str(ds,Tag.SeriesDescription,VR.LO,"PAI DERIVED NIFTI");
        str(ds,Tag.FrameOfReferenceUID,VR.UI,p.frameUid());
        nul(ds,Tag.PositionReferenceIndicator,VR.LO);
        str(ds,Tag.Manufacturer,VR.LO,"Project PAI");
        str(ds,Tag.ManufacturerModelName,VR.LO,"M8");
        str(ds,Tag.SoftwareVersions,VR.LO,"M8-MR-1");
        str(ds,Tag.ImageType,VR.CS,"DERIVED","SECONDARY","OTHER");
        str(ds,Tag.InstanceNumber,VR.IS,Integer.toString(slice.instanceNumber()));
        String date=DateTimeFormatter.ofPattern("uuuuMMdd").withZone(ZoneOffset.UTC).format(p.generatedAt());
        String time=DateTimeFormatter.ofPattern("HHmmss.SSSSSS").withZone(ZoneOffset.UTC).format(p.generatedAt());
        str(ds,Tag.ContentDate,VR.DA,date);str(ds,Tag.ContentTime,VR.TM,time);
        str(ds,Tag.InstanceCreationDate,VR.DA,date);str(ds,Tag.InstanceCreationTime,VR.TM,time);
        str(ds,Tag.TimezoneOffsetFromUTC,VR.SH,"+0000");
        str(ds,Tag.DerivationDescription,VR.ST,"NIfTI to DICOM; source metadata not restored");
        str(ds,Tag.SOPClassUID,VR.UI,UID.MRImageStorage);
        str(ds,Tag.SOPInstanceUID,VR.UI,slice.sopUid());
        str(ds,Tag.ImageOrientationPatient,VR.DS,p.orientationDs().toArray(String[]::new));
        str(ds,Tag.ImagePositionPatient,VR.DS,slice.positionDs().toArray(String[]::new));
        str(ds,Tag.PixelSpacing,VR.DS,p.pixelSpacingDs().toArray(String[]::new));
        if(p.sliceSpacingDs().isPresent())str(ds,Tag.SpacingBetweenSlices,VR.DS,p.sliceSpacingDs().orElseThrow());
        nul(ds,Tag.SliceThickness,VR.DS);
        str(ds,Tag.ScanningSequence,VR.CS,p.metadata().scanningSequence().name());
        str(ds,Tag.SequenceVariant,VR.CS,p.metadata().sequenceVariant().name());
        nul(ds,Tag.ScanOptions,VR.CS);nul(ds,Tag.MRAcquisitionType,VR.CS);
        nul(ds,Tag.RepetitionTime,VR.DS);nul(ds,Tag.EchoTime,VR.DS);
        nul(ds,Tag.EchoTrainLength,VR.IS);nul(ds,Tag.TriggerTime,VR.DS);
        if(p.metadata().scanningSequence()==M8Metadata.ScanningSequence.IR)nul(ds,Tag.InversionTime,VR.DS);
        nul(ds,Tag.ContrastBolusAgent,VR.LO);
        ds.setInt(Tag.Rows,VR.US,p.height());ds.setInt(Tag.Columns,VR.US,p.width());
        ds.setInt(Tag.SamplesPerPixel,VR.US,1);str(ds,Tag.PhotometricInterpretation,VR.CS,"MONOCHROME2");
        ds.setInt(Tag.BitsAllocated,VR.US,16);ds.setInt(Tag.BitsStored,VR.US,16);
        ds.setInt(Tag.HighBit,VR.US,15);
        boolean signed=p.sourceScalar()==ScalarType.INT8||p.sourceScalar()==ScalarType.INT16;
        ds.setInt(Tag.PixelRepresentation,VR.US,signed?1:0);
        str(ds,Tag.RescaleSlope,VR.DS,p.slopeDs());
        str(ds,Tag.RescaleIntercept,VR.DS,p.interceptDs());
        str(ds,Tag.RescaleType,VR.LO,"US");
        int count=Math.multiplyExact(p.width(),p.height());
        ByteBuffer pixels=ByteBuffer.allocate(Math.multiplyExact(count,2)).order(ByteOrder.LITTLE_ENDIAN);
        for(int y=0;y<p.height();y++)for(int x=0;x<p.width();x++){
            long v=image.voxels().rawValueAt(x,y,slice.sourceZ());
            if(v<Short.MIN_VALUE||v>65535L||(signed&&v>Short.MAX_VALUE)||(!signed&&v<0))
                throw M8Fault.fail(M8Fault.Phase.WRITE,M8Fault.Code.INVALID_INPUT);
            pixels.putShort((short)v);
        }
        ds.setBytes(Tag.PixelData,VR.OW,pixels.array());
        return ds;
    }
}
