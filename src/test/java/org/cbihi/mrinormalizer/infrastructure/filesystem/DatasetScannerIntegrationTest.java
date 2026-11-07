package org.cbihi.mrinormalizer.infrastructure.filesystem;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import org.cbihi.mrinormalizer.application.dataset.model.*;
import org.cbihi.mrinormalizer.application.service.FormatDetectionService;
import org.cbihi.mrinormalizer.domain.model.*;
import org.cbihi.mrinormalizer.infrastructure.detection.DicomFormatProbe;
import org.cbihi.mrinormalizer.infrastructure.detection.NiftiFormatProbe;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatasetScannerIntegrationTest {
    @TempDir Path temporary;
    private static final InventoryLimits LIMITS = new InventoryLimits(100, 20, 30);

    @Test void actualDicomPlainAndGzipNiftiRemainOnlyRecognitionEvidence() throws Exception {
        var roots = roots();
        Files.write(roots.input.resolve("one.dcm"), dicom());
        Files.write(roots.input.resolve("two.nii"), nifti());
        Files.write(roots.input.resolve("three.nii.gz"), gzip(nifti()));
        var result = scanner(roots, detector()).scan(); assertTrue(result.complete());
        assertEquals(Set.of(DetectionOutcome.DICOM, DetectionOutcome.NIFTI, DetectionOutcome.NIFTI_GZ),
                result.entries().stream().map(e -> e.assessment().initialDetection().outcome()).collect(java.util.stream.Collectors.toSet()));
        for (var entry : result.entries()) {
            assertEquals(ValidityStatus.NOT_ASSESSED, entry.assessment().validity());
            assertEquals(SupportStatus.NOT_ASSESSED, entry.assessment().support());
            assertEquals(ConversionReadiness.REQUIRES_VALIDATION, entry.assessment().readiness());
        }
    }

    @Test void misleadingAndExtensionlessNamesNeverOverrideContent() throws Exception {
        var roots = roots(); Files.write(roots.input.resolve("Dicom Series Looks Nifti.nii"), dicom());
        Files.write(roots.input.resolve("extensionless"), nifti()); Files.write(roots.input.resolve("fake.dcm"), new byte[] {1, 2, 3, 4});
        var result = scanner(roots, detector()).scan(); assertTrue(result.complete());
        assertEquals(List.of(DetectionOutcome.DICOM, DetectionOutcome.NIFTI, DetectionOutcome.UNKNOWN),
                result.entries().stream().map(e -> e.assessment().initialDetection().outcome()).toList());
        assertTrue(result.entries().get(0).assessment().initialDetection().extensionMismatch());
        assertTrue(result.entries().get(1).assessment().initialDetection().extensionMismatch());
        assertEquals(DetectionDiagnostic.UNSUPPORTED_FORMAT, result.entries().get(2).assessment().initialDetection().diagnostic());
    }

    @Test void corruptUnknownAndEmptyInputsKeepOriginalDiagnosticsAndConservativeStates() throws Exception {
        var roots = roots(); var bad = nifti(); bad[344] = 'x';
        Files.write(roots.input.resolve("bad"), bad); Files.write(roots.input.resolve("empty"), new byte[0]);
        Files.write(roots.input.resolve("random"), new byte[] {1, 2, 3, 4});
        var result = scanner(roots, detector()).scan(); assertFalse(result.complete());
        assertEquals(List.of(DetectionDiagnostic.INVALID_NIFTI, DetectionDiagnostic.EMPTY_INPUT, DetectionDiagnostic.UNSUPPORTED_FORMAT),
                result.entries().stream().map(e -> e.assessment().initialDetection().diagnostic()).toList());
        assertEquals(ValidityStatus.INVALID, result.entries().get(0).assessment().validity());
        assertEquals(ConversionReadiness.BLOCKED, result.entries().get(0).assessment().readiness());
        assertTrue(result.failures().isEmpty()); // M5-owned diagnostics are not copied into F2 codes.
    }

    @Test void actualInvalidGzipIsPreservedWithoutInventingPayloadValidation() throws Exception {
        var roots = roots(); Files.write(roots.input.resolve("bad.nii.gz"), new byte[] {0x1f, (byte) 0x8b, 0, 1});
        var result = scanner(roots, detector()).scan();
        assertEquals(DetectionDiagnostic.INVALID_GZIP, result.entries().get(0).assessment().initialDetection().diagnostic());
        assertEquals(ValidityStatus.INVALID, result.entries().get(0).assessment().validity());
    }

    @Test void ioUnreadableAndBudgetResultsRemainDistinctAndNeverBecomeCompleteOrReady() throws Exception {
        var roots = roots(); Files.write(roots.input.resolve("a"), new byte[] {1});
        for (var diagnostic : List.of(DetectionDiagnostic.INPUT_NOT_READABLE, DetectionDiagnostic.IO_ERROR, DetectionDiagnostic.INPUT_TOO_LARGE)) {
            var detector = new FormatDetectionService(List.of(source -> DetectionResult.unknown(diagnostic)));
            var result = scanner(roots, detector).scan(); assertFalse(result.complete()); assertTrue(result.failures().isEmpty());
            var assessment = result.entries().get(0).assessment();
            assertEquals(diagnostic, assessment.initialDetection().diagnostic());
            assertEquals(ConversionReadiness.BLOCKED, assessment.readiness());
            assertEquals(diagnostic == DetectionDiagnostic.INPUT_TOO_LARGE ? ValidityStatus.INCONCLUSIVE : ValidityStatus.NOT_ASSESSED, assessment.validity());
        }
    }

    @Test void truncatedPayloadAndTrailerDoNotInvokeReadersOrUpgradeRecognition() throws Exception {
        var roots = roots(); byte[] compressed = gzip(nifti());
        Files.write(roots.input.resolve("header-only.nii"), nifti());
        Files.write(roots.input.resolve("no-trailer.nii.gz"), java.util.Arrays.copyOf(compressed, compressed.length - 8));
        var result = scanner(roots, detector()).scan();
        for (var entry : result.entries()) {
            assertEquals(ValidityStatus.NOT_ASSESSED, entry.assessment().validity());
            assertEquals(ConversionReadiness.REQUIRES_VALIDATION, entry.assessment().readiness());
        }
    }

    @Test void scanPreservesAllSourceAndOutputBytesAndDoesNotWriteReportingArtifacts() throws Exception {
        var roots = roots(); var source = Files.write(roots.input.resolve("Patient Name.1.2.3.dcm"), dicom());
        var output = Files.write(roots.output.resolve("generated.nii"), nifti());
        var manifest = Files.createDirectory(roots.output.resolve("manifest")); Files.write(manifest.resolve("plan.json"), new byte[] {5});
        var beforeSource = snapshot(roots.input); var beforeOutput = snapshot(roots.output);
        var result = scanner(roots, detector()).scan(); assertEquals(List.of("Patient Name.1.2.3.dcm"), result.entries().stream().map(e -> e.source().path()).toList());
        assertSameBytes(beforeSource, snapshot(roots.input)); assertSameBytes(beforeOutput, snapshot(roots.output));
        assertTrue(Files.exists(source)); assertTrue(Files.exists(output));
        assertFalse(result.toString().contains(roots.input.toString())); assertFalse(result.toString().contains(roots.output.toString()));
        assertTrue(result.entries().get(0).source().path().contains("Patient Name")); // operational, restricted identity; not anonymous
    }

    @Test void compiledScannerDependenciesCannotReachPixelsSeriesConversionHashingOrPublicReporting() throws Exception {
        var allowed = Set.of("org/cbihi/mrinormalizer/infrastructure/filesystem/NioDatasetScanner",
                "org/cbihi/mrinormalizer/application/port/out/DatasetScanner",
                "org/cbihi/mrinormalizer/application/dataset/model/InventoryLimits",
                "org/cbihi/mrinormalizer/application/dataset/model/InventoryLocation",
                "org/cbihi/mrinormalizer/application/dataset/model/InventoryEntry",
                "org/cbihi/mrinormalizer/application/dataset/model/InventoryFailure",
                "org/cbihi/mrinormalizer/application/dataset/model/DatasetInventory",
                "org/cbihi/mrinormalizer/application/dataset/model/FormatAssessment",
                "org/cbihi/mrinormalizer/application/service/FormatDetectionService",
                "org/cbihi/mrinormalizer/domain/model/InputSource",
                "org/cbihi/mrinormalizer/application/provenance/manifest/RelativePath");
        var classes = new HashSet<Class<?>>(); classes.add(NioDatasetScanner.class); classes.addAll(List.of(NioDatasetScanner.class.getDeclaredClasses()));
        for (var type : classes) {
            var references = classDependencies(type);
            for (String dependency : references.classes) {
                String top = dependency.replaceFirst("^\\[+L", "").replaceFirst(";$", "").split("\\$", 2)[0];
                if (top.startsWith("org/")) assertTrue(allowed.contains(top), dependency);
                assertFalse(top.startsWith("java/security/") || top.startsWith("java/nio/channels/")
                        || top.startsWith("java/util/zip/") || top.startsWith("java/io/") && !top.equals("java/io/IOException"), dependency);
            }
            for (String method : references.methods) if (method.startsWith("java/nio/file/Files#")) {
                assertTrue(Set.of("java/nio/file/Files#readAttributes", "java/nio/file/Files#isSameFile",
                        "java/nio/file/Files#newDirectoryStream").contains(method), method);
            }
        }
        assertEquals(List.of("scan"), java.util.Arrays.stream(NioDatasetScanner.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers())).map(m -> m.getName()).toList());
    }

    // Inspect compiled class references, not source strings. Existing M5 probes remain
    // the sole recognition path; no dcm4che/pixel/series/reader dependency is allowed in F2.
    private static CompiledReferences classDependencies(Class<?> type) throws IOException {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            assertNotNull(stream); var in = new DataInputStream(stream); assertEquals(0xcafebabe, in.readInt());
            in.readUnsignedShort(); in.readUnsignedShort(); int count = in.readUnsignedShort();
            String[] utf8 = new String[count]; int[] names = new int[count];
            int[] tags = new int[count], owners = new int[count], signatures = new int[count], memberNames = new int[count];
            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte(); tags[i] = tag;
                switch (tag) {
                    case 1 -> utf8[i] = in.readUTF();
                    case 7 -> names[i] = in.readUnsignedShort();
                    case 3, 4 -> in.skipNBytes(4);
                    case 5, 6 -> { in.skipNBytes(8); i++; }
                    case 8, 16, 19, 20 -> in.skipNBytes(2);
                    case 9, 10, 11 -> { owners[i] = in.readUnsignedShort(); signatures[i] = in.readUnsignedShort(); }
                    case 12 -> { memberNames[i] = in.readUnsignedShort(); in.readUnsignedShort(); }
                    case 17, 18 -> in.skipNBytes(4);
                    case 15 -> in.skipNBytes(3);
                    default -> throw new IOException("Unknown class constant tag");
                }
            }
            var result = new HashSet<String>(); for (int name : names) if (name != 0) result.add(utf8[name]);
            var methods = new HashSet<String>();
            for (int i = 1; i < count; i++) if (tags[i] == 10 || tags[i] == 11)
                methods.add(utf8[names[owners[i]]] + "#" + utf8[memberNames[signatures[i]]]);
            return new CompiledReferences(result, methods);
        }
    }
    private record CompiledReferences(Set<String> classes, Set<String> methods) { }
    private Roots roots() throws IOException { return new Roots(Files.createDirectory(temporary.resolve("input")), Files.createDirectory(temporary.resolve("output"))); }
    private static NioDatasetScanner scanner(Roots roots, FormatDetectionService detector) { return new NioDatasetScanner(roots.input, roots.output, LIMITS, detector); }
    private static FormatDetectionService detector() { return new FormatDetectionService(List.of(new DicomFormatProbe(), new NiftiFormatProbe())); }
    private static byte[] nifti() {
        var bytes = new byte[348]; ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(348);
        bytes[344] = 'n'; bytes[345] = '+'; bytes[346] = '1'; return bytes;
    }
    private static byte[] gzip(byte[] bytes) throws IOException {
        var bytesOut = new ByteArrayOutputStream(); try (var gzip = new GZIPOutputStream(bytesOut)) { gzip.write(bytes); } return bytesOut.toByteArray();
    }
    private static byte[] dicom() throws IOException {
        var dataset = new Attributes(); dataset.setString(Tag.SOPClassUID, VR.UI, UID.MRImageStorage);
        dataset.setString(Tag.SOPInstanceUID, VR.UI, "1.2.826.0.1.3680043.10.5013.2.1");
        var out = new ByteArrayOutputStream(); try (var stream = new DicomOutputStream(out, UID.ExplicitVRLittleEndian)) {
            stream.writeFileMetaInformation(dataset.createFileMetaInformation(UID.ExplicitVRLittleEndian)); stream.writeDataset(null, dataset);
        }
        return out.toByteArray(); // no Pixel Data fixture
    }
    private static Map<String, byte[]> snapshot(Path root) throws IOException {
        var result = new java.util.TreeMap<String, byte[]>(); try (var paths = Files.walk(root)) {
            for (var path : paths.toList()) result.put(root.relativize(path).toString(), Files.isDirectory(path) ? null : Files.readAllBytes(path));
        }
        return result;
    }
    private static void assertSameBytes(Map<String, byte[]> before, Map<String, byte[]> after) {
        assertEquals(before.keySet(), after.keySet()); for (String key : before.keySet()) assertArrayEquals(before.get(key), after.get(key));
    }
    private record Roots(Path input, Path output) { }
}
