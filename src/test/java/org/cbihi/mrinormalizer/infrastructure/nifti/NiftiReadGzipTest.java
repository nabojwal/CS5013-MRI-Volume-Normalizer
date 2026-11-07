package org.cbihi.mrinormalizer.infrastructure.nifti;

import static org.cbihi.mrinormalizer.infrastructure.nifti.Nifti1VolumeReaderTest.*;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.*;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.CRC32;
import org.cbihi.mrinormalizer.application.dataset.nifti.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NiftiReadGzipTest {
    @TempDir Path root;
    byte[] basic() throws Exception {return gzip(fixture(2,ByteOrder.LITTLE_ENDIAN,1,1,1).array());}
    NiftiReadResult run(byte[] a) throws Exception {return read(root,a,limits());}
    // Independent gzip optional fields: FEXTRA, FNAME, FCOMMENT, FHCRC.
    static byte[] optional(byte[] gz) throws IOException {
        var out=new ByteArrayOutputStream();byte[] h=Arrays.copyOf(gz,10);h[3]=30;out.write(h);
        out.write(new byte[]{2,0,9,8});out.write(new byte[]{'N','A','M','E',0});out.write(new byte[]{'X','Y','Z',0});
        var crc=new CRC32();crc.update(out.toByteArray());out.write((int)crc.getValue()&255);out.write((int)(crc.getValue()>>>8)&255);
        out.write(gz,10,gz.length-10);return out.toByteArray();
    }
    @Test void T32_optionalAndMalformedHeaders() throws Exception {
        byte[] a=optional(basic());assertTrue(run(a).volume().isPresent());
        for(int at:new int[]{2,3,23,24}) {byte[] bad=a.clone();bad[at]=(byte)(at==2?7:at==3?0x80:bad[at]^1);failure(NiftiReadFailure.GZIP_INTEGRITY_FAILED,run(bad));}
        for(int length:new int[]{2,5,9,11,13,18,22,24})failure(NiftiReadFailure.GZIP_INTEGRITY_FAILED,run(Arrays.copyOf(a,length)));
        byte[] bad=Arrays.copyOf(a,19);bad[18]=1;failure(NiftiReadFailure.GZIP_INTEGRITY_FAILED,run(bad));
    }
    @Test void T33_corruptionAndTruncation() throws Exception {
        byte[] a=basic();
        for(int at:new int[]{10,a.length-8,a.length-4}) {byte[] bad=a.clone();bad[at]^=(byte)255;failure(NiftiReadFailure.GZIP_INTEGRITY_FAILED,run(bad));}
        for(int n=1;n<=8;n++)failure(NiftiReadFailure.GZIP_INTEGRITY_FAILED,run(Arrays.copyOf(a,a.length-n)));
    }
    @Test void T34_singleMemberPhysicalEof() throws Exception {
        byte[] a=basic();for(byte[] tail:new byte[][]{a,gzip(new byte[0]),new byte[]{1,2,3},new byte[]{0}}) {
            byte[] joined=Arrays.copyOf(a,a.length+tail.length);System.arraycopy(tail,0,joined,a.length,tail.length);failure(NiftiReadFailure.TRAILING_DATA,run(joined));}
    }
    @Test void T35_expansionAndHeaderBounds() throws Exception {
        byte[] a=gzip(fixture(2,ByteOrder.LITTLE_ENDIAN,100,100,1).array());
        failure(NiftiReadFailure.RESOURCE_LIMIT,read(root,a,new NiftiReadLimits(100000,1000,10000,4096,2_000_000,4096)));
        byte[] h=Arrays.copyOf(basic(),10000);h[3]=8;Arrays.fill(h,10,h.length,(byte)'Q');
        failure(NiftiReadFailure.RESOURCE_LIMIT,run(h));
    }
}
