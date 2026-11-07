package org.cbihi.mrinormalizer.application.service;

import java.time.*;
import java.util.*;
import org.cbihi.mrinormalizer.application.conversion.NiftiAffineMapper;
import org.cbihi.mrinormalizer.application.dataset.nifti.*;
import org.cbihi.mrinormalizer.application.port.out.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.*;
import org.cbihi.mrinormalizer.application.request.*;
import org.cbihi.mrinormalizer.application.result.*;
import org.cbihi.mrinormalizer.application.validation.*;
import org.cbihi.mrinormalizer.domain.model.*;

/** One-shot F8 application workflow; never assumes a byte hash proves journal ACK. */
public final class VerifiedConversionEvidenceService implements AutoCloseable {
    private final DicomSeriesService m6;
    private final NiftiVolumeWriter m7;
    private final NiftiVolumeReader f7;
    private final VerifiedNiftiOutputAccess access;
    private final ManifestStore store;
    private final NiftiOutputComparator comparator;
    private final Clock clock;
    private boolean used,closed;

    public VerifiedConversionEvidenceService(DicomSeriesService existingM6,NiftiVolumeWriter existingM7Writer,
            NiftiVolumeReader outputRootBoundF7,VerifiedNiftiOutputAccess outputAccess,
            ManifestStore v2JobStore,NiftiOutputComparator comparator,Clock clock) {
        this.m6=Objects.requireNonNull(existingM6);this.m7=Objects.requireNonNull(existingM7Writer);
        this.f7=Objects.requireNonNull(outputRootBoundF7);this.access=Objects.requireNonNull(outputAccess);
        this.store=Objects.requireNonNull(v2JobStore);this.comparator=Objects.requireNonNull(comparator);
        this.clock=Objects.requireNonNull(clock);
    }
    private static ManifestFailure issue(ManifestFailure.Phase p,ManifestFailure.Code c) {return new ManifestFailure(p,c);}
    private static ManifestFailure failed() {return issue(ManifestFailure.Phase.POST_WRITE_VALIDATION,ManifestFailure.Code.VERIFICATION_FAILED);}
    private static final class StateHolder {
        ManifestReceipt head; boolean intent;
        Instant start,finish;
        int count;
        ContentDigest d1;
        ProcessingEvidence process;
        PostWriteVerificationEvidence evidence;
        ContentDigest finalDigest;
        VerifiedNiftiOutputAccess.PreparedOutput stage;
        VerifiedConversionResult.Publication publication=VerifiedConversionResult.Publication.NOT_PUBLISHED;
        ProvenancePersistenceException persistence;
        EnumSet<ConversionFault> faults=EnumSet.noneOf(ConversionFault.class);
        EnumSet<NiftiComparisonFailure> compare=EnumSet.noneOf(NiftiComparisonFailure.class);
        List<ManifestFailure> workflow=new ArrayList<>();
        boolean uncertain;
    }
    public synchronized VerifiedConversionResult executeOne(VerifiedConversionRequest req) {
        if(used||closed)throw new IllegalStateException("F8 execution is one-use");
        used=true;
        var st=new StateHolder();
        if(req!=null)st.head=req.acknowledgedRunningHead();
        try{
            if(req==null) throw new Failed(ConversionFault.PLAN_MISMATCH);
            var initial=store.replay(Optional.of(req.acknowledgedRunningHead()));
            if(initial.isEmpty()||!initial.orElseThrow().receipt().equals(req.acknowledgedRunningHead())
                    ||initial.orElseThrow().plan().schemaVersion()!=2
                    ||initial.orElseThrow().state()!=ManifestState.JobState.RUNNING)
                throw new Failed(ConversionFault.PLAN_MISMATCH);
            var plan=initial.orElseThrow().plan();
            var op=plan.operations().stream().filter(o->o.operationId().equals(req.operationId()))
                    .findFirst().orElseThrow(()->new Failed(ConversionFault.PLAN_MISMATCH));
            var observed=initial.orElseThrow().operations().stream().filter(o->o.operationId().equals(req.operationId()))
                    .findFirst().orElseThrow(()->new Failed(ConversionFault.PLAN_MISMATCH));
            if(op.kind()!=ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI||!op.destination().equals(req.destination())
                    ||observed.observation().state()!=State.NOT_STARTED)
                throw new Failed(ConversionFault.PLAN_MISMATCH);
            var sources=new ArrayList<SourceFileRecord>();
            for(var r:op.sources()){
                var file=plan.sources().stream().filter(x->x.source().equals(r)).findFirst()
                        .orElseThrow(()->new Failed(ConversionFault.PLAN_MISMATCH));
                if(file.digest().isEmpty()||!file.failures().isEmpty())throw new Failed(ConversionFault.PLAN_MISMATCH);
                sources.add(file);
            }
            VerifiedNiftiOutputAccess.SourceBinding binding;
            try{binding=access.validateSelectedSources(req.selectedInputs(),sources);}
            catch(VerifiedNiftiOutputAccess.AccessFailure ex){merge(st,ex);throw new Controlled();}
            if(!binding.sources().equals(op.sources()))throw new Failed(ConversionFault.SOURCE_BINDING_UNPROVEN);
            st.count=binding.matchedExpectedSourceCount();
            st.start=clock.instant();
            st.head=append(req,st,State.IN_PROGRESS,Optional.empty(),List.of());
            st.intent=true;
            try{st.stage=access.prepare(req.operationId(),req.destination(),st.head);}
            catch(VerifiedNiftiOutputAccess.AccessFailure ex){merge(st,ex);throw new Controlled();}
            // Exactly one call to M6 is allowed: M7 receives the captured, not re-created, source volume.
            final DicomProcessingResult[] capture=new DicomProcessingResult[1];
            final int[] calls={0};
            DicomSeriesService once = call -> {
                if(++calls[0]!=1 || call==null || !req.selectedInputs().equals(call.inputs())
                        || !req.selectedSeriesInstanceUid().equals(call.selectedSeriesInstanceUid()))
                    throw new IllegalStateException("M6 capture contract refused");
                return capture[0]=m6.process(call);
            };
            DicomToNiftiResult written;
            try {
                written=new DefaultDicomToNiftiService(once,m7).convert(new DicomToNiftiRequest(
                        req.selectedInputs(),req.selectedSeriesInstanceUid(),st.stage.stageTarget()));
            }catch(IllegalStateException ex){throw new Failed(ConversionFault.M6_CAPTURE_INVALID);}
            catch(RuntimeException ex){throw new Failed(ConversionFault.M7_FAILED);}
            if(calls[0]!=1||capture[0]==null)throw new Failed(ConversionFault.M6_CAPTURE_INVALID);
            if(!capture[0].successful())throw new Failed(ConversionFault.M6_FAILED);
            if(!written.successful())throw new Failed(ConversionFault.M7_FAILED);
            st.process=ManifestProjection.conversion(written);
            if(!written.output().equals(st.stage.stageTarget()))throw new Failed(ConversionFault.REPORT_INCONSISTENT);
            try{st.d1=st.stage.digestClosedStage();}
            catch(VerifiedNiftiOutputAccess.AccessFailure ex){merge(st,ex);throw new Controlled();}
            st.finish=clock.instant();
            st.head=append(req,st,State.WRITTEN_UNVERIFIED,Optional.empty(),List.of());
            NiftiReadResult reopened;
            try{reopened=f7.read(st.stage.stageReadReference());}
            catch(RuntimeException ex){throw new Failed(ConversionFault.REOPEN_FAILED);}
            if(reopened.failures().contains(NiftiReadFailure.INTERRUPTED)) {
                Thread.currentThread().interrupt();throw new Failed(ConversionFault.INTERRUPTED);
            }
            if(reopened.failures().contains(NiftiReadFailure.RESOURCE_LIMIT))throw new Failed(ConversionFault.RESOURCE_LIMIT);
            if(!reopened.failures().isEmpty())throw new Failed(ConversionFault.REOPEN_FAILED);
            try {
                if(!st.d1.equals(st.stage.digestClosedStage()))throw new Failed(ConversionFault.STAGE_CHANGED);
            }catch(VerifiedNiftiOutputAccess.AccessFailure ex){merge(st,ex);throw new Controlled();}
            var compared=comparator.compare(capture[0].volume(),written.validationReport(),reopened,
                    st.stage.sessionId(),st.d1);
            if(compared.verified().isEmpty()){
                st.compare.addAll(compared.failures());
                for(var c:compared.failures())st.faults.add(comparisonFault(c));
                st.workflow.add(failed());throw new Controlled();
            }
            st.evidence=compared.verified().orElseThrow();
            st.head=append(req,st,State.STAGED_VERIFIED,Optional.of(st.evidence),List.of());
            VerifiedNiftiOutputAccess.PublicationResult publication;
            try {publication=st.stage.publishVerified(st.head);}
            catch(VerifiedNiftiOutputAccess.AccessFailure ex){merge(st,ex);throw new Controlled();}
            if(publication.publication()!=VerifiedNiftiOutputAccess.Publication.PUBLISHED_BYTES_CHECKED){
                for(var a:publication.faults())addFault(st,a);
                st.uncertain=publication.publication()==VerifiedNiftiOutputAccess.Publication.UNKNOWN;
                st.publication=st.uncertain?VerifiedConversionResult.Publication.UNKNOWN
                        :VerifiedConversionResult.Publication.NOT_PUBLISHED;
                if(st.uncertain)st.faults.add(ConversionFault.PUBLICATION_UNKNOWN);
                else st.faults.add(ConversionFault.OUTPUT_CONFLICT);
                throw new Controlled();
            }
            st.publication=VerifiedConversionResult.Publication.PUBLISHED_UNACKNOWLEDGED;
            st.finalDigest=publication.finalDigest().orElseThrow();
            st.head=append(req,st,State.VERIFIED_PUBLISHED,Optional.of(st.evidence),List.of());
            // Independent current-head proof; a return from append alone is not sufficient.
            var finalState=store.replay(Optional.of(st.head));
            if(finalState.isEmpty()||!finalState.orElseThrow().receipt().equals(st.head)){
                st.faults.add(ConversionFault.FINAL_ACK_FAILED);st.uncertain=true;throw new Controlled();
            }
            st.publication=VerifiedConversionResult.Publication.VERIFIED_AND_ACKNOWLEDGED;
            try{st.stage.close();}
            catch(VerifiedNiftiOutputAccess.AccessFailure ex){merge(st,ex);st.uncertain=true;}
            if(st.uncertain)return result(st,VerifiedConversionResult.Status.RECOVERY_REQUIRED);
            return result(st,VerifiedConversionResult.Status.VERIFIED_PUBLISHED);
        }catch(ProvenancePersistenceException e){
            st.persistence=e;
            st.faults.add(st.publication==VerifiedConversionResult.Publication.PUBLISHED_UNACKNOWLEDGED
                    ?ConversionFault.FINAL_ACK_FAILED:st.d1!=null?ConversionFault.STAGED_ACK_FAILED:ConversionFault.INTENT_NOT_ACKNOWLEDGED);
            if(e.outcome()!=ProvenancePersistenceException.PublicationOutcome.NOT_PUBLISHED || st.intent || st.stage!=null)st.uncertain=true;
        }catch(Failed failure){st.faults.add(failure.fault);}
        catch(Controlled ignored){}
        catch(RuntimeException unexpected){st.faults.add(ConversionFault.PUBLICATION_UNAVAILABLE);st.uncertain=true;}
        // Failure path: delete only owned staging where no link attributable is possible.
        if(st.stage!=null){
            try{
                if(st.publication==VerifiedConversionResult.Publication.NOT_PUBLISHED && !st.uncertain)
                    st.stage.discardUnpublished();
            }catch(VerifiedNiftiOutputAccess.AccessFailure ex){merge(st,ex);st.uncertain=true;}
            try{st.stage.close();}
            catch(VerifiedNiftiOutputAccess.AccessFailure ex){merge(st,ex);st.uncertain=true;}
        }
        if(st.intent && st.publication==VerifiedConversionResult.Publication.NOT_PUBLISHED && !st.uncertain){
            if(st.workflow.isEmpty())st.workflow.add(issue(ManifestFailure.Phase.EXECUTION,ManifestFailure.Code.VERIFICATION_FAILED));
            try {
                append(req,st,State.FAILED,st.evidence==null?Optional.empty():Optional.of(st.evidence),st.workflow);
            }catch(ProvenancePersistenceException e){st.persistence=e;st.uncertain=true;st.faults.add(ConversionFault.FINAL_ACK_FAILED);}
            catch(RuntimeException e){st.uncertain=true;st.faults.add(ConversionFault.FINAL_ACK_FAILED);}
        }
        if(st.uncertain&&st.publication==VerifiedConversionResult.Publication.NOT_PUBLISHED)
            st.publication=VerifiedConversionResult.Publication.UNKNOWN;
        return result(st,st.uncertain||st.publication!=VerifiedConversionResult.Publication.NOT_PUBLISHED
                ?VerifiedConversionResult.Status.RECOVERY_REQUIRED:VerifiedConversionResult.Status.FAILED);
    }
    private ManifestReceipt append(VerifiedConversionRequest request,StateHolder st,State state,
            Optional<PostWriteVerificationEvidence> verified,List<ManifestFailure> wf){
        var details=verified.map(v->new ConversionVerification(v.sessionId(),v.stagedDigest(),
                v.comparedVoxelCount(),v.comparedCornerCount(),v.maxWorldDeviationMm(),v.weakSourceIdentityObserved(),v.policyId()));
        Optional<Instant> start=Optional.ofNullable(st.start);
        Optional<Instant> finish=Optional.ofNullable(st.finish);
        if(state==State.FAILED&&finish.isEmpty())finish=Optional.of(clock.instant());
        var record=new CheckpointRecord(2,request.acknowledgedRunningHead().jobId(),st.head.sequence()+1,
                st.head.headSha256(),clock.instant(),Kind.OPERATION_OBSERVED,Optional.of(request.operationId()),
                Optional.of(new Observation(state,start,finish,Optional.ofNullable(st.d1),
                        st.count,Optional.empty(),Optional.ofNullable(st.process),details,
                        state==State.FAILED?List.copyOf(st.faults):List.of())),Optional.empty(),wf);
        var candidate=store.append(record,st.head);
        var replayed=store.replay(Optional.of(candidate));
        if(replayed.isEmpty()||!replayed.orElseThrow().receipt().equals(candidate))
            throw new IllegalStateException("Journal ACK not confirmed");
        st.head=candidate;
        return candidate;
    }
    private static ConversionFault comparisonFault(NiftiComparisonFailure f){
        return switch(f){
            case VOXEL_COUNT_DIFFERS -> ConversionFault.DIMENSIONS_DIFFER;
            case READ_INTERRUPTED -> ConversionFault.INTERRUPTED;
            case WEAK_IDENTITY_UNQUALIFIED -> ConversionFault.WEAK_IDENTITY_UNQUALIFIED;
            default -> ConversionFault.valueOf(f.name());
        };
    }
    private static void merge(StateHolder st,VerifiedNiftiOutputAccess.AccessFailure e){for(var f:e.faults())addFault(st,f);}
    private static void addFault(StateHolder st,VerifiedNiftiOutputAccess.AccessFault fault){
        ConversionFault code=switch(fault){
            case SOURCE_PATH_INVALID, SOURCE_CONTAINMENT_UNPROVEN -> ConversionFault.SOURCE_BINDING_UNPROVEN;
            case SOURCE_CHANGED, SOURCE_DIGEST_MISMATCH -> ConversionFault.SOURCE_DIGEST_UNPROVEN;
            case STAGE_HASH_FAILED -> ConversionFault.STAGE_DIGEST_FAILED;
            default -> ConversionFault.valueOf(fault.name());
        };
        st.faults.add(code);
        ManifestFailure m=switch(fault){
            case SOURCE_PATH_INVALID -> issue(ManifestFailure.Phase.EXECUTION,ManifestFailure.Code.INVALID_REFERENCE);
            case SOURCE_CONTAINMENT_UNPROVEN -> issue(ManifestFailure.Phase.EXECUTION,ManifestFailure.Code.CONTAINMENT_UNPROVEN);
            case RESOURCE_LIMIT -> issue(ManifestFailure.Phase.EXECUTION,ManifestFailure.Code.RESOURCE_LIMIT);
            case STAGE_CREATE_FAILED,STAGE_CLOSE_FAILED -> issue(ManifestFailure.Phase.EXECUTION,ManifestFailure.Code.WRITE_FAILED);
            case OUTPUT_CONFLICT -> issue(ManifestFailure.Phase.EXECUTION,ManifestFailure.Code.OUTPUT_CONFLICT);
            case PUBLICATION_UNAVAILABLE -> issue(ManifestFailure.Phase.EXECUTION,ManifestFailure.Code.PUBLICATION_UNAVAILABLE);
            case PUBLICATION_UNKNOWN,RELEASE_FAILED -> issue(ManifestFailure.Phase.EXECUTION,ManifestFailure.Code.RECOVERY_REQUIRED);
            case CLEANUP_FAILED -> issue(ManifestFailure.Phase.EXECUTION,ManifestFailure.Code.CLEANUP_FAILED);
            case INTERRUPTED -> issue(ManifestFailure.Phase.EXECUTION,ManifestFailure.Code.INTERRUPTED);
            default -> issue(ManifestFailure.Phase.EXECUTION,ManifestFailure.Code.VERIFICATION_FAILED);
        };
        if(!st.workflow.contains(m))st.workflow.add(m);
        if(fault==VerifiedNiftiOutputAccess.AccessFault.RELEASE_FAILED||fault==VerifiedNiftiOutputAccess.AccessFault.CLEANUP_FAILED
                ||fault==VerifiedNiftiOutputAccess.AccessFault.PUBLICATION_UNKNOWN)st.uncertain=true;
    }
    private static VerifiedConversionResult result(StateHolder s,VerifiedConversionResult.Status status){
        var publication=status==VerifiedConversionResult.Status.FAILED?VerifiedConversionResult.Publication.NOT_PUBLISHED:s.publication;
        return new VerifiedConversionResult(status,publication,Optional.ofNullable(s.head),Optional.ofNullable(s.evidence),
                status==VerifiedConversionResult.Status.VERIFIED_PUBLISHED?Optional.ofNullable(s.finalDigest):Optional.empty(),
                List.copyOf(s.faults),List.copyOf(s.compare),s.workflow,Optional.ofNullable(s.persistence));
    }
    private static final class Failed extends RuntimeException {
        private final ConversionFault fault;
        Failed(ConversionFault f){super(null,null,false,false);fault=f;}
    }
    private static final class Controlled extends RuntimeException {
        Controlled(){super(null,null,false,false);}
    }
    @Override public synchronized void close(){
        if(closed)return;closed=true;
        access.close();
        // Store belongs to caller; do not close it from the borrowed F8 service.
    }
}
