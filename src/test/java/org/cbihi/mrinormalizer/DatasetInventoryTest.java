package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.cbihi.mrinormalizer.application.dataset.model.*;
import org.cbihi.mrinormalizer.application.port.out.DatasetScanner;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;
import org.cbihi.mrinormalizer.domain.model.*;
import org.junit.jupiter.api.Test;

class DatasetInventoryTest {
    @Test void limitsRequireExplicitPositiveFiniteBudgets() {
        var supplied = new InventoryLimits(3, 2, 1);
        assertEquals(3, supplied.maxEntries());
        assertEquals(2, supplied.maxDepth());
        assertEquals(1, supplied.maxFailures());
        var largest = new InventoryLimits(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, largest.maxEntries());
        assertEquals(Integer.MAX_VALUE, largest.maxDepth());
        assertEquals(Integer.MAX_VALUE, largest.maxFailures());
        assertEquals(List.of(int.class, int.class, int.class), java.util.Arrays.stream(
                InventoryLimits.class.getRecordComponents()).map(c -> c.getType()).toList());
        assertEquals(1, InventoryLimits.class.getConstructors().length);
        assertEquals(3, InventoryLimits.class.getConstructors()[0].getParameterCount());
        for (int bad : new int[] {0, -1}) {
            assertThrows(IllegalArgumentException.class, () -> new InventoryLimits(bad, 1, 1));
            assertThrows(IllegalArgumentException.class, () -> new InventoryLimits(1, bad, 1));
            assertThrows(IllegalArgumentException.class, () -> new InventoryLimits(1, 1, bad));
        }
    }

    @Test void diagnosticLocationsPreserveExactSpellingWithoutBecomingOperationalReferences() {
        for (String spelling : List.of("a", "dir/file", "name.", "name ", "AUX", "patient:scan", "abc:def",
                "a\\b", "a:b", "C:patient", "Patient Name/1.2.3/é.dcm", "CON", "folder/name.",
                "folder/name ", "folder/name:part", "folder/literal\\name", "e\u0301", "é", "\uD800\uDC00")) {
            assertEquals(spelling, new InventoryLocation(spelling).spelling());
        }
        assertNotEquals(new InventoryLocation("é"), new InventoryLocation("e\u0301"));
        for (String nonportable : List.of("name.", "name ", "AUX", "patient:scan", "abc:def", "a\\b",
                "CON", "folder/name.", "folder/name ", "folder/name:part", "folder/literal\\name")) {
            assertEquals(nonportable, new InventoryLocation(nonportable).spelling());
            assertThrows(IllegalArgumentException.class, () -> new RelativePath(RelativePath.Root.SOURCE, nonportable));
        }
        assertFalse(RelativePath.class.isAssignableFrom(InventoryLocation.class));
    }

    @Test void diagnosticLocationsRejectAbsoluteTraversalAndEmptyPathPayloads() {
        assertThrows(IllegalArgumentException.class, () -> new InventoryLocation(null));
        for (String payload : List.of("", "/patient/image", "//server/share/image", "\\patient\\image",
                "\\\\server\\share\\image", "C:/patient/image", "C:\\patient\\image",
                "file:///patient/image", "https://provider/error", ".", "..",
                "a/./b", "a/../b", "../a", "a/..", "a\\..\\b", "a\\.\\b", "a//b", "a/", "a\\")) {
            assertThrows(IllegalArgumentException.class, () -> new InventoryLocation(payload), payload);
        }
    }

    @Test void diagnosticLocationsRejectControlsMalformedUnicodeAndOversizedMessagePayloads() {
        for (int control = 0; control <= 0x9f; control++) {
            if (Character.isISOControl(control)) {
                String payload = "entry" + (char) control + "provider message";
                var failure = assertThrows(IllegalArgumentException.class, () -> new InventoryLocation(payload));
                assertNull(failure.getCause());
                assertFalse(failure.getMessage().contains("provider"));
            }
        }
        for (String payload : List.of("\uD800", "\uDC00", "a\uD800b", "x".repeat(4097),
                "java.nio.file.AccessDeniedException: /private/patient\n at provider.File.open",
                "IOException: provider failure\n at provider.File.open(File.java:42)")) {
            var failure = assertThrows(IllegalArgumentException.class, () -> new InventoryLocation(payload));
            assertNull(failure.getCause());
            assertEquals("Inventory location must be a bounded relative filesystem spelling", failure.getMessage());
        }
        assertEquals("x".repeat(4096), new InventoryLocation("x".repeat(4096)).spelling());
    }

