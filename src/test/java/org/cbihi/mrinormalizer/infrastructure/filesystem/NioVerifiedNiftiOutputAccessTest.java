package org.cbihi.mrinormalizer.infrastructure.filesystem;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.cbihi.mrinormalizer.application.port.out.VerifiedNiftiOutputAccess;

class NioVerifiedNiftiOutputAccessTest {
    @Test void T23_accessFaultIsRestricted() {
        var ex=new VerifiedNiftiOutputAccess.AccessFailure(List.of(
                VerifiedNiftiOutputAccess.AccessFault.STAGE_CHANGED,
                VerifiedNiftiOutputAccess.AccessFault.SOURCE_PATH_INVALID));
        assertEquals(List.of(VerifiedNiftiOutputAccess.AccessFault.SOURCE_PATH_INVALID,
                VerifiedNiftiOutputAccess.AccessFault.STAGE_CHANGED),ex.faults());
        assertEquals("Verified NIfTI output access failed",ex.getMessage());
        assertNull(ex.getCause());assertEquals(0,ex.getSuppressed().length);
        assertThrows(UnsupportedOperationException.class,()->ex.faults().clear());
    }
    @Test void T24_publicationCannotClaimUnverifiedDigest() {
        assertThrows(IllegalArgumentException.class,()->new VerifiedNiftiOutputAccess.PublicationResult(
                VerifiedNiftiOutputAccess.Publication.PUBLISHED_BYTES_CHECKED,Optional.empty(),List.of()));
    }

