package org.cbihi.mrinormalizer.bootstrap;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import org.cbihi.mrinormalizer.application.ebo.*;
import org.cbihi.mrinormalizer.infrastructure.ebo.EboFileDiscovery;
import org.cbihi.mrinormalizer.infrastructure.ebo.EboReportWriter;
import org.cbihi.mrinormalizer.application.conversion.M8GeometryPolicy;
import org.cbihi.mrinormalizer.application.m8.*;
import org.cbihi.mrinormalizer.application.service.M8ConversionService;
import org.cbihi.mrinormalizer.infrastructure.dicom.*;
import org.cbihi.mrinormalizer.infrastructure.filesystem.NioM8Workspace;
import java.time.Clock;
import java.util.UUID;

/** Evaluation-only CLI. No DICOM or M8 engine behavior is reimplemented. */
public final class EboCommand {
    private EboCommand(){}
    /** Composition root creates all one-shot M8 adapters afresh per input. */
    private static M8Result runM8(M8Request request,Path source,Path output){
        M8Limits limits=M8Limits.defaults();Clock clock=Clock.systemUTC();
        var service=new M8ConversionService(new NioM8Workspace(source,output,limits,clock),
            new Dcm4cheMrSliceWriter(),new Dcm4cheMrSliceVerifier(limits),
            new M8GeometryPolicy(limits,UUID::randomUUID),clock);
        return service.execute(request);
    }
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    public static int run(String[] args,PrintStream out,PrintStream err){
        if(args==null||out==null||err==null)return 2;
        EboRequest request;
        List<String> selected;
        try{
            Map<String,String> options=new HashMap<>();
            for(int i=0;i<args.length;i+=2){
                if(i+1>=args.length||!java.util.Set.of("--input-file","--input-dir","--output-root","--metadata-profile").contains(args[i])
                    ||args[i+1].startsWith("--")||options.putIfAbsent(args[i],args[i+1])!=null)
                    throw new IllegalArgumentException("Invalid EBO command arguments");
            }
            if(options.size()!=3||options.containsKey("--input-file")==options.containsKey("--input-dir")
                ||!options.containsKey("--output-root")||!options.containsKey("--metadata-profile"))
                throw new IllegalArgumentException("One input and explicit metadata profile required");
            EboRequest.Mode mode=options.containsKey("--input-file")?EboRequest.Mode.SINGLE:EboRequest.Mode.DIRECTORY;
            Path input=Path.of(options.get(mode==EboRequest.Mode.SINGLE?"--input-file":"--input-dir"));
            request=new EboRequest(mode,input,Path.of(options.get("--output-root")),options.get("--metadata-profile"));
            // Discover and validate exactly once, before any M8 job or report I/O.
            // The immutable list passed to the controller is the one that passed preflight.
            selected=new EboFileDiscovery().discover(request).stream()
                .map(EboFileDiscovery.SourceFile::relativeName).toList();
        }catch(RuntimeException e){err.println("EBO_INVALID_REQUEST");return 2;}
        try{
            var profile=EboEvaluationProfile.metadata();
            out.println("metadataProfile=evaluation "+EboEvaluationProfile.DISCLOSURE
                +" mrOriginConfirmed="+profile.mrOriginConfirmed()
                +" bipedCoordinatesConfirmed="+profile.bipedCoordinatesConfirmed()
                +" scanningSequence="+profile.scanningSequence()
                +" sequenceVariant="+profile.sequenceVariant());
            EboResult result=new EboBatchController(
                r->selected,
                EboCommand::runM8,
                new EboReportWriter()::publish).execute(request);
            out.println("batchId="+result.batchId()+" status="+result.overallStatus()
                +" discovered="+result.discoveredCount()+" attempted="+result.attemptedCount()
                +" success="+result.successCount()+" rejected="+result.rejectedCount()
                +" failed="+result.failedCount()+" recoveryRequired="+result.recoveryRequiredCount()
                +" unattempted="+result.unattemptedCount()+" report="+result.reportPath().orElseThrow());
            for(var entry:result.entries())out.println("input="+entry.input()+" state="+entry.state()
                +" jobId="+(entry.jobId()==null?"NONE":entry.jobId())
                +" output="+(entry.outputDirectory()==null?"NONE":entry.outputDirectory())
                +" comparedSlices="+entry.comparedSlices()+" faults="+entry.faults());
            return switch(result.overallStatus()){
                case SUCCESS->0;case INVALID_REQUEST->2;case FAILED->3;
                case PARTIAL_SUCCESS->4;case RECOVERY_REQUIRED->5;case INTERRUPTED->130;
            };
        }catch(IOException|RuntimeException e){err.println("EBO_REPORT_OR_CONTROLLER_UNCERTAIN");return 5;}
    }
}
