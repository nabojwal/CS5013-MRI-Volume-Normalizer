package org.cbihi.mrinormalizer.ebo;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.tools.ToolProvider;
import org.cbihi.mrinormalizer.application.ebo.EboRequest;
import org.cbihi.mrinormalizer.bootstrap.EboCommand;
import org.cbihi.mrinormalizer.infrastructure.ebo.EboFileDiscovery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Five strictly bounded executable closure assertions. Java 21, native Windows NTFS.
 * Only synthetic medical-image fixtures. Does not write to the frozen demonstration tree. */
public final class EboAcceptanceClosureTest {
    @TempDir Path temp;

    private static String[] args(Path input, Path out, boolean dir) {
        return new String[]{dir ? "--input-dir" : "--input-file",input.toString(),
            "--output-root",out.toString(),"--metadata-profile","evaluation"};
    }
    private static int run(String[] argv, ByteArrayOutputStream stdout,ByteArrayOutputStream stderr){
        return EboCommand.run(argv,new PrintStream(stdout),new PrintStream(stderr));
    }
    private static List<Path> immediate(Path path) throws IOException {
        try(var stream=Files.list(path)){return stream.toList();}
    }
    private static List<Path> selected(Path root,String regex) throws IOException {
        return immediate(root).stream().filter(p->p.getFileName().toString().matches(regex))
            .sorted(Comparator.comparing(p->p.getFileName().toString())).toList();
    }
    private static String sha(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
    private static Map<String,String> publicInventory(Path series) throws Exception {
        return immediate(series).stream().filter(Files::isRegularFile).collect(Collectors.toMap(
            p->p.getFileName().toString(),p->{try{return sha(p);}catch(Exception e){throw new RuntimeException(e);}}));
    }
    private static Path tiny(Path dir,String name) throws Exception {
        return Files.write(dir.resolve(name),EboCommandTest.nifti(4,false,ByteOrder.LITTLE_ENDIAN,2));
    }
    private static void assertNoArtifacts(Path out) throws IOException {
        assertTrue(immediate(out).isEmpty(),"Preflight refusal must create no M8 series, private journal or EBO report");
    }
    private static void assertCorrectPublicSeries(Path path,int slices) throws IOException {
        assertTrue(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS));
        assertEquals(slices,selected(path,"image-[0-9]{6}\\.dcm").size());
        assertEquals(1,selected(path,"PAI-M8-COMPLETE\\.json").size());
    }

    @Test void t03_unknownFlagAndMissingValueReturn2WithoutArtifacts() throws Exception {
        Path in=tiny(temp,"a.nii"),out=Files.createDirectory(temp.resolve("out"));
        ByteArrayOutputStream stdout=new ByteArrayOutputStream(),stderr=new ByteArrayOutputStream();
        String[] unknown={"--input-file",in.toString(),"--output-root",out.toString(),
            "--metadata-profile","evaluation","--unknown","abc"};
        assertEquals(2,run(unknown,stdout,stderr));
        assertTrue(stderr.toString().contains("EBO_INVALID_REQUEST"));
        assertNoArtifacts(out);
        stdout.reset();stderr.reset();
        String[] missing={"--input-file",in.toString(),"--output-root",out.toString(),"--metadata-profile"};
        assertEquals(2,run(missing,stdout,stderr));
        assertTrue(stderr.toString().contains("EBO_INVALID_REQUEST"));
        assertNoArtifacts(out);
    }

    @Test void t06_missingInputAndNativeUncProviderAreRejectedInPreflight() throws Exception {
        Path out=Files.createDirectory(temp.resolve("out"));
        Path missing=temp.resolve("missing.nii");
        assertFalse(Files.exists(missing));
        ByteArrayOutputStream stdout=new ByteArrayOutputStream(),stderr=new ByteArrayOutputStream();
        assertEquals(2,run(args(missing,out,false),stdout,stderr));
        assertNoArtifacts(out);
        // Actual Windows SMB/UNC fixture is an M8-unsupported provider/root.
        // An unavailable fixture is a failed native qualification, never a silent pass.
        assertTrue(System.getProperty("os.name","").toLowerCase().contains("win"),
            "T06 requires native Windows");
        String uncName=System.getProperty("ebo.test.uncRoot","\\\\localhost\\F6Qualification$");
        Path unc=Path.of(uncName);
        assertTrue(Files.isDirectory(unc,LinkOption.NOFOLLOW_LINKS),"T06 BLOCKED: accessible UNC fixture required: "+uncName);
        Path input=tiny(temp,"valid.nii");
        EboRequest request=new EboRequest(EboRequest.Mode.SINGLE,input,unc,"evaluation");
        assertThrows(IllegalArgumentException.class,()->new EboFileDiscovery().discover(request),
            "UNSUPPORTED_PROVIDER: UNC output must be rejected before any M8 job or report I/O");
        assertNoArtifacts(out);
    }