    @Test void failureLocationBoundaryRequiresTheControlledValueRatherThanFreeText() throws Exception {
        var location = new InventoryLocation("folder/name.");
        var failure = new InventoryFailure(InventoryFailure.Code.UNREPRESENTABLE_REFERENCE, Optional.of(location));
        assertSame(location, failure.location().orElseThrow());
        assertTrue(new InventoryFailure(InventoryFailure.Code.INPUT_ROOT_INVALID, Optional.empty()).location().isEmpty());
        assertThrows(NoSuchMethodException.class, () -> InventoryFailure.class.getConstructor(InventoryFailure.Code.class, String.class));
        Type optionalLocation = InventoryFailure.class.getRecordComponents()[1].getGenericType();
        var generic = assertInstanceOf(ParameterizedType.class, optionalLocation);
        assertEquals(Optional.class, generic.getRawType());
        assertEquals(List.of(InventoryLocation.class), List.of(generic.getActualTypeArguments()));
        assertEquals(optionalLocation, InventoryFailure.class.getMethod("location").getGenericReturnType());
        assertThrows(NoSuchMethodException.class, () -> InventoryFailure.class.getMethod("relativePath"));
        assertEquals(optionalLocation, InventoryFailure.class.getConstructors()[0].getGenericParameterTypes()[1]);
    }

    @Test void controlledFailureLocationsHaveUnsignedUtf8OrderingAndFiniteCodeTies() {
        var faults = List.of(fault("\uD800\uDC00"), fault("\uE000"), fault("é"), fault("e\u0301"), fault("CON"),
                new InventoryFailure(InventoryFailure.Code.TRAVERSAL_FAILED, Optional.of(new InventoryLocation("CON"))),
                new InventoryFailure(InventoryFailure.Code.INPUT_ROOT_INVALID, Optional.empty()));
        var inventory = new DatasetInventory(List.of(), faults);
        assertEquals(List.of("", "CON", "CON", "e\u0301", "é", "\uE000", "\uD800\uDC00"),
                inventory.failures().stream().map(f -> f.location().map(InventoryLocation::spelling).orElse("")).toList());
        assertEquals(InventoryFailure.Code.SYMLINK_DISALLOWED, inventory.failures().get(1).code());
        assertEquals(InventoryFailure.Code.TRAVERSAL_FAILED, inventory.failures().get(2).code());
    }

    @Test void entriesRetainExactSourceSpellingAndSuppliedAssessment() {
        var assessment = assessment(DetectionOutcome.DICOM);
        var source = new RelativePath(RelativePath.Root.SOURCE, "Patient Name/1.2.3/é.dcm");
        var entry = new InventoryEntry(source, assessment);
        assertSame(source, entry.source()); assertSame(assessment, entry.assessment());
        assertThrows(IllegalArgumentException.class, () -> new InventoryEntry(null, assessment));
        assertThrows(IllegalArgumentException.class, () -> new InventoryEntry(source, null));
        assertThrows(IllegalArgumentException.class, () -> new InventoryEntry(
                new RelativePath(RelativePath.Root.OUTPUT, "a"), assessment));
    }

    @Test void inventoriesDefensivelyCopyAndSortUnsignedUtf8WithoutNormalizing() {
        var input = new ArrayList<>(List.of(entry("\uD800\uDC00"), entry("\uE000"), entry("é"), entry("e\u0301"), entry("A"), entry("a")));
        var faults = new ArrayList<>(List.of(fault("z"), fault("A")));
        var inventory = new DatasetInventory(input, faults);
        input.clear(); faults.clear();
        assertEquals(List.of("A", "a", "e\u0301", "é", "\uE000", "\uD800\uDC00"),
                inventory.entries().stream().map(e -> e.source().path()).toList());
        assertEquals(List.of("A", "z"), inventory.failures().stream().map(f -> f.location().orElseThrow().spelling()).toList());
        assertThrows(UnsupportedOperationException.class, () -> inventory.entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> inventory.failures().clear());
        assertFalse(inventory.complete());
    }

