package org.cbihi.mrinormalizer.infrastructure.nifti;

import static org.cbihi.mrinormalizer.infrastructure.nifti.Nifti1VolumeReaderTest.*;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import org.cbihi.mrinormalizer.application.dataset.nifti.*;
import org.cbihi.mrinormalizer.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NiftiReadIntegrationTest {
    @TempDir Path root;
    byte[] bytes() {return fixture(2,ByteOrder.LITTLE_ENDIAN,1,1,1).array();}
    // Deterministic seams prove branch behavior, never native provider qualification.
    static class Access implements Nifti1VolumeReader.Access {
        final byte[] data;int closes,opens,observations;boolean closeFail,zero,shortReads,nullKey,change,link,special,unproven;
        Access(byte[] data) {this.data=data;}
        public BasicFileAttributes attributes(Path p) throws IOException {
            var real=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);int observation=++observations;
            return new BasicFileAttributes() {
                public FileTime lastModifiedTime(){return real.lastModifiedTime();}public FileTime lastAccessTime(){return real.lastAccessTime();}public FileTime creationTime(){return real.creationTime();}
                public boolean isRegularFile(){return special?false:real.isRegularFile();}public boolean isDirectory(){return real.isDirectory();}public boolean isSymbolicLink(){return link&&!real.isDirectory();}public boolean isOther(){return special;}
                public long size(){return real.size();}
                public Object fileKey(){
                    if(nullKey)return null;
                    if(change&&real.isRegularFile())return observation>2?"substituted":"original";
                    return real.fileKey();
                }
            };
        }
        public InputStream open(Path p) throws IOException {
            if(unproven)throw new UnsupportedOperationException();opens++;
            return new ByteArrayInputStream(data) {
                @Override public synchronized int read(byte[] b,int o,int n) {if(zero)return 0;return super.read(b,o,shortReads?Math.min(n,1):n);}
                @Override public void close() throws IOException {closes++;if(closeFail)throw new IOException("PRIVATE_CLOSE_SENTINEL");}
            };
        }
    }
    NiftiReadResult injected(Access access) throws Exception {
        Files.write(root.resolve("file"),bytes());return new Nifti1VolumeReader(root,limits(),access).read(source("file"));
    }
    @Test void T36_shortZeroReadFailureInterrupt() throws Exception {
        var a=new Access(bytes());a.shortReads=true;assertTrue(injected(a).volume().isPresent());assertEquals(1,a.closes);
        a=new Access(bytes());a.zero=true;failure(NiftiReadFailure.READ_FAILED,injected(a));assertEquals(1,a.closes);
        // A checked failing stream is used separately from the in-memory short-read fixture.
        Files.write(root.resolve("file"),bytes());
        var fault=new Nifti1VolumeReader.Access(){public BasicFileAttributes attributes(Path p)throws IOException{return Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}public InputStream open(Path p){return new InputStream(){public int read()throws IOException{throw new IOException("PRIVATE_EXCEPTION_SENTINEL");}};}};
        failure(NiftiReadFailure.READ_FAILED,new Nifti1VolumeReader(root,limits(),fault).read(source("file")));
        Thread.currentThread().interrupt();try {failure(NiftiReadFailure.INTERRUPTED,new Nifti1VolumeReader(root,limits()).read(source("file")));assertTrue(Thread.currentThread().isInterrupted());}finally{Thread.interrupted();}
    }
    @Test void T37_rootAndInjectedDiscoveryContinuity() throws Exception {
        assertThrows(IllegalArgumentException.class,()->new Nifti1VolumeReader(null,limits()));assertThrows(IllegalArgumentException.class,()->new Nifti1VolumeReader(root,null));
        assertThrows(IllegalArgumentException.class,()->new Nifti1VolumeReader(root.resolve("missing"),limits()));
        var reader=new Nifti1VolumeReader(root,limits());assertThrows(IllegalArgumentException.class,()->reader.read(null));
        failure(NiftiReadFailure.PATH_OUTSIDE_ROOT,reader.read(new org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath(org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath.Root.OUTPUT,"file")));
        failure(NiftiReadFailure.INPUT_UNAVAILABLE,reader.read(source("missing")));
        Files.createDirectory(root.resolve("directory"));failure(NiftiReadFailure.SPECIAL_FILE_UNSUPPORTED,reader.read(source("directory")));
        var a=new Access(bytes());a.link=true;failure(NiftiReadFailure.SYMLINK_DISALLOWED,injected(a));assertEquals(0,a.opens);
        a=new Access(bytes());a.special=true;failure(NiftiReadFailure.SPECIAL_FILE_UNSUPPORTED,injected(a));assertEquals(0,a.opens);
        a=new Access(bytes());a.change=true;failure(NiftiReadFailure.SOURCE_CHANGED,injected(a));assertEquals(1,a.closes);
        a=new Access(bytes());a.unproven=true;failure(NiftiReadFailure.CONTAINMENT_UNPROVEN,injected(a));
    }
    @Test void T38_nullKeyWeakEvidenceInjected() throws Exception {var a=new Access(bytes());a.nullKey=true;var r=injected(a);assertTrue(r.volume().isPresent());assertTrue(r.findings().contains(NiftiReadFinding.WEAK_SOURCE_IDENTITY));}
    @Test void T39_releaseFailuresInjected() throws Exception {
        var a=new Access(bytes());a.closeFail=true;var r=injected(a);failure(NiftiReadFailure.CLOSE_FAILED,r);assertTrue(r.findings().contains(NiftiReadFinding.SFORM_SELECTED));assertEquals(1,a.closes);
        a=new Access(new byte[]{0});a.closeFail=true;r=injected(a);failure(NiftiReadFailure.TRUNCATED_INPUT,r);failure(NiftiReadFailure.CLOSE_FAILED,r);assertEquals(1,a.closes);
        a=new Access(gzip(bytes()));a.closeFail=true;failure(NiftiReadFailure.CLOSE_FAILED,injected(a));assertEquals(1,a.closes);
    }
    @Test void T40_nativeReadCloseRenamePreservesSource() throws Exception {
        Path p=root.resolve("ordinary");Files.write(p,bytes());var before=Files.readAttributes(p,BasicFileAttributes.class);byte[] content=Files.readAllBytes(p);
        Set<String> names;try(var s=Files.list(root)){names=s.map(x->x.getFileName().toString()).collect(java.util.stream.Collectors.toSet());}
        var r=new Nifti1VolumeReader(root,limits()).read(source("ordinary"));assertTrue(r.volume().isPresent());
        assertArrayEquals(content,Files.readAllBytes(p));assertEquals(before.lastModifiedTime(),Files.getLastModifiedTime(p));
        try(var s=Files.list(root)){assertEquals(names,s.map(x->x.getFileName().toString()).collect(java.util.stream.Collectors.toSet()));}
        Files.move(p,root.resolve("renamed"));Files.move(root.resolve("renamed"),p);
    }
    @Test void T41_protectedWriterIntegration() throws Exception {
        for(ScalarType t:ScalarType.values())for(boolean gz:new boolean[]{false,true}) {
            var v=new ImageVolume(new VolumeGeometry(2,1,1,2,1,3,new double[]{10,20,30},new double[]{1,0,0},new double[]{0,1,0},new double[]{0,0,-1},CoordinateSystem.NIFTI_RAS),new ImmutableVoxelData(2,1,1,t,new long[]{0,1}),new IntensityTransform(true,-2,7));
            String name=t.name()+(gz?".nii.gz":".nii");new Nifti1VolumeWriter().write(v,v.geometry().voxelToWorldAffine(),new OutputTarget(root.resolve(name).toString()));
            var r=new Nifti1VolumeReader(root,limits()).read(source(name));var actual=r.volume().orElseThrow();assertEquals(t,actual.voxels().scalarType());assertEquals(1,actual.voxels().rawValueAt(1,0,0));assertEquals(v.intensityTransform(),actual.intensityTransform());
            NiftiReadGeometryTest.point(actual,1,1,1,11,22,27);
        }
    }
    @Test void T43_privateSentinelsNotEmitted() throws Exception {
        var oldOut=System.out;var oldErr=System.err;var capture=new ByteArrayOutputStream();
        try(var print=new PrintStream(capture)) {System.setOut(print);System.setErr(print);
            byte[] data=bytes();System.arraycopy("PRIVATE_HEADER_SENTINEL".getBytes(java.nio.charset.StandardCharsets.US_ASCII),0,data,148,23);
            var a=new Access(data);a.closeFail=true;var r=injected(a);assertFalse(r.failures().toString().contains("PRIVATE"));assertFalse(r.findings().toString().contains("PRIVATE"));
        }finally {System.setOut(oldOut);System.setErr(oldErr);}
        assertEquals("",capture.toString());
    }
    @Test void T36_interruptDuringDecodeInjected() throws Exception {
        Files.write(root.resolve("file"),bytes());
        var a=new Access(bytes()) {
            @Override public InputStream open(Path p) {
                opens++;
                return new ByteArrayInputStream(data) {
                    int total;
                    @Override public synchronized int read(byte[] b,int off,int len) {
                        int n=super.read(b,off,Math.min(len,1));total+=Math.max(0,n);
                        if(total==350)Thread.currentThread().interrupt();return n;
                    }
                    @Override public void close(){closes++;}
                };
            }
        };
        try {failure(NiftiReadFailure.INTERRUPTED,injected(a));assertTrue(Thread.currentThread().isInterrupted());assertEquals(1,a.closes);}finally{Thread.interrupted();}
    }
    @Test void T37_eachContinuityObservationInjected() throws Exception {
        Files.createDirectory(root.resolve("nested"));Files.write(root.resolve("nested/file"),bytes());
        for(int changed:new int[]{0,1,2}) {
            var a=new Access(bytes()) {
                int call;
                @Override public BasicFileAttributes attributes(Path p)throws IOException {
                    BasicFileAttributes real=super.attributes(p);int i=call++;
                    if(i!=3+changed)return real;
                    return new BasicFileAttributes() {
                        public FileTime lastModifiedTime(){return FileTime.fromMillis(real.lastModifiedTime().toMillis()+1000);}
                        public FileTime lastAccessTime(){return real.lastAccessTime();}public FileTime creationTime(){return real.creationTime();}
                        public boolean isRegularFile(){return real.isRegularFile();}public boolean isDirectory(){return real.isDirectory();}public boolean isSymbolicLink(){return false;}public boolean isOther(){return false;}
                        public long size(){return real.size();}public Object fileKey(){return real.fileKey();}
                    };
                }
            };
            var r=new Nifti1VolumeReader(root,limits(),a).read(source("nested/file"));failure(NiftiReadFailure.SOURCE_CHANGED,r);assertEquals(1,a.closes);
        }
    }
    @Test void T39_inflaterReleasedIndependentlyOfOwnedStream() throws Exception {
        var raw=new ByteArrayInputStream(gzip(bytes()));
        var stream=new StrictGzipInputStream(raw,4096);
        var field=StrictGzipInputStream.class.getDeclaredField("inflater");field.setAccessible(true);
        var inflater=(java.util.zip.Inflater)field.get(stream);
        stream.close();stream.close();assertThrows(NullPointerException.class,()->inflater.getBytesRead());
        // Closing the strict view leaves the separately-owned file stream for the adapter to release.
        assertTrue(raw.available()>0);
    }
    @Test void T43_checkedReadExceptionAndGzipNamesStayPrivate() throws Exception {
        var capture=new ByteArrayOutputStream();var out=System.out;var err=System.err;
        try(var print=new PrintStream(capture)) {
            System.setOut(print);System.setErr(print);Files.write(root.resolve("file"),bytes());
            var a=new Access(bytes()) {
                @Override public InputStream open(Path p) {
                    return new InputStream() {public int read()throws IOException{throw new IOException("PRIVATE_EXCEPTION_SENTINEL");}public void close()throws IOException{throw new IOException("PRIVATE_CLOSE_SENTINEL");}};
                }
            };
            var r=new Nifti1VolumeReader(root,limits(),a).read(source("file"));failure(NiftiReadFailure.READ_FAILED,r);failure(NiftiReadFailure.CLOSE_FAILED,r);assertFalse(r.failures().toString().contains("PRIVATE"));
            byte[] gz=gzip(bytes());var framed=new ByteArrayOutputStream();byte[] h=Arrays.copyOf(gz,10);h[3]=24;framed.write(h);
            framed.write("PRIVATE_GZIP_SENTINEL".getBytes(java.nio.charset.StandardCharsets.US_ASCII));framed.write(0);framed.write("PRIVATE_COMMENT_SENTINEL".getBytes(java.nio.charset.StandardCharsets.US_ASCII));framed.write(0);framed.write(gz,10,gz.length-10);
            r=read(root,framed.toByteArray(),limits());assertTrue(r.volume().isPresent());assertFalse(r.findings().toString().contains("PRIVATE"));
        }finally{System.setOut(out);System.setErr(err);}
        assertEquals("",capture.toString());
    }

    @Test void T36_shortGzipAndPayloadReadFailureInjected() throws Exception {
        var a=new Access(gzip(bytes()));a.shortReads=true;assertTrue(injected(a).volume().isPresent());assertEquals(1,a.closes);
        Files.write(root.resolve("file"),bytes());
        var fault=new Access(bytes()) {
            @Override public InputStream open(Path p) {
                return new InputStream() {
                    int position;
                    public int read()throws IOException {if(position==352)throw new IOException("PRIVATE_PAYLOAD_EXCEPTION");return position<data.length?Byte.toUnsignedInt(data[position++]):-1;}
                    public void close(){closes++;}
                };
            }
        };
        failure(NiftiReadFailure.READ_FAILED,injected(fault));assertEquals(1,fault.closes);
    }
    @Test void T39_gzipIntegrityAndCloseFailuresBothRetained() throws Exception {
        byte[] corrupt=gzip(bytes());corrupt[corrupt.length-8]^=1;var a=new Access(corrupt);a.closeFail=true;
        var r=injected(a);failure(NiftiReadFailure.GZIP_INTEGRITY_FAILED,r);failure(NiftiReadFailure.CLOSE_FAILED,r);assertEquals(1,a.closes);
    }

}
