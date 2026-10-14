package org.cbihi.mrinormalizer.architecture;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.cbihi.mrinormalizer.application.dataset.copy.*;
import org.cbihi.mrinormalizer.application.port.out.VerifiedCopyAccess;
import org.cbihi.mrinormalizer.application.service.*;
import org.cbihi.mrinormalizer.infrastructure.filesystem.NioVerifiedCopyAccess;
import org.junit.jupiter.api.Test;

class VerifiedCopyBoundaryTest {
    // T59: exact public record components and nested API, no alternate public production declaration.
    @Test void frozenPublicApiAndPrivateCapabilityStayExact() {
        var components = Map.of(CopyLimits.class, List.of("maxFileBytes", "maxReadBytes", "bufferBytes", "maxFailures"),
                CopyFailure.class, List.of("stage", "code", "source", "operationId"),
                SourceDigestBatch.class, List.of("status", "inventory", "discovery", "digests", "failures"),
                SourceDigestBatch.DigestFact.class, List.of("source", "digest"),
                CopyExecutionResult.class, List.of("status", "journal", "operations", "failures"),
                CopyExecutionResult.OperationResult.class, List.of("operationId", "outcome", "outputDigest"));
        for (var entry : components.entrySet()) {
            assertTrue(entry.getKey().isRecord()); assertEquals(entry.getValue(), Arrays.stream(entry.getKey().getRecordComponents()).map(RecordComponent::getName).toList());
            assertEquals(1, entry.getKey().getConstructors().length);
        }
        assertEquals(Set.of("acquire", "revalidate", "prepare", "inspect", "close"), publicNames(VerifiedCopyAccess.class));
        assertEquals(Set.of("verifiedSourceDigest", "publish", "close"), publicNames(VerifiedCopyAccess.PreparedCopy.class));
        assertEquals(Set.of("execute", "inspectRecovery", "close"), publicNames(CopyExecutionService.class));
        assertEquals(Set.of("acquire"), publicNames(SourceDigestService.class));
        assertEquals(1, NioVerifiedCopyAccess.class.getConstructors().length);
        assertEquals(0, CopyExecutionService.AcknowledgedCopyIntent.class.getConstructors().length);
        assertEquals(Set.of("claim", "checkLive"), publicNames(CopyExecutionService.AcknowledgedCopyIntent.class));
    }
    private static Set<String> publicNames(Class<?> type) {
        return new HashSet<>(Arrays.stream(type.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers())).map(Method::getName).toList());
    }
    // T60: inspect actual compiled constant-pool class references recursively, not source substrings.
    @Test void applicationCodeDoesNotReadHashOrParseImagingBytes() throws Exception {
        for (var type : List.of(CopyLimits.class, CopyFailure.class, SourceDigestBatch.class, CopyExecutionResult.class,
                SourceDigestService.class, CopyExecutionService.class, VerifiedCopyAccess.class)) inspect(type);
    }
    private static void inspect(Class<?> type) throws Exception {
        try (var in = new DataInputStream(type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class"))) {
            assertEquals(0xcafebabe, in.readInt()); in.readUnsignedShort(); in.readUnsignedShort(); int count = in.readUnsignedShort();
            var strings = new String[count]; var classes = new ArrayList<Integer>();
            for (int i = 1; i < count; i++) switch (in.readUnsignedByte()) {
                case 1 -> strings[i] = in.readUTF();
                case 7 -> classes.add(in.readUnsignedShort());
                case 8, 16, 19, 20 -> in.readUnsignedShort();
                case 3, 4, 9, 10, 11, 12, 17, 18 -> in.readInt();
                case 5, 6 -> { in.readLong(); i++; }
                case 15 -> { in.readUnsignedByte(); in.readUnsignedShort(); }
                default -> fail("Unrecognized class-file constant");
            }
            for (int index : classes) {
                String name = strings[index];
                assertFalse(name.startsWith("java/nio/file/") || name.startsWith("java/nio/channels/")
                        || name.startsWith("java/io/File") || name.equals("java/security/MessageDigest")
                        || name.startsWith("org/dcm4che3/") || name.contains("DefaultDicomToNifti")
                        || name.contains("NiftiVolumeWriter") || name.startsWith("javax/swing/"), type.getName() + " -> " + name);
            }
        }
        for (var nested : type.getDeclaredClasses()) inspect(nested);
    }
    // T61/T62: required tests have no disabled/skipping annotation or assumption shortcut.
    @Test void candidateTestsHaveNoSkipOrDisableShortcut() throws Exception {
        for (String name : List.of("VerifiedCopyModelTest", "CopyJournalSequenceTest", "SourceDigestAcquisitionTest",
                "VerifiedCopyExecutionTest", "VerifiedCopyProviderTest", "VerifiedCopyRecoveryTest", "VerifiedCopyBoundaryTest")) {
            try (var files = Files.walk(Path.of("src/test/java"))) {
                var path = files.filter(p -> p.getFileName().toString().equals(name + ".java")).findFirst().orElseThrow();
                var text = Files.readString(path);
                assertFalse(text.contains("@" + "Disabled")); assertFalse(text.contains("Assum" + "ptions."));
            }
        }
    }

    @Test void closedFailureExceptionCannotExposePatientUidRootOrProviderSentinels() throws Exception {
        var base = Files.createTempDirectory("f6-privacy-");
        try {
            var f = new org.cbihi.mrinormalizer.infrastructure.filesystem.SourceDigestAcquisitionTest.Fixture(base);
            try (var access = f.access()) {
                var b = access.acquire(f.inventory, f.discovery); var p = f.plan(b); assertTrue(access.revalidate(p, b).isEmpty());
                try (var handle = access.prepare(p.copies().getFirst())) {
                    var error = assertThrows(IllegalArgumentException.class, () -> handle.publish(null));
                    for (String secret : List.of("patient-sentinel", "1.2.840.999", f.source.toString(), p.copies().getFirst().expectedDigest().sha256())) assertFalse(error.toString().contains(secret));
                }
            }
            var failure = new CopyFailure(CopyFailure.Stage.COPY, CopyFailure.Code.READ_FAILED, Optional.empty(), Optional.empty());
            var controlled = new VerifiedCopyAccess.AccessFailure(List.of(failure)); controlled.addSuppressed(new IOException("patient-sentinel 1.2.840.999 " + f.source));
            assertNull(controlled.getCause()); assertEquals(0, controlled.getSuppressed().length); assertEquals(0, controlled.getStackTrace().length);
        } finally { try (var paths = Files.walk(base)) { for (var p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); } }
    }
    @Test void genericRecordAndMethodSignaturesMatchFrozenValueInventory() throws Exception {
        assertEquals("java.util.Optional<org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath>", CopyFailure.class.getRecordComponents()[2].getGenericType().getTypeName());
        assertEquals("java.util.Optional<java.lang.String>", CopyFailure.class.getRecordComponents()[3].getGenericType().getTypeName());
        assertEquals("java.util.Map<org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath, org.cbihi.mrinormalizer.application.provenance.manifest.ContentDigest>", SourceDigestBatch.class.getMethod("suppliedDigests").getGenericReturnType().getTypeName());
        assertEquals(List.of("NOTHING_TO_COPY", "JOURNAL_COMPLETED", "FAILED", "RECOVERY_REQUIRED"), Arrays.stream(CopyExecutionResult.Status.values()).map(Enum::name).toList());
        assertEquals(List.of("NOT_STARTED", "COMPLETED", "IDENTICAL_EXISTING", "FAILED", "RECOVERY_REQUIRED"), Arrays.stream(CopyExecutionResult.Outcome.values()).map(Enum::name).toList());
        assertEquals(List.of("NOT_PUBLISHED", "VERIFIED", "UNVERIFIED", "UNKNOWN"), Arrays.stream(VerifiedCopyAccess.Publication.values()).map(Enum::name).toList());
        assertEquals(List.of("INPUT_UNAVAILABLE", "SOURCE_CHANGED", "DISCOVERY_BINDING_UNPROVEN", "CONTAINMENT_UNPROVEN", "ACCESS_CONTROL_UNAVAILABLE", "PROVIDER_UNQUALIFIED", "RESOURCE_LIMIT", "HASH_FAILED", "READ_FAILED", "WRITE_FAILED", "OUTPUT_CONFLICT", "VERIFICATION_FAILED", "PUBLICATION_UNPROVEN", "CLEANUP_FAILED", "INTERRUPTED", "JOURNAL_BLOCKED", "RECOVERY_REQUIRED"), Arrays.stream(CopyFailure.Code.values()).map(Enum::name).toList());
        assertEquals(Set.of("acquire", "revalidate", "prepare", "inspect", "close"), publicNames(NioVerifiedCopyAccess.class));
    }

}
