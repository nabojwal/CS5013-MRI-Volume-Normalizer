package org.cbihi.mrinormalizer.infrastructure.filesystem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.cbihi.mrinormalizer.application.dataset.model.DatasetInventory;
import org.cbihi.mrinormalizer.application.dataset.model.FormatAssessment;
import org.cbihi.mrinormalizer.application.dataset.model.InventoryEntry;
import org.cbihi.mrinormalizer.application.dataset.model.InventoryFailure;
import org.cbihi.mrinormalizer.application.dataset.model.InventoryFailure.Code;
import org.cbihi.mrinormalizer.application.dataset.model.InventoryLimits;
import org.cbihi.mrinormalizer.application.dataset.model.InventoryLocation;
import org.cbihi.mrinormalizer.application.port.out.DatasetScanner;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;
import org.cbihi.mrinormalizer.application.service.FormatDetectionService;
import org.cbihi.mrinormalizer.domain.model.InputSource;

/**
 * Sequential, bounded, read-only NIO discovery. No format probing is duplicated:
 * each eligible file passes through accepted M5 detection then F0 assessment.
 * Configured aliases are canonicalized; discovered links are not followed.
 * Canonical containment, no-follow attributes and available identity evidence
 * are checked before/after enumeration and recognition. Stable non-null provider
 * keys are compared; null keys do not fabricate identity from a pathname.
 *
 * This is not a filesystem transaction. An unchanged observed state gives stable
 * logical results; concurrent mutation blocks where detected. M5 opens a pathname
 * itself, so hostile swaps between checks and that open cannot be eliminated here.
 * Null-key providers offer weaker substitution evidence. No snapshot isolation,
 * durable lease, provider-wide race resistance or later execution safety is claimed.
 */
public final class NioDatasetScanner implements DatasetScanner {
    private final Path inputRoot;
    private final Path outputRoot;
    private final InventoryLimits limits;
    private final FormatDetectionService detection;
    private final FileAccess access;

    public NioDatasetScanner(Path inputRoot, Path outputRoot, InventoryLimits limits, FormatDetectionService detection) {
        this(inputRoot, outputRoot, limits, detection, new FileAccess());
    }

    NioDatasetScanner(Path inputRoot, Path outputRoot, InventoryLimits limits, FormatDetectionService detection, FileAccess access) {
        if (inputRoot == null || outputRoot == null || limits == null || detection == null || access == null) {
            throw new IllegalArgumentException("Scanner configuration must be non-null");
        }
        this.inputRoot = inputRoot; this.outputRoot = outputRoot;
        this.limits = limits; this.detection = detection; this.access = access;
    }

    @Override public DatasetInventory scan() { return new Scan().run(); }

    // Test-only package-private mechanical seam. No alternate production recognizer,
    // public traversal framework or provider-specific permission assumption.
    static class FileAccess {
        Path real(Path path) throws IOException { return path.toRealPath(); }
        BasicFileAttributes attributes(Path path) throws IOException {
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        }
        DirectoryStream<Path> directory(Path path) throws IOException { return Files.newDirectoryStream(path); }
        boolean same(Path first, Path second) throws IOException { return Files.isSameFile(first, second); }
    }

    private record Root(Path path, BasicFileAttributes attributes) { }
    private record Work(Path path, int depth) { }
    private static final class ScanFault extends Exception {
        private final Code code;
        private ScanFault(Code code) { this.code = code; }
    }

    private final class Scan {
        private final List<InventoryEntry> entries = new ArrayList<>();
        private final List<InventoryFailure> failures = new ArrayList<>();
        private final ArrayDeque<Work> pending = new ArrayDeque<>();
        private Root input;
        private Root output;
        private long discovered;
        private int ordinaryFailures;
        private boolean stopped;

