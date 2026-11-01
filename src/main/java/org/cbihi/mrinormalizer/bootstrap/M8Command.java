package org.cbihi.mrinormalizer.bootstrap;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.cbihi.mrinormalizer.application.conversion.M8GeometryPolicy;
import org.cbihi.mrinormalizer.application.m8.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;
import org.cbihi.mrinormalizer.application.service.M8ConversionService;
import org.cbihi.mrinormalizer.infrastructure.dicom.Dcm4cheMrSliceVerifier;
import org.cbihi.mrinormalizer.infrastructure.dicom.Dcm4cheMrSliceWriter;
import org.cbihi.mrinormalizer.infrastructure.filesystem.NioM8Workspace;

/** CLI composition root; not a GUI, forward workflow or dependency container. */
public final class M8Command {
    private M8Command() {}
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    public static int run(String[] args,PrintStream out,PrintStream err){
        if(args==null||out==null||err==null)return 2;
        final java.util.Set<String> flags=java.util.Set.of("--source-root","--input","--output-root",
                "--confirm-mr","--confirm-biped","--scanning-sequence","--sequence-variant");
        Map<String,String> params=new LinkedHashMap<>();
        try{
            for(int i=0;i<args.length;i++){
                String key=args[i];
                if(!flags.contains(key)||params.containsKey(key))throw new IllegalArgumentException();
                if(key.equals("--confirm-mr")||key.equals("--confirm-biped"))params.put(key,"true");
                else{if(i+1>=args.length||args[i+1].startsWith("--"))throw new IllegalArgumentException();params.put(key,args[++i]);}
            }
            if(params.size()!=flags.size())throw new IllegalArgumentException();
            M8Metadata metadata=new M8Metadata(true,true,
                    M8Metadata.ScanningSequence.valueOf(params.get("--scanning-sequence")),
                    M8Metadata.SequenceVariant.valueOf(params.get("--sequence-variant")));
            RelativePath relative=new RelativePath(RelativePath.Root.SOURCE,params.get("--input").replace('\\','/'));
            Path source=Path.of(params.get("--source-root"));
            Path output=Path.of(params.get("--output-root"));
            M8Request request=new M8Request(UUID.randomUUID(),relative,metadata);
            M8Limits limits=M8Limits.defaults();Clock clock=Clock.systemUTC();
            M8ConversionService service=new M8ConversionService(new NioM8Workspace(source,output,limits,clock),
                    new Dcm4cheMrSliceWriter(),new Dcm4cheMrSliceVerifier(limits),
                    new M8GeometryPolicy(limits,UUID::randomUUID),clock);
            M8Result result=service.execute(request);
            out.println("status="+result.status()+" jobId="+result.jobId()+" output="
                    +result.outputDirectory().map(RelativePath::path).orElse("NONE")
                    +" comparedSlices="+result.evidence().size()+" publication="+result.publication());
            for(M8Fault f:result.faults())err.println("fault="+f.phase()+"/"+f.code());
            return switch(result.status()){case SUCCESS->0;case REJECTED->2;case FAILED->3;case RECOVERY_REQUIRED->4;};
        }catch(IllegalArgumentException e){err.println("M8_USAGE_INVALID");return 2;}
        catch(RuntimeException e){err.println("M8_OPERATION_FAILED");return 4;}
    }
}
