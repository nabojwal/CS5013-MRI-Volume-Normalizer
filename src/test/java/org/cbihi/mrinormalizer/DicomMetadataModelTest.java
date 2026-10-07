package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.cbihi.mrinormalizer.application.dataset.dicom.*;
import org.cbihi.mrinormalizer.application.dataset.dicom.DicomMetadataInspection.FailureCode;
import org.cbihi.mrinormalizer.application.port.out.DicomMetadataReader;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;
import org.junit.jupiter.api.Test;

public class DicomMetadataModelTest {
    // T01
    @Test void publicContractsMatchFrozenComponents() throws Exception {
        assertComponents(DicomMetadataLimits.class, "maxMetadataBytes", "maxNestingDepth");
        assertComponents(DicomDiscoveryMetadata.class, "studyInstanceUid", "seriesInstanceUid", "sopInstanceUid",
                "modality", "sopClassUid", "transferSyntaxUid", "frameOfReferenceUid", "rows", "columns",
                "numberOfFrames", "acquisitionNumber", "temporalPositionIdentifier", "echoNumbers", "imageType");
        assertComponents(DicomMetadataInspection.class, "source", "metadata", "failure");
        assertComponents(DicomMetadataCatalog.class, "inspections");
        assertEquals(List.of("RESOURCE_LIMIT_EXCEEDED", "METADATA_READ_FAILED", "INCOMPLETE_METADATA"),
                Arrays.stream(FailureCode.values()).map(Enum::name).toList());
        assertEquals(DicomMetadataInspection.class,
                DicomMetadataReader.class.getMethod("inspect", RelativePath.class).getReturnType());
        assertEquals(List.of(int.class, int.class), Arrays.stream(DicomMetadataLimits.class.getRecordComponents()).map(c -> c.getType()).toList());
        assertTrue(Arrays.stream(DicomDiscoveryMetadata.class.getRecordComponents()).limit(6).allMatch(c -> c.getType() == String.class));
        assertEquals(DicomMetadataCatalog.class, org.cbihi.mrinormalizer.application.service.DicomMetadataInspectionService.class
                .getMethod("inspect", org.cbihi.mrinormalizer.application.dataset.model.DatasetInventory.class).getReturnType());
        assertEquals(1, org.cbihi.mrinormalizer.application.service.DicomMetadataInspectionService.class.getConstructors().length);
        assertEquals(List.of("java.util.Optional<java.lang.String>", "java.util.Optional<java.lang.Integer>",
                "java.util.Optional<java.lang.Integer>", "java.util.Optional<java.lang.Integer>",
                "java.util.Optional<java.lang.Integer>", "java.util.Optional<java.lang.Integer>",
                "java.util.List<java.lang.Integer>", "java.util.List<java.lang.String>"),
                Arrays.stream(DicomDiscoveryMetadata.class.getRecordComponents()).skip(6)
                        .map(c -> c.getGenericType().getTypeName()).toList());
    }

