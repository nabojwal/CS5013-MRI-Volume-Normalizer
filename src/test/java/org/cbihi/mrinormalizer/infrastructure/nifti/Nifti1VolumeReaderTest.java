package org.cbihi.mrinormalizer.infrastructure.nifti;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPOutputStream;
import org.cbihi.mrinormalizer.application.dataset.nifti.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;
import org.cbihi.mrinormalizer.domain.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class Nifti1VolumeReaderTest {
    @TempDir Path root;
    // Independent binary fixtures. No production header, writer, quaternion or tolerance helper.
    static NiftiReadLimits limits() { return new NiftiReadLimits(1_000_000, 1_000_000, 10_000, 4096, 2_000_000, 4096); }
    static ByteBuffer fixture(int type, ByteOrder order, int x, int y, int z) {
        int bytes = type == 4 || type == 512 ? 2 : 1;
        ByteBuffer b = ByteBuffer.allocate(352 + x*y*z*bytes).order(order);
        b.putInt(0,348); b.putShort(40,(short)3);
        b.putShort(42,(short)x); b.putShort(44,(short)y); b.putShort(46,(short)z);
        b.putShort(70,(short)type); b.putShort(72,(short)(8*bytes));
        b.putFloat(76,1); b.putFloat(80,1); b.putFloat(84,2); b.putFloat(88,3);
        b.putFloat(108,352); b.put(123,(byte)2); b.putShort(254,(short)1);
        b.putFloat(280,1); b.putFloat(300,2); b.putFloat(320,3);
        b.put(344,(byte)'n'); b.put(345,(byte)'+'); b.put(346,(byte)'1');
        return b;
    }
    static byte[] gzip(byte[] bytes) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var gz = new GZIPOutputStream(out)) { gz.write(bytes); }
        return out.toByteArray();
    }
    static RelativePath source(String name) { return new RelativePath(RelativePath.Root.SOURCE,name); }
    static NiftiReadResult read(Path root, byte[] bytes, NiftiReadLimits limits) throws IOException {
        Files.write(root.resolve("synthetic.bin"), bytes);
        return new Nifti1VolumeReader(root,limits).read(source("synthetic.bin"));
    }
    NiftiReadResult read(ByteBuffer b) throws IOException { return read(root,b.array(),limits()); }
    static void failure(NiftiReadFailure f, NiftiReadResult result) {
        assertTrue(result.volume().isEmpty()); assertTrue(result.failures().contains(f),result.failures().toString());
    }
    static Stream<org.junit.jupiter.params.provider.Arguments> scalarCases() {
        return Stream.of(2,256,512,4).flatMap(t -> Stream.of(ByteOrder.LITTLE_ENDIAN,ByteOrder.BIG_ENDIAN)
            .flatMap(o -> Stream.of(false,true).map(g -> org.junit.jupiter.params.provider.Arguments.of(t,o,g))));
    }
    @ParameterizedTest @MethodSource("scalarCases")
    void T04_allTypesOrdersContainers(int type, ByteOrder order, boolean compressed) throws Exception {
        var b=fixture(type,order,3,1,1);
        long min=type==256?-128:type==4?-32768:0, max=type==2?255:type==256?127:type==512?65535:32767;
        for(int i=0;i<3;i++) { long v=i==0?min:i==1?0:max;
            if(type==4||type==512)b.putShort(352+2*i,(short)v);else b.put(352+i,(byte)v); }
        var r=read(root,compressed?gzip(b.array()):b.array(),limits());
        assertTrue(r.failures().isEmpty(),r.failures().toString());
        var v=r.volume().orElseThrow().voxels(); assertInstanceOf(ImmutableVoxelData.class,v);
        assertEquals(min,v.rawValueAt(0,0,0)); assertEquals(0,v.rawValueAt(1,0,0)); assertEquals(max,v.rawValueAt(2,0,0));
        assertEquals(switch(type){case 2->ScalarType.UINT8;case 256->ScalarType.INT8;case 512->ScalarType.UINT16;default->ScalarType.INT16;},v.scalarType());
    }
    @Test void T05_asymmetricIndexing() throws Exception {
        var b=fixture(2,ByteOrder.LITTLE_ENDIAN,2,3,4);for(int i=0;i<24;i++)b.put(352+i,(byte)(i+1));
        var v=read(b).volume().orElseThrow();
        for(int z=0;z<4;z++)for(int y=0;y<3;y++)for(int x=0;x<2;x++)assertEquals(1+x+2*y+6*z,v.voxels().rawValueAt(x,y,z));
    }
    @Test void T07_dimensions() throws Exception {
        for(int offset:new int[]{40,42,44,46,48,50,52,54})for(int value:new int[]{-1,0,2,4}) {
            if(offset==40&&value==3 || offset>=48&&(value==0||value==1) || offset>40&&offset<48&&value>0)continue;
            var b=fixture(2,ByteOrder.LITTLE_ENDIAN,1,1,1);b.putShort(offset,(short)value);
            failure(NiftiReadFailure.UNSUPPORTED_DIMENSIONALITY,read(b));
        }
    }
    @Test void T08_variantClassification() throws Exception {
        var b=fixture(2,ByteOrder.LITTLE_ENDIAN,1,1,1);b.put(345,(byte)'i');failure(NiftiReadFailure.UNSUPPORTED_VARIANT,read(b));
        b=fixture(2,ByteOrder.LITTLE_ENDIAN,1,1,1);b.putInt(0,540);failure(NiftiReadFailure.UNSUPPORTED_VARIANT,read(b));
        b=fixture(2,ByteOrder.LITTLE_ENDIAN,1,1,1);b.put(344,(byte)'x');failure(NiftiReadFailure.INVALID_HEADER,read(b));
        b=fixture(2,ByteOrder.LITTLE_ENDIAN,1,1,1);b.putInt(0,347);failure(NiftiReadFailure.INVALID_HEADER,read(b));
        b=fixture(2,ByteOrder.LITTLE_ENDIAN,1,1,1);for(int o=344;o<348;o++)b.put(o,(byte)0);failure(NiftiReadFailure.UNSUPPORTED_VARIANT,read(b));
    }
    @Test void T09_datatypesAndBitpix() throws Exception {
        for(int type:new int[]{8,16,32,64,128,768,1024,0}) {var b=fixture(2,ByteOrder.LITTLE_ENDIAN,1,1,1);b.putShort(70,(short)type);failure(NiftiReadFailure.UNSUPPORTED_DATATYPE,read(b));}
        for(int type:new int[]{2,256,4,512})for(int bits:new int[]{0,8,16,32}) {
            if(bits==(type==2||type==256?8:16))continue;
            var b=fixture(type,ByteOrder.LITTLE_ENDIAN,1,1,1);b.putShort(72,(short)bits);failure(NiftiReadFailure.INVALID_HEADER,read(b));
        }
    }
    @Test void T10_intentAndNames() throws Exception {
        var b=fixture(2,ByteOrder.LITTLE_ENDIAN,1,1,1); b.putShort(68,(short)1);failure(NiftiReadFailure.UNSUPPORTED_INTENT,read(b));
        b.putShort(68,(short)0);
        for(String name:List.of("noextension","misleading.nii.gz")) { Files.write(root.resolve(name),b.array());assertTrue(new Nifti1VolumeReader(root,limits()).read(source(name)).failures().isEmpty()); }
    }
    @Test void T24_scaling() throws Exception {
        for(float slope:new float[]{0,-0f,-2,3,Float.NaN,Float.POSITIVE_INFINITY})for(float intercept:new float[]{7,Float.NaN}) {
            var b=fixture(2,ByteOrder.LITTLE_ENDIAN,1,1,1); b.putFloat(112,slope);b.putFloat(116,intercept);b.put(352,(byte)99);
            var r=read(b);
            if(!Float.isFinite(slope)||slope!=0&&!Float.isFinite(intercept)) failure(NiftiReadFailure.INVALID_SCALING,r);
            else {var v=r.volume().orElseThrow();assertEquals(99,v.voxels().rawValueAt(0,0,0));assertEquals(slope!=0,v.intensityTransform().declared());assertEquals(slope==0?1:slope,v.intensityTransform().slope());assertEquals(slope==0?0:intercept,v.intensityTransform().intercept());}
        }
    }
}
