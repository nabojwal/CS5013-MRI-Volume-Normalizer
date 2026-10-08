package org.cbihi.mrinormalizer.architecture;

import static org.junit.jupiter.api.Assertions.*;

import java.io.DataInputStream;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.cbihi.mrinormalizer.application.dataset.dicom.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;
import org.cbihi.mrinormalizer.application.service.DicomSeriesDiscoveryService;
import org.junit.jupiter.api.Test;

class DicomSeriesDiscoveryBoundaryTest {
    private static final List<Class<?>> TYPES = List.of(DicomSeriesKey.class, DicomSeriesMember.class, DicomSeriesScreeningFinding.class,
            DicomSeriesCandidate.class, DicomSeriesDiscovery.class, DicomSeriesDiscoveryService.class);
    private static final Set<String> FORBIDDEN_METHODS = Set.of("screeningPassed", "isReady", "isValid", "isSupported", "isCompatible", "complete");

    // T23
    @Test void compiledDependenciesRespectF4Boundary() throws Exception {
        var inspected = new HashSet<String>();
        for (var type : TYPES) inspect(type, inspected);
        for (var field : DicomSeriesDiscoveryService.class.getDeclaredFields()) assertTrue(Modifier.isStatic(field.getModifiers()), "No retained per-discovery state");
    }

    private static void inspect(Class<?> type, Set<String> inspected) throws Exception {
        if (!inspected.add(type.getName())) return;
        for (var component : type.isRecord() ? type.getRecordComponents() : new RecordComponent[0]) allowed(component.getGenericType());
        for (var field : type.getDeclaredFields()) allowed(field.getGenericType());
        for (var method : type.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) assertFalse(FORBIDDEN_METHODS.contains(method.getName()));
            allowed(method.getGenericReturnType()); for (var parameter : method.getGenericParameterTypes()) allowed(parameter);
        }
        for (var ctor : type.getDeclaredConstructors()) for (var parameter : ctor.getGenericParameterTypes()) allowed(parameter);
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            assertNotNull(stream); var input = new DataInputStream(stream); assertEquals(0xcafebabe, input.readInt());
            input.readUnsignedShort(); input.readUnsignedShort(); int count = input.readUnsignedShort();
            var strings = new ArrayList<String>(); var classIndices = new ArrayList<Integer>(); var utf8 = new String[count];
            for (int i = 1; i < count; i++) switch (input.readUnsignedByte()) {
                case 1 -> { utf8[i] = input.readUTF(); strings.add(utf8[i]); }
                case 3, 4 -> input.skipNBytes(4);
                case 5, 6 -> { input.skipNBytes(8); i++; }
                case 7 -> classIndices.add(input.readUnsignedShort());
                case 8, 16, 19, 20 -> input.skipNBytes(2);
                case 9, 10, 11, 12, 17, 18 -> input.skipNBytes(4);
                case 15 -> input.skipNBytes(3);
                default -> fail("Unknown constant pool tag");
            }
            for (var s : strings) for (var banned : List.of("org/dcm4che", "java/nio/file/", "java/io/", "java/awt/", "javax/swing/", "com/formdev/",
                    "/infrastructure/", "/presentation/", "DicomInstanceReader", "DicomInstance;", "DicomInstance)", "DicomProcessing",
                    "DicomSeriesRequest", "DefaultDicomSeriesService", "DicomSeriesService", "Dcm4cheInstanceReader", "ImageVolume", "VoxelData",
                    "PixelEncoding", "PixelValueType", "SliceGeometry", "VolumeGeometry", "DicomToNifti", "NiftiToDicom", "ConversionService",
                    "java/security/", "ContentDigest", "SelectedSourceFingerprint", "ManifestStore", "ManifestWriter", "PublicReportWriter",
                    "JsonManifest", "JsonPublicReport", "java/util/logging/", "org/slf4j/", "java/lang/System")) {
                assertFalse(s.contains(banned), type.getName() + " -> " + banned);
            }
            for (int index : classIndices) {
                String name = utf8[index]; assertNotNull(name);
                while (name.startsWith("[")) name = name.substring(1);
                if (name.length() == 1 && "ZBCSIJFD".contains(name)) continue;
                if (name.startsWith("L") && name.endsWith(";")) name = name.substring(1, name.length() - 1);
                String binaryName = name.replace('/', '.'); assertTrue(permitted(binaryName), binaryName);
                // Include compiler-generated enum-switch helpers, which reflection's
                // getDeclaredClasses() does not necessarily enumerate.
                if (TYPES.stream().anyMatch(t -> binaryName.startsWith(t.getName() + "$"))) {
                    inspect(Class.forName(binaryName, false, type.getClassLoader()), inspected);
                }
            }
        }
        for (var nested : type.getDeclaredClasses()) inspect(nested, inspected);
    }
    private static void allowed(Type type) {
        if (type instanceof ParameterizedType p) { allowed(p.getRawType()); for (var a : p.getActualTypeArguments()) allowed(a); return; }
        if (type instanceof GenericArrayType a) { allowed(a.getGenericComponentType()); return; }
        if (type instanceof WildcardType w) { for (var b : w.getUpperBounds()) allowed(b); for (var b : w.getLowerBounds()) allowed(b); return; }
        if (type instanceof TypeVariable<?>) return;
        assertInstanceOf(Class.class, type); var c = (Class<?>)type;
        if (c.isArray()) { allowed(c.getComponentType()); return; }
        assertTrue(c.isPrimitive() || permitted(c.getName()), c.getName());
    }
    private static boolean permitted(String name) {
        return name.startsWith("java.lang.") || name.startsWith("java.util.") || name.startsWith("java.nio.charset.")
                || TYPES.stream().anyMatch(t -> name.equals(t.getName()) || name.startsWith(t.getName() + "$"))
                || name.equals(DicomDiscoveryMetadata.class.getName()) || name.equals(DicomMetadataInspection.class.getName())
                || name.equals(DicomMetadataCatalog.class.getName()) || name.equals(RelativePath.class.getName())
                || name.equals(RelativePath.Root.class.getName());
    }
}