    // T02
    @Test void constructorsRejectInvalidStatesWithoutPayloadLeaks() {
        assertEquals(Integer.MAX_VALUE, new DicomMetadataLimits(Integer.MAX_VALUE, Integer.MAX_VALUE).maxNestingDepth());
        for (int n : new int[] {0, -1}) {
            assertThrows(IllegalArgumentException.class, () -> new DicomMetadataLimits(n, 1));
            assertThrows(IllegalArgumentException.class, () -> new DicomMetadataLimits(1, n));
        }
        for (String uid : Arrays.asList(null, "", "patient-sentinel", "1..2", "1.02", "1.2\u0000", "1.2.", "1".repeat(65))) {
            var e = assertThrows(IllegalArgumentException.class, () -> metadata(uid, List.of(1), List.of("ORIGINAL", "PRIMARY")));
            assertNull(e.getCause()); assertFalse(e.getMessage().contains("patient"));
        }
        assertThrows(IllegalArgumentException.class, () -> metadata("1.2", Arrays.asList((Integer) null), List.of()));
        assertThrows(IllegalArgumentException.class, () -> metadata("1.2", List.of(), List.of("lowercase")));
        assertThrows(IllegalArgumentException.class, () -> new DicomMetadataInspection(source("a"), Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new DicomMetadataInspection(source("a"),
                Optional.of(metadata()), Optional.of(FailureCode.METADATA_READ_FAILED)));
        assertThrows(IllegalArgumentException.class, () -> new DicomMetadataInspection(
                new RelativePath(RelativePath.Root.OUTPUT, "a"), Optional.empty(), Optional.of(FailureCode.METADATA_READ_FAILED)));
        assertThrows(IllegalArgumentException.class, () -> new DicomMetadataInspection(null, Optional.empty(), Optional.of(FailureCode.METADATA_READ_FAILED)));
        assertThrows(IllegalArgumentException.class, () -> new DicomMetadataInspection(source("a"), null, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new DicomMetadataCatalog(null));
        assertThrows(IllegalArgumentException.class, () -> new DicomMetadataCatalog(Arrays.asList((DicomMetadataInspection) null)));
    }

    // T03
    @Test void collectionsAreImmutableAndDefensivelyCopied() {
        var echoes = new ArrayList<>(List.of(2, 1, 2));
        var types = new ArrayList<>(List.of("ORIGINAL", "PRIMARY", "OTHER"));
        var value = metadata("1.2", echoes, types); echoes.clear(); types.clear();
        assertEquals(List.of(2, 1, 2), value.echoNumbers());
        assertEquals(List.of("ORIGINAL", "PRIMARY", "OTHER"), value.imageType());
        assertThrows(UnsupportedOperationException.class, () -> value.echoNumbers().clear());
        assertThrows(UnsupportedOperationException.class, () -> value.imageType().clear());
        var input = new ArrayList<>(List.of(success("a")));
        var catalog = new DicomMetadataCatalog(input); input.clear();
        assertEquals(1, catalog.inspections().size()); assertTrue(catalog.complete());
        assertThrows(UnsupportedOperationException.class, () -> catalog.inspections().clear());
    }

    // T04
    @Test void catalogRejectsDuplicatesAndUsesUnsignedUtf8Order() {
        var c = new DicomMetadataCatalog(List.of(success("\uD800\uDC00"), success("\uE000"), success("é"), success("e\u0301"), success("A")));
        assertEquals(List.of("A", "e\u0301", "é", "\uE000", "\uD800\uDC00"), c.inspections().stream().map(i -> i.source().path()).toList());
        assertThrows(IllegalArgumentException.class, () -> new DicomMetadataCatalog(List.of(success("a"), success("a"))));
        assertTrue(new DicomMetadataCatalog(List.of()).complete());
        assertFalse(new DicomMetadataCatalog(List.of(new DicomMetadataInspection(source("a"), Optional.empty(),
                Optional.of(FailureCode.INCOMPLETE_METADATA)))).complete());
    }

    public static RelativePath source(String path) { return new RelativePath(RelativePath.Root.SOURCE, path); }
    public static DicomDiscoveryMetadata metadata() { return metadata("1.2", List.of(1), List.of("ORIGINAL", "PRIMARY")); }
    public static DicomDiscoveryMetadata metadata(String study, List<Integer> echoes, List<String> types) {
        return new DicomDiscoveryMetadata(study, "1.3", "1.4", "MR", "1.2.840.10008.5.1.4.1.1.4",
                "1.2.840.10008.1.2.1", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), echoes, types);
    }
    public static DicomMetadataInspection success(String path) {
        return new DicomMetadataInspection(source(path), Optional.of(metadata()), Optional.empty());
    }
    private static void assertComponents(Class<?> type, String... names) {
        assertTrue(type.isRecord());
        assertEquals(List.of(names), Arrays.stream(type.getRecordComponents()).map(c -> c.getName()).toList());
        assertEquals(1, type.getConstructors().length);
    }
}
