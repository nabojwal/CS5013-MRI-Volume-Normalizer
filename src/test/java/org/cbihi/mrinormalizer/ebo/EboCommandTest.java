package org.cbihi.mrinormalizer.ebo;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import org.cbihi.mrinormalizer.bootstrap.EboCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** T01–T06, T14–T15, T19–T20: real M8 through EboCommand. */
public class EboCommandTest {
    @TempDir Path temp;
    public static byte[] nifti(int scalar, boolean gzip, ByteOrder endian, int depth) throws IOException {
        int w=3,h=2,n=w*h*depth, width=(scalar==2||scalar==256)?1:2;
        ByteBuffer b=ByteBuffer.allocate(352+n*width).order(endian);
        b.putInt(0,348);b.putShort(40,(short)3);b.putShort(42,(short)w);b.putShort(44,(short)h);
        b.putShort(46,(short)depth);b.putShort(70,(short)scalar);b.putShort(72,(short)(8*width));
        b.putFloat(76,1f);b.putFloat(80,1f);b.putFloat(84,1f);b.putFloat(88,1f);
        b.putFloat(108,352f);b.put(123,(byte)2);b.putShort(254,(short)1);
        b.putFloat(280,1f);b.putFloat(300,1f);b.putFloat(320,1f);
        b.put(344,(byte)'n');b.put(345,(byte)'+');b.put(346,(byte)'1');
        for(int i=0;i<n;i++) {
            int v=switch(scalar) {case 2->i%2==0?0:255;case 256->i%2==0?-128:127;
                case 512->i%2==0?0:65535;default->i%2==0?-32768:32767;};
            if(width==1)b.put(352+i,(byte)v);else b.putShort(352+2*i,(short)v);
        }
        if(!gzip)return b.array();
        var bytes=new ByteArrayOutputStream();
        try(var zip=new GZIPOutputStream(bytes)){zip.write(b.array());}
        return bytes.toByteArray();
    }
    private static List<Path> children(Path path)throws IOException {
        try(var stream=Files.list(path)){return stream.toList();}
    }
    /** Public M8 series are identified exclusively by the EBO report, never by a recursive
     * walk of the output root: M8 may retain .pai-m8-<jobId> private journal artifacts. */
    private record PublicSeries(String input, UUID jobId, Path path, int comparedSlices) {}
    private static List<PublicSeries> publishedSeries(Path output) throws IOException {
        List<Path> reports=children(output).stream()
            .filter(p->p.getFileName().toString().matches("ebo-[0-9a-f-]{36}-summary\\.json"))
            .toList();
        assertEquals(1,reports.size(),"Exactly one EBO report expected");
        String json=Files.readString(reports.getFirst());
        Pattern pattern=Pattern.compile("\\\"input\\\":\\\"([^\\\"]+)\\\",\\\"jobId\\\":\\\"([0-9a-f-]{36})\\\",\\\"state\\\":\\\"SUCCESS\\\",\\\"m8Status\\\":\\\"SUCCESS\\\",\\\"publication\\\":\\\"COMPLETE_BYTES_VERIFIED\\\",\\\"comparedSlices\\\":(\\d+),\\\"outputDirectory\\\":\\\"(m8-[0-9a-f-]{36})\\\"");
        Matcher matcher=pattern.matcher(json);
        List<PublicSeries> series=new ArrayList<>();
        while(matcher.find()) {
            UUID job=UUID.fromString(matcher.group(2));
            Path path=output.resolve(matcher.group(4));
            assertEquals("m8-"+job,matcher.group(4),"Report job/output identity mismatch");
            assertEquals(output.toAbsolutePath().normalize(),path.toAbsolutePath().normalize().getParent());
            assertTrue(Files.isDirectory(path),"Missing public series in report");
            series.add(new PublicSeries(matcher.group(1),job,path,Integer.parseInt(matcher.group(3))));
        }
        return series;
    }
    private static void assertPublicSeries(PublicSeries series,int expectedDepth) throws IOException {
        assertEquals(expectedDepth,series.comparedSlices());
        assertEquals(expectedDepth,children(series.path()).stream()
            .filter(p->p.getFileName().toString().matches("image-[0-9]{6}\\.dcm")).count());
        assertEquals(1,children(series.path()).stream()
            .filter(p->p.getFileName().toString().equals("PAI-M8-COMPLETE.json")).count());
    }
    private static String digest(Path path)throws Exception {
        var md=java.security.MessageDigest.getInstance("SHA-256");
        return java.util.HexFormat.of().formatHex(md.digest(Files.readAllBytes(path)));
    }
    private static String[] cli(Path input, Path output, boolean directory){return new String[]{
        directory?"--input-dir":"--input-file", input.toString(),"--output-root",output.toString(),
        "--metadata-profile","evaluation"};}
    private int run(String[] argv, ByteArrayOutputStream stdout) {
        return EboCommand.run(argv,new PrintStream(stdout),new PrintStream(new ByteArrayOutputStream()));
    }
    @Test void t01_t05_t14_singleRealJobAndExplicitTemplate()throws Exception {
        Path source=Files.write(temp.resolve("a.nii"),nifti(4,false,ByteOrder.LITTLE_ENDIAN,3));
        Path output=Files.createDirectory(temp.resolve("out")); var stdout=new ByteArrayOutputStream();
        String baseline=digest(source);
        assertEquals(0,run(cli(source,output,false),stdout));
        assertTrue(stdout.toString().contains("caller-selected non-clinical template assumptions"));
        assertTrue(stdout.toString().contains("SE"));assertTrue(stdout.toString().contains("NONE"));
        List<PublicSeries> series=publishedSeries(output);
        assertEquals(1,series.size());
        assertEquals("a.nii",series.getFirst().input());
        assertPublicSeries(series.getFirst(),3);
        assertEquals(baseline,digest(source));
    }
    @Test void t02_t15_directoryCreatesIndependentSeries()throws Exception {
        Path input=Files.createDirectory(temp.resolve("input")); Path output=Files.createDirectory(temp.resolve("out"));
        Files.write(input.resolve("a.nii"),nifti(4,false,ByteOrder.LITTLE_ENDIAN,3));
        Files.write(input.resolve("b.nii.gz"),nifti(512,true,ByteOrder.BIG_ENDIAN,1));
        assertEquals(0,run(cli(input,output,true),new ByteArrayOutputStream()));
        List<PublicSeries> series=publishedSeries(output);
        assertEquals(2,series.size());
        assertEquals(Set.of("a.nii","b.nii.gz"),Set.of(series.get(0).input(),series.get(1).input()));
        assertNotEquals(series.get(0).jobId(),series.get(1).jobId());
        for(PublicSeries item:series)assertPublicSeries(item,item.input().equals("a.nii")?3:1);
    }
    @Test void t03_t04_t06_usageIsFailClosed()throws Exception {
        Path source=Files.write(temp.resolve("a.nii"),nifti(4,false,ByteOrder.LITTLE_ENDIAN,3));
        Path output=Files.createDirectory(temp.resolve("out"));
        assertEquals(2,run(new String[]{"--input-file",source.toString(),"--input-dir",temp.toString(),
            "--output-root",output.toString(),"--metadata-profile","evaluation"},new ByteArrayOutputStream()));
        assertEquals(2,run(new String[]{"--input-file",source.toString(),"--output-root",output.toString(),
            "--metadata-profile","clinical"},new ByteArrayOutputStream()));
        assertEquals(2,run(new String[]{"--input-file",source.toString(),"--output-root",output.toString(),
            "--metadata-profile","evaluation","--metadata-profile","evaluation"},new ByteArrayOutputStream()));
        assertEquals(2,run(cli(source,temp.resolve("absent"),false),new ByteArrayOutputStream()));
        assertEquals(0,children(output).size());
    }
    @Test void t12_hardLinkDuplicatesRejectedBeforeAnyM8JobOrReport() throws Exception {
        Path input=Files.createDirectory(temp.resolve("aliased-in"));
        Path output=Files.createDirectory(temp.resolve("aliased-out"));
        Path original=Files.write(input.resolve("a.nii"),nifti(4,false,ByteOrder.LITTLE_ENDIAN,3));
        Path alias=input.resolve("b.nii.gz");
        Files.createLink(alias,original);
        assertTrue(Files.isSameFile(original,alias));
        var stdout=new ByteArrayOutputStream();
        assertEquals(2,run(cli(input,output,true),stdout));
        assertEquals(0,children(output).size(),"Duplicate sources must be refused before M8 or report I/O");
        assertTrue(Files.isSameFile(original,alias),"Existing source aliases must remain intact");
    }
    @Test void t12_logicalNameCollisionsRejectedWhenFilesystemPermitsThem() throws Exception {
        Path input=Files.createDirectory(temp.resolve("case-in"));
        Path output=Files.createDirectory(temp.resolve("case-out"));
        Path first=Files.write(input.resolve("a.nii"),nifti(4,false,ByteOrder.LITTLE_ENDIAN,1));
        Path second=Files.write(input.resolve("A.NII"),nifti(4,false,ByteOrder.LITTLE_ENDIAN,1));
        if(Files.isSameFile(first,second))return; // Case-insensitive NTFS cannot have this pair.
        assertEquals(2,run(cli(input,output,true),new ByteArrayOutputStream()));
        assertTrue(children(output).isEmpty(),"Logical filename conflict must stop before M8/report I/O");
    }
    @Test void t19_t20_scalarEndiannessCompressionAndDepth()throws Exception {
        int jobs=0;
        for(int scalar:new int[]{2,256,4,512})for(ByteOrder order:new ByteOrder[]{ByteOrder.LITTLE_ENDIAN,ByteOrder.BIG_ENDIAN})
            for(boolean gzip:new boolean[]{false,true})for(int depth:new int[]{1,3}){
                Path input=Files.write(temp.resolve("input-"+jobs+(gzip?".nii.gz":".nii")),nifti(scalar,gzip,order,depth));
                Path out=Files.createDirectory(temp.resolve("output-"+jobs));jobs++;
                var stdout=new ByteArrayOutputStream();
                assertEquals(0,run(cli(input,out,false),stdout),"case="+jobs+" stdout="+stdout);
                List<PublicSeries> series=publishedSeries(out);
                assertEquals(1,series.size());
                assertPublicSeries(series.getFirst(),depth);
            }
        assertEquals(32,jobs);
    }
}