    // Real-provider fixtures. A failed native test is NOT replaced by an injected result.
    private static final class NativeRig implements AutoCloseable {
        final java.nio.file.Path root,source,output,privateRoot,original,finalPath;
        final org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath destination;
        final org.cbihi.mrinormalizer.application.provenance.manifest.ManifestReceipt intent;
        final JsonManifestStore store;
        final NioVerifiedNiftiOutputAccess access;
        final String op="e".repeat(64);
        final java.util.UUID job=java.util.UUID.randomUUID();
        final java.time.Instant time=java.time.Instant.now();
        NativeRig() throws Exception {
            root=java.nio.file.Files.createTempDirectory("f8-native-provider-");
            source=java.nio.file.Files.createDirectory(root.resolve("source"));
            output=java.nio.file.Files.createDirectory(root.resolve("output"));
            privateRoot=java.nio.file.Files.createDirectory(output.resolve("private-stage"));
            original=source.resolve("fixture.dcm");
            java.nio.file.Files.write(original,new byte[]{1,2,3,4});
            finalPath=output.resolve("verified.nii");
            var rel=new org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath(
                    org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath.Root.SOURCE,"fixture.dcm");
            destination=new org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath(
                    org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath.Root.OUTPUT,"verified.nii");
            var sha=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(java.nio.file.Files.readAllBytes(original)));
            var sourceRecord=new org.cbihi.mrinormalizer.application.provenance.manifest.SourceFileRecord(rel,
                    Optional.of(new org.cbihi.mrinormalizer.application.provenance.manifest.ContentDigest(4,sha)),
                    org.cbihi.mrinormalizer.application.dataset.model.FormatAssessment.fromDetection(
                            org.cbihi.mrinormalizer.domain.model.DetectionResult.identified(
                                    org.cbihi.mrinormalizer.domain.model.DetectionOutcome.DICOM)),List.of());
            var plan=new org.cbihi.mrinormalizer.application.provenance.manifest.ProvenanceManifest(2,job,time,
                    List.of(sourceRecord),List.of(new org.cbihi.mrinormalizer.application.provenance.manifest.ManifestOperation(
                            op, org.cbihi.mrinormalizer.application.provenance.manifest.ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI,
                            List.of(rel),destination)));
            store=new JsonManifestStore(source,output,output.resolve("manifest"),job);
            var head=store.create(plan);
            head=store.append(new org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord(
                    2,job,1,head.headSha256(),time,
                    org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.Kind.JOB_OBSERVED,
                    Optional.empty(),Optional.empty(),Optional.of(
                            org.cbihi.mrinormalizer.application.provenance.manifest.ManifestState.JobState.RUNNING),
                    List.of()),head);
            intent=store.append(new org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord(
                    2,job,2,head.headSha256(),time,
                    org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.Kind.OPERATION_OBSERVED,
                    Optional.of(op),Optional.of(new org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.Observation(
                            org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.State.IN_PROGRESS,
                            Optional.of(time),Optional.empty(),Optional.empty(),1,Optional.empty(),Optional.empty())),
                    Optional.empty(),List.of()),head);
            access=new NioVerifiedNiftiOutputAccess(source,output,privateRoot,store,1024,1024,1024*1024);
            var bound=access.validateSelectedSources(List.of(new org.cbihi.mrinormalizer.domain.model.InputSource(original.toString())),
                    List.of(sourceRecord));
            assertEquals(1,bound.matchedExpectedSourceCount());
        }
        @Override public void close() throws Exception {
            access.close();store.close();
        }
    }
    @Test void T25_privateStageUsesApprovedSuffixAndRoot() throws Exception {
        try(var rig=new NativeRig()){
            var stage=rig.access.prepare(rig.op,rig.destination,rig.intent);
            var stagePath=java.nio.file.Path.of(stage.stageTarget().reference());
            assertTrue(stagePath.startsWith(rig.privateRoot));
            assertTrue(stagePath.getFileName().toString().startsWith(".stage-"));
            assertTrue(stagePath.getFileName().toString().endsWith(".nii"));
            var stageParent=stagePath.getParent();
            if(java.nio.file.Files.getFileAttributeView(stageParent,
                    java.nio.file.attribute.PosixFileAttributeView.class,
                    java.nio.file.LinkOption.NOFOLLOW_LINKS)!=null){
                assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"),
                        java.nio.file.Files.getPosixFilePermissions(stageParent,
                                java.nio.file.LinkOption.NOFOLLOW_LINKS));
            } else {
                var acl=java.nio.file.Files.getFileAttributeView(stageParent,
                        java.nio.file.attribute.AclFileAttributeView.class,
                        java.nio.file.LinkOption.NOFOLLOW_LINKS);
                assertNotNull(acl);
                var owner=java.nio.file.Files.getOwner(stageParent,java.nio.file.LinkOption.NOFOLLOW_LINKS);
                for(var entry:acl.getAcl())if(entry.type()==java.nio.file.attribute.AclEntryType.ALLOW)
                    assertEquals(owner,entry.principal());
            }
            assertFalse(java.nio.file.Files.exists(rig.finalPath));
            assertEquals(org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath.Root.SOURCE,
                    stage.stageReadReference().root());
            stage.discardUnpublished();stage.close();
            assertFalse(java.nio.file.Files.exists(stagePath));
        }
    }
    @Test void T26_noFinalFileBeforeVerifiedPublish() throws Exception {
        try(var rig=new NativeRig()){
            var stage=rig.access.prepare(rig.op,rig.destination,rig.intent);
            java.nio.file.Files.write(java.nio.file.Path.of(stage.stageTarget().reference()),new byte[]{9,8,7,6});
            assertFalse(java.nio.file.Files.exists(rig.finalPath));
            stage.digestClosedStage();
            assertFalse(java.nio.file.Files.exists(rig.finalPath));
            stage.digestClosedStage();
            assertFalse(java.nio.file.Files.exists(rig.finalPath));
            stage.discardUnpublished();stage.close();
        }
    }
    @Test void T30_digestLifecyclePinsAndRejectsPrematureOrRepeatedCalls() throws Exception {
        try(var rig=new NativeRig()){
            var stage=rig.access.prepare(rig.op,rig.destination,rig.intent);
            var bytes=new byte[]{10,11,12,13};
            java.nio.file.Files.write(java.nio.file.Path.of(stage.stageTarget().reference()),bytes);
            assertEquals(4,stage.digestClosedStage().sizeBytes());
            var d2=stage.digestClosedStage();
            assertEquals(4,d2.sizeBytes());
            var third=assertThrows(VerifiedNiftiOutputAccess.AccessFailure.class,stage::digestClosedStage);
            assertTrue(third.faults().contains(VerifiedNiftiOutputAccess.AccessFault.PUBLICATION_UNAVAILABLE));
            assertFalse(java.nio.file.Files.exists(rig.finalPath));
            stage.discardUnpublished();stage.close();
        }
    }
    @Test void T27_d1D2MutationFailsClosedAndNeverPublishes() throws Exception {
        var rig=new NativeRig();
        try{
            var stage=rig.access.prepare(rig.op,rig.destination,rig.intent);
            var stagePath=java.nio.file.Path.of(stage.stageTarget().reference());
            java.nio.file.Files.write(stagePath,new byte[]{10,11,12,13});
            stage.digestClosedStage();
            // Stage is pinned: mutation must either be refused by the native provider
            // or be discovered by the next digest. Never allow verified publication.
            boolean refused=false;
            try{java.nio.file.Files.write(stagePath,new byte[]{10,11,12,14});}
            catch(java.nio.file.FileSystemException e){refused=true;}
            if(!refused){
                var error=assertThrows(VerifiedNiftiOutputAccess.AccessFailure.class,stage::digestClosedStage);
                assertTrue(error.faults().contains(VerifiedNiftiOutputAccess.AccessFault.STAGE_CHANGED));
            }
            assertFalse(java.nio.file.Files.exists(rig.finalPath));
            // A tampered owned stage might be retained for recovery rather than deleted.
            // This test never treats cleanup refusal as successful cleanup.
            try {stage.discardUnpublished();}catch(VerifiedNiftiOutputAccess.AccessFailure expected) {
                assertTrue(expected.faults().contains(VerifiedNiftiOutputAccess.AccessFault.STAGE_CHANGED));
            }
            try {stage.close();}catch(VerifiedNiftiOutputAccess.AccessFailure expected) {
                assertTrue(expected.faults().contains(VerifiedNiftiOutputAccess.AccessFault.CLEANUP_FAILED));
            }
        } finally {
            try { rig.close(); }
            catch (VerifiedNiftiOutputAccess.AccessFailure sticky) {
                assertTrue(sticky.faults().contains(VerifiedNiftiOutputAccess.AccessFault.CLEANUP_FAILED));
            }
        }
    }
    @Test void T31_noReplaceUnderPreexistingDestination() throws Exception {
        try(var rig=new NativeRig()){
            byte[] other=new byte[]{30,31,32};
            java.nio.file.Files.write(rig.finalPath,other);
            var error=assertThrows(VerifiedNiftiOutputAccess.AccessFailure.class,
                    ()->rig.access.prepare(rig.op,rig.destination,rig.intent));
            assertEquals(List.of(VerifiedNiftiOutputAccess.AccessFault.OUTPUT_CONFLICT),error.faults());
            assertArrayEquals(other,java.nio.file.Files.readAllBytes(rig.finalPath));
            try (var entries=java.nio.file.Files.list(rig.privateRoot)) {
                assertEquals(0,entries.count());
            }
        }
    }
    @Test void T32_providerQualificationCannotBeSkipped() throws Exception {
        // A real cross-provider refusal: java.nio zipfs is intentionally not an approved
        // native POSIX/Windows filesystem. No fake hooks or environment inference.
        var archive=java.nio.file.Files.createTempFile("f8-unqualified-", ".zip");
        java.nio.file.Files.delete(archive);
        try(var zip=java.nio.file.FileSystems.newFileSystem(java.net.URI.create("jar:"+archive.toUri()),
                java.util.Map.of("create","true"))){
            var root=zip.getPath("/");
            java.nio.file.Files.createDirectory(root.resolve("private-stage"));
            try(var rig=new NativeRig()) {
                var refused=assertThrows(VerifiedNiftiOutputAccess.AccessFailure.class,
                        ()->new NioVerifiedNiftiOutputAccess(rig.source,root,
                                root.resolve("private-stage"),rig.store,1024,1024,1024));
                assertTrue(refused.faults().contains(VerifiedNiftiOutputAccess.AccessFault.PUBLICATION_UNAVAILABLE));
            }
        }
    }
}
