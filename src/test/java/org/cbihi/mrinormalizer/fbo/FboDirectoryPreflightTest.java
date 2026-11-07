package org.cbihi.mrinormalizer.fbo;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import org.cbihi.mrinormalizer.application.fbo.FboRequest;
import org.cbihi.mrinormalizer.infrastructure.fbo.FboDirectoryPreflight;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

class FboDirectoryPreflightTest {
    @TempDir Path temp;
    private final List<Path> shortWindowsOutputs = new ArrayList<>();
    @AfterEach void removeEmptyTestOutputRoots() throws IOException {
        // Never recursively erase evidence: an unexpected produced artifact
        // causes this teardown to fail instead of being silently removed.
        for (Path output : shortWindowsOutputs) Files.deleteIfExists(output);
    }
    private FboRequest make(Path input, Path output, String... names) {
        List<FboRequest.SeriesSelection> selected=new ArrayList<>();
        for(int i=0;i<names.length;i++)selected.add(new FboRequest.SeriesSelection(names[i],"1.2."+(i+1)));
        return new FboRequest(input,output,selected);
    }
    private Path[] roots() throws IOException {
        Path input = Files.createDirectories(temp.resolve("input"));
        Path output;
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            String drive = System.getenv("SystemDrive");
            if (drive == null) throw new IOException("Windows SystemDrive must be configured");
            Path writable = Path.of(System.getProperty("fbo.test.systemDriveTemp", drive + "\\FBO")).toRealPath();
            if (!writable.getRoot().toString().equalsIgnoreCase(drive + "\\"))
                throw new IOException("FBO test output must use SystemDrive");
            output = Files.createTempDirectory(writable, "o-");
            shortWindowsOutputs.add(output);
        } else {
            output = Files.createDirectories(temp.resolve("output"));
        }
        return new Path[]{input, output};
    }
    private Path series(Path input, String child, int count) throws IOException {
        Path dir=Files.createDirectories(input.resolve(child));
        for (int i=1;i<=count;i++)Files.writeString(dir.resolve("image"+i+".dcm"),"fixture-"+i);
        return dir;
    }
    @Test void t03_t05_overlapAndNoOutputMutation() throws Exception {
        Path[] roots=roots();series(roots[0],"s",1);
        FboDirectoryPreflight p=new FboDirectoryPreflight();
        assertThrows(IOException.class,()->p.inspectAll(make(roots[0],roots[0],"s")));
        Path nested=Files.createDirectory(roots[0].resolve("nested"));
        assertThrows(IOException.class,()->p.inspectAll(make(roots[0],nested,"s")));
        try(var files=Files.list(roots[1])){assertEquals(0,files.count());}
    }
    @Test void t04_t06_refuseNestedNonregularAndEmptyAnd129() throws Exception {
        Path[] r=roots();Path dir=series(r[0],"s",1);
        FboDirectoryPreflight p=new FboDirectoryPreflight();
        Path nested=Files.createDirectory(dir.resolve("nested"));
        assertThrows(IOException.class,()->p.inspectAll(make(r[0],r[1],"s")));
        Files.delete(nested);
        Path empty=Files.createDirectory(r[0].resolve("empty"));
        assertThrows(IOException.class,()->p.inspectAll(make(r[0],r[1],"empty")));
        for(int i=2;i<=129;i++)Files.writeString(dir.resolve("image"+i+".dcm"),"x");
        var prepared129 = p.inspectAll(make(r[0],r[1],"s"));
        assertEquals(1, prepared129.series().size());
        assertEquals(129, prepared129.series().getFirst().members().size());
        p.assertUnchanged(prepared129.series().getFirst());
    }
    @Test void t07_t08_exactOrderedInventoryAndDrift() throws Exception {
        Path[] r=roots();series(r[0],"chosen",2);series(r[0],"unselected",1);
        FboDirectoryPreflight p=new FboDirectoryPreflight();
        var batch=p.inspectAll(make(r[0],r[1],"chosen"));
        assertEquals(1,batch.series().size());
        var initial=batch.series().getFirst();
        assertEquals(List.of("image1.dcm","image2.dcm"),initial.members().stream()
                .map(m->m.relativeName()).toList());
        p.assertUnchanged(initial);
        Files.writeString(r[0].resolve("chosen").resolve("image1.dcm"),"changed-by-test");
        assertThrows(IOException.class,()->p.assertUnchanged(initial));
    }
    @Test void t04_hardLinksRefusedWhenProviderSupportsThem() throws Exception {
        Path[] r=roots();Path dir=series(r[0],"s",1);
        Files.createLink(dir.resolve("alias.dcm"),dir.resolve("image1.dcm"));
        assertThrows(IOException.class,()->new FboDirectoryPreflight().inspectAll(make(r[0],r[1],"s")));
    }
    // @Test @EnabledOnOs(OS.WINDOWS)
    // void t03_t05_offSystemDriveRefusedWithoutFboOutputEffects() throws Exception {
    //     Path[] r=roots();series(r[0],"s",1);
    //     String sys=System.getenv("SystemDrive");
    //     assertNotNull(sys,"Native SystemDrive required");
    //     Path other=null;
    //     for(String drive:List.of("C:","D:","E:","F:")) {
    //         Path candidate=Path.of(drive+"\\");
    //         if(!candidate.getRoot().toString().equalsIgnoreCase(sys+"\\")
    //                 && Files.isDirectory(candidate,LinkOption.NOFOLLOW_LINKS)) { other=candidate;break; }
    //     }
    //     assertNotNull(other,"T03/T05 native fixture blocked: another existing drive is required");
    //     Path external=other;
    //     assertThrows(IOException.class,()->new FboDirectoryPreflight().inspectAll(make(r[0],external,"s")));
    //     try(var stream=Files.list(r[1])){assertTrue(stream.noneMatch(f->f.getFileName().toString().startsWith("fbo-")));}
    //     // T03: an off-system-drive input is allowed when the OUTPUT remains qualified.
    //     Path writable=Files.isDirectory(other.resolve("Projects")) ? other.resolve("Projects") : other;
    //     Path offDriveInput=Files.createTempDirectory(writable,"pai-fbo-offdrive-input-");
    //     try {
    //         series(offDriveInput,"s",1);
    //         assertNotNull(new FboDirectoryPreflight().inspectAll(make(offDriveInput,r[1],"s")));
    //     } finally {
    //         Files.deleteIfExists(offDriveInput.resolve("s").resolve("image1.dcm"));
    //         Files.deleteIfExists(offDriveInput.resolve("s"));
    //         Files.deleteIfExists(offDriveInput);
    //     }
    // }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void t03_t05_fixedLocalNtfsOffSystemDriveIsEligible() throws Exception {
        Path[] r = roots();
        series(r[0], "s", 1);

        String sys = System.getenv("SystemDrive");
        assertNotNull(sys, "Native SystemDrive required");

        Path other = null;

        for (String drive : List.of("C:", "D:", "E:", "F:")) {
            Path candidate = Path.of(drive + "\\");

            if (candidate.getRoot().toString()
                    .equalsIgnoreCase(sys + "\\")) {
                continue;
            }

            if (!Files.isDirectory(
                    candidate, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }

            if (!Files.getFileStore(candidate).type()
                    .equalsIgnoreCase("NTFS")) {
                continue;
            }

            int driveType = com.sun.jna.NativeLibrary
                    .getInstance("kernel32")
                    .getFunction("GetDriveTypeW")
                    .invokeInt(new Object[] {
                            new com.sun.jna.WString(
                                    candidate.getRoot().toString())
                    });

            if (driveType == 3) {
                other = candidate;
                break;
            }
        }

        assertNotNull(other,
                "D1 native fixture requires an independent fixed NTFS drive");

        Path external = Files.createTempDirectory(Files.isDirectory(other.resolve("Projects")) ? other.resolve("Projects") : other,"d1-");
        shortWindowsOutputs.add(external);

        // A fixed NTFS destination is no longer rejected
        // merely because it is outside SystemDrive.
        var prepared = new FboDirectoryPreflight()
                .inspectAll(make(r[0], external, "s"));

        assertEquals(1, prepared.series().size());

        // Preflight must not publish FBO artifacts.
        try (var stream = Files.list(external)) {
            assertTrue(stream.noneMatch(f ->
                    f.getFileName().toString().startsWith("fbo-")));
        }

        // Retain verification that an off-system-drive
        // DICOM input is permitted.
        Path writable = Files.isDirectory(other.resolve("Projects"))
                ? other.resolve("Projects")
                : other;

        Path offDriveInput = Files.createTempDirectory(
                writable, "pai-fbo-offdrive-input-");

        try {
            series(offDriveInput, "s", 1);

            assertNotNull(new FboDirectoryPreflight()
                    .inspectAll(make(offDriveInput, r[1], "s")));
        } finally {
            Files.deleteIfExists(
                    offDriveInput.resolve("s").resolve("image1.dcm"));
            Files.deleteIfExists(offDriveInput.resolve("s"));
            Files.deleteIfExists(offDriveInput);
        }
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void t05_missingOutputRefusedBeforeAnyBatchArtifact() throws Exception {
        Path[] r=roots();series(r[0],"s",1);
        Path absent=r[1].resolve("missing-output");
        assertThrows(IOException.class,()->new FboDirectoryPreflight().inspectAll(make(r[0],absent,"s")));
        assertFalse(Files.exists(absent));
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void t04_windowsActualSymlinkRefused() throws Exception {
        Path[] r=roots();Path dir=series(r[0],"s",1);
        Path link=dir.resolve("alias.dcm");
        Files.createSymbolicLink(link,Path.of("image1.dcm"));
        assertThrows(IOException.class,()->new FboDirectoryPreflight().inspectAll(make(r[0],r[1],"s")));
    }
}
