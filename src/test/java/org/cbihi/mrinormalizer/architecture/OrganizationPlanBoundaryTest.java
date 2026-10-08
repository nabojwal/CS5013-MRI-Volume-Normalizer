package org.cbihi.mrinormalizer.architecture;

import static org.junit.jupiter.api.Assertions.*;
import static org.cbihi.mrinormalizer.OrganizationPlanModelTest.*;
import java.io.DataInputStream;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.cbihi.mrinormalizer.application.dataset.dicom.*;
import org.cbihi.mrinormalizer.application.dataset.model.*;
import org.cbihi.mrinormalizer.application.dataset.organization.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.application.service.OrganizationPlanningService;
import org.cbihi.mrinormalizer.domain.model.*;
import org.junit.jupiter.api.Test;

class OrganizationPlanBoundaryTest {
    private static final List<Class<?>> TYPES = List.of(OrganizationCopyOperation.class, OrganizationSourceDecision.class, OrganizationPlan.class, OrganizationPlanningService.class);
    private static final Set<Class<?>> ACCEPTED = Set.of(RelativePath.class, RelativePath.Root.class, ContentDigest.class,
            DatasetInventory.class, InventoryEntry.class, InventoryFailure.class, FormatAssessment.class, ValidityStatus.class,
            DicomSeriesDiscovery.class, DicomSeriesCandidate.class, DicomSeriesMember.class, DicomDiscoveryMetadata.class, DicomMetadataInspection.class,
            DetectionOutcome.class, DetectionDiagnostic.class, DetectionResult.class);
    // F5-T41
    @Test void t41CompiledDependenciesExcludeFilesystemAndImagingReaders() throws Exception {
        var visited = new HashSet<String>(); for (var type : TYPES) inspect(type, visited);
        for (var field : OrganizationPlanningService.class.getDeclaredFields()) assertTrue(Modifier.isStatic(field.getModifiers()));
    }
    // F5-T42
    @Test void t42NoPersistenceExecutionOrPresentationSurface() throws Exception {
        for (var type : TYPES) {
            var strings = pool(type).strings();
            for (var text : strings) for (var banned : List.of("ProvenanceManifest", "ManifestOperation", "ManifestFailure", "SourceFileRecord", "ManifestReceipt", "CheckpointRecord", "ManifestStore", "PublicReport", "DicomSeriesService", "DicomToNifti", "NiftiToDicom", "ImageVolume", "DependencyContainer", "/presentation/", "/infrastructure/", "javax/swing/", "java/awt/", "java/util/logging/", "org/slf4j/", "java/lang/System", "java/time/", "java/util/UUID", "java/util/Random")) assertFalse(text.contains(banned), type.getName() + " -> " + banned);
        }
    }
    // F5-T43: scope is additionally audited against Git outside the test runner.
    @Test void t43ExactlyFrozenF5SourceAndTestSurface() throws Exception {
        var base = Path.of("src/main/java/org/cbihi/mrinormalizer");
        var modelNames = Set.of("OrganizationCopyOperation.java", "OrganizationSourceDecision.java", "OrganizationPlan.java");
        try (var paths = Files.list(base.resolve("application/dataset/organization"))) {
            assertEquals(modelNames, new HashSet<>(paths.map(p -> p.getFileName().toString()).toList()));
        }
        assertTrue(Files.isRegularFile(base.resolve("application/service/OrganizationPlanningService.java")));
        for (var test : List.of("OrganizationPlanModelTest", "OrganizationPlanningServiceTest", "OrganizationNamingTest", "OrganizationPlanCompatibilityTest", "OrganizationPlanIntegrationTest", "architecture/OrganizationPlanBoundaryTest")) assertTrue(Files.isRegularFile(Path.of("src/test/java/org/cbihi/mrinormalizer/" + test + ".java")));
        for (var type : TYPES) if (type.isRecord()) {
            var allowedNames = new HashSet<>(List.of("equals", "hashCode", "toString")); for (var c : type.getRecordComponents()) allowedNames.add(c.getName());
            for (var method : type.getDeclaredMethods()) if (Modifier.isPublic(method.getModifiers())) assertTrue(allowedNames.contains(method.getName()));
        }
    }
    // F5-T44
    @Test void t44OutwardArgumentErrorsAreFixedAndNonIdentifying() {
        var secret = "restricted-sentinel/é/𐀀"; var inv = inventory(entry(secret, DetectionOutcome.DICOM));
        invalid(() -> plan(inv, emptyDiscovery(), Map.of(source(secret), digest())));
        invalid(() -> plan(inv, discovery(success(secret, "1.987654321", "1.123456789", "1.987654322")), Map.of(source("restricted-sentinel-extra"), digest())));
        invalid(() -> new OrganizationCopyOperation("restricted-sentinel", source(secret), output("target"), digest()));
        invalid(() -> new OrganizationPlan(2, OrganizationPlan.Status.COMPLETE, inv, emptyDiscovery(), List.of(), List.of()));
    }
    private static void inspect(Class<?> type, Set<String> visited) throws Exception {
        if (!visited.add(type.getName())) return;
        for (var f : type.getDeclaredFields()) allowed(f.getGenericType());
        for (var m : type.getDeclaredMethods()) { allowed(m.getGenericReturnType()); for (var p : m.getGenericParameterTypes()) allowed(p); }
        for (var c : type.getDeclaredConstructors()) for (var p : c.getGenericParameterTypes()) allowed(p);
        var pool = pool(type);
        for (var text : pool.strings()) for (var banned : List.of("java/io/", "java/nio/file/", "org/dcm4che", "/infrastructure/", "java/security/DigestInputStream", "java/security/DigestOutputStream")) assertFalse(text.contains(banned), type.getName() + " -> " + banned);
        for (var name : pool.classes()) {
            while (name.startsWith("[")) name = name.substring(1);
            if (name.length() == 1 && "ZBCSIJFD".contains(name)) continue;
            if (name.startsWith("L") && name.endsWith(";")) name = name.substring(1, name.length() - 1);
            String binary = name.replace('/', '.'); assertTrue(permitted(binary), binary);
            if (TYPES.stream().anyMatch(t -> binary.startsWith(t.getName() + "$"))) inspect(Class.forName(binary, false, type.getClassLoader()), visited);
        }
        for (var nested : type.getDeclaredClasses()) inspect(nested, visited);
    }
    private record Pool(List<String> strings, List<String> classes) {}
    private static Pool pool(Class<?> type) throws Exception {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            assertNotNull(stream); var in = new DataInputStream(stream); assertEquals(0xcafebabe, in.readInt()); in.readUnsignedShort(); in.readUnsignedShort(); int count = in.readUnsignedShort();
            var strings = new ArrayList<String>(); var indices = new ArrayList<Integer>(); var utf8 = new String[count];
            for (int i = 1; i < count; i++) switch (in.readUnsignedByte()) {
                case 1 -> { utf8[i] = in.readUTF(); strings.add(utf8[i]); }
                case 3, 4 -> in.skipNBytes(4); case 5, 6 -> { in.skipNBytes(8); i++; }
                case 7 -> indices.add(in.readUnsignedShort()); case 8, 16, 19, 20 -> in.skipNBytes(2);
                case 9, 10, 11, 12, 17, 18 -> in.skipNBytes(4); case 15 -> in.skipNBytes(3); default -> fail("Unknown class constant");
            }
            return new Pool(strings, indices.stream().map(i -> utf8[i]).toList());
        }
    }
    private static void allowed(Type type) {
        if (type instanceof ParameterizedType p) { allowed(p.getRawType()); for (var a : p.getActualTypeArguments()) allowed(a); return; }
        if (type instanceof GenericArrayType a) { allowed(a.getGenericComponentType()); return; }
        if (type instanceof WildcardType w) { for (var b : w.getUpperBounds()) allowed(b); for (var b : w.getLowerBounds()) allowed(b); return; }
        if (type instanceof TypeVariable<?>) return;
        var c = (Class<?>)type; if (c.isArray()) { allowed(c.getComponentType()); return; }
        assertTrue(c.isPrimitive() || permitted(c.getName()), c.getName());
    }
    private static boolean permitted(String name) {
        return name.startsWith("java.lang.") || name.startsWith("java.util.") || name.startsWith("java.nio.charset.") || name.equals("java.nio.ByteBuffer")
                || name.equals("java.security.MessageDigest") || name.equals("java.security.NoSuchAlgorithmException")
                || TYPES.stream().anyMatch(t -> name.equals(t.getName()) || name.startsWith(t.getName() + "$"))
                || ACCEPTED.stream().anyMatch(t -> name.equals(t.getName()));
    }
}
