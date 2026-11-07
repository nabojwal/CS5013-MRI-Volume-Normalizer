package org.cbihi.mrinormalizer.infrastructure.fbo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.*;
import org.cbihi.mrinormalizer.application.fbo.FboBatchController.*;
import org.cbihi.mrinormalizer.application.fbo.FboRequest;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;

/** Preflight-only, bounded, no-follow inventory and immediate pre-dispatch equality check. */
public final class FboDirectoryPreflight implements Preflight {
    private static final long MAX_FILE = 64L << 20;
    private static final long MAX_SERIES = 512L << 20;
    private static final Set<String> UNIX_TYPES = Set.of("ext2","ext3","ext4","xfs","btrfs","overlay","apfs","hfs","ufs","zfs");
    private static BasicFileAttributes attrs(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }
    private static Path strictDirectory(Path candidate) throws IOException {
        Path p = candidate.toAbsolutePath().normalize();
        Path current = p.getRoot();
        if (current == null) throw new IOException("Path has no filesystem root");
        for (Path name : p) {
            current = current.resolve(name);
            BasicFileAttributes a = attrs(current);
            if (!a.isDirectory() || a.isSymbolicLink() || a.isOther()
                    || !current.toRealPath().equals(current))
                throw new IOException("Untrusted directory component");
        }
        if (!Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS) || !p.toRealPath().equals(p))
            throw new IOException("Untrusted directory");
        return p;
    }
    static void outputQualified(Path p) throws IOException {
        org.cbihi.mrinormalizer.infrastructure.filesystem.DestinationQualification.requireLocal(p);
    }

    // Probe actual Java create/link/readback capability at the projected path length.
    // The native backend remains authoritative; there is no drive-letter or 240-character whitelist.
    private static final String UUID_SHAPE = "00000000-0000-0000-0000-000000000000";

    static void qualifyF9StageDepth(Path futureJobRoot, UUID jobId) throws IOException {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            Path firstStage = futureJobRoot.toAbsolutePath().normalize()
                    .resolve(".pai-f9-manifest")
                    .resolve(jobId.toString())
                    .resolve("records").resolve("000000")
                    .resolve(".stage-" + UUID_SHAPE + ".tmp");
            Path existing = futureJobRoot.toAbsolutePath().normalize();
            while (!Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) existing = existing.getParent();
            org.cbihi.mrinormalizer.infrastructure.filesystem.DestinationQualification.requirePathCapacity(existing,
                    firstStage.toString().length() - existing.toString().length());
        }
    }

    private static void qualifyBatchStageDepth(Path outputParent) throws IOException {
        // All FBO batch/job UUIDs are 36 characters, and ordinals are 001..016.
        // No mutation occurs until this check succeeds for the deepest F1 stage.
        Path futureJob = outputParent.resolve("fbo-" + UUID_SHAPE)
                .resolve("job-016-" + UUID_SHAPE);
        qualifyF9StageDepth(futureJob, UUID.fromString(UUID_SHAPE));
    }

    private static DirectoryObservation observeDirectory(Path dir) throws IOException {
        BasicFileAttributes a = attrs(dir);
        if (!a.isDirectory() || a.isSymbolicLink()) throw new IOException("Untrusted series directory");
        return new DirectoryObservation(a.fileKey(), a.creationTime(), a.lastModifiedTime());
    }
    private static boolean same(DirectoryObservation a, DirectoryObservation b) {
        return Objects.equals(a.fileKeyOrNull(), b.fileKeyOrNull())
                && a.creationTime().equals(b.creationTime())
                && a.lastModifiedTime().equals(b.lastModifiedTime());
    }
    private static String sha(Path file, long expected) throws IOException {
        try {
            MessageDigest h = MessageDigest.getInstance("SHA-256");
            byte[] bytes = new byte[65536]; long total = 0;
            try (InputStream in = Files.newInputStream(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                int n;
                while ((n = in.read(bytes)) != -1) {
                    total += n;
                    if (total > MAX_FILE || total > expected) throw new IOException("Source limit or source drift");
                    h.update(bytes, 0, n);
                }
            }
            if (total != expected) throw new IOException("Source changed during hashing");
            return HexFormat.of().formatHex(h.digest());
        } catch (NoSuchAlgorithmException error) { throw new IOException("Digest unavailable", error); }
    }
    private static List<PreparedFile> inventory(Path source) throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
            for (Path p : stream) {
                if (!attrs(p).isRegularFile() || Files.isSymbolicLink(p) || !p.toRealPath().equals(p))
                    throw new IOException("Untrusted series member");
                new RelativePath(RelativePath.Root.SOURCE, p.getFileName().toString());
                files.add(p);
            }
        }
        if (files.isEmpty()) throw new IOException("Empty series");
        files.sort(Comparator.comparing(p -> p.getFileName().toString()));
        Set<String> logicalNames = new HashSet<>();
        List<PreparedFile> observed = new ArrayList<>();
        long aggregate = 0;
        for (Path p : files) {
            String name = p.getFileName().toString();
            if (!logicalNames.add(Normalizer.normalize(name, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)))
                throw new IOException("Colliding filenames");
            BasicFileAttributes before = attrs(p);
            if (!before.isRegularFile() || before.isSymbolicLink() || before.size() > MAX_FILE)
                throw new IOException("Unsafe file or limit");
            if (before.size() > MAX_SERIES - aggregate) throw new IOException("Selected-series byte limit");
            for (Path prior : files.subList(0, files.indexOf(p)))
                if (Files.isSameFile(prior, p)) throw new IOException("Hard-linked aliases");
            String digest = sha(p, before.size());
            BasicFileAttributes after = attrs(p);
            if (!before.isRegularFile() || !after.isRegularFile()
                    || before.size() != after.size()
                    || !before.creationTime().equals(after.creationTime())
                    || !before.lastModifiedTime().equals(after.lastModifiedTime())
                    || !Objects.equals(before.fileKey(), after.fileKey()))
                throw new IOException("Changed source during inspection");
            aggregate += after.size();
            observed.add(new PreparedFile(name, after.size(), after.creationTime(),
                    after.lastModifiedTime(), after.fileKey(), digest));
        }
        return List.copyOf(observed);
    }
    @Override public PreparedBatch inspectAll(FboRequest request) throws IOException {
        if (request == null) throw new IOException("Missing request");
        Path input = strictDirectory(request.inputParent());
        Path output = strictDirectory(request.outputParent());
        if (input.startsWith(output) || output.startsWith(input) || Files.isSameFile(input, output))
            throw new IOException("Overlapping roots");
        outputQualified(output); // qualify the local destination BEFORE any batch output I/O
        qualifyBatchStageDepth(output); // refuse unqualified Windows path depth BEFORE batch creation
        List<PreparedSeries> prepared = new ArrayList<>();
        List<Path> knownMembers = new ArrayList<>();
        for (var selection : request.selections()) {
            Path directory = strictDirectory(input.resolve(selection.childDirectory()));
            if (!directory.getParent().equals(input) || directory.equals(output) || output.startsWith(directory)
                    || directory.startsWith(output)) throw new IOException("Invalid prepared directory");
            for (var previous : prepared)
                if (Files.isSameFile(previous.canonicalSourceDirectory(), directory))
                    throw new IOException("Aliased prepared series");
            DirectoryObservation before = observeDirectory(directory);
            List<PreparedFile> members = inventory(directory);
            if (!same(before, observeDirectory(directory))) throw new IOException("Directory changed during preflight");
            for (PreparedFile member : members) {
                Path current = directory.resolve(member.relativeName());
                for (Path prior : knownMembers) {
                    // Avoid millions of expensive same-file checks for differing content sizes.
                    if (Files.size(prior) == member.byteLength() && Files.isSameFile(prior, current))
                        throw new IOException("Hard-linked inputs across selections");
                }
                knownMembers.add(current);
            }
            prepared.add(new PreparedSeries(prepared.size() + 1, directory,
                    selection.seriesInstanceUid(), before, members));
        }
        return new PreparedBatch(input, output, prepared);
    }
    @Override public void assertUnchanged(PreparedSeries prepared) throws IOException {
        if (prepared == null) throw new IOException("Missing prepared series");
        Path current = strictDirectory(prepared.canonicalSourceDirectory());
        if (!current.equals(prepared.canonicalSourceDirectory())
                || !same(prepared.directory(), observeDirectory(current)))
            throw new IOException("Directory observation drift");
        List<PreparedFile> now = inventory(current);
        if (!now.equals(prepared.members()) || !same(prepared.directory(), observeDirectory(current)))
            throw new IOException("Prepared member inventory changed");
    }
}