    @Test void duplicateOrNullFactsCannotPretendToBeAnInventory() {
        assertThrows(IllegalArgumentException.class, () -> new DatasetInventory(List.of(entry("a"), entry("a")), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new DatasetInventory(null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new DatasetInventory(List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new InventoryFailure(null, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new InventoryFailure(InventoryFailure.Code.TRAVERSAL_FAILED, null));
        assertThrows(IllegalArgumentException.class, () -> new DatasetInventory(List.of(), List.of(fault("a"), fault("a"))));
    }

    @Test void completenessMeansDiscoveryEvidenceNotConversionReadiness() {
        assertTrue(new DatasetInventory(List.of(), List.of()).complete());
        for (var result : List.of(DetectionResult.identified(DetectionOutcome.DICOM),
                DetectionResult.corrupt(DetectionDiagnostic.INVALID_NIFTI),
                DetectionResult.unknown(DetectionDiagnostic.UNSUPPORTED_FORMAT))) {
            var entry = new InventoryEntry(new RelativePath(RelativePath.Root.SOURCE, "a"), FormatAssessment.fromDetection(result));
            assertTrue(new DatasetInventory(List.of(entry), List.of()).complete());
            assertNotEquals(ConversionReadiness.READY, entry.assessment().readiness());
        }
        for (var diagnostic : List.of(DetectionDiagnostic.IO_ERROR, DetectionDiagnostic.INPUT_TOO_LARGE,
                DetectionDiagnostic.INPUT_NOT_READABLE, DetectionDiagnostic.EMPTY_INPUT)) {
            var value = FormatAssessment.fromDetection(DetectionResult.unknown(diagnostic));
            assertFalse(new DatasetInventory(List.of(new InventoryEntry(new RelativePath(RelativePath.Root.SOURCE, "a"), value)), List.of()).complete());
            assertEquals(diagnostic, value.initialDetection().diagnostic());
        }
    }

    @Test void applicationInventoryContractsExposeOnlyControlledValuesAndNoIoCapabilities() {
        var values = Set.of(InventoryLimits.class, InventoryLocation.class, InventoryEntry.class, InventoryFailure.class,
                InventoryFailure.Code.class, DatasetInventory.class, DatasetScanner.class);
        for (var type : values) {
            for (var field : type.getDeclaredFields()) assertSafe(field.getGenericType(), values);
            for (var method : type.getDeclaredMethods()) if (java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                assertSafe(method.getGenericReturnType(), values);
                for (var parameter : method.getGenericParameterTypes()) {
                    boolean languageEquals = type.isRecord() && method.getName().equals("equals")
                            && method.getParameterCount() == 1 && parameter == Object.class;
                    if (!languageEquals) assertSafe(parameter, values);
                }
            }
            for (var constructor : type.getConstructors()) for (var parameter : constructor.getGenericParameterTypes()) assertSafe(parameter, values);
        }
        for (var type : List.of(InventoryLimits.class, InventoryLocation.class, InventoryEntry.class, InventoryFailure.class, DatasetInventory.class)) {
            assertTrue(type.isRecord()); assertEquals(1, type.getConstructors().length);
            var expectedMethods = new java.util.HashSet<>(List.of("equals", "hashCode", "toString"));
            for (var component : type.getRecordComponents()) expectedMethods.add(component.getName());
            if (type == DatasetInventory.class) expectedMethods.add("complete");
            assertEquals(expectedMethods, java.util.Arrays.stream(type.getDeclaredMethods())
                    .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers())).map(m -> m.getName())
                    .collect(java.util.stream.Collectors.toSet()));
        }
        assertEquals(List.of("maxEntries", "maxDepth", "maxFailures"), java.util.Arrays.stream(InventoryLimits.class.getRecordComponents()).map(c -> c.getName()).toList());
        assertEquals(List.of("spelling"), java.util.Arrays.stream(InventoryLocation.class.getRecordComponents()).map(c -> c.getName()).toList());
        assertEquals(String.class, InventoryLocation.class.getRecordComponents()[0].getType());
        assertEquals(List.of("entries", "failures"), java.util.Arrays.stream(DatasetInventory.class.getRecordComponents()).map(c -> c.getName()).toList());
        assertEquals(List.of("scan"), java.util.Arrays.stream(DatasetScanner.class.getDeclaredMethods()).map(m -> m.getName()).toList());
        assertEquals(DatasetInventory.class, DatasetScanner.class.getDeclaredMethods()[0].getReturnType());
        assertEquals(List.of("source", "assessment"), java.util.Arrays.stream(InventoryEntry.class.getRecordComponents()).map(c -> c.getName()).toList());
        assertEquals(List.of("code", "location"), java.util.Arrays.stream(InventoryFailure.class.getRecordComponents()).map(c -> c.getName()).toList());
    }

    private static void assertSafe(Type type, Set<Class<?>> values) {
        if (type instanceof ParameterizedType generic) {
            assertSafe(generic.getRawType(), values);
            for (var argument : generic.getActualTypeArguments()) assertSafe(argument, values);
        } else {
            assertInstanceOf(Class.class, type); var raw = (Class<?>) type;
            if (raw.isArray()) { assertEquals(InventoryFailure.Code.class, raw.getComponentType()); return; }
            assertTrue(raw.isPrimitive() || values.contains(raw) || Set.of(String.class, List.class, Optional.class,
                    RelativePath.class, FormatAssessment.class).contains(raw), raw.getName());
        }
    }
    private static FormatAssessment assessment(DetectionOutcome outcome) { return FormatAssessment.fromDetection(DetectionResult.identified(outcome)); }
    private static InventoryEntry entry(String name) { return new InventoryEntry(new RelativePath(RelativePath.Root.SOURCE, name), assessment(DetectionOutcome.NIFTI)); }
    private static InventoryFailure fault(String name) { return new InventoryFailure(InventoryFailure.Code.SYMLINK_DISALLOWED, Optional.of(new InventoryLocation(name))); }
}
