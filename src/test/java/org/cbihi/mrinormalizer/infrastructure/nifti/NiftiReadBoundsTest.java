package org.cbihi.mrinormalizer.infrastructure.nifti;

import static org.cbihi.mrinormalizer.infrastructure.nifti.Nifti1VolumeReaderTest.*;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.cbihi.mrinormalizer.application.dataset.nifti.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NiftiReadBoundsTest {
    @TempDir Path root;
    ByteBuffer basic() {return fixture(2,ByteOrder.LITTLE_ENDIAN,1,1,1);}
    NiftiReadResult run(byte[] b) throws Exception {return read(root,b,limits());}
    static byte[] prefix(ByteBuffer b,int offset) {byte[] a=Arrays.copyOf(b.array(),offset+1);ByteBuffer.wrap(a).order(b.order()).putFloat(108,offset);a[offset]=42;return a;}
    @Test void T25_offsets() throws Exception {
        for(float off:new float[]{352,353,351,352.5f,Float.NaN,Float.POSITIVE_INFINITY,4097}) {var b=basic();b.putFloat(108,off);var r=run(off==353?prefix(b,353):b.array());
            if(off==352||off==353)assertTrue(r.volume().isPresent());else failure(off==4097?NiftiReadFailure.RESOURCE_LIMIT:NiftiReadFailure.INVALID_HEADER,r);}
    }
    @Test void T26_paddingAndExtensions() throws Exception {
        byte[] a=prefix(basic(),384);Arrays.fill(a,352,384,(byte)127);assertTrue(run(a).volume().isPresent());
        a=prefix(basic(),384);a[348]=1;var b=ByteBuffer.wrap(a).order(ByteOrder.LITTLE_ENDIAN);b.putInt(352,16);b.putInt(356,999);b.putInt(368,16);b.putInt(372,0);
        byte[] sentinel="PRIVATE!".getBytes(java.nio.charset.StandardCharsets.US_ASCII);System.arraycopy(sentinel,0,a,360,8);
        var r=run(a);assertTrue(r.volume().isPresent());assertTrue(r.findings().contains(NiftiReadFinding.EXTENSIONS_NOT_INTERPRETED));assertFalse(r.failures().toString().contains("PRIVATE"));
    }
    @Test void T27_malformedExtensions() throws Exception {
        for(int size:new int[]{0,8,15,17,48}) {byte[] a=prefix(basic(),384);a[348]=1;ByteBuffer.wrap(a).order(ByteOrder.LITTLE_ENDIAN).putInt(352,size);failure(NiftiReadFailure.INVALID_EXTENSION,run(a));}
        byte[] a=prefix(basic(),368);a[348]=1;var b=ByteBuffer.wrap(a).order(ByteOrder.LITTLE_ENDIAN);b.putInt(352,16);b.putInt(356,-1);failure(NiftiReadFailure.INVALID_EXTENSION,run(a));
        a=prefix(basic(),384);a[348]=1;ByteBuffer.wrap(a).order(ByteOrder.LITTLE_ENDIAN).putInt(352,16);failure(NiftiReadFailure.INVALID_EXTENSION,run(a));
        for(int i=349;i<352;i++) {a=basic().array();a[i]=1;failure(NiftiReadFailure.INVALID_EXTENSION,run(a));}
    }
    @Test void T28_hugeDimensionsBeforeAllocation() throws Exception {
        var b=basic();for(int o:new int[]{42,44,46})b.putShort(o,Short.MAX_VALUE);
        failure(NiftiReadFailure.RESOURCE_LIMIT,run(b.array()));
        var l=new NiftiReadLimits(Long.MAX_VALUE,Long.MAX_VALUE,Integer.MAX_VALUE-8L,Long.MAX_VALUE,Long.MAX_VALUE,10);
        failure(NiftiReadFailure.RESOURCE_LIMIT,read(root,b.array(),l));
        b.putShort(46,(short)3);failure(NiftiReadFailure.RESOURCE_LIMIT,read(root,b.array(),l));
    }
    @Test void T29_exactAndOneOverIndependentBounds() throws Exception {
        byte[] a=basic().array();byte[] gz=gzip(a);
        long[] exact={a.length,a.length,1,352,1_048_592,10};
        for(int i=0;i<5;i++) {long[] l={1_000_000,1_000_000,10_000,4096,2_000_000,4096};l[i]=exact[i];assertTrue(read(root,a,lim(l)).volume().isPresent());
            if(i==2) {var b=fixture(2,ByteOrder.LITTLE_ENDIAN,2,1,1);failure(NiftiReadFailure.RESOURCE_LIMIT,read(root,b.array(),lim(l)));}
            else if(i==3)failure(NiftiReadFailure.RESOURCE_LIMIT,read(root,prefix(basic(),353),lim(l)));
            else {l[i]--;failure(NiftiReadFailure.RESOURCE_LIMIT,read(root,a,lim(l)));}}
        var l=new NiftiReadLimits(gz.length,a.length,1,352,1_048_592,10);assertTrue(read(root,gz,l).volume().isPresent());
        failure(NiftiReadFailure.RESOURCE_LIMIT,read(root,gz,new NiftiReadLimits(gz.length-1,a.length,1,352,1_048_592,10)));
        byte[] optional=NiftiReadGzipTest.optional(gz);assertTrue(read(root,optional,new NiftiReadLimits(10000,10000,1,352,1_048_592,25)).volume().isPresent());
        failure(NiftiReadFailure.RESOURCE_LIMIT,read(root,optional,new NiftiReadLimits(10000,10000,1,352,1_048_592,24)));
    }
    static NiftiReadLimits lim(long[] a) {return new NiftiReadLimits(a[0],a[1],a[2],a[3],a[4],a[5]);}
    @Test void T30_allocationStructureAndDefensiveClone() throws Exception {
        String reader=Files.readString(Path.of("src/main/java/org/cbihi/mrinormalizer/infrastructure/nifti/Nifti1VolumeReader.java"));
        assertEquals(1,reader.split("new long\\[",-1).length-1);
        assertFalse(reader.contains("readAllBytes"));assertFalse(reader.contains("ByteArrayOutputStream"));
        assertTrue(reader.contains("16L"));assertTrue(reader.contains("1_048_576"));
        long[] input={7};var vox=new org.cbihi.mrinormalizer.domain.model.ImmutableVoxelData(1,1,1,org.cbihi.mrinormalizer.domain.model.ScalarType.UINT8,input);input[0]=8;assertEquals(7,vox.rawValueAt(0,0,0));
    }
    @Test void T31_plainTermination() throws Exception {
        byte[] a=prefix(basic(),368);
        for(int size:new int[]{0,1,100,347,348,350,352,360,368})failure(NiftiReadFailure.TRUNCATED_INPUT,run(Arrays.copyOf(a,size)));
        failure(NiftiReadFailure.TRAILING_DATA,run(Arrays.copyOf(a,a.length+1)));
    }
    @Test void T28_offsetConversionAndAdditionEdges() throws Exception {
        var b=basic();b.putFloat(108,0x1p63f);
        var huge=new NiftiReadLimits(Long.MAX_VALUE,Long.MAX_VALUE,Integer.MAX_VALUE-8L,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE);
        failure(NiftiReadFailure.RESOURCE_LIMIT,read(root,b.array(),huge));
        b.putFloat(108,Float.MAX_VALUE);failure(NiftiReadFailure.RESOURCE_LIMIT,read(root,b.array(),huge));
        // At the maximal supported N, 16N + allowance remains representable in long.
        b.putFloat(108,352);b.putShort(42,(short)32767);b.putShort(44,(short)32767);b.putShort(46,(short)2);
        failure(NiftiReadFailure.RESOURCE_LIMIT,read(root,b.array(),new NiftiReadLimits(Long.MAX_VALUE,Long.MAX_VALUE,Integer.MAX_VALUE-8L,352,1_048_576,10)));
    }

    @Test void T30_allByteArraysAreFixedParserBuffers() throws Exception {
        for(String name:List.of("Nifti1VolumeReader","Nifti1Header","StrictGzipInputStream")) {
            String text=Files.readString(Path.of("src/main/java/org/cbihi/mrinormalizer/infrastructure/nifti/"+name+".java"));
            var matcher=java.util.regex.Pattern.compile("new byte\\[([^]]+)\\]").matcher(text);
            while(matcher.find()){int bound=Integer.parseInt(matcher.group(1));assertTrue(bound>0&&bound<=8192);}
            assertFalse(text.contains("readAllBytes"));assertFalse(text.contains("ByteArrayOutputStream"));
        }
    }

}