    @Test void t12_actualWindowsSymlinkInputRefusedWithoutArtifacts() throws Exception {
        assertTrue(System.getProperty("os.name","").toLowerCase().contains("win"),
            "T12 requires native Windows symlink qualification");
        Path in=Files.createDirectory(temp.resolve("in")),out=Files.createDirectory(temp.resolve("out"));
        Path target=tiny(in,"target.nii");
        Path link=in.resolve("alias.nii");
        try { Files.createSymbolicLink(link,target.getFileName()); }
        catch (IOException | UnsupportedOperationException | SecurityException e) {
            fail("T12 BLOCKED: native Windows symbolic link fixture unavailable; record privilege/provider: "+e,e);
        }
        assertTrue(Files.isSymbolicLink(link));
        var before=sha(target);
        ByteArrayOutputStream stdout=new ByteArrayOutputStream(),stderr=new ByteArrayOutputStream();
        assertEquals(2,run(args(in,out,true),stdout,stderr));
        assertTrue(stderr.toString().contains("EBO_INVALID_REQUEST"));
        assertNoArtifacts(out);
        assertEquals(before,sha(target),"Symlink target must remain unchanged");
    }

    @Test void t18_realEboCommandRepeatPreservesPriorSeriesMarkerAndReportBytes() throws Exception {
        Path in=tiny(temp,"synthetic.nii"),out=Files.createDirectory(temp.resolve("out"));
        var inputHash=sha(in);
        ByteArrayOutputStream stdout=new ByteArrayOutputStream(),stderr=new ByteArrayOutputStream();
        assertEquals(0,run(args(in,out,false),stdout,stderr),"Run 1: "+stderr);
        var reports1=selected(out,"ebo-[0-9a-f-]{36}-summary\\.json");
        var series1=selected(out,"m8-[0-9a-f-]{36}");
        assertEquals(1,reports1.size());assertEquals(1,series1.size());
        assertCorrectPublicSeries(series1.getFirst(),2);
        String report1Sha=sha(reports1.getFirst());
        Map<String,String> inventory1=publicInventory(series1.getFirst());
        assertEquals(3,inventory1.size(),"2 public DICOM + 1 marker");
        stdout.reset();stderr.reset();
        assertEquals(0,run(args(in,out,false),stdout,stderr),"Run 2: "+stderr);
        var reports2=selected(out,"ebo-[0-9a-f-]{36}-summary\\.json");
        var series2=selected(out,"m8-[0-9a-f-]{36}");
        assertEquals(2,reports2.size(),"Both EBO reports must persist");
        assertEquals(2,series2.size(),"Both M8 series must persist");
        Path second=series2.stream().filter(p->!p.equals(series1.getFirst())).findFirst().orElseThrow();
        assertCorrectPublicSeries(second,2);
        assertEquals(report1Sha,sha(reports1.getFirst()),"First report was modified");
        assertEquals(inventory1,publicInventory(series1.getFirst()),"First public series/marker was modified");
        assertEquals(inputHash,sha(in),"Synthetic source changed");
    }

