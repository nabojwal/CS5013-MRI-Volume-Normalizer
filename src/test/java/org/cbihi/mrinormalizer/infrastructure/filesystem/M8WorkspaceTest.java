package org.cbihi.mrinormalizer.infrastructure.filesystem;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.cbihi.mrinormalizer.application.m8.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;

final class M8WorkspaceTest {
    @TempDir Path root;
    private M8Request request(){return new M8Request(UUID.randomUUID(),new RelativePath(RelativePath.Root.SOURCE,"a.nii"),
        new M8Metadata(true,true,M8Metadata.ScanningSequence.SE,M8Metadata.SequenceVariant.NONE));}
    @Test void m8_17_outOfRootTraversalRefusedByLogicalReference(){
        assertThrows(IllegalArgumentException.class,()->new RelativePath(RelativePath.Root.SOURCE,"../escape.nii"));
    }
    @Test void m8_14_existingJobPathNeverOverwritten()throws Exception {
        Path in=Files.createDirectory(root.resolve("in"));Path out=Files.createDirectory(root.resolve("out"));
        Files.write(in.resolve("a.nii"),new byte[]{0,1,2});
        M8Request request=request();Path reserved=Files.createDirectory(out.resolve("m8-"+request.jobId()));
        Files.writeString(reserved.resolve("sentinel"),"preserve");
        var workspace=new NioM8Workspace(in,out,M8Limits.defaults(),Clock.systemUTC());
        assertThrows(M8Fault.Failure.class,()->workspace.open(request));
        assertEquals("preserve",Files.readString(reserved.resolve("sentinel")));
    }
    @Test void m8_16_sourceFileMustNotBeModifiedBySnapshot()throws Exception {
        Path in=Files.createDirectory(root.resolve("in2"));Path out=Files.createDirectory(root.resolve("out2"));
        Path src=in.resolve("a.nii");Files.write(src,new byte[]{0,1,2,3});
        var before=Files.readAllBytes(src);
        var session=new NioM8Workspace(in,out,M8Limits.defaults(),Clock.systemUTC()).open(request());
        session.verifySnapshotUnchanged();session.close();
        assertArrayEquals(before,Files.readAllBytes(src));
    }
    @Test void m8_18_nativeQualificationFlagProvesActualFilesystemCapabilities()throws Exception{
        if(!Boolean.getBoolean("m8.test.native"))return; // separately reported non-native unit run
        String filesystem=Files.getFileStore(root).type().toLowerCase(java.util.Locale.ROOT);
        boolean windows=System.getProperty("os.name","").toLowerCase(java.util.Locale.ROOT).contains("win");
        assertTrue(windows?filesystem.contains("ntfs"):filesystem.equals("ext4"),
            "Native qualification requires local NTFS/ext4, not an injected fixture");
        Path first=Files.writeString(root.resolve("hardlink-source"),"identity");
        Path second=root.resolve("hardlink-target");
        Files.createLink(second,first);
        assertTrue(Files.isSameFile(first,second),"Actual same-store hard-link required");
        Path symlink=root.resolve("symlink-target");
        Files.createSymbolicLink(symlink,first.getFileName());
        assertTrue(Files.isSymbolicLink(symlink),"Actual native symlink fixture required");
        if(!windows){
            var permissions=Files.getPosixFilePermissions(root);
            assertFalse(permissions.isEmpty());
        }else{
            assertNotNull(Files.getFileAttributeView(root,
                java.nio.file.attribute.AclFileAttributeView.class));
        }
    }
    @Test void m8_17_nofollowSymlinkSourceIsRejectedWithoutTouchingTarget()throws Exception{
        Path in=Files.createDirectory(root.resolve("link-in"));
        Path out=Files.createDirectory(root.resolve("link-out"));
        Path target=Files.writeString(in.resolve("target.nii"),"unchanged");
        Path link=in.resolve("a.nii");
        try{Files.createSymbolicLink(link,target.getFileName());}
        catch(UnsupportedOperationException|java.nio.file.FileSystemException e){
            if(Boolean.getBoolean("m8.test.native"))fail("Native symlink fixture unavailable",e);
            return;
        }
        var workspace=new NioM8Workspace(in,out,M8Limits.defaults(),Clock.systemUTC());
        assertThrows(M8Fault.Failure.class,()->workspace.open(request()));
        assertEquals("unchanged",Files.readString(target));
    }
    @Test void m8_18_nativeWindowsFileKeysMustBeAvailableForFrozenIdentityPolicy()throws Exception{
        if(!Boolean.getBoolean("m8.test.native") ||
            !System.getProperty("os.name","").toLowerCase(java.util.Locale.ROOT).contains("win"))return;
        // This is a capability gate, not an alternate or weakened identity policy.
        // Earlier Windows observations found a null fileKey; report that as a
        // genuine provider blocker until a native, equivalent proof is approved.
        Path source=Files.write(root.resolve("ntfs-identity-probe"),new byte[]{3,1,4});
        var rootAttr=Files.readAttributes(root,java.nio.file.attribute.BasicFileAttributes.class,
            LinkOption.NOFOLLOW_LINKS);
        var fileAttr=Files.readAttributes(source,java.nio.file.attribute.BasicFileAttributes.class,
            LinkOption.NOFOLLOW_LINKS);
        assertNotNull(rootAttr.fileKey(),"NTFS root fileKey missing: fail closed; require owner-approved identity mechanism");
        assertNotNull(fileAttr.fileKey(),"NTFS source fileKey missing: fail closed; require native identity evidence");
    }

}
