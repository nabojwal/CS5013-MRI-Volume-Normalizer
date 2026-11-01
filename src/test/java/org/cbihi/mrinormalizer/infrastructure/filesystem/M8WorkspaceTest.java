package org.cbihi.mrinormalizer.infrastructure.filesystem;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.List;
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
    @Test void m8_18_nativeWindowsIdentityDoesNotDependOnJavaFileKey()throws Exception{
        if(!System.getProperty("os.name","").toLowerCase(java.util.Locale.ROOT).contains("win"))return;
        Path source=Files.write(root.resolve("ntfs-identity-probe"),new byte[]{3,1,4});
        Path same=root.resolve("ntfs-identity-link");
        Files.createLink(same,source);
        var id=M8WindowsNtfsIdentity.read(source);
        assertEquals(id,M8WindowsNtfsIdentity.read(same),"Hard links must share file ID");
        assertNotEquals(id,M8WindowsNtfsIdentity.read(root),"File and parent cannot share ID");
        Path replacement=Files.write(root.resolve("ntfs-replacement"),new byte[]{3,1,4});
        assertNotEquals(id,M8WindowsNtfsIdentity.read(replacement),
            "Identical bytes must not be mistaken for object identity");
    }

    @Test void m8_18_windowsPrivateAclIsRestrictedAtCreationAndInheritedBySnapshot()throws Exception{
        if(!System.getProperty("os.name","").startsWith("Windows"))return;
        Path in=Files.createDirectory(root.resolve("acl-in")),out=Files.createDirectory(root.resolve("acl-out"));
        byte[] original={0,1,2,3};Files.write(in.resolve("a.nii"),original);
        var lookup=out.getFileSystem().getUserPrincipalLookupService();
        var broad=lookup.lookupPrincipalByName("Everyone");
        var outputAcl=Files.getFileAttributeView(out,AclFileAttributeView.class);
        var acl=new java.util.ArrayList<>(outputAcl.getAcl());
        acl.add(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(broad)
            .setPermissions(AclEntryPermission.WRITE_DATA,AclEntryPermission.APPEND_DATA)
            .setFlags(AclEntryFlag.FILE_INHERIT,AclEntryFlag.DIRECTORY_INHERIT).build());
        outputAcl.setAcl(acl);var before=outputAcl.getAcl();
        var request=request();Path privateRoot=out.resolve(".pai-m8-"+request.jobId());
        var current=lookup.lookupPrincipalByName(System.getProperty("user.name"));
        var workspace=new NioM8Workspace(in,out,M8Limits.defaults(),Clock.systemUTC(),(phase,z)->{
            if(phase.equals("snapshot-open")){
                assertEquals(current,Files.getOwner(privateRoot));
                assertOwnerOnlyAcl(privateRoot,current);
            }
        });
        try(var session=workspace.open(request)){
            session.verifySnapshotUnchanged();
            assertOwnerOnlyAcl(privateRoot,current);
            assertOwnerOnlyAcl(privateRoot.resolve("source.nii"),current);
            var privateAcl=Files.getFileAttributeView(privateRoot,AclFileAttributeView.class).getAcl();
            assertTrue(privateAcl.getFirst().flags().containsAll(
                java.util.Set.of(AclEntryFlag.FILE_INHERIT,AclEntryFlag.DIRECTORY_INHERIT)));
            session.cleanupOwnedData();
        }
        assertFalse(Files.exists(privateRoot));
        assertEquals(before,outputAcl.getAcl(),"Caller-owned output ACL must be unchanged");
        assertArrayEquals(original,Files.readAllBytes(in.resolve("a.nii")));
    }
    private static void assertOwnerOnlyAcl(Path path,UserPrincipal owner)throws java.io.IOException{
        List<AclEntry> acl=Files.getFileAttributeView(path,AclFileAttributeView.class).getAcl();
        assertFalse(acl.isEmpty());
        for(var entry:acl){
            assertEquals(AclEntryType.ALLOW,entry.type());
            assertEquals(owner,entry.principal(),"No inherited non-owner access permitted");
        }
        assertTrue(acl.stream().anyMatch(e->e.permissions().containsAll(
            java.util.EnumSet.allOf(AclEntryPermission.class))),"Owner requires complete access");
    }
    @Test void m8_18_windowsAclValidatorStillRejectsInheritedNonOwnerWrites()throws Exception{
        if(!System.getProperty("os.name","").startsWith("Windows"))return;
        Path parent=Files.createDirectory(root.resolve("broad-parent"));
        var view=Files.getFileAttributeView(parent,AclFileAttributeView.class);
        var acl=new java.util.ArrayList<>(view.getAcl());
        acl.add(AclEntry.newBuilder().setType(AclEntryType.ALLOW)
            .setPrincipal(parent.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("Everyone"))
            .setPermissions(AclEntryPermission.WRITE_DATA)
            .setFlags(AclEntryFlag.FILE_INHERIT,AclEntryFlag.DIRECTORY_INHERIT).build());
        view.setAcl(acl);Path child=Files.createDirectory(parent.resolve("inherited"));
        var validator=NioM8Workspace.class.getDeclaredMethod("ensurePrivateDirectory",Path.class);
        validator.setAccessible(true);
        var failure=assertThrows(java.lang.reflect.InvocationTargetException.class,()->validator.invoke(null,child));
        assertInstanceOf(java.io.IOException.class,failure.getCause());
        assertEquals("Private directory ACL too broad",failure.getCause().getMessage());
    }
    @Test void m8_18_existingPrivateDirectoryAndAclAreNeverChanged()throws Exception{
        if(!System.getProperty("os.name","").startsWith("Windows"))return;
        Path in=Files.createDirectory(root.resolve("reserved-in")),out=Files.createDirectory(root.resolve("reserved-out"));
        Files.write(in.resolve("a.nii"),new byte[]{1,2,3});var request=request();
        Path reserved=Files.createDirectory(out.resolve(".pai-m8-"+request.jobId()));
        Files.writeString(reserved.resolve("sentinel"),"preserve");
        var view=Files.getFileAttributeView(reserved,AclFileAttributeView.class);var before=view.getAcl();
        var failure=assertThrows(M8Fault.Failure.class,()->
            new NioM8Workspace(in,out,M8Limits.defaults(),Clock.systemUTC()).open(request));
        assertEquals(List.of(new M8Fault(M8Fault.Phase.REQUEST,M8Fault.Code.OUTPUT_CONFLICT)),failure.faults());
        assertFalse(failure.effectsUncertain());assertEquals(before,view.getAcl());
        assertEquals("preserve",Files.readString(reserved.resolve("sentinel")));
    }
}
