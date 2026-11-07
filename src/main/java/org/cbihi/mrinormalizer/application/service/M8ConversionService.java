package org.cbihi.mrinormalizer.application.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.cbihi.mrinormalizer.application.conversion.M8GeometryPolicy;
import org.cbihi.mrinormalizer.application.dataset.nifti.*;
import org.cbihi.mrinormalizer.application.m8.*;
import org.cbihi.mrinormalizer.application.port.out.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.ContentDigest;
import org.cbihi.mrinormalizer.domain.model.ImageVolume;

/** Single-use M8 workflow. No predecessor service and no forward conversion invocation. */
public final class M8ConversionService {
    private final M8Workspace workspace;
    private final DicomMrSliceWriter writer;
    private final DicomMrSliceVerifier verifier;
    private final M8GeometryPolicy planner;
    private final Clock clock;
    private boolean executed;
    public M8ConversionService(M8Workspace workspace, DicomMrSliceWriter writer,
                               DicomMrSliceVerifier verifier,M8GeometryPolicy planner,Clock clock){
        this.workspace=java.util.Objects.requireNonNull(workspace);
        this.writer=java.util.Objects.requireNonNull(writer);
        this.verifier=java.util.Objects.requireNonNull(verifier);
        this.planner=java.util.Objects.requireNonNull(planner);
        this.clock=java.util.Objects.requireNonNull(clock);
    }
    public synchronized M8Result execute(M8Request request){
        if(executed)throw new IllegalStateException("M8 service is single-use");
        executed=true;
        if(request==null)throw new IllegalArgumentException("Request required");
        M8Workspace.Session session=null;
        M8Plan plan=null;
        M8Workspace.Receipt receipt=null;
        ContentDigest digest=null;
        List<M8Workspace.SliceEvidence> evidence=new ArrayList<>();
        List<NiftiReadFinding> findings=new ArrayList<>();
        List<NiftiReadFailure> sourceFailures=new ArrayList<>();
        List<M8Fault> faults=new ArrayList<>();
        M8Result.Status status=M8Result.Status.REJECTED;
        M8Result.Publication publication=M8Result.Publication.NONE;
        boolean journaled=false,publishAttempted=false,releaseSucceeded=false;
        try {
            if(!request.metadata().complete())throw M8Fault.fail(M8Fault.Phase.REQUEST,M8Fault.Code.METADATA_REQUIRED);
            session=workspace.open(request);
            digest=session.sourceDigest();
            // A gzip source can expand far beyond encoded size. Preflight F7's maximum
            // configured voxel allocation before asking F7 to allocate its long[].
            long maxVoxelBytes;
            try { maxVoxelBytes=Math.multiplyExact(16L,32_000_000L); }
            catch(ArithmeticException e){throw M8Fault.fail(M8Fault.Phase.SOURCE,M8Fault.Code.RESOURCE_LIMIT);}
            if(Runtime.getRuntime().maxMemory() <= maxVoxelBytes + 256L*(1<<20))
                throw M8Fault.fail(M8Fault.Phase.SOURCE,M8Fault.Code.RESOURCE_LIMIT);
            NiftiReadResult result=session.readSnapshot();
            findings.addAll(result.findings());sourceFailures.addAll(result.failures());
            if(result.volume().isEmpty())throw new M8Fault.Failure(
                List.of(classifySourceFailure(result.failures())),
                result.failures().contains(NiftiReadFailure.CLOSE_FAILED));
            ImageVolume image=result.volume().orElseThrow();
            plan=planner.plan(request,image,findings,digest,Instant.now(clock));
            session.verifySnapshotUnchanged();
            receipt=session.acknowledge(M8Workspace.Event.INTENT,plan,List.of(),List.of());journaled=true;
            for(int z=0;z<plan.depth();z++){
                if(Thread.currentThread().isInterrupted())throw M8Fault.fail(M8Fault.Phase.WRITE,M8Fault.Code.INTERRUPTED);
                try(OutputStream target=session.createStage(z)){
                    writer.write(image,plan,z,target);
                } catch(java.io.IOException ioe) {
                    throw M8Fault.fail(M8Fault.Phase.WRITE,M8Fault.Code.WRITE_FAILED);
                }
                ContentDigest before=session.digestClosedStage(z);
                DicomMrSliceVerifier.Check check;
                try(InputStream input=session.reopenStage(z)){
                    check=verifier.verify(image,plan,z,input);
                } catch(java.io.IOException ioe) {
                    throw M8Fault.fail(M8Fault.Phase.RELEASE,M8Fault.Code.RELEASE_FAILED);
                }
                ContentDigest after=session.digestClosedStage(z);
                if(!before.equals(after))throw M8Fault.fail(M8Fault.Phase.VERIFY,M8Fault.Code.STAGE_CHANGED);
                evidence.add(new M8Workspace.SliceEvidence(z,plan.slices().get(z).filename(),
                        plan.slices().get(z).sopUid(),after,check));
            }
            session.verifySnapshotUnchanged();
            receipt=session.acknowledge(M8Workspace.Event.STAGED_VERIFIED,plan,evidence,List.of());
            receipt=session.acknowledge(M8Workspace.Event.PUBLISHING,plan,evidence,List.of());
            publishAttempted=true;
            session.publish(receipt,evidence);
            session.verifyPublished(evidence);
            receipt=session.acknowledge(M8Workspace.Event.PUBLISHED,plan,evidence,List.of());
            session.cleanupOwnedData();
            receipt=session.acknowledge(M8Workspace.Event.COMPLETED,plan,evidence,List.of());
            session.sealCompletion(receipt);
            publication=session.publicationKnowledge();
            session.close();releaseSucceeded=true;session=null;
            status=M8Result.Status.SUCCESS;
            return new M8Result(request.jobId(),status,publication,
                    Optional.of(new org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath(
                            org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath.Root.OUTPUT,
                            "m8-"+request.jobId())),Optional.of(digest),Optional.of(receipt),
                    evidence,findings,sourceFailures,List.of());
        }catch(RuntimeException error){
            M8Fault primary;
            boolean effectsUncertain=error instanceof M8Fault.Failure f && f.effectsUncertain();
            if(error instanceof M8Fault.Failure f){faults.addAll(f.faults());primary=faults.getFirst();}
            else primary=new M8Fault(M8Fault.Phase.WRITE,M8Fault.Code.INTERNAL_ERROR);
            if(faults.isEmpty())faults.add(primary);
            // An unexpected unchecked failure after effectful workspace operations is
            // inherently uncertain, even when cleanup subsequently succeeds.
            if(!(error instanceof M8Fault.Failure) && session!=null)effectsUncertain=true;
            boolean interrupted=primary.code()==M8Fault.Code.INTERRUPTED||Thread.currentThread().isInterrupted();
            if(session!=null){
                publication=session.publicationKnowledge();
                boolean uncertain=effectsUncertain||publishAttempted||publication!=M8Result.Publication.NONE
                        ||primary.code()==M8Fault.Code.ACK_UNCERTAIN
                        ||primary.code()==M8Fault.Code.PUBLICATION_UNCERTAIN;
                // Do not acknowledge FAILED until private cleanup is positively confirmed.
                if(!uncertain){
                    try{session.cleanupOwnedData();}
                    catch(RuntimeException e){uncertain=true;faults.add(new M8Fault(M8Fault.Phase.CLEANUP,M8Fault.Code.CLEANUP_FAILED));}
                }
                if(journaled&&!uncertain&&plan!=null){
                    try{receipt=session.acknowledge(M8Workspace.Event.FAILED,plan,evidence,faults);}
                    catch(RuntimeException e){uncertain=true;faults.add(new M8Fault(M8Fault.Phase.JOURNAL,M8Fault.Code.ACK_UNCERTAIN));}
                }
                if(uncertain){
                    try{if(plan!=null)session.acknowledge(M8Workspace.Event.RECOVERY_REQUIRED,plan,evidence,faults);}
                    catch(RuntimeException ignored){/* retain the initial failure; no false acknowledgment */}
                }
                try{session.close();releaseSucceeded=true;}
                catch(RuntimeException e){uncertain=true;faults.add(new M8Fault(M8Fault.Phase.RELEASE,M8Fault.Code.RELEASE_FAILED));}
                status=uncertain?M8Result.Status.RECOVERY_REQUIRED:
                        journaled?M8Result.Status.FAILED:M8Result.Status.REJECTED;
                publication=session.publicationKnowledge();
            }else status=effectsUncertain?M8Result.Status.RECOVERY_REQUIRED:M8Result.Status.REJECTED;
            if(interrupted)Thread.currentThread().interrupt();
            return new M8Result(request.jobId(),status,publication,
                    publication==M8Result.Publication.NONE?Optional.empty():Optional.of(new org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath(
                            org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath.Root.OUTPUT,"m8-"+request.jobId())),
                    Optional.ofNullable(digest),Optional.ofNullable(receipt),
                    evidence,findings,sourceFailures,faults);
        }
    }
    /** Preserve F7's frozen, typed source classifications; no manufactured success. */
    private static M8Fault classifySourceFailure(List<NiftiReadFailure> failures){
        if(failures.isEmpty())return new M8Fault(M8Fault.Phase.SOURCE,M8Fault.Code.INVALID_INPUT);
        NiftiReadFailure f=failures.getFirst();
        M8Fault.Code code=switch(f){
            case PATH_OUTSIDE_ROOT,SYMLINK_DISALLOWED,CONTAINMENT_UNPROVEN -> M8Fault.Code.CONTAINMENT_UNPROVEN;
            case SOURCE_CHANGED -> M8Fault.Code.SOURCE_CHANGED;
            case SPECIAL_FILE_UNSUPPORTED,UNSUPPORTED_VARIANT,UNSUPPORTED_DIMENSIONALITY,
                 UNSUPPORTED_DATATYPE,UNSUPPORTED_INTENT,UNSUPPORTED_SPATIAL_UNITS -> M8Fault.Code.UNSUPPORTED_INPUT;
            case INVALID_HEADER,INVALID_SCALING,GEOMETRY_UNAVAILABLE,TRANSFORM_DISAGREEMENT,
                 INVALID_EXTENSION,TRUNCATED_INPUT,TRAILING_DATA,GZIP_INTEGRITY_FAILED -> M8Fault.Code.INVALID_INPUT;
            case INPUT_UNAVAILABLE,READ_FAILED -> M8Fault.Code.READ_FAILED;
            case RESOURCE_LIMIT -> M8Fault.Code.RESOURCE_LIMIT;
            case INTERRUPTED -> M8Fault.Code.INTERRUPTED;
            case CLOSE_FAILED -> M8Fault.Code.RELEASE_FAILED;
        };
        return new M8Fault(f==NiftiReadFailure.CLOSE_FAILED?M8Fault.Phase.RELEASE:M8Fault.Phase.SOURCE,code);
    }
}
