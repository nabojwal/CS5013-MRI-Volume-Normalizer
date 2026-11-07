package org.cbihi.mrinormalizer.infrastructure.ebo;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Set;
import org.cbihi.mrinormalizer.application.ebo.EboRequest;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;

/** No-follow, bounded top-level candidate inventory; M8 repeats its own containment checks. */
public final class EboFileDiscovery {
    public static final int MAX_CANDIDATES=256;
    public record SourceFile(String relativeName) {}
    private static boolean candidate(String name){
        String lowered=name.toLowerCase(Locale.ROOT);
        return lowered.endsWith(".nii")||lowered.endsWith(".nii.gz");
    }
    private static Path safeRoot(Path path){
        Path p=path.toAbsolutePath().normalize();
        // M8 accepts only local filesystem roots: refuse UNC before any job/report I/O.
        if(System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win")
                && p.toString().startsWith("\\\\"))
            throw new IllegalArgumentException("Unsupported filesystem root");
        try{
            Path cur=p.getRoot();
            for(Path segment:p){
                cur=cur.resolve(segment);
                if(Files.isSymbolicLink(cur))throw new IllegalArgumentException("Symlink path refused");
            }
            if(!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))
                throw new IllegalArgumentException("Directory required");
            return p.toRealPath(LinkOption.NOFOLLOW_LINKS);
        }catch(IOException|SecurityException e){throw new IllegalArgumentException("Directory unavailable",e);}
    }
    public List<SourceFile> discover(EboRequest request){
        Path out=safeRoot(request.outputRoot());
        Path in=request.mode()==EboRequest.Mode.DIRECTORY?safeRoot(request.input()):
            safeRoot(request.input().toAbsolutePath().normalize().getParent());
        if(request.mode()==EboRequest.Mode.DIRECTORY && (out.startsWith(in)||in.startsWith(out)))
            throw new IllegalArgumentException("Input/output roots overlap");
        List<SourceFile> found=new ArrayList<>();
        if(request.mode()==EboRequest.Mode.SINGLE){
            Path file=request.input().toAbsolutePath().normalize();
            validateCandidate(file);
            found.add(new SourceFile(file.getFileName().toString()));
        }else{
            try(DirectoryStream<Path> stream=Files.newDirectoryStream(in)){
                for(Path file:stream){
                    if(!candidate(file.getFileName().toString()))continue;
                    validateCandidate(file);
                    found.add(new SourceFile(file.getFileName().toString()));
                    if(found.size()>MAX_CANDIDATES)throw new IllegalArgumentException("Batch candidate limit exceeded");
                }
            }catch(IOException|SecurityException e){throw new IllegalArgumentException("Input enumeration failed",e);}
        }
        found.sort(Comparator.comparing(SourceFile::relativeName));
        if(found.isEmpty())throw new IllegalArgumentException("No NIfTI candidates");
        // D07: refuse colliding logical names and aliasing hard links before any M8 job.
        // Do not rely on BasicFileAttributes.fileKey(): native Windows Java may return null.
        Set<String> logicalNames=new HashSet<>();
        List<Path> originals=new ArrayList<>();
        for(SourceFile file:found){
            String logical=Normalizer.normalize(file.relativeName(),Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT);
            if(!logicalNames.add(logical))throw new IllegalArgumentException("Conflicting NIfTI input names");
            Path current=in.resolve(file.relativeName());
            validateCandidate(current);
            try {
                for(Path previous:originals){
                    // Files.isSameFile detects same-file hard links even where fileKey is null.
                    if(Files.isSameFile(previous,current))
                        throw new IllegalArgumentException("Duplicate NIfTI file identity");
                }
            } catch(IOException|SecurityException e){
                throw new IllegalArgumentException("Cannot verify NIfTI file identity",e);
            }
            originals.add(current);
        }
        return List.copyOf(found);
    }
    private static void validateCandidate(Path path){
        if(!candidate(path.getFileName().toString())||Files.isSymbolicLink(path)
                ||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("Unsafe or unsupported NIfTI candidate");
        new RelativePath(RelativePath.Root.SOURCE,path.getFileName().toString());
    }
}
