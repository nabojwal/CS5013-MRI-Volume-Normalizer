package org.cbihi.mrinormalizer.architecture;

import static org.junit.jupiter.api.Assertions.*;

import java.io.DataInputStream;
import java.lang.reflect.*;
import java.util.*;

import org.cbihi.mrinormalizer.application.dataset.dicom.*;
import org.cbihi.mrinormalizer.application.port.out.DicomMetadataReader;
import org.cbihi.mrinormalizer.application.service.DicomMetadataInspectionService;
import org.cbihi.mrinormalizer.infrastructure.dicom.Dcm4cheMetadataReader;
import org.junit.jupiter.api.Test;

class DicomMetadataBoundaryTest {
    // T30
    @Test void compiledF3DependenciesRespectOwnership() throws Exception {
        var contracts = List.of(DicomMetadataLimits.class, DicomDiscoveryMetadata.class, DicomMetadataInspection.class,
                DicomMetadataCatalog.class, DicomMetadataReader.class, DicomMetadataInspectionService.class);
        for (Class<?> c : contracts) {
            for (var component : c.isRecord() ? c.getRecordComponents() : new RecordComponent[0]) allowed(component.getGenericType());
            for (var field : c.getDeclaredFields()) allowed(field.getGenericType());
            for (var method : c.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers())) {
                    allowed(method.getGenericReturnType()); for (var t : method.getGenericParameterTypes()) allowed(t);
                }
            }
            for (var ctor : c.getConstructors()) for (var t : ctor.getGenericParameterTypes()) allowed(t);
            checkCompiled(c);
        }
        checkCompiled(Dcm4cheMetadataReader.class);
        for (var helper : Dcm4cheMetadataReader.class.getDeclaredClasses()) checkCompiled(helper);
        assertEquals(1, Dcm4cheMetadataReader.class.getConstructors().length);
        assertEquals(1, DicomMetadataReader.class.getDeclaredMethods().length);
    }

    private static void allowed(Type t) {
        if (t instanceof ParameterizedType p) { allowed(p.getRawType()); for (var a : p.getActualTypeArguments()) allowed(a); return; }
        if (!(t instanceof Class<?>)) fail("Unexpected generic boundary");
        Class<?> c = (Class<?>)t;
        assertFalse(c.isArray(), c.getName());
        String n = c.getName();
        assertTrue(c.isPrimitive() || n.startsWith("java.lang.") || n.equals("java.util.List") || n.equals("java.util.Optional")
                || n.startsWith("org.cbihi.mrinormalizer.application.dataset.dicom.")
                || n.equals("org.cbihi.mrinormalizer.application.dataset.model.DatasetInventory")
                || n.equals("org.cbihi.mrinormalizer.application.port.out.DicomMetadataReader")
                || n.startsWith("org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath"), n);
    }
    private static void checkCompiled(Class<?> c) throws Exception {
        try (var input = c.getResourceAsStream("/" + c.getName().replace('.', '/') + ".class")) {
            assertNotNull(input); var in = new DataInputStream(input); assertEquals(0xcafebabe, in.readInt());
            in.readUnsignedShort(); in.readUnsignedShort(); int count = in.readUnsignedShort();
            var strings = new ArrayList<String>();
            for (int i = 1; i < count; i++) switch (in.readUnsignedByte()) {
                case 1 -> strings.add(in.readUTF());
                case 3, 4 -> in.skipNBytes(4);
                case 5, 6 -> { in.skipNBytes(8); i++; }
                case 7, 8, 16, 19, 20 -> in.skipNBytes(2);
                case 9, 10, 11, 12, 17, 18 -> in.skipNBytes(4);
                case 15 -> in.skipNBytes(3);
                default -> fail("Unknown constant pool tag");
            }
            for (String s : strings) for (String banned : List.of("DicomInstanceReader", "Dcm4cheInstanceReader", "DicomInstance;",
                    "DicomSeriesService", "ImageVolume", "VoxelData", "java/awt", "javax/swing", "/presentation/",
                    "readDataset", "readDatasetUntilPixelData", "skipSequence", "skipItem", "getFragments", "IncludeBulkData.URI")) {
                assertFalse(s.contains(banned), c.getName() + " -> " + banned);
            }
        }
    }
}