        private DatasetInventory run() {
            try { input = root(inputRoot); }
            catch (IOException | RuntimeException | ScanFault failure) { fail(Code.INPUT_ROOT_INVALID, null); return finish(); }
            try { output = root(outputRoot); }
            catch (IOException | RuntimeException | ScanFault failure) { fail(Code.OUTPUT_ROOT_INVALID, null); return finish(); }
            try {
                if (input.path.startsWith(output.path) || output.path.startsWith(input.path) || access.same(input.path, output.path)) {
                    fail(Code.ROOTS_OVERLAP, null); return finish();
                }
                verifyRoots();
            } catch (ScanFault failure) { fail(failure.code, null); return finish(); }
            catch (IOException | RuntimeException failure) { fail(Code.CONTAINMENT_UNPROVEN, null); return finish(); }
            pending.push(new Work(input.path, 0));
            while (!pending.isEmpty() && !stopped) {
                Work work = pending.pop();
                try { visit(work); }
                catch (ScanFault failure) { fail(failure.code, location(work.path)); }
                catch (IOException | RuntimeException failure) { fail(Code.TRAVERSAL_FAILED, location(work.path)); }
            }
            // A final root check also protects empty scans and changes after the last file.
            if (!stopped) {
                try { verifyRoots(); }
                catch (ScanFault failure) { fail(failure.code, null); }
                catch (IOException | RuntimeException failure) { fail(Code.CONTAINMENT_UNPROVEN, null); }
            }
            return finish();
        }

        private Root root(Path configured) throws IOException, ScanFault {
            Path real = access.real(configured);
            if (!real.isAbsolute() || !real.equals(real.normalize())) throw new ScanFault(Code.CONTAINMENT_UNPROVEN);
            var attributes = access.attributes(real);
            if (!attributes.isDirectory() || attributes.isSymbolicLink()) throw new ScanFault(Code.CONTAINMENT_UNPROVEN);
            return new Root(real, attributes);
        }

        private void verifyRoots() throws IOException, ScanFault {
            for (Root root : List.of(input, output)) {
                Code changed = root == input ? Code.SOURCE_CHANGED : Code.CONTAINMENT_UNPROVEN;
                if (!access.real(root.path).equals(root.path)) throw new ScanFault(changed);
                var current = access.attributes(root.path);
                if (!current.isDirectory() || current.isSymbolicLink() || !stable(root.attributes, current)) throw new ScanFault(changed);
            }
        }

        private void visit(Work work) throws IOException, ScanFault {
            verifyRoots();
            if (!work.path.startsWith(input.path) || !work.path.equals(work.path.normalize())) throw new ScanFault(Code.PATH_OUTSIDE_ROOT);
            String spelling = location(work.path);
            if (work.depth > limits.maxDepth() || spelling != null && spelling.length() > 4096) throw new ScanFault(Code.RESOURCE_LIMIT);
            RelativePath source = null;
            if (spelling != null) {
                try { source = new RelativePath(RelativePath.Root.SOURCE, spelling); }
                catch (IllegalArgumentException rejectedName) { throw new ScanFault(Code.UNREPRESENTABLE_REFERENCE); }
            }
            var before = access.attributes(work.path);
            if (before.isSymbolicLink()) throw new ScanFault(Code.SYMLINK_DISALLOWED);
            if (!before.isRegularFile() && !before.isDirectory()) throw new ScanFault(Code.SPECIAL_FILE_UNSUPPORTED);
            contained(work.path);
            if (!stable(before, access.attributes(work.path))) throw new ScanFault(Code.SOURCE_CHANGED);
            if (before.isDirectory()) {
                enumerate(work, before); return;
            }
            final FormatAssessment assessment;
            try { assessment = FormatAssessment.fromDetection(detection.detect(new InputSource(work.path.toString()))); }
            catch (RuntimeException detectionFailure) { throw new ScanFault(Code.DETECTION_FAILED); }
            contained(work.path); verifyRoots();
            if (!stable(before, access.attributes(work.path))) throw new ScanFault(Code.SOURCE_CHANGED);
            entries.add(new InventoryEntry(source, assessment));
        }

