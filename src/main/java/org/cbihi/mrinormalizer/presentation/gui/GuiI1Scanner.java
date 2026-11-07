package org.cbihi.mrinormalizer.presentation.gui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

/** Non-mutating candidate preview. All backend qualification remains authoritative. */
public final class GuiI1Scanner {
    public enum Direction { NIFTI_TO_DICOM, DICOM_TO_NIFTI }
    public enum InputKind { NIFTI_FILE, NIFTI_DIRECTORY, DICOM_DIRECT, DICOM_PREPARED_PARENT, DICOM_DISCOVERY_ONLY }

    /** Metadata-only DICOM preview, with explicit executability separate from discovery. */
    public record Candidate(String label, String childName, Path directory,
                            String seriesUid, List<String> memberNames, int instanceCount,
                            boolean eligible, String finding) {
        public Candidate {
            Objects.requireNonNull(label);
            Objects.requireNonNull(directory);
            Objects.requireNonNull(finding);
            memberNames = List.copyOf(memberNames);
            if (instanceCount < 0) throw new IllegalArgumentException("Negative instance count");
        }
        /** Compatibility for the already-qualified six-file GUI execution tests. */
        public Candidate(String label, String childName, Path directory,
                         String seriesUid, List<String> memberNames) {
            this(label, childName, directory, seriesUid, memberNames,
                    memberNames.size(), true, "Preview only; backend validation required");
        }
    }
    public record ScanResult(Direction direction, InputKind kind, Path input, List<Candidate> candidates, GuiI1DicomPreparation.Snapshot snapshot) {
        public ScanResult(Direction direction, InputKind kind, Path input, List<Candidate> candidates) {
            this(direction, kind, input, candidates, null);
        }
        public ScanResult {
            Objects.requireNonNull(direction);
            Objects.requireNonNull(kind);
            Objects.requireNonNull(input);
            candidates = List.copyOf(candidates);
            if (candidates.isEmpty()) throw new IllegalArgumentException("No selected candidates");
        }
    }

    private static final int MAX_NIFTI_CANDIDATES = 256;
    private final GuiI1DicomDiscovery dicom;
    public GuiI1Scanner() { this(new GuiI1DicomDiscovery()); }
    public GuiI1Scanner(GuiI1DicomDiscovery dicom) {
        this.dicom = Objects.requireNonNull(dicom);
    }

    private static Path canonicalNoLinks(Path source) throws IOException {
        Path normalized = source.toAbsolutePath().normalize();
        Path cursor = normalized.getRoot();
        if (cursor == null) throw new IOException("Input path has no root");
        for (Path part : normalized) {
            cursor = cursor.resolve(part);
            if (Files.isSymbolicLink(cursor)) throw new IOException("Linked paths are not supported");
        }
        Path real = normalized.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!real.equals(normalized)) throw new IOException("Input path does not have stable containment");
        return real;
    }
    private static boolean niftiName(Path source) {
        String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".nii") || name.endsWith(".nii.gz");
    }
    private static void sniffNifti(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)
                || !niftiName(path)) throw new IOException("NIfTI candidate is not a regular supported file");
        byte[] header;
        try (InputStream raw = Files.newInputStream(path)) {
            byte[] prefix = raw.readNBytes(2);
            if (prefix.length != 2) throw new IOException("Truncated NIfTI input");
        }
        try (InputStream raw = Files.newInputStream(path)) {
            // Signature-sniff only. The accepted F7/M8 engine performs complete integrity checks.
            int first = raw.read(), second = raw.read();
            // Reopen, since Files.newInputStream does not guarantee mark support.
            try (InputStream again = Files.newInputStream(path);
                 InputStream content = first == 0x1f && second == 0x8b
                         ? new GZIPInputStream(again) : again) {
                header = content.readNBytes(348);
            }
        } catch (SecurityException invalid) { throw new IOException("Cannot inspect NIfTI input", invalid); }
        if (header.length != 348) throw new IOException("Incomplete NIfTI header");
        ByteBuffer b = ByteBuffer.wrap(header);
        int le = b.order(ByteOrder.LITTLE_ENDIAN).getInt(0);
        int be = b.order(ByteOrder.BIG_ENDIAN).getInt(0);
        if (le != 348 && be != 348) throw new IOException("Not a NIfTI-1 header");
        b.order(le == 348 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        if (b.getShort(40) != 3 || header[344] != 'n' || header[345] != '+'
                || header[346] != '1' || header[347] != 0)
            throw new IOException("Unsupported NIfTI dimensionality or container");
    }
    public ScanResult scan(Direction direction, Path source) throws IOException {
        Objects.requireNonNull(direction);
        Objects.requireNonNull(source);
        Path input = canonicalNoLinks(source);
        if (direction == Direction.NIFTI_TO_DICOM) {
            if (Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS)) {
                sniffNifti(input);
                return new ScanResult(direction, InputKind.NIFTI_FILE, input,
                        List.of(new Candidate("1 NIfTI volume · candidate", null, input.getParent(), null,
                                List.of(input.getFileName().toString()))));
            }
            if (!Files.isDirectory(input, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Select a NIfTI file or directory");
            List<Path> paths = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(input)) {
                for (Path p : stream) {
                    if (!niftiName(p)) continue;
                    if (paths.size() == MAX_NIFTI_CANDIDATES) throw new IOException("Too many NIfTI candidates");
                    paths.add(p);
                }
            }
            paths.sort(Comparator.comparing(p -> p.getFileName().toString()));
            if (paths.isEmpty()) throw new IOException("No supported NIfTI filename candidates");
            List<Candidate> rows = new ArrayList<>();
            for (int i = 0; i < paths.size(); i++) {
                sniffNifti(paths.get(i));
                rows.add(new Candidate("NIfTI volume %03d · candidate".formatted(i + 1), null, input,
                        null, List.of(paths.get(i).getFileName().toString())));
            }
            return new ScanResult(direction, InputKind.NIFTI_DIRECTORY, input, rows);
        }
        if (!Files.isDirectory(input, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Select a DICOM source file or folder");
        var snapshot = GuiI1DicomPreparation.capture(input);
        GuiI1DicomDiscovery.Result found = dicom.discover(input);
        GuiI1DicomPreparation.revalidate(snapshot);
        List<Candidate> candidates = new ArrayList<>();
        int ordinal = 0;
        for (GuiI1DicomDiscovery.Series series : found.series()) {
            ordinal++;
            String child = series.folder().getParent() != null
                    && series.folder().getParent().equals(input)
                    ? series.folder().getFileName().toString() : null;
            String label = "Series %02d · %d instances".formatted(ordinal, series.instances());
            candidates.add(new Candidate(label, child, series.folder(), series.seriesUid(),
                    series.memberNames(), series.instances(), series.eligible(), series.finding()));
        }
        boolean direct = candidates.size() == 1 && candidates.getFirst().eligible()
                && (candidates.getFirst().directory().equals(input) || Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS));
        boolean anyPrepared = candidates.stream().anyMatch(c -> c.eligible() && c.childName() != null);
        InputKind kind = direct ? InputKind.DICOM_DIRECT
                : anyPrepared ? InputKind.DICOM_PREPARED_PARENT : InputKind.DICOM_DISCOVERY_ONLY;
        return new ScanResult(direction, kind, input, candidates, snapshot);
    }
}
