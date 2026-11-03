package org.cbihi.mrinormalizer.presentation.gui;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;

/**
 * Bounded, read-only metadata discovery. A discovered series is NOT a backend-validated volume.
 * Never infer a usable F9/FBO request from an interleaved or nested folder layout.
 */
public final class GuiI1DicomDiscovery {
    public static final int MAX_DISCOVERY_FILES = 10_000;
    public static final int MAX_DIRECTORY_DEPTH = 8;
    private static final long MAX_FILE_BYTES = 64L << 20;
    private static final long MAX_SERIES_BYTES = 512L << 20;

    /** One distinct SOP instance (not one frame); no patient attributes are retained. */
    public record Metadata(String studyUid, String seriesUid, String sopUid, String modality,
                           int numberOfFrames) {}
    @FunctionalInterface public interface MetadataProbe {
        Metadata read(Path file) throws IOException;
    }
    public record Series(Path folder, String relativeFolder, String seriesUid,
                         List<String> memberNames, int instances, boolean eligible,
                         String finding) {
        public Series { memberNames = List.copyOf(memberNames); }
    }
    public record Result(List<Series> series, int inspectedFiles, int unidentifiedFiles) {
        public Result { series = List.copyOf(series); }
    }
    private record Key(String study, String series) {}
    private record Member(Path file, String sop, long bytes, int frames) {}
    private static final class Group {
        final Key key;
        final List<Member> members = new ArrayList<>();
        final Set<String> sops = new HashSet<>();
        boolean duplicate, multiFrame;
        Group(Key key) { this.key = key; }
    }
    private final MetadataProbe probe;
    public GuiI1DicomDiscovery() { this(GuiI1DicomDiscovery::readMetadata); }
    public GuiI1DicomDiscovery(MetadataProbe probe) { this.probe = java.util.Objects.requireNonNull(probe); }

