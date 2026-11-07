package org.cbihi.mrinormalizer.infrastructure.filesystem;

import java.nio.file.*;
import java.util.*;
import org.cbihi.mrinormalizer.infrastructure.headless.F9WindowsComposition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DestinationQualificationTest {
    @TempDir Path temp;
    @Test void localDirectoryWithSpacesQualifiesWithoutChangingItsEntries() throws Exception {
        Path root=Files.createDirectory(temp.resolve("PAI Output"));
        assertEquals(root.toRealPath(),DestinationQualification.requireLocal(root));
        DestinationQualification.requirePathCapacity(root,203);
        try(var stream=Files.list(root)){assertEquals(0,stream.count());}
    }
    @Test void missingDirectoryFailsWithoutCreatingIt() {
        Path absent=temp.resolve("absent");
        assertThrows(java.io.IOException.class,()->DestinationQualification.requireLocal(absent));
        assertFalse(Files.exists(absent));
    }
    @Test void privateCreatorOwnerMatchesActuallyCreatedPrivateDirectory() throws Exception {
        Path dir=Files.createDirectory(temp.resolve("private"),DestinationQualification.privateAttributes(temp,true));
        assertEquals(DestinationQualification.privateOwner(temp),Files.getOwner(dir));
        DestinationQualification.verifyPrivateDirectory(dir);
        try(var store=new JsonManifestStore(Files.createDirectory(temp.resolve("source")),dir,dir.resolve("manifest"),UUID.randomUUID())) {
            assertTrue(store.replay(Optional.empty()).isEmpty());
        }
    }
    @Test void manifestUsesCreatorIdentityWithoutRequiringDestinationOwnership() throws Exception {
        String configured=System.getProperty("d1.test.destination", "");
        Path destination=configured.isBlank()?Files.createDirectory(temp.resolve("chosen destination")):Path.of(configured).toRealPath();
        Path source=Files.createDirectory(temp.resolve("source-for-owner"));
        Path namespace=destination.resolve(".pai-d1-owner-probe-"+UUID.randomUUID());
        var before=Files.getOwner(destination);
        System.out.println("D1_DESTINATION_OWNER="+before+" D1_PRIVATE_OWNER="+DestinationQualification.privateOwner(destination));
        try(var store=new JsonManifestStore(source,destination,namespace,UUID.randomUUID())) {
            assertTrue(store.replay(Optional.empty()).isEmpty());
            assertEquals(DestinationQualification.privateOwner(destination),Files.getOwner(namespace));
        }
        assertEquals(before,Files.getOwner(destination));
        // The unique private journal is retained as diagnostic evidence; no existing namespace is touched.
    }
    @Test void existingPrivateDirectoryIsNotRepaired() throws Exception {
        Path output=Files.createDirectory(temp.resolve("output"));Path source=Files.createDirectory(temp.resolve("source"));
        Path stage=Files.createDirectory(output.resolve(".pai-f9-private-stage"));
        Files.writeString(source.resolve("slice"),"fixture");
        var view=Files.getFileAttributeView(stage,java.nio.file.attribute.PosixFileAttributeView.class);
        if(view!=null) {
            var before=java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x");Files.setPosixFilePermissions(stage,before);
            var ex=assertThrows(F9WindowsComposition.InitializationFailure.class,()->F9WindowsComposition.open(source,output,UUID.randomUUID()));
            assertEquals(F9WindowsComposition.InitializationStage.PRIVATE_STAGE,ex.stage());assertFalse(ex.recoveryRequired());
            assertEquals(before,Files.getPosixFilePermissions(stage));
        } else {
            var acl=Files.getFileAttributeView(stage,java.nio.file.attribute.AclFileAttributeView.class);
            var before=acl.getAcl();
            try { DestinationQualification.verifyPrivateDirectory(stage); }
            catch(java.io.IOException expected) { assertEquals(before,acl.getAcl()); }
        }
    }
}