    @Test void t26_faultInjectedReportWriterAfterRealM8PublicationReturns5AndPreservesSeries() throws Exception {
        Path input=tiny(temp,"synthetic.nii"),out=Files.createDirectory(temp.resolve("out"));
        Path shadowSrc=Files.createDirectories(temp.resolve("fault-src/org/cbihi/mrinormalizer/infrastructure/ebo"))
            .resolve("EboReportWriter.java");
        Path compiled=Files.createDirectory(temp.resolve("fault-classes"));
        // A deliberately isolated test-only replacement for report persistence. The actual
        // EboCommand bytecode and its real M8 conversion pipeline remain unmodified.
        String source="""
            package org.cbihi.mrinormalizer.infrastructure.ebo;
            import java.io.IOException;
            import java.nio.file.Files;
            import java.nio.file.Path;
            import org.cbihi.mrinormalizer.application.ebo.EboResult;
            public final class EboReportWriter {
                public static int calls;
                public static boolean publishedSeen;
                public static byte[] markerBefore;
                public static byte[] firstDicomBefore;
                public Path publish(EboResult result,Path out) throws IOException {
                    calls++;
                    if(result.successCount()!=1 || result.entries().size()!=1)
                        throw new IOException("Precondition: one completed M8 job required");
                    var entry=result.entries().get(0);
                    Path series=out.resolve(entry.outputDirectory());
                    publishedSeen=Files.isRegularFile(series.resolve("PAI-M8-COMPLETE.json"));
                    if(!publishedSeen)throw new IOException("Precondition: no published M8 marker");
                    markerBefore=Files.readAllBytes(series.resolve("PAI-M8-COMPLETE.json"));
                    firstDicomBefore=Files.readAllBytes(series.resolve("image-000001.dcm"));
                    throw new IOException("INJECTED_REPORT_PUBLICATION_FAILURE");
                }
            }
            """;
        Files.writeString(shadowSrc,source);
        var compiler=ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler,"JDK 21 compiler required for test-only class isolation");
        Path prod=Path.of(EboCommand.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        int compilation=compiler.run(null,null,null,"--release","21","-classpath",prod.toString(),
            "-d",compiled.toString(),shadowSrc.toString());
        assertEquals(0,compilation,"Test-only fault writer must compile");
        Set<String> isolate=Set.of("org.cbihi.mrinormalizer.bootstrap.EboCommand",
            "org.cbihi.mrinormalizer.infrastructure.ebo.EboReportWriter");
        try(URLClassLoader loader=new URLClassLoader(new URL[]{compiled.toUri().toURL(),prod.toUri().toURL()},
                EboAcceptanceClosureTest.class.getClassLoader()) {
            @Override protected synchronized Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                if(isolate.contains(name)){
                    Class<?> cls=findLoadedClass(name);
                    if(cls==null)cls=findClass(name);
                    if(resolve)resolveClass(cls);
                    return cls;
                }
                return super.loadClass(name,resolve);
            }
        }){
            Class<?> command=loader.loadClass("org.cbihi.mrinormalizer.bootstrap.EboCommand");
            var method=command.getMethod("run",String[].class,PrintStream.class,PrintStream.class);
            ByteArrayOutputStream stdout=new ByteArrayOutputStream(),stderr=new ByteArrayOutputStream();
            int code=(Integer)method.invoke(null,(Object)args(input,out,false),
                new PrintStream(stdout),new PrintStream(stderr));
            assertEquals(5,code,"Report publication uncertainty must surface as CLI exit 5");
            var writer=loader.loadClass("org.cbihi.mrinormalizer.infrastructure.ebo.EboReportWriter");
            assertEquals(1,writer.getField("calls").getInt(null));
            assertTrue(writer.getField("publishedSeen").getBoolean(null),
                "Injected failure must occur after real M8 public series is committed");
            var paths=selected(out,"m8-[0-9a-f-]{36}");
            assertEquals(1,paths.size());
            assertArrayEquals((byte[])writer.getField("markerBefore").get(null),
                Files.readAllBytes(paths.getFirst().resolve("PAI-M8-COMPLETE.json")),
                "M8 public marker altered by report failure");
            assertArrayEquals((byte[])writer.getField("firstDicomBefore").get(null),
                Files.readAllBytes(paths.getFirst().resolve("image-000001.dcm")),
                "M8 DICOM payload altered by report failure");
            assertTrue(stderr.toString().contains("EBO_REPORT_OR_CONTROLLER_UNCERTAIN"));
            assertFalse(stdout.toString().contains(" status=SUCCESS "),
                "No EBO batch SUCCESS claim may be printed");
        }
        assertTrue(selected(out,"ebo-[0-9a-f-]{36}-summary\\.json").isEmpty(),
            "Fault-injected EBO writer must not leave a success summary");
        var published=selected(out,"m8-[0-9a-f-]{36}");
        assertEquals(1,published.size(),"Published M8 series must be retained after report failure");
        assertCorrectPublicSeries(published.getFirst(),2);
        assertEquals(3,publicInventory(published.getFirst()).size());
    }
}
