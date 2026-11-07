package org.cbihi.mrinormalizer.infrastructure.filesystem;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.cbihi.mrinormalizer.application.dataset.model.*;
import org.cbihi.mrinormalizer.application.service.FormatDetectionService;
import org.cbihi.mrinormalizer.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NioDatasetScannerTest {
    @TempDir Path temporary;
    private static final InventoryLimits LIMITS = new InventoryLimits(1000, 30, 100);

    @Test void separatedExistingRootsAndEmptyDatasetAreComplete() throws Exception {
        var roots = roots(); var inventory = scanner(roots, LIMITS, access()).scan();
        assertTrue(inventory.complete()); assertTrue(inventory.entries().isEmpty()); assertTrue(inventory.failures().isEmpty());
    }

    @Test void invalidMissingFileAndMalformedRootsHaveControlledRootFailures() throws Exception {
        var roots = roots(); var file = Files.write(temporary.resolve("file"), new byte[] {1});
        assertCode(new NioDatasetScanner(temporary.resolve("missing"), roots.output, LIMITS, detector()).scan(), InventoryFailure.Code.INPUT_ROOT_INVALID);
        assertCode(new NioDatasetScanner(file, roots.output, LIMITS, detector()).scan(), InventoryFailure.Code.INPUT_ROOT_INVALID);
        assertCode(new NioDatasetScanner(roots.input, temporary.resolve("missing"), LIMITS, detector()).scan(), InventoryFailure.Code.OUTPUT_ROOT_INVALID);
        assertCode(new NioDatasetScanner(roots.input, file, LIMITS, detector()).scan(), InventoryFailure.Code.OUTPUT_ROOT_INVALID);
        assertThrows(IllegalArgumentException.class, () -> new NioDatasetScanner(null, roots.output, LIMITS, detector()));
        assertThrows(IllegalArgumentException.class, () -> new NioDatasetScanner(roots.input, roots.output, null, detector()));
        assertThrows(IllegalArgumentException.class, () -> new NioDatasetScanner(roots.input, roots.output, LIMITS, null));
    }

    @Test void equalAndBothNestedRootDirectionsAreRejectedBeforeDetection() throws Exception {
        var roots = roots(); var nested = Files.createDirectory(roots.input.resolve("nested"));
        var calls = new AtomicInteger(); var detector = detector(calls);
        for (var pair : List.of(new Roots(roots.input, roots.input), new Roots(roots.input, nested), new Roots(nested, roots.input))) {
            assertCode(new NioDatasetScanner(pair.input, pair.output, LIMITS, detector).scan(), InventoryFailure.Code.ROOTS_OVERLAP);
        }
        assertEquals(0, calls.get());
    }

    @Test void safeConfiguredAncestorAliasIsResolvedWithoutBlanketRejection() throws Exception {
        var roots = roots(); Files.write(roots.input.resolve("image"), new byte[] {1});
        Path alias = temporary.resolve("alias").resolve("input");
        var access = new NioDatasetScanner.FileAccess() {
            @Override Path real(Path p) throws IOException { return p.equals(alias) ? roots.input.toRealPath() : super.real(p); }
        };
        var result = new NioDatasetScanner(alias, roots.output, LIMITS, detector(), access).scan();
        assertTrue(result.complete()); assertEquals(List.of("image"), paths(result));
    }

    @Test void canonicalAliasesResolvingToOverlapAreRejected() throws Exception {
        var roots = roots(); Path alias = temporary.resolve("alias");
        var access = new NioDatasetScanner.FileAccess() {
            @Override Path real(Path p) throws IOException { return p.equals(alias) ? roots.input.toRealPath() : super.real(p); }
        };
        assertCode(new NioDatasetScanner(alias, roots.input, LIMITS, detector(), access).scan(), InventoryFailure.Code.ROOTS_OVERLAP);
    }

    @Test void unavailableCanonicalEvidenceFailsClosedWithoutProviderText() throws Exception {
        var roots = roots(); var access = new NioDatasetScanner.FileAccess() {
            @Override Path real(Path p) throws IOException { throw new IOException("PATIENT secret absolute root"); }
        };
        var result = scanner(roots, LIMITS, access).scan();
        assertCode(result, InventoryFailure.Code.INPUT_ROOT_INVALID);
        assertFalse(result.toString().contains("PATIENT")); assertTrue(result.failures().get(0).location().isEmpty());
    }

    @Test void recursiveFilesAndDeepSupportedNestingRetainExactRelativeIdentity() throws Exception {
        var roots = roots(); var parent = roots.input;
        for (int i = 0; i < 20; i++) parent = Files.createDirectory(parent.resolve("d"));
        Files.write(parent.resolve("Patient Name.1.2.3"), new byte[] {1});
        Files.write(roots.input.resolve("root"), new byte[] {1});
        var result = scanner(roots, LIMITS, access()).scan();
        assertTrue(result.complete()); assertEquals(List.of("d/".repeat(20) + "Patient Name.1.2.3", "root"), paths(result));
        assertEquals(result, scanner(roots, LIMITS, access()).scan());
    }

    @Test void exactEntryBudgetIncludesDirectoriesAndFirstOverNeverTruncatesSilently() throws Exception {
        var roots = roots(); var child = Files.createDirectory(roots.input.resolve("d"));
        Files.write(child.resolve("one"), new byte[] {1}); Files.write(roots.input.resolve("two"), new byte[] {1});
        assertTrue(scanner(roots, new InventoryLimits(3, 3, 3), access()).scan().complete());
        var over = scanner(roots, new InventoryLimits(2, 3, 3), access()).scan();
        assertCode(over, InventoryFailure.Code.RESOURCE_LIMIT); assertFalse(over.complete());
        assertEquals(over, scanner(roots, new InventoryLimits(2, 3, 3), reverse()).scan());
    }

    @Test void wideDirectoryOverBudgetHasDeterministicBlockedOutcome() throws Exception {
        var roots = roots(); for (int i = 0; i < 50; i++) Files.write(roots.input.resolve("file-" + i), new byte[] {1});
        assertEquals(50, scanner(roots, new InventoryLimits(50, 1, 2), access()).scan().entries().size());
        var normal = scanner(roots, new InventoryLimits(49, 1, 2), access()).scan();
        assertCode(normal, InventoryFailure.Code.RESOURCE_LIMIT); assertTrue(normal.entries().isEmpty());
        assertEquals(normal, scanner(roots, new InventoryLimits(49, 1, 2), reverse()).scan());
    }

    @Test void depthLimitIsExplicitAtExactBoundaryAndFirstOver() throws Exception {
        var roots = roots(); Path d = Files.createDirectory(roots.input.resolve("d")); Files.write(d.resolve("a"), new byte[] {1});
        assertTrue(scanner(roots, new InventoryLimits(5, 2, 3), access()).scan().complete());
        assertCode(scanner(roots, new InventoryLimits(5, 1, 3), access()).scan(), InventoryFailure.Code.RESOURCE_LIMIT);
    }

    @Test void discoveredFileAndDirectorySymlinksAreNeverDetectedOrDescended() throws Exception {
        var roots = roots(); Path file = Files.write(roots.input.resolve("file"), new byte[] {1});
        Path directory = Files.createDirectory(roots.input.resolve("directory")); Files.write(directory.resolve("hidden"), new byte[] {1});
        var calls = new AtomicInteger(); var access = new NioDatasetScanner.FileAccess() {
            @Override BasicFileAttributes attributes(Path p) throws IOException {
                var actual = super.attributes(p); return p.equals(file) || p.equals(directory) ? modified(actual, false, false, true, false, actual.fileKey()) : actual;
            }
            @Override DirectoryStream<Path> directory(Path p) throws IOException {
                assertNotEquals(directory, p, "Discovered symlink directory was followed"); return super.directory(p);
            }
        };
        var result = new NioDatasetScanner(roots.input, roots.output, LIMITS, detector(calls), access).scan();
        assertFalse(result.complete()); assertTrue(result.entries().isEmpty()); assertEquals(2, result.failures().size());
        assertTrue(result.failures().stream().allMatch(f -> f.code() == InventoryFailure.Code.SYMLINK_DISALLOWED)); assertEquals(0, calls.get());
    }

    @Test void specialEntriesFailClosedWithoutInvokingDetection() throws Exception {
        var roots = roots(); Path file = Files.write(roots.input.resolve("special"), new byte[] {1});
        var access = new NioDatasetScanner.FileAccess() {
            @Override BasicFileAttributes attributes(Path p) throws IOException {
                var a = super.attributes(p); return p.equals(file) ? modified(a, false, false, false, true, a.fileKey()) : a;
            }
        };
        assertCode(scanner(roots, LIMITS, access).scan(), InventoryFailure.Code.SPECIAL_FILE_UNSUPPORTED);
    }

    @Test void escapedRealEntryAndInjectedForeignDirectoryChildAreBlocked() throws Exception {
        var roots = roots(); Path file = Files.write(roots.input.resolve("image"), new byte[] {1});
        Path foreign = Files.write(temporary.resolve("foreign"), new byte[] {1});
        var escape = new NioDatasetScanner.FileAccess() {
            @Override Path real(Path p) throws IOException { return p.equals(file) ? foreign.toRealPath() : super.real(p); }
        };
        assertCode(scanner(roots, LIMITS, escape).scan(), InventoryFailure.Code.PATH_OUTSIDE_ROOT);
        var foreignChild = new NioDatasetScanner.FileAccess() {
            @Override DirectoryStream<Path> directory(Path p) { return stream(List.of(foreign)); }
        };
        assertCode(scanner(roots, LIMITS, foreignChild).scan(), InventoryFailure.Code.CONTAINMENT_UNPROVEN);
    }

    @Test void nonrepresentableSourceNamesAreRetainedAsBlockedIdentityNotRenamed() throws Exception {
        var roots = roots(); Path file = Files.write(roots.input.resolve("actual"), new byte[] {1});
        Path invalid = roots.input.resolve("name."); // seam avoids Windows name normalization
        var access = new NioDatasetScanner.FileAccess() {
            @Override DirectoryStream<Path> directory(Path p) { return stream(List.of(invalid)); }
        };
        var result = scanner(roots, LIMITS, access).scan();
        assertCode(result, InventoryFailure.Code.UNREPRESENTABLE_REFERENCE);
        assertEquals(Optional.of(new InventoryLocation("name.")), result.failures().get(0).location());
        assertTrue(result.entries().isEmpty()); assertTrue(Files.exists(file));
    }

    @Test void entryBudgetCountsRejectedDiscoveredNamesBeforeRecognition() throws Exception {
        var roots = roots(); Path accepted = Files.write(roots.input.resolve("a"), new byte[] {1});
        Path rejected = roots.input.resolve("name.");
        var access = new NioDatasetScanner.FileAccess() {
            @Override DirectoryStream<Path> directory(Path p) { return stream(List.of(rejected, accepted)); }
        };
        var exact = scanner(roots, new InventoryLimits(2, 1, 1), access).scan();
        assertEquals(List.of("a"), paths(exact));
        assertEquals(List.of(new InventoryFailure(InventoryFailure.Code.UNREPRESENTABLE_REFERENCE,
                Optional.of(new InventoryLocation("name.")))), exact.failures());
        assertFalse(exact.complete());
        var exhausted = scanner(roots, new InventoryLimits(1, 1, 1), access).scan();
        assertCode(exhausted, InventoryFailure.Code.RESOURCE_LIMIT);
        assertTrue(exhausted.entries().isEmpty());
        assertEquals(1, exhausted.failures().size());
    }

    @Test void attributeAndTraversalIoFailuresKeepControlledRelativeLocation() throws Exception {
        var roots = roots(); var file = Files.write(roots.input.resolve("a"), new byte[] {1});
        var attrs = new NioDatasetScanner.FileAccess() {
            @Override BasicFileAttributes attributes(Path p) throws IOException { if (p.equals(file)) throw new IOException("secret patient"); return super.attributes(p); }
        };
        var result = scanner(roots, LIMITS, attrs).scan(); assertCode(result, InventoryFailure.Code.TRAVERSAL_FAILED);
        assertEquals(Optional.of(new InventoryLocation("a")), result.failures().get(0).location()); assertFalse(result.toString().contains("secret"));
        var traversal = new NioDatasetScanner.FileAccess() {
            @Override DirectoryStream<Path> directory(Path p) throws IOException { throw new IOException("secret provider"); }
        };
        assertCode(scanner(roots, LIMITS, traversal).scan(), InventoryFailure.Code.TRAVERSAL_FAILED);
    }

    @Test void iteratorAndCloseFailuresDoNotPublishAnApparentlyCompleteDirectory() throws Exception {
        var roots = roots(); Files.write(roots.input.resolve("a"), new byte[] {1});
        for (boolean closeFault : new boolean[] {false, true}) {
            var access = new NioDatasetScanner.FileAccess() {
                @Override DirectoryStream<Path> directory(Path p) {
                    return new DirectoryStream<>() {
                        public Iterator<Path> iterator() {
                            if (!closeFault) throw new java.nio.file.DirectoryIteratorException(new IOException("secret"));
                            return List.of(p.resolve("a")).iterator();
                        }
                        public void close() throws IOException { if (closeFault) throw new IOException("secret"); }
                    };
                }
            };
            var result = scanner(roots, LIMITS, access).scan();
            assertCode(result, InventoryFailure.Code.TRAVERSAL_FAILED); assertTrue(result.entries().isEmpty());
        }
    }

    @Test void failureBudgetRetainsPriorFactsAndOneExplicitTerminalLimit() throws Exception {
        var roots = roots(); for (String name : List.of("a", "b", "c")) Files.write(roots.input.resolve(name), new byte[] {1});
        var access = new NioDatasetScanner.FileAccess() {
            @Override BasicFileAttributes attributes(Path p) throws IOException {
                var a = super.attributes(p); return p.equals(roots.input) || p.equals(roots.output) ? a : modified(a, false, false, false, true, a.fileKey());
            }
        };
        var result = scanner(roots, new InventoryLimits(9, 2, 1), access).scan();
        assertEquals(2, result.failures().size()); // one ordinary fact plus one reserved terminal fact
        assertEquals(1, result.failures().stream().filter(f -> f.code() == InventoryFailure.Code.SPECIAL_FILE_UNSUPPORTED).count());
        assertEquals(1, result.failures().stream().filter(f -> f.code() == InventoryFailure.Code.RESOURCE_LIMIT).count());
        assertFalse(result.complete());
    }

    @Test void enumerationAndCreationOrderDoNotBecomeSemanticOrder() throws Exception {
        var first = roots(); var second = new Roots(Files.createDirectory(temporary.resolve("input2")), Files.createDirectory(temporary.resolve("output2")));
        for (String name : List.of("z", "A-one", "a-two", "é")) Files.write(first.input.resolve(name), new byte[] {1});
        for (String name : List.of("é", "a-two", "A-one", "z")) Files.write(second.input.resolve(name), new byte[] {1});
        var expected = scanner(first, LIMITS, access()).scan();
        assertEquals(List.of("A-one", "a-two", "z", "é"), paths(expected));
        assertEquals(expected, scanner(first, LIMITS, reverse()).scan());
        assertEquals(expected, scanner(second, LIMITS, reverse()).scan());
        assertThrows(UnsupportedOperationException.class, () -> expected.entries().clear());
    }

    @Test void changedFileBeforeOrDuringDetectionDoesNotRetainTrustedEntry() throws Exception {
        var roots = roots(); var file = Files.write(roots.input.resolve("a"), new byte[] {1});
        var calls = new AtomicInteger(); var access = new NioDatasetScanner.FileAccess() {
            @Override BasicFileAttributes attributes(Path p) throws IOException {
                var a = super.attributes(p);
                if (p.equals(file)) return modified(a, a.isRegularFile(), a.isDirectory(), false, false, "key-" + calls.incrementAndGet());
                return a;
            }
        };
        assertCode(scanner(roots, LIMITS, access).scan(), InventoryFailure.Code.SOURCE_CHANGED);
        assertTrue(scanner(roots, LIMITS, access).scan().entries().isEmpty());
        var mutationDetector = new FormatDetectionService(List.of(input -> {
            try { Files.write(Path.of(input.reference()), new byte[] {2, 3}); } catch (IOException e) { throw new AssertionError(e); }
            return DetectionResult.identified(DetectionOutcome.DICOM);
        }));
        var result = new NioDatasetScanner(roots.input, roots.output, LIMITS, mutationDetector).scan();
        assertCode(result, InventoryFailure.Code.SOURCE_CHANGED); assertTrue(result.entries().isEmpty());
    }

    @Test void nullProviderFileKeysDoNotAutomaticallyRejectContainedEntries() throws Exception {
        var roots = roots(); Files.write(roots.input.resolve("a"), new byte[] {1});
        var access = new NioDatasetScanner.FileAccess() {
            @Override BasicFileAttributes attributes(Path p) throws IOException { var a = super.attributes(p); return modified(a, a.isRegularFile(), a.isDirectory(), a.isSymbolicLink(), a.isOther(), null); }
        };
        assertTrue(scanner(roots, LIMITS, access).scan().complete());
    }

    @Test void incoherentOrThrowingDetectionProducesOnlyControlledF2Failure() throws Exception {
        var roots = roots(); Files.write(roots.input.resolve("a"), new byte[] {1});
        for (var detector : List.of(new FormatDetectionService(List.of(input -> { throw new IllegalStateException("secret"); })),
                new FormatDetectionService(List.of(input -> new DetectionResult(DetectionOutcome.DICOM, DetectionDiagnostic.IO_ERROR, false))))) {
            var result = new NioDatasetScanner(roots.input, roots.output, LIMITS, detector).scan();
            assertCode(result, InventoryFailure.Code.DETECTION_FAILED); assertFalse(result.toString().contains("secret"));
        }
    }

    @Test void configuredRootSubstitutionAfterValidationStopsDiscovery() throws Exception {
        var roots = roots(); Files.write(roots.input.resolve("a"), new byte[] {1});
        for (Path root : List.of(roots.input, roots.output)) {
            var reads = new AtomicInteger(); var access = new NioDatasetScanner.FileAccess() {
                @Override BasicFileAttributes attributes(Path p) throws IOException {
                    var a = super.attributes(p); if (p.equals(root)) return modified(a, false, true, false, false, "root-" + reads.incrementAndGet()); return a;
                }
            };
            assertCode(scanner(roots, LIMITS, access).scan(), root.equals(roots.input)
                    ? InventoryFailure.Code.SOURCE_CHANGED : InventoryFailure.Code.CONTAINMENT_UNPROVEN);
        }
    }

    private Roots roots() throws IOException { return new Roots(Files.createDirectory(temporary.resolve("input")), Files.createDirectory(temporary.resolve("output"))); }
    private static List<String> paths(DatasetInventory value) { return value.entries().stream().map(e -> e.source().path()).toList(); }
    private static void assertCode(DatasetInventory result, InventoryFailure.Code code) {
        assertFalse(result.complete()); assertTrue(result.failures().stream().anyMatch(f -> f.code() == code), result.toString());
    }
    private static FormatDetectionService detector() { return detector(new AtomicInteger()); }
    private static FormatDetectionService detector(AtomicInteger calls) {
        return new FormatDetectionService(List.of(input -> { calls.incrementAndGet(); return DetectionResult.identified(DetectionOutcome.DICOM); }));
    }
    private static NioDatasetScanner.FileAccess access() { return new NioDatasetScanner.FileAccess(); }
    private static NioDatasetScanner scanner(Roots roots, InventoryLimits limits, NioDatasetScanner.FileAccess access) {
        return new NioDatasetScanner(roots.input, roots.output, limits, detector(), access);
    }
    private static NioDatasetScanner.FileAccess reverse() {
        return new NioDatasetScanner.FileAccess() {
            @Override DirectoryStream<Path> directory(Path p) throws IOException {
                var children = new ArrayList<Path>(); try (var stream = super.directory(p)) { stream.forEach(children::add); }
                Collections.reverse(children); return stream(children);
            }
        };
    }
    private static DirectoryStream<Path> stream(List<Path> paths) {
        return new DirectoryStream<>() { public Iterator<Path> iterator() { return paths.iterator(); } public void close() { } };
    }
    private record Roots(Path input, Path output) { }
    private static BasicFileAttributes modified(BasicFileAttributes a, boolean file, boolean dir, boolean link, boolean other, Object key) {
        return new BasicFileAttributes() {
            public FileTime lastModifiedTime() { return a.lastModifiedTime(); }
            public FileTime lastAccessTime() { return a.lastAccessTime(); }
            public FileTime creationTime() { return a.creationTime(); }
            public boolean isRegularFile() { return file; } public boolean isDirectory() { return dir; }
            public boolean isSymbolicLink() { return link; } public boolean isOther() { return other; }
            public long size() { return a.size(); } public Object fileKey() { return key; }
        };
    }
}
