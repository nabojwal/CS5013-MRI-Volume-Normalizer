package org.cbihi.mrinormalizer.presentation.gui;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.util.*;
import org.cbihi.mrinormalizer.infrastructure.filesystem.DestinationQualification;

/** D2 wrapper-owned immutable copies; never reconstructs images. Original files are read only.
 * Cooperative namespace contract: detected substitution retains evidence and prevents success. */
public final class GuiI1DicomPreparation implements AutoCloseable {
    public record Observation(String relative, long size, FileTime created, FileTime modified, Object identity, String sha256) { }
    public record Snapshot(Path root, List<Observation> files) {
        public Snapshot { files = List.copyOf(files); }
    }
    private record Owned(Path path, Object identity, String hash) { }
    private final Path root;
    private final List<Owned> owned = new ArrayList<>();
    private final List<GuiI1Scanner.Candidate> candidates = new ArrayList<>();
    private Path evidence;
    private GuiI1DicomPreparation(Path root) throws IOException { this.root = root; own(root, null); }
    public Path root() { return root; }
    public List<GuiI1Scanner.Candidate> candidates() { return List.copyOf(candidates); }
    public Path evidence() { return evidence; }
    private static MessageDigest sha() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String hash(Path file, long expected) throws IOException {
        MessageDigest h = sha(); long count = 0;
        try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = in.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Selection interrupted");
                count += n;
                if (count > expected || count > (64L << 20)) throw new IOException("Selection file changed or exceeds limit");
                h.update(buffer, 0, n);
            }
        }
        if (count != expected) throw new IOException("Selection file length changed");
        return HexFormat.of().formatHex(h.digest());
    }
    private static void noLinks(Path root, Path path) throws IOException {
        if (!path.startsWith(root) || !path.toRealPath().equals(path)) throw new IOException("Selection containment changed");
        for (Path p = path; p != null; p = p.getParent())
            if (Files.isSymbolicLink(p) || Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther())
                throw new IOException("Linked selection refused");
    }
    public static Snapshot capture(Path selected) throws IOException {
        Path scope = selected.toAbsolutePath().normalize();
        boolean oneFile = Files.isRegularFile(scope, LinkOption.NOFOLLOW_LINKS);
        Path root = DestinationQualification.requireLocal(oneFile ? scope.getParent() : scope); noLinks(root, scope);
        List<Path> files = new ArrayList<>();
        try (var walk = Files.walk(scope, GuiI1DicomDiscovery.MAX_DIRECTORY_DEPTH + 1)) {
            Iterator<Path> it = walk.iterator(); int entries = 0;
            while (it.hasNext()) {
                Path p = it.next(); noLinks(root, p);
                if (++entries > 20000) throw new IOException("Selection entry limit exceeded");
                if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                    if (root.relativize(p).getNameCount() > GuiI1DicomDiscovery.MAX_DIRECTORY_DEPTH)
                        throw new IOException("Selection depth limit exceeded");
                    continue;
                }
                if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) || files.size() == GuiI1DicomDiscovery.MAX_DISCOVERY_FILES)
                    throw new IOException("Unsupported selection or file limit");
                files.add(p);
            }
        }
        files.sort(Comparator.comparing(p -> root.relativize(p).toString()));
        Set<Object> identities = new HashSet<>();
        List<Observation> observations = new ArrayList<>(); long total = 0;
        for (Path p : files) {
            var a = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (a.size() > (64L << 20) || (total += a.size()) > (8L << 30)) throw new IOException("Selection byte limit exceeded");
            Object id = DestinationQualification.entryIdentity(p);
            String digest = hash(p, a.size());
            var b = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!id.equals(DestinationQualification.entryIdentity(p)) || a.size() != b.size()
                    || !a.lastModifiedTime().equals(b.lastModifiedTime()) || !a.creationTime().equals(b.creationTime()))
                throw new IOException("Selection changed during inspection");
            // Independent copies of duplicate SOPs are rejected by metadata discovery; reject hard-link aliases too.
            if (!identities.add(id)) throw new IOException("Aliased source objects");
            observations.add(new Observation(root.relativize(p).toString(), a.size(), a.creationTime(), a.lastModifiedTime(), id, digest));
        }
        return new Snapshot(scope, observations);
    }
    public static void revalidate(Snapshot snapshot) throws IOException {
        if (!snapshot.equals(capture(snapshot.root()))) throw new IOException("DICOM selection changed; inspect inputs again");
    }
    private void own(Path path, String digest) throws IOException {
        owned.add(new Owned(path, DestinationQualification.entryIdentity(path), digest));
    }
    private static String escape(String text) {
        StringBuilder s = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            if (c == '\\' || c == '"') s.append('\\').append(c);
            else if (c < 32) s.append(String.format("\\u%04x", (int)c)); else s.append(c);
        }
        return s.append('"').toString();
    }
    public static GuiI1DicomPreparation prepare(GuiI1Scanner.ScanResult scan, List<Integer> selected, Path output) throws IOException {
        Snapshot frozen = scan.snapshot() == null ? capture(scan.input()) : scan.snapshot();
        revalidate(frozen);
        boolean oneFile = Files.isRegularFile(frozen.root(),LinkOption.NOFOLLOW_LINKS);
        Path originalBase = oneFile ? frozen.root().getParent() : frozen.root();
        Path parent = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
        if (parent.startsWith(frozen.root()) || parent.startsWith(output))
            throw new IOException("Private preparation location overlaps input/output");
        Path root = Files.createTempDirectory(parent, "pai-d2-", DestinationQualification.privateAttributes(parent, true));
        GuiI1DicomPreparation prepared = new GuiI1DicomPreparation(root);
        try {
            DestinationQualification.verifyPrivateDirectory(root);
            StringBuilder json = new StringBuilder("{\"schema\":\"PAI-D2-1\",\"originalRoot\":")
                    .append(escape(originalBase.toString())).append(",\"members\":[");
            boolean first = true; int ordinal = 0;
            Set<String> stems = new HashSet<>();
            for (int index : selected) {
                var candidate = scan.candidates().get(index); ordinal++;
                String stem = GuiI1Execution.niftiFilename(oneFile
                        ? Path.of(frozen.root().getFileName().toString().replaceFirst("(?i)\\.dcm$", ""))
                        : candidate.directory());
                stem = stem.substring(0, stem.length() - 4);
                if (scan.candidates().stream().filter(c -> c.directory().equals(candidate.directory())).count() > 1)
                    stem = stem.substring(0, Math.min(stem.length(), 80)) + "-series-" + (index+1);
                if (!stems.add(stem.toLowerCase(Locale.ROOT))) {
                    stem = stem.substring(0, Math.min(stem.length(), 80)) + "-series-" + ordinal;
                    if (!stems.add(stem.toLowerCase(Locale.ROOT))) throw new IOException("Ambiguous series names");
                }
                Path dir = Files.createDirectory(root.resolve(stem), DestinationQualification.privateAttributes(root, true)); prepared.own(dir, null);
                List<String> names = new ArrayList<>(); int member = 0; long bytes = 0;
                for (String name : candidate.memberNames()) {
                    Path original = candidate.directory().resolve(name).normalize(); noLinks(originalBase, original);
                    String relative = originalBase.relativize(original).toString();
                    Observation observed = frozen.files().stream().filter(o -> o.relative().equals(relative)).findFirst()
                            .orElseThrow(() -> new IOException("Selected object absent from frozen inventory"));
                    if ((bytes += observed.size()) > (512L << 20)) throw new IOException("Selected series exceeds byte limit");
                    String local = "image-%06d.dcm".formatted(++member); Path copy = dir.resolve(local);
                    try (InputStream in = Files.newInputStream(original, LinkOption.NOFOLLOW_LINKS);
                         var channel = FileChannel.open(copy, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                                 DestinationQualification.privateAttributes(dir, false))) {
                        prepared.own(copy, null); byte[] buffer = new byte[65536]; int n; long count = 0;
                        while ((n = in.read(buffer)) != -1) {
                            if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Selection interrupted");
                            if ((count += n) > observed.size()) throw new IOException("Source changed during preparation");
                            var b = java.nio.ByteBuffer.wrap(buffer, 0, n); while (b.hasRemaining()) channel.write(b);
                        }
                        if (count != observed.size()) throw new IOException("Source changed during preparation");
                        channel.force(true);
                    }
                    String actual = hash(copy, observed.size());
                    if (!actual.equals(observed.sha256())) throw new IOException("Prepared copy differs from selected source");
                    prepared.owned.set(prepared.owned.size()-1, new Owned(copy, DestinationQualification.entryIdentity(copy), actual));
                    names.add(local);
                    if (!first) json.append(','); first = false;
                    json.append("{\"source\":").append(escape(relative)).append(",\"prepared\":")
                            .append(escape(stem + "/" + local)).append(",\"sha256\":").append(escape(actual)).append('}');
                }
                prepared.candidates.add(new GuiI1Scanner.Candidate(candidate.label(), stem, dir, candidate.seriesUid(),
                        names, names.size(), true, "Immutable source copies; backend validation required"));
            }
            revalidate(frozen);
            // Persist original-to-prepared mapping privately beside the output, not in ephemeral temp storage.
            Path evidenceRoot = output.resolve(".pai-d2-evidence");
            DestinationQualification.requireLocal(output);
            if (Files.notExists(evidenceRoot, LinkOption.NOFOLLOW_LINKS)) {
                try { Files.createDirectory(evidenceRoot, DestinationQualification.privateAttributes(output, true)); }
                catch (FileAlreadyExistsException concurrent) { /* verify, never repair */ }
            }
            DestinationQualification.verifyPrivateDirectory(evidenceRoot);
            prepared.evidence = evidenceRoot.resolve(UUID.randomUUID() + ".json");
            byte[] record = json.append("]}\n").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try (var channel = FileChannel.open(prepared.evidence, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                    DestinationQualification.privateAttributes(evidenceRoot, false))) {
                var buffer = java.nio.ByteBuffer.wrap(record);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try (var in = Files.newInputStream(prepared.evidence, LinkOption.NOFOLLOW_LINKS)) {
                if (!Arrays.equals(record, in.readNBytes(record.length+1))) throw new IOException("Preparation evidence readback failed");
            }
            return prepared;
        } catch (IOException | RuntimeException failure) {
            try { prepared.close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    @Override public void close() throws IOException {
        IOException failure = null;
        for (int i = owned.size()-1; i >= 0; i--) {
            Owned entry = owned.get(i);
            try {
                noLinks(root, entry.path());
                for (Owned ancestor : owned) {
                    if (!ancestor.path().equals(entry.path()) && entry.path().startsWith(ancestor.path())
                            && !ancestor.identity().equals(DestinationQualification.entryIdentity(ancestor.path())))
                        throw new IOException("Preparation ancestor replaced; retained for recovery");
                }
                if (!entry.identity().equals(DestinationQualification.entryIdentity(entry.path())))
                    throw new IOException("Preparation entry replaced; retained for recovery");
                if (entry.hash() != null && !entry.hash().equals(hash(entry.path(), Files.size(entry.path()))))
                    throw new IOException("Preparation copy changed; retained for recovery");
                Files.delete(entry.path());
            } catch (IOException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
        }
        if (failure != null) throw failure;
    }
}
