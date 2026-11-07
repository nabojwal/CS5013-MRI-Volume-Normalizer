package org.cbihi.mrinormalizer.infrastructure.provenance;

import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadFinding;
import org.cbihi.mrinormalizer.application.m8.*;
import org.cbihi.mrinormalizer.application.port.out.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.ContentDigest;
import org.cbihi.mrinormalizer.domain.model.*;

/** Strict versioned UTF-8 canonical JSON; unsupported fields and noncanonical bytes are rejected. */
public final class M8JournalCodec {
    private final M8Limits limits;
    public M8JournalCodec(M8Limits limits){this.limits=java.util.Objects.requireNonNull(limits);}
    private static Map<String,Object> o(Object... kv){
        Map<String,Object> r=new LinkedHashMap<>();
        for(int i=0;i<kv.length;i+=2)r.put((String)kv[i],kv[i+1]);
        return r;
    }
    public byte[] encode(M8Workspace.Checkpoint cp){
        if(cp==null)throw invalid();
        M8Plan p=cp.plan();
        var ps=new ArrayList<Object>();
        for(M8Plan.Slice slice:p.slices())ps.add(o("sourceZ",slice.sourceZ(),"instanceNumber",slice.instanceNumber(),
                "filename",slice.filename(),"sopUid",slice.sopUid(),"positionDs",slice.positionDs()));
        List<Object> fs=new ArrayList<>();
        for(M8Workspace.SliceEvidence x:cp.slices())fs.add(o("sourceZ",x.sourceZ(),"filename",x.filename(),
                "sopUid",x.sopUid(),"size",x.digest().sizeBytes(),"sha256",x.digest().sha256(),
                "comparedVoxels",x.check().comparedVoxels(),"comparedCorners",x.check().comparedCorners(),
                "maxWorldError",Double.toHexString(x.check().maxWorldErrorMm()),
                "maxIntensityError",Double.toHexString(x.check().maxIntensityError())));
        List<Object> faults=new ArrayList<>();
        for(M8Fault f:cp.faults())faults.add(o("phase",f.phase().name(),"code",f.code().name()));
        Map<String,Object> plan=o("jobId",p.jobId().toString(),"sourceSize",p.sourceDigest().sizeBytes(),
                "sourceSha256",p.sourceDigest().sha256(),"sourceScalar",p.sourceScalar().name(),
                "width",p.width(),"height",p.height(),"depth",p.depth(),
                "affine",affine(p.sourceRasAffine()),
                "intensityDeclared",p.sourceIntensity().declared(),
                "slope",Double.toHexString(p.sourceIntensity().slope()),
                "intercept",Double.toHexString(p.sourceIntensity().intercept()),
                "findings",p.sourceFindings().stream().map(Enum::name).toList(),
                "mrConfirmed",p.metadata().mrOriginConfirmed(),"bipedConfirmed",p.metadata().bipedCoordinatesConfirmed(),
                "scanning",p.metadata().scanningSequence().name(),"variant",p.metadata().sequenceVariant().name(),
                "generatedAt",p.generatedAt().toString(),"studyUid",p.studyUid(),"seriesUid",p.seriesUid(),
                "frameUid",p.frameUid(),"orientationDs",p.orientationDs(),"pixelSpacingDs",p.pixelSpacingDs(),
                "sliceSpacingDs",p.sliceSpacingDs().orElse(null),"slopeDs",p.slopeDs(),
                "interceptDs",p.interceptDs(),"stepSign",p.sliceStepSign(),"slices",ps);
        Map<String,Object> root=o("schema","PAI-M8-JOURNAL-1","profile","M8-MR-1",
                "jobId",cp.plan().jobId().toString(),"sequence",cp.sequence(),
                "previousSha256",cp.previousSha256(),"event",cp.event().name(),
                "utc",cp.utc().toString(),"plan",plan,"slices",fs,"faults",faults);
        StringBuilder b=new StringBuilder();append(b,root,0);b.append('\n');
        byte[] bytes=b.toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>limits.maxCheckpointBytes())throw invalid();
        return bytes;
    }
    private static List<String> affine(AffineMatrix4 a){
        List<String> vals=new ArrayList<>();for(int i=0;i<4;i++)for(int j=0;j<4;j++)vals.add(Double.toHexString(a.get(i,j)));
        return vals;
    }
    private static void append(StringBuilder b,Object x,int depth){
        if(depth>16)throw invalid();
        if(x==null){b.append("null");return;}
        if(x instanceof Boolean||x instanceof Integer||x instanceof Long){b.append(x);return;}
        if(x instanceof String s){b.append('"');for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            switch(c){case '"'->b.append("\\\"");case '\\'->b.append("\\\\");
                case '\n'->b.append("\\n");case '\r'->b.append("\\r");case '\t'->b.append("\\t");
                default->{if(c<32)b.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)c));else b.append(c);}
            }}b.append('"');return;}
        if(x instanceof Map<?,?> m){b.append('{');boolean first=true;for(var e:m.entrySet()){
            if(!first)b.append(',');first=false;append(b,e.getKey(),depth+1);b.append(':');append(b,e.getValue(),depth+1);
        }b.append('}');return;}
        if(x instanceof List<?> list){b.append('[');boolean first=true;for(var item:list){
            if(!first)b.append(',');first=false;append(b,item,depth+1);
        }b.append(']');return;}
        throw invalid();
    }
    public M8Workspace.Checkpoint decode(byte[] bytes){
        if(bytes==null||bytes.length<2||bytes.length>limits.maxCheckpointBytes())throw invalid();
        String s;
        try{s=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        }catch(java.nio.charset.CharacterCodingException e){throw invalid();}
        if(!s.endsWith("\n")||s.startsWith("\ufeff"))throw invalid();
        Object parsed=new Parser(s).parse();
        try{
            Map<String,Object> root=map(parsed,"schema","profile","jobId","sequence","previousSha256",
                    "event","utc","plan","slices","faults");
            if(!"PAI-M8-JOURNAL-1".equals(root.get("schema"))||!"M8-MR-1".equals(root.get("profile")))throw invalid();
            Map<String,Object> p=map(root.get("plan"),"jobId","sourceSize","sourceSha256","sourceScalar",
                    "width","height","depth","affine","intensityDeclared","slope","intercept",
                    "findings","mrConfirmed","bipedConfirmed","scanning","variant","generatedAt",
                    "studyUid","seriesUid","frameUid","orientationDs","pixelSpacingDs",
                    "sliceSpacingDs","slopeDs","interceptDs","stepSign","slices");
            List<M8Plan.Slice> slices=new ArrayList<>();
            for(Object el:list(p.get("slices"))){var m=map(el,"sourceZ","instanceNumber","filename","sopUid","positionDs");
                slices.add(new M8Plan.Slice(integer(m.get("sourceZ")),integer(m.get("instanceNumber")),
                        str(m.get("filename")),str(m.get("sopUid")),strings(m.get("positionDs"))));}
            List<String> aff=strings(p.get("affine"));if(aff.size()!=16)throw invalid();
            double[][] coords=new double[4][4];for(int i=0;i<16;i++)coords[i/4][i%4]=Double.valueOf(aff.get(i));
            List<NiftiReadFinding> findings=new ArrayList<>();
            for(String f:strings(p.get("findings")))findings.add(NiftiReadFinding.valueOf(f));
            M8Plan plan=new M8Plan(UUID.fromString(str(p.get("jobId"))),
                    new ContentDigest(number(p.get("sourceSize")),str(p.get("sourceSha256"))),
                    ScalarType.valueOf(str(p.get("sourceScalar"))),integer(p.get("width")),integer(p.get("height")),
                    integer(p.get("depth")),new AffineMatrix4(coords),
                    new IntensityTransform(bool(p.get("intensityDeclared")),Double.valueOf(str(p.get("slope"))),
                            Double.valueOf(str(p.get("intercept")))),findings,
                    new M8Metadata(bool(p.get("mrConfirmed")),bool(p.get("bipedConfirmed")),
                            M8Metadata.ScanningSequence.valueOf(str(p.get("scanning"))),
                            M8Metadata.SequenceVariant.valueOf(str(p.get("variant")))),
                    Instant.parse(str(p.get("generatedAt"))),str(p.get("studyUid")),str(p.get("seriesUid")),
                    str(p.get("frameUid")),strings(p.get("orientationDs")),strings(p.get("pixelSpacingDs")),
                    Optional.ofNullable(p.get("sliceSpacingDs")).map(M8JournalCodec::str),
                    str(p.get("slopeDs")),str(p.get("interceptDs")),integer(p.get("stepSign")),slices);
            List<M8Workspace.SliceEvidence> evidence=new ArrayList<>();
            for(Object el:list(root.get("slices"))){var m=map(el,"sourceZ","filename","sopUid","size",
                    "sha256","comparedVoxels","comparedCorners","maxWorldError","maxIntensityError");
                evidence.add(new M8Workspace.SliceEvidence(integer(m.get("sourceZ")),str(m.get("filename")),
                        str(m.get("sopUid")),new ContentDigest(number(m.get("size")),str(m.get("sha256"))),
                        new DicomMrSliceVerifier.Check(number(m.get("comparedVoxels")),integer(m.get("comparedCorners")),
                                Double.valueOf(str(m.get("maxWorldError"))),Double.valueOf(str(m.get("maxIntensityError"))))));}
            List<M8Fault> faults=new ArrayList<>();for(Object el:list(root.get("faults"))){
                var m=map(el,"phase","code");faults.add(new M8Fault(M8Fault.Phase.valueOf(str(m.get("phase"))),
                        M8Fault.Code.valueOf(str(m.get("code")))));}
            M8Workspace.Checkpoint result=new M8Workspace.Checkpoint(number(root.get("sequence")),
                    str(root.get("previousSha256")),M8Workspace.Event.valueOf(str(root.get("event"))),
                    Instant.parse(str(root.get("utc"))),plan,evidence,faults);
            if(!str(root.get("jobId")).equals(plan.jobId().toString())||!java.util.Arrays.equals(encode(result),bytes))throw invalid();
            return result;
        }catch(RuntimeException e){throw invalid();}
    }
    private static String str(Object o){if(!(o instanceof String s))throw invalid();return s;}
    private static long number(Object o){if(!(o instanceof Long l))throw invalid();return l;}
    private static int integer(Object o){return Math.toIntExact(number(o));}
    private static boolean bool(Object o){if(!(o instanceof Boolean b))throw invalid();return b;}
    @SuppressWarnings("unchecked") private static List<Object> list(Object o){if(!(o instanceof List<?> l))throw invalid();return (List<Object>)l;}
    private static List<String> strings(Object o){List<String> out=new ArrayList<>();for(Object x:list(o))out.add(str(x));return out;}
    private static Map<String,Object> map(Object o,String... keys){
        if(!(o instanceof Map<?,?> v)||v.size()!=keys.length)throw invalid();
        Map<String,Object> m=new LinkedHashMap<>();int i=0;
        for(var e:v.entrySet()){
            if(!keys[i++].equals(e.getKey()))throw invalid();m.put((String)e.getKey(),e.getValue());
        }return m;
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid M8 journal checkpoint");}
    /** Strict JSON parser: rejects duplicate keys, invalid escape, whitespace, trailing data, noncanonical encodings. */
    private static final class Parser{
        private final String s; private int p;
        Parser(String s){this.s=s;}
        Object parse(){Object x=value(0);if(p!=s.length()-1||s.charAt(p)!='\n')throw invalid();return x;}
        private Object value(int depth){if(depth>16||p>=s.length())throw invalid();char c=s.charAt(p);
            if(c=='{')return object(depth+1);if(c=='[')return array(depth+1);if(c=='"')return string();
            if(s.startsWith("true",p)){p+=4;return true;}if(s.startsWith("false",p)){p+=5;return false;}
            if(s.startsWith("null",p)){p+=4;return null;}
            if(c=='-'||(c>='0'&&c<='9'))return integer();throw invalid();
        }
        private Map<String,Object> object(int depth){p++;Map<String,Object> o=new LinkedHashMap<>();
            if(peek('}')){p++;return o;}
            for(;;){String k=string();if(!peek(':'))throw invalid();p++;Object v=value(depth);
                if(o.containsKey(k))throw invalid();o.put(k,v);
                if(peek('}')){p++;return o;}if(!peek(','))throw invalid();p++;}
        }
        private List<Object> array(int depth){p++;List<Object> r=new ArrayList<>();if(peek(']')){p++;return r;}
            for(;;){r.add(value(depth));if(peek(']')){p++;return r;}if(!peek(','))throw invalid();p++;}
        }
        private String string(){if(!peek('"'))throw invalid();p++;StringBuilder b=new StringBuilder();
            for(;;){if(p>=s.length())throw invalid();char c=s.charAt(p++);if(c=='"')return b.toString();
                if(c=='\\'){if(p>=s.length())throw invalid();char e=s.charAt(p++);switch(e){
                    case '"','\\','/'->b.append(e);case 'n'->b.append('\n');case 'r'->b.append('\r');
                    case 't'->b.append('\t');case 'b'->b.append('\b');case 'f'->b.append('\f');
                    case 'u'->{if(p+4>s.length())throw invalid();b.append((char)Integer.parseInt(s.substring(p,p+4),16));p+=4;}
                    default->throw invalid();}}
                else if(c<32)throw invalid();else b.append(c);}
        }
        private long integer(){int begin=p;if(peek('-'))p++;if(p>=s.length())throw invalid();
            if(peek('0'))p++;else{if(s.charAt(p)<'1'||s.charAt(p)>'9')throw invalid();
                while(p<s.length()&&Character.isDigit(s.charAt(p)))p++;}
            return Long.parseLong(s.substring(begin,p));
        }
        private boolean peek(char x){return p<s.length()&&s.charAt(p)==x;}
    }
}
