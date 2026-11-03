package org.cbihi.mrinormalizer.presentation.gui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;
import org.cbihi.mrinormalizer.application.request.F9HeadlessRequest;
import org.cbihi.mrinormalizer.application.result.F9HeadlessResult;
import org.cbihi.mrinormalizer.bootstrap.EboCommand;
import org.cbihi.mrinormalizer.bootstrap.FboCommand;
import org.cbihi.mrinormalizer.infrastructure.headless.F9WindowsComposition;

/** UI dispatcher. Existing F9, EBO and FBO remain the only conversion authorities. */
public final class GuiI1Execution {
    public enum Route { F9_ONE_DICOM_SERIES, FBO_SEQUENTIAL_DICOM_BATCH, EBO_ONE_NIFTI, EBO_NIFTI_DIRECTORY }
    public enum State { VERIFIED, PARTIAL, REJECTED, FAILED, RECOVERY_REQUIRED, INTERRUPTED }
    public record Outcome(State state, Route route, String summary, Path outputRoot) {
        public Outcome { Objects.requireNonNull(state); Objects.requireNonNull(route);
            Objects.requireNonNull(summary); Objects.requireNonNull(outputRoot); }
    }
    /** Injection seam for offline GUI tests; production adapter never reimplements conversion. */
    public interface Backend {
        int ebo(String[] args);
        int fbo(String[] args);
        F9HeadlessResult f9(Path preparedSeries, Path outputRoot, String uid, List<String> memberNames);
    }
    private static final class RealBackend implements Backend {
        @Override public int ebo(String[] args) {
            try (var sink = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)) {
                return EboCommand.run(args, sink, sink);
            }
        }
        @Override public int fbo(String[] args) {
            try (var sink = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)) {
                return FboCommand.run(args, sink, sink);
            }
        }
        @Override public F9HeadlessResult f9(Path preparedSeries, Path outputRoot,
                                               String uid, List<String> memberNames) {
            UUID jobId = UUID.randomUUID();
            var relativeMembers = memberNames.stream()
                    .map(name -> new RelativePath(RelativePath.Root.SOURCE, name)).toList();
            var request = new F9HeadlessRequest(jobId, uid, relativeMembers,
                    new RelativePath(RelativePath.Root.OUTPUT, niftiFilename(preparedSeries)));
            var service = F9WindowsComposition.open(preparedSeries, outputRoot, jobId);
            F9HeadlessResult result;
            boolean closed = false;
            try {
                result = service.executeOne(request);
            } catch (RuntimeException error) {
                result = F9HeadlessResult.uncertain(F9HeadlessResult.Reason.PUBLICATION_UNCERTAIN,
                        java.util.Optional.empty());
            } finally {
                try { service.close(); closed = true; }
                catch (Exception uncertainClose) { closed = false; }
            }
            if (!closed) return F9HeadlessResult.uncertain(
                    F9HeadlessResult.Reason.PUBLICATION_UNCERTAIN, result.acknowledgedHead());
            return result;
        }
    }

    /** Name only from the selected local DICOM series directory; never from patient tags. */
    public static String niftiFilename(Path preparedSeries) {
        Objects.requireNonNull(preparedSeries, "preparedSeries");
        Path component = preparedSeries.getFileName();
        if (component == null) throw new IllegalArgumentException("Series directory has no local name");
        String stem = component.toString();
        if (stem.isBlank() || stem.length() > 100 || stem.equals(".") || stem.equals("..")
                || stem.endsWith(".") || stem.endsWith(" ")
                || stem.matches("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?$"))
            throw new IllegalArgumentException("Unsafe DICOM series directory name");
        for (char ch : stem.toCharArray())
            if (ch < 32 || "<>:\"/\\|?*".indexOf(ch) >= 0)
                throw new IllegalArgumentException("Unsafe DICOM series directory name");
        return stem.toLowerCase(Locale.ROOT).endsWith(".nii") ? stem : stem + ".nii";
    }

    private final Backend backend;
    public GuiI1Execution() { this(new RealBackend()); }
    public GuiI1Execution(Backend backend) { this.backend = Objects.requireNonNull(backend); }

    public static Route plan(GuiI1Scanner.ScanResult scan, List<Integer> selected) {
        Objects.requireNonNull(scan);
        if (selected == null || selected.isEmpty()) throw new IllegalArgumentException("Select at least one series");
        Set<Integer> unique = new HashSet<>();
        for (Integer index : selected)
            if (index == null || index < 0 || index >= scan.candidates().size() || !unique.add(index))
                throw new IllegalArgumentException("Invalid or duplicate selected row");
        if (scan.direction() == GuiI1Scanner.Direction.NIFTI_TO_DICOM) {
            if (selected.size() != scan.candidates().size())
                throw new IllegalArgumentException("EBO directory mode processes all discovered candidates");
            return scan.kind() == GuiI1Scanner.InputKind.NIFTI_FILE ? Route.EBO_ONE_NIFTI
                    : Route.EBO_NIFTI_DIRECTORY;
        }
        if (scan.kind() == GuiI1Scanner.InputKind.DICOM_DISCOVERY_ONLY)
            throw new IllegalArgumentException("Discovered series are not in a qualified prepared layout");
        for (int index : selected) {
            if (!scan.candidates().get(index).eligible())
                throw new IllegalArgumentException("Selected DICOM series exceeds the current qualified profile");
        }
        if (selected.size() > 16) throw new IllegalArgumentException("FBO supports at most 16 selected series");
        if (scan.kind() == GuiI1Scanner.InputKind.DICOM_DIRECT && selected.size() != 1)
            throw new IllegalArgumentException("One prepared directory can only be dispatched once");
        return selected.size() == 1 ? Route.F9_ONE_DICOM_SERIES : Route.FBO_SEQUENTIAL_DICOM_BATCH;
    }

    /** Admission check: safe directory, no links/overlap, local NTFS where required. */
    static Path validateOutput(GuiI1Scanner.ScanResult scan, Path selectedRoot, Route route) throws IOException {
        Path output = selectedRoot.toAbsolutePath().normalize();
        Path cursor = output.getRoot();
        if (cursor == null) throw new IOException("Destination root unavailable");
        for (Path segment : output) {
            cursor = cursor.resolve(segment);
            if (Files.isSymbolicLink(cursor)) throw new IOException("Linked destination refused");
        }
        if (!Files.isDirectory(output, LinkOption.NOFOLLOW_LINKS) || !output.toRealPath().equals(output))
            throw new IOException("Choose an existing, stable destination folder");
        Path input = scan.input().toAbsolutePath().normalize();
        if (input.equals(output) || input.startsWith(output) || output.startsWith(input))
            throw new IOException("Input and output must be independent directories");
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        if (windows) {
            String filesystem = Files.getFileStore(output).type();
            if (!"NTFS".equalsIgnoreCase(filesystem) || output.toString().startsWith("\\\\"))
                throw new IOException("Qualified local NTFS destination required");
            if (route == Route.F9_ONE_DICOM_SERIES || route == Route.FBO_SEQUENTIAL_DICOM_BATCH) {
                String drive = System.getenv("SystemDrive");
                if (drive == null || !output.getRoot().toString().equalsIgnoreCase(drive + "\\"))
                    throw new IOException("DICOM-to-NIfTI output must be on the Windows system drive");
                if (route == Route.FBO_SEQUENTIAL_DICOM_BATCH && output.toString().length() + 203 > 240)
                    throw new IOException("Destination too deep for qualified FBO staging");
                if (route == Route.F9_ONE_DICOM_SERIES && output.toString().length() + 128 > 240)
                    throw new IOException("Destination too deep for conservative F9 staging");
            }
        }
        return output;
    }

    private static Outcome classify(int exitCode, Route route, Path root) {
        return switch (exitCode) {
            case 0 -> new Outcome(State.VERIFIED, route, "Backend verified and published the requested output.", root);
            case 2 -> new Outcome(State.REJECTED, route, "Backend rejected the input or selected configuration.", root);
            case 3 -> new Outcome(State.FAILED, route, "Backend reported a conversion or verification failure.", root);
            case 4 -> new Outcome(State.PARTIAL, route, "Some series published; others did not. Inspect the backend report.", root);
            case 130 -> new Outcome(State.INTERRUPTED, route, "Backend interrupted; verify the report before further processing.", root);
            default -> new Outcome(State.RECOVERY_REQUIRED, route, "Publication or reporting is uncertain. Preserve the output directory.", root);
        };
    }

    @FunctionalInterface public interface Progress { void onEvent(String text); }
    public Outcome execute(GuiI1Scanner.ScanResult scan,List<Integer> selected,Path root) throws IOException {
        return execute(scan,selected,root,message->{});
    }
    public Outcome execute(GuiI1Scanner.ScanResult scan,List<Integer> selected,Path chosenOutput,
                           Progress progress) throws IOException {
        Route route=plan(scan,selected);
        Path output=validateOutput(scan,chosenOutput,route);
        if(Thread.currentThread().isInterrupted())return new Outcome(State.INTERRUPTED,route,"Interrupted before dispatch.",output);
        final String fingerprint;
        try {fingerprint=GuiI1CompletionLedger.fingerprint(scan,selected,output);}
        catch(IOException unverified) {return new Outcome(State.REJECTED,route,"Source identity could not be established.",output);}
        try {
            if(GuiI1CompletionLedger.exists(output,fingerprint))
                return new Outcome(State.REJECTED,route,"Identical input and destination already VERIFIED; conversion blocked.",output);
        } catch(IOException unsafe) {
            return new Outcome(State.RECOVERY_REQUIRED,route,"Unable to read completion history; do not retry automatically.",output);
        }
        Outcome result;
        try {
            if(route==Route.EBO_ONE_NIFTI||route==Route.EBO_NIFTI_DIRECTORY) {
                List<String> names=selected.stream().map(i->scan.candidates().get(i).memberNames().getFirst()).toList();
                GuiI1OutputOrganizer.names(names);
                for(String name:names)if(Files.exists(output.resolve(GuiI1OutputOrganizer.stem(name)),LinkOption.NOFOLLOW_LINKS))
                    return new Outcome(State.REJECTED,route,"Input-derived output already exists; refusing replacement.",output);
                int verified=0,failed=0,rejected=0;State halted=null;
                for(int i=0;i<names.size();i++) {
                    if(Thread.currentThread().isInterrupted()){halted=State.INTERRUPTED;break;}
                    String name=names.get(i);
                    progress.onEvent("Processing "+(i+1)+" of "+names.size()+": "+name);
                    Path file=route==Route.EBO_ONE_NIFTI?scan.input():scan.input().resolve(name);
                    int exit=GuiI1OutputOrganizer.run(file,name,output,backend);
                    State state=classify(exit,route,output).state();
                    progress.onEvent(name+" — "+state);
                    if(state==State.VERIFIED)verified++;
                    else if(state==State.FAILED)failed++;
                    else if(state==State.REJECTED)rejected++;
                    else {halted=state;break;}
                }
                State aggregate=halted!=null?halted:verified==names.size()?State.VERIFIED:
                    verified>0?State.PARTIAL:failed>0?State.FAILED:State.REJECTED;
                result=new Outcome(aggregate,route,"Verified "+verified+" of "+names.size()+
                        " input(s); failed "+failed+", rejected "+rejected+".",output);
            } else if(route==Route.F9_ONE_DICOM_SERIES) {
                var c=scan.candidates().get(selected.getFirst());
                String targetName=niftiFilename(c.directory());
                if(Files.exists(output.resolve(targetName),LinkOption.NOFOLLOW_LINKS))
                    return new Outcome(State.REJECTED,route,"Named NIfTI output already exists; refusing replacement: "+targetName,output);
                progress.onEvent("Processing "+c.label()+" ("+c.instanceCount()+" instances) → "+targetName);
                F9HeadlessResult f9=backend.f9(c.directory(),output,c.seriesUid(),c.memberNames());
                result=switch(f9.status()) {
                    case VERIFIED_PUBLISHED -> new Outcome(State.VERIFIED,route,"F9 reopened, verified and published the selected series.",output);
                    case FAILED -> new Outcome(State.FAILED,route,"F9 conversion or verification failed.",output);
                    case RECOVERY_REQUIRED -> new Outcome(State.RECOVERY_REQUIRED,route,"F9 publication/cleanup uncertain; preserve output.",output);
                };
                progress.onEvent(c.label()+" — "+result.state());
            } else {
                List<String> args=new ArrayList<>();
                args.add("--input-root");args.add(scan.input().toString());args.add("--output-root");args.add(output.toString());
                for(int index:selected) {
                    var c=scan.candidates().get(index);
                    if(c.childName()==null)throw new IllegalArgumentException("FBO requires prepared directories");
                    args.add("--series");args.add(c.childName());args.add(c.seriesUid());
                }
                progress.onEvent("Processing "+selected.size()+" selected DICOM series (FBO batch-level events only)");
                result=classify(backend.fbo(args.toArray(String[]::new)),route,output);
                progress.onEvent("DICOM batch — "+result.state());
            }
        } catch(IllegalArgumentException invalid) {
            return new Outcome(State.REJECTED,route,"Unsafe or colliding output names.",output);
        } catch(IOException | RuntimeException uncertain) {
            return new Outcome(State.RECOVERY_REQUIRED,route,"Backend or publication state uncertain; preserve .pai-internal and output.",output);
        }
        if(result.state()!=State.VERIFIED)return result;
        try {
            if(!fingerprint.equals(GuiI1CompletionLedger.fingerprint(scan,selected,output)))
                return new Outcome(State.RECOVERY_REQUIRED,route,"Source changed during conversion; inspect published outputs.",output);
            GuiI1CompletionLedger.mark(output,fingerprint);
        } catch(IOException uncertain) {
            return new Outcome(State.RECOVERY_REQUIRED,route,"Publication may be verified, but GUI history acknowledgment is uncertain.",output);
        }
        return result;
    }
}
