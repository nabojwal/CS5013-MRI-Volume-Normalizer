package org.cbihi.mrinormalizer.presentation.gui;

import java.io.IOException;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;

/** Isolated GUI output projection; original M8 publication and recovery evidence remain intact. */
public final class GuiI1OutputOrganizer {
    private GuiI1OutputOrganizer() {}
    public static String stem(String name) {
        String lower=name.toLowerCase(Locale.ROOT);
        String value=lower.endsWith(".nii.gz")?name.substring(0,name.length()-7)
                 :lower.endsWith(".nii")?name.substring(0,name.length()-4):"";
        if (value.isBlank()||value.length()>100||value.equals(".")||value.equals("..")||
            value.endsWith(".")||value.endsWith(" ")||
            value.matches("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?$"))
            throw new IllegalArgumentException("Unsafe NIfTI series name");
        for(char c:value.toCharArray())if(c<32||"<>:\"/\\|?*".indexOf(c)>=0)
            throw new IllegalArgumentException("Unsafe NIfTI series name");
        return value;
    }
    public static void names(List<String> names) {
        Set<String> used=new HashSet<>();
        for(String name:names)if(!used.add(Normalizer.normalize(stem(name),Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Duplicate output folder name");
    }
    private static Path directory(Path dir) throws IOException {
        if(Files.notExists(dir,LinkOption.NOFOLLOW_LINKS))Files.createDirectory(dir);
        if(!Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(dir))
            throw new IOException("Unsafe private workspace");
        return dir;
    }
    public static int run(Path input,String original,Path output,GuiI1Execution.Backend backend) throws IOException {
        Path named=output.resolve(stem(original));
        if(Files.exists(named,LinkOption.NOFOLLOW_LINKS))return 2;
        Path internal=directory(output.resolve(".pai-internal"));
        Path jobs=directory(internal.resolve("jobs"));
        Path job=Files.createDirectory(jobs.resolve("run-"+UUID.randomUUID()));
        if(System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win")
                && job.toString().length()+95>240) throw new IOException("Output path too deep for M8 staging");
        int exit=backend.ebo(new String[]{"--input-file",input.toString(),"--output-root",job.toString(),
                "--metadata-profile","evaluation"});
        if(exit!=0)return exit;
        List<Path> verified=new ArrayList<>();
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(job,"m8-*")) {
            for(Path p:entries)if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)&&!Files.isSymbolicLink(p))
                verified.add(p);
        }
        if(verified.size()!=1)throw new IOException("Missing/ambiguous verified M8 publication");
        Path originalPublic=verified.getFirst();
        // EBO exit 0 is the frozen authoritative verification/publication outcome.
        // Do not assume an undocumented marker filename or forge an M8 receipt.
        Path staging=Files.createDirectory(internal.resolve(".export-"+UUID.randomUUID()));
        int count=0;
        try(DirectoryStream<Path> content=Files.newDirectoryStream(originalPublic)) {
            for(Path source:content) {
                String name=source.getFileName().toString();
                if(name.equals("PAI-M8-COMPLETE.json"))continue;
                if(!name.matches("image-[0-9]{6}\\.dcm")||Files.isSymbolicLink(source)||
                        !Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Unexpected M8 published file");
                Path linked=staging.resolve(name);
                Files.createLink(linked,source);
                if(!Files.isSameFile(source,linked))throw new IOException("Hard-link identity mismatch");
                count++;
            }
        }
        if(count==0)throw new IOException("No verified DICOM files in publication");
        if(Files.exists(named,LinkOption.NOFOLLOW_LINKS))throw new IOException("Output name occupied");
        // No replacement; leave unpublished private stage if an error occurs.
        Files.move(staging,named);
        if(!Files.isDirectory(named,LinkOption.NOFOLLOW_LINKS))throw new IOException("Output export uncertain");
        return 0;
    }
}