    private static String validUid(String uid) throws IOException {
        if (uid == null || uid.isBlank() || uid.length() > 64
                || !uid.matches("[0-9]+(?:\\.[0-9]+)*"))
            throw new IOException("Missing or invalid DICOM UID");
        for (String part : uid.split("\\.")) {
            if (part.length() > 1 && part.charAt(0) == '0')
                throw new IOException("Noncanonical DICOM UID");
        }
        return uid;
    }
    private static Metadata readMetadata(Path file) throws IOException {
        try (DicomInputStream in = new DicomInputStream(file.toFile())) {
            // Stops at PixelData. No image pixels or bulk-data contents are requested.
            in.setIncludeBulkData(DicomInputStream.IncludeBulkData.NO);
            Attributes a = in.readDataset(-1, Tag.PixelData);
            if (a == null) throw new IOException("No DICOM metadata");
            String study = validUid(a.getString(Tag.StudyInstanceUID));
            String series = validUid(a.getString(Tag.SeriesInstanceUID));
            String sop = validUid(a.getString(Tag.SOPInstanceUID));
            String modality = a.getString(Tag.Modality);
            if (modality == null || modality.isBlank()) throw new IOException("Missing DICOM modality");
            int frames = a.getInt(Tag.NumberOfFrames, 1);
            if (frames < 1) throw new IOException("Invalid frame count");
            return new Metadata(study, series, sop, modality, frames);
        } catch (RuntimeException bad) { throw new IOException("DICOM metadata cannot be read", bad); }
    }
    private static Path checkedChild(Path root, Path child) throws IOException {
        if (Files.isSymbolicLink(child) || !child.toAbsolutePath().normalize().startsWith(root)
                || !child.toRealPath(LinkOption.NOFOLLOW_LINKS).equals(child.toAbsolutePath().normalize()))
            throw new IOException("Untrusted DICOM input entry or path alias");
        return child;
    }
    private static List<Path> list(Path root, Path directory) throws IOException {
        List<Path> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path entry : stream) {
                Path p = checkedChild(root, entry);
                if (Files.isSymbolicLink(p)
                        || !(Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)
                        || Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)))
                    throw new IOException("Unsupported or linked DICOM input entry");
                children.add(p);
            }
        }
        children.sort(Comparator.comparing(p -> p.getFileName().toString()));
        return children;
    }
    private static void collect(Path root, Path dir, int depth, List<Path> files,
                                Set<Path> visited) throws IOException {
        if (depth > MAX_DIRECTORY_DEPTH) throw new IOException("DICOM discovery depth limit exceeded");
        if (!visited.add(dir)) throw new IOException("Repeated DICOM directory");
        for (Path p : list(root, dir)) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("DICOM discovery interrupted");
            if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) collect(root, p, depth + 1, files, visited);
            else {
                if (files.size() >= MAX_DISCOVERY_FILES)
                    throw new IOException("DICOM discovery file limit exceeded; results are not complete");
                files.add(p);
            }
        }
    }
    private static String folderKey(Path path) {
        return Normalizer.normalize(path.toString(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }
    public Result discover(Path root) throws IOException {
        if (root == null || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(root)) throw new IOException("Select an existing DICOM folder");
        Path input = root.toAbsolutePath().normalize();
        if (!input.toRealPath(LinkOption.NOFOLLOW_LINKS).equals(input))
            throw new IOException("Unstable DICOM folder path");
        // The GUI scans on a worker thread; an incomplete scan is never represented as complete.
        List<Path> files = new ArrayList<>();
        collect(input, input, 0, files, new HashSet<>());
        if (files.isEmpty()) throw new IOException("No DICOM input files were found");
        Map<Key, Group> groups = new LinkedHashMap<>();
        Map<Path, Integer> unidentified = new HashMap<>();
        int unidentifiedCount = 0;
        for (Path file : files) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("DICOM discovery interrupted");
            // Do not probe arbitrarily large files even when their metadata is near the header.
            if (Files.size(file) > MAX_FILE_BYTES)
                throw new IOException("DICOM discovery source exceeds the current safe per-file budget");
            Metadata m;
            try { m = probe.read(file); }
            catch (IOException unreadable) {
                unidentified.merge(file.getParent(), 1, Integer::sum);
                unidentifiedCount++;
                continue;
            }
            if (m == null) throw new IOException("DICOM metadata probe returned no result");
            if (!"MR".equals(m.modality())) {
                unidentified.merge(file.getParent(), 1, Integer::sum);
                unidentifiedCount++;
                continue;
            }
            Key key;
            try { key = new Key(validUid(m.studyUid()), validUid(m.seriesUid()));
                validUid(m.sopUid()); }
            catch (IOException invalid) {
                unidentified.merge(file.getParent(), 1, Integer::sum);
                unidentifiedCount++;
                continue;
            }
            Group group = groups.computeIfAbsent(key, Group::new);
            if (!group.sops.add(m.sopUid())) group.duplicate = true;
            if (m.numberOfFrames() != 1) group.multiFrame = true;
            group.members.add(new Member(file, m.sopUid(), Files.size(file), m.numberOfFrames()));
        }
        if (groups.isEmpty()) throw new IOException("No conventional MR DICOM series could be identified");
        Map<Path, Set<Key>> folderGroups = new HashMap<>();
        for (Group g : groups.values())
            for (Member member : g.members)
                folderGroups.computeIfAbsent(member.file.getParent(), ignoredKey -> new HashSet<>()).add(g.key);
        List<Series> results = new ArrayList<>();
        List<Group> ordered = new ArrayList<>(groups.values());
        ordered.sort(Comparator.comparing(g -> g.members.stream()
                .map(m -> input.relativize(m.file).toString()).min(String::compareTo).orElse("")));
        for (Group group : ordered) {
            List<Member> members = group.members;
            Path folder = members.getFirst().file.getParent();
            boolean oneFolder = members.stream().allMatch(m -> m.file.getParent().equals(folder));
            int n = group.sops.size();
            long bytes = 0;
            boolean sizeQualified = true;
            for (Member m : members) {
                if (m.bytes > MAX_FILE_BYTES || m.bytes <= 0 || m.bytes > MAX_SERIES_BYTES - bytes)
                    sizeQualified = false;
                if (sizeQualified) bytes += m.bytes;
            }
            List<Path> actualChildren = oneFolder ? list(input, folder) : List.of();
            boolean plainFolder = oneFolder && actualChildren.size() == members.size()
                    && actualChildren.stream().allMatch(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS));
            boolean pure = plainFolder && folderGroups.getOrDefault(folder, Set.of()).size() == 1
                    && unidentified.getOrDefault(folder, 0) == 0;
            // Only an entire source folder or an immediate child can reach the frozen F9/FBO ports.
            boolean direct = folder.equals(input) && groups.size() == 1
                    && unidentifiedCount == 0 && folderGroups.size() == 1
                    && files.stream().allMatch(f -> f.getParent().equals(input));
            boolean immediate = folder.getParent() != null && folder.getParent().equals(input);
            boolean layout = (direct || immediate) && pure;
            boolean eligible = layout && !group.duplicate
                    && !group.multiFrame && sizeQualified;
            String finding = group.duplicate ? "Duplicate SOP Instance UID; cannot dispatch"
                    : group.multiFrame ? "Enhanced/multiframe DICOM is not in current execution profile"
                    : !layout ? "Discovery only: mixed or nested layout; prepare a separate series folder"
                    : !sizeQualified ? "Over current byte budget"
                    : "Preview only; backend validation required";
            List<String> names = new ArrayList<>();
            if (oneFolder) {
                for (Member m : members) names.add(m.file.getFileName().toString());
                names.sort(String::compareTo);
            }
            String relative = oneFolder ? input.relativize(folder).toString() : "(multiple folders)";
            results.add(new Series(folder, relative, group.key.series, names, n, eligible, finding));
        }
        return new Result(results, files.size(), unidentifiedCount);
    }
}