        private void contained(Path path) throws IOException, ScanFault {
            // Inspect actual ancestors, not just lexical startsWith. Configured
            // aliases are already resolved; a discovered alias is never accepted.
            Path current = input.path;
            Path relative = input.path.relativize(path);
            for (int index = 0; index < relative.getNameCount() - 1; index++) {
                current = current.resolve(relative.getName(index));
                var attributes = access.attributes(current);
                if (!attributes.isDirectory() || attributes.isSymbolicLink()) throw new ScanFault(Code.CONTAINMENT_UNPROVEN);
            }
            Path real = access.real(path);
            if (!real.startsWith(input.path)) throw new ScanFault(Code.PATH_OUTSIDE_ROOT);
            if (!real.equals(path)) throw new ScanFault(Code.CONTAINMENT_UNPROVEN);
        }

        private void enumerate(Work work, BasicFileAttributes before) throws IOException, ScanFault {
            long remaining = limits.maxEntries() - discovered;
            var children = new ArrayList<Path>(); var distinct = new HashSet<Path>();
            try (var stream = access.directory(work.path)) {
                for (Path child : stream) {
                    if (child == null || !child.isAbsolute() || !child.equals(child.normalize())
                            || !work.path.equals(child.getParent()) || !distinct.add(child)) throw new ScanFault(Code.CONTAINMENT_UNPROVEN);
                    if (children.size() >= remaining || location(child).length() > 4096) throw new ScanFault(Code.RESOURCE_LIMIT);
                    children.add(child);
                }
            }
            // Never publish children from an incompletely enumerated/closed directory.
            contained(work.path); verifyRoots();
            if (!stable(before, access.attributes(work.path))) throw new ScanFault(Code.SOURCE_CHANGED);
            children.sort((left, right) -> {
                String a = location(left), b = location(right);
                int comparison = Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
                return comparison != 0 ? comparison : a.compareTo(b);
            });
            discovered = Math.addExact(discovered, children.size());
            // Reverse push gives deterministic depth-first processing regardless of
            // enumeration order. Final values are globally ordered separately.
            for (int index = children.size() - 1; index >= 0; index--) pending.push(new Work(children.get(index), Math.incrementExact(work.depth)));
        }

        private String location(Path path) {
            if (path.equals(input.path)) return null;
            var relative = input.path.relativize(path); var segments = new ArrayList<String>();
            for (Path segment : relative) segments.add(segment.toString());
            return String.join("/", segments); // preserve each literal name, including a rejected backslash
        }

        private void fail(Code code, String location) {
            if (stopped) return;
            Optional<InventoryLocation> controlledLocation = Optional.empty();
            if (location != null) {
                try { controlledLocation = Optional.of(new InventoryLocation(location)); }
                catch (IllegalArgumentException rejectedSpelling) {
                    // Keep the controlled failure, without repairing an unsafe
                    // discovered spelling or substituting exception/provider text.
                }
            }
            if (code == Code.RESOURCE_LIMIT || ordinaryFailures >= limits.maxFailures()) {
                failures.add(new InventoryFailure(Code.RESOURCE_LIMIT, controlledLocation));
                stopped = true;
            } else {
                var fact = new InventoryFailure(code, controlledLocation);
                if (!failures.contains(fact)) { failures.add(fact); ordinaryFailures = Math.incrementExact(ordinaryFailures); }
            }
        }
        private DatasetInventory finish() { return new DatasetInventory(entries, failures); }
    }

    private static boolean stable(BasicFileAttributes before, BasicFileAttributes after) {
        if (before.isRegularFile() != after.isRegularFile() || before.isDirectory() != after.isDirectory()
                || before.isSymbolicLink() != after.isSymbolicLink() || after.isSymbolicLink() || after.isOther()
                || !before.creationTime().equals(after.creationTime())) return false;
        Object first = before.fileKey(), second = after.fileKey();
        if (first != null && second != null && !first.equals(second)) return false;
        if ((first == null) != (second == null)) return false;
        return !before.isRegularFile() || before.size() == after.size() && before.lastModifiedTime().equals(after.lastModifiedTime());
    }
}
