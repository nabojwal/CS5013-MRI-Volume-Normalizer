package org.cbihi.mrinormalizer.application.ebo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.cbihi.mrinormalizer.application.m8.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;

/** Sequential controller: never shares a single-use M8 service or writes DICOM itself. */
public final class EboBatchController {
    @FunctionalInterface public interface CandidateDiscovery {
        List<String> discover(EboRequest request);
    }
    @FunctionalInterface public interface JobRunner {
        M8Result run(M8Request request,Path sourceRoot,Path outputRoot);
    }
    @FunctionalInterface public interface ReportSink {
        Path publish(EboResult result,Path outputRoot) throws IOException;
    }
    private final CandidateDiscovery discovery;
    private final JobRunner runner;
    private final ReportSink sink;
    public EboBatchController(CandidateDiscovery discovery,JobRunner runner,ReportSink sink){
        this.discovery=java.util.Objects.requireNonNull(discovery);
        this.runner=java.util.Objects.requireNonNull(runner);
        this.sink=java.util.Objects.requireNonNull(sink);
    }
    public EboResult execute(EboRequest request) throws IOException {
        List<String> files=List.copyOf(discovery.discover(request));
        Path sourceRoot=request.mode()==EboRequest.Mode.SINGLE?request.input().toAbsolutePath().normalize().getParent():request.input();
        List<EboResult.Entry> entries=new ArrayList<>(files.size());
        boolean halt=false,interrupted=false,recovery=false;
        for(int i=0;i<files.size();i++){
            String filename=files.get(i); int ordinal=i+1;
            if(halt){entries.add(new EboResult.Entry(ordinal,filename,null,EboResult.EntryState.NOT_ATTEMPTED,
                null,null,0,null,List.of()));continue;}
            if(Thread.currentThread().isInterrupted()){
                interrupted=true;halt=true;
                entries.add(new EboResult.Entry(ordinal,filename,null,EboResult.EntryState.NOT_ATTEMPTED,
                    null,null,0,null,List.of()));continue;
            }
            UUID jobId=UUID.randomUUID();
            try{
                M8Request job=new M8Request(jobId,new RelativePath(RelativePath.Root.SOURCE,filename),EboEvaluationProfile.metadata());
                M8Result m8=java.util.Objects.requireNonNull(runner.run(job,sourceRoot,request.outputRoot()));
                if(!jobId.equals(m8.jobId()))throw new IllegalStateException("M8 job ID mismatch");
                boolean success=m8.status()==M8Result.Status.SUCCESS
                    &&m8.publication()==M8Result.Publication.COMPLETE_BYTES_VERIFIED;
                boolean critical=m8.status()==M8Result.Status.RECOVERY_REQUIRED
                    ||m8.publication()!=M8Result.Publication.NONE&&!success
                    ||m8.status()==M8Result.Status.SUCCESS&&!success;
                EboResult.EntryState state=critical?EboResult.EntryState.RECOVERY_REQUIRED:switch(m8.status()){
                    case SUCCESS -> EboResult.EntryState.SUCCESS;
                    case REJECTED -> EboResult.EntryState.REJECTED;
                    case FAILED -> EboResult.EntryState.FAILED;
                    case RECOVERY_REQUIRED -> EboResult.EntryState.RECOVERY_REQUIRED;
                };
                entries.add(new EboResult.Entry(ordinal,filename,jobId,state,m8.status().name(),
                    m8.publication().name(),m8.evidence().size(),
                    m8.outputDirectory().map(RelativePath::path).orElse(null),
                    m8.faults().stream().map(f->f.phase()+"/"+f.code()).toList()));
                if(critical){recovery=true;halt=true;}
            }catch(RuntimeException unexpected){
                // M8 may already have performed external effects. Never continue or invent success.
                recovery=true;halt=true;
                entries.add(new EboResult.Entry(ordinal,filename,jobId,EboResult.EntryState.RECOVERY_REQUIRED,
                    null,"UNKNOWN",0,null,List.of("CONTROLLER/UNEXPECTED_EXCEPTION")));
            }
            if(Thread.currentThread().isInterrupted()){
                interrupted=true;halt=true;
            }
        }
        int success=(int)entries.stream().filter(e->e.state()==EboResult.EntryState.SUCCESS).count();
        EboResult.OverallStatus overall=recovery?EboResult.OverallStatus.RECOVERY_REQUIRED:
            interrupted?EboResult.OverallStatus.INTERRUPTED:
            success==files.size()?EboResult.OverallStatus.SUCCESS:
            success==0?EboResult.OverallStatus.FAILED:EboResult.OverallStatus.PARTIAL_SUCCESS;
        var result=new EboResult(UUID.randomUUID(),request.mode().name(),entries,overall,Optional.empty());
        // A pre-existing interrupt would prevent the synchronous report I/O. Briefly clear
        // it only for best-effort reporting of NOT_ATTEMPTED inputs, then restore it.
        boolean restoreInterrupt=Thread.interrupted();
        try {return result.withReportPath(sink.publish(result,request.outputRoot()));}
        finally {if(restoreInterrupt)Thread.currentThread().interrupt();}
    }
}
