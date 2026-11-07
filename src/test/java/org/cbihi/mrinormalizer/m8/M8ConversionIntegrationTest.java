package org.cbihi.mrinormalizer.m8;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.zip.GZIPOutputStream;
import org.cbihi.mrinormalizer.domain.model.ScalarType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.cbihi.mrinormalizer.bootstrap.M8Command;

/** Generated synthetic NIfTI only; no medical payload or arbitrary external DICOM. */
final class M8ConversionIntegrationTest {
    @TempDir Path scratch;
    static byte[] nifti(boolean unsupported) {
        int width=2,height=2,depth=3;
        ByteBuffer b=ByteBuffer.allocate(352+width*height*depth*2).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0,348);b.putShort(40,(short)(unsupported?4:3));
        b.putShort(42,(short)width);b.putShort(44,(short)height);b.putShort(46,(short)depth);
        if(unsupported)b.putShort(48,(short)2);
        b.putShort(70,(short)4);b.putShort(72,(short)16);b.putFloat(108,352);
        b.putFloat(80,1);b.putFloat(84,2);b.putFloat(88,3);
        b.put(123,(byte)2);b.putShort(254,(short)1);
        b.putFloat(280,1);b.putFloat(300,2);b.putFloat(320,3);
        b.put(344,(byte)'n');b.put(345,(byte)'+');b.put(346,(byte)'1');
        for(int i=0;i<width*height*depth;i++)b.putShort(352+2*i,(short)i);
        return b.array();
    }
    /** Finite 4 scalar × 2 endian × 2 compression × 2 depth fixture matrix. */
    static byte[] supported(ScalarType scalar, ByteOrder order, boolean gzip, int depth)throws IOException{
        int w=3,h=2,n=w*h*depth;
        int bytes=(scalar==ScalarType.INT8||scalar==ScalarType.UINT8)?1:2;
        ByteBuffer b=ByteBuffer.allocate(352+n*bytes).order(order);
        b.putInt(0,348);b.putShort(40,(short)3);
        b.putShort(42,(short)w);b.putShort(44,(short)h);b.putShort(46,(short)depth);
        int datatype=switch(scalar){case UINT8->2;case INT8->256;case UINT16->512;case INT16->4;};
        b.putShort(70,(short)datatype);b.putShort(72,(short)(8*bytes));
        b.putFloat(76,1f);b.putFloat(80,1f);b.putFloat(84,1.5f);b.putFloat(88,2f);
        b.putFloat(108,352f);b.put(123,(byte)2);b.putShort(254,(short)1);
        b.putFloat(280,1);b.putFloat(300,1.5f);b.putFloat(320,2);
        b.put(344,(byte)'n');b.put(345,(byte)'+');b.put(346,(byte)'1');
        for(int i=0;i<n;i++){
            long value=switch(scalar){
                case UINT8 -> i%2==0?0:255;
                case INT8 -> i%2==0?-128:127;
                case UINT16 -> i%2==0?0:65535;
                case INT16 -> i%2==0?-32768:32767;
            };
            if(bytes==1)b.put(352+i,(byte)value);
            else b.putShort(352+i*2,(short)value);
        }
        byte[] plain=b.array();
        if(!gzip)return plain;
        ByteArrayOutputStream packed=new ByteArrayOutputStream();
        try(GZIPOutputStream out=new GZIPOutputStream(packed)){out.write(plain);}
        return packed.toByteArray();
    }
    @Test void m8_02_supportedThirtyTwoCaseMatrixMustCreateCompleteVerifiedSeries()throws Exception{
        int cases=0;
        for(ScalarType scalar:ScalarType.values())
            for(ByteOrder order:new ByteOrder[]{ByteOrder.LITTLE_ENDIAN,ByteOrder.BIG_ENDIAN})
                for(boolean gzip:new boolean[]{false,true})
                    for(int depth:new int[]{1,3}){
                        Path base=Files.createDirectory(scratch.resolve("case-"+(cases++)));
                        Path source=Files.createDirectory(base.resolve("input"));
                        Path output=Files.createDirectory(base.resolve("output"));
                        String inputName=gzip?"synthetic.nii.gz":"synthetic.nii";
                        byte[] bytes=supported(scalar,order,gzip,depth);
                        Files.write(source.resolve(inputName),bytes);
                        ByteArrayOutputStream stdout=new ByteArrayOutputStream();
                        ByteArrayOutputStream stderr=new ByteArrayOutputStream();
                        int code=M8Command.run(args(source,output,inputName),new PrintStream(stdout),new PrintStream(stderr));
                        assertEquals(0,code,scalar+" "+order+" gzip="+gzip+" depth="+depth
                            +" stdout="+stdout+" stderr="+stderr);
                        try(var children=Files.list(output)){
                            Path published=children.filter(x->x.getFileName().toString().startsWith("m8-"))
                                .findFirst().orElseThrow();
                            try(var entries=Files.list(published)){
                                assertEquals(depth+1,entries.count(),"Expected DICOM slices + completion marker");
                            }
                        }
                        assertArrayEquals(bytes,Files.readAllBytes(source.resolve(inputName)));
                    }
        assertEquals(32,cases);
    }
    static String[] args(Path source,Path output,String filename){return new String[]{
        "--source-root",source.toString(),"--input",filename,"--output-root",output.toString(),
        "--confirm-mr","--confirm-biped","--scanning-sequence","SE","--sequence-variant","NONE"};}
    @Test void m8_02_independentSyntheticConversionToThreeDcmInstances()throws Exception {
        Path source=Files.createDirectory(scratch.resolve("input"));
        Path output=Files.createDirectory(scratch.resolve("output"));
        Files.write(source.resolve("synthetic.nii"),nifti(false));
        ByteArrayOutputStream stream=new ByteArrayOutputStream();
        int code=M8Command.run(args(source,output,"synthetic.nii"),new PrintStream(stream),System.err);
        assertEquals(0,code,stream.toString());
        try(var children=Files.list(output)){
            Path completed=children.filter(p->p.getFileName().toString().startsWith("m8-")).findFirst().orElseThrow();
            try(var files=Files.list(completed)){
                assertEquals(4,files.count(),"Three DICOM slices and one verified completion marker");
            }
        }
    }
    @Test void m8_04_rejectsUnsupportedFourthDimensionWithoutPublishedOutput()throws Exception {
        Path source=Files.createDirectory(scratch.resolve("badinput"));
        Path output=Files.createDirectory(scratch.resolve("badoutput"));
        Files.write(source.resolve("unsupported.nii"),nifti(true));
        assertEquals(2,M8Command.run(args(source,output,"unsupported.nii"),
                new PrintStream(new ByteArrayOutputStream()),new PrintStream(new ByteArrayOutputStream())));
        try(var names=Files.list(output)){
            assertEquals(0,names.filter(p->p.getFileName().toString().startsWith("m8-")).count());
        }
    }
}
