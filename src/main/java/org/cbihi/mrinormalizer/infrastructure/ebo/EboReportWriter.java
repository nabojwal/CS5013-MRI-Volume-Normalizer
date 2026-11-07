package org.cbihi.mrinormalizer.infrastructure.ebo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import org.cbihi.mrinormalizer.application.ebo.*;

/** Small bounded JSON report. Same-filesystem no-replace link publication, verified by readback. */
public final class EboReportWriter {
    private static String q(String value){
        if(value==null)return "null";
        StringBuilder b=new StringBuilder("\"");
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            switch(c){case '"'->b.append("\\\"");case '\\'->b.append("\\\\");
                case '\n'->b.append("\\n");case '\r'->b.append("\\r");case '\t'->b.append("\\t");
                default->{if(c<32)b.append(String.format("\\u%04x",(int)c));else b.append(c);}}
        }
        return b.append('"').toString();
    }
    private static void field(StringBuilder b,String key,String value){b.append(q(key)).append(':').append(q(value));}
    private static void number(StringBuilder b,String key,int value){b.append(q(key)).append(':').append(value);}
    private static String json(EboResult r){
        StringBuilder b=new StringBuilder(1024+r.entries().size()*256);
        b.append('{');field(b,"schemaVersion","PAI-EBO-REPORT-1.0");b.append(',');
        field(b,"batchId",r.batchId().toString());b.append(',');field(b,"inputMode",r.inputMode());b.append(',');
        b.append("\"metadataProfile\":{");field(b,"name","evaluation");b.append(',');
        field(b,"source",EboEvaluationProfile.DISCLOSURE);b.append(',');
        b.append("\"mrOriginConfirmed\":true,\"bipedCoordinatesConfirmed\":true,");
        field(b,"scanningSequence","SE");b.append(',');field(b,"sequenceVariant","NONE");b.append("},");
        number(b,"discoveredCount",r.discoveredCount());b.append(',');
        number(b,"attemptedCount",r.attemptedCount());b.append(',');
        number(b,"successCount",r.successCount());b.append(',');
        number(b,"rejectedCount",r.rejectedCount());b.append(',');
        number(b,"failedCount",r.failedCount());b.append(',');
        number(b,"recoveryRequiredCount",r.recoveryRequiredCount());b.append(',');
        number(b,"unattemptedCount",r.unattemptedCount());b.append(',');
        field(b,"overallStatus",r.overallStatus().name());b.append(",\"entries\":[");
        for(int i=0;i<r.entries().size();i++){
            EboResult.Entry e=r.entries().get(i);if(i>0)b.append(',');b.append('{');
            number(b,"ordinal",e.ordinal());b.append(',');field(b,"input",e.input());b.append(',');
            field(b,"jobId",e.jobId()==null?null:e.jobId().toString());b.append(',');
            field(b,"state",e.state().name());b.append(',');field(b,"m8Status",e.m8Status());b.append(',');
            field(b,"publication",e.publication());b.append(',');number(b,"comparedSlices",e.comparedSlices());b.append(',');
            field(b,"outputDirectory",e.outputDirectory());b.append(",\"faults\":[");
            for(int j=0;j<e.faults().size();j++){if(j>0)b.append(',');b.append(q(e.faults().get(j)));}
            b.append("]}");
        }
        return b.append("]}\n").toString();
    }
    public Path publish(EboResult result,Path outputRoot) throws IOException {
        Path root=outputRoot.toRealPath();
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid report root");
        Path finalReport=root.resolve("ebo-"+result.batchId()+"-summary.json");
        byte[] content=json(result).getBytes(StandardCharsets.UTF_8);
        Path stage=Files.createTempFile(root,".ebo-"+result.batchId()+"-",".tmp");
        try{
            try(FileChannel channel=FileChannel.open(stage,StandardOpenOption.WRITE)){
                ByteBuffer b=ByteBuffer.wrap(content);
                while(b.hasRemaining())channel.write(b);
                channel.force(true);
            }
            Files.createLink(finalReport,stage); // Atomic no-replace publication on supported local stores.
            if(!java.util.Arrays.equals(content,Files.readAllBytes(finalReport)))
                throw new IOException("Published EBO report differs from staged bytes");
        }finally{
            // Never delete published report; remove only our private temporary name.
            Files.deleteIfExists(stage);
        }
        return finalReport;
    }
}
