package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.cbihi.mrinormalizer.application.dataset.dicom.*;
import org.cbihi.mrinormalizer.application.dataset.dicom.DicomSeriesScreeningFinding.Code;
import org.cbihi.mrinormalizer.application.dataset.dicom.DicomSeriesScreeningFinding.Reason;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;
import org.cbihi.mrinormalizer.application.service.DicomSeriesDiscoveryService;
import org.junit.jupiter.api.Test;

public class DicomSeriesDiscoveryModelTest {
    // T01
    @Test void publicContractsMatchFrozenApi() throws Exception {
        components(DicomSeriesKey.class, "studyInstanceUid", "seriesInstanceUid");
        components(DicomSeriesMember.class, "source", "metadata");
        components(DicomSeriesScreeningFinding.class, "code", "reason");
        components(DicomSeriesCandidate.class, "key", "members", "findings");
        components(DicomSeriesDiscovery.class, "candidates", "unassigned");
        assertEquals(List.of(String.class, String.class), Arrays.stream(DicomSeriesKey.class.getRecordComponents()).map(c -> c.getType()).toList());
        assertEquals(List.of(RelativePath.class, DicomDiscoveryMetadata.class), Arrays.stream(DicomSeriesMember.class.getRecordComponents()).map(c -> c.getType()).toList());
        assertEquals(List.of(Code.class, Reason.class), Arrays.stream(DicomSeriesScreeningFinding.class.getRecordComponents()).map(c -> c.getType()).toList());
        assertEquals(List.of(DicomSeriesKey.class.getName(), "java.util.List<" + DicomSeriesMember.class.getName() + ">",
                "java.util.List<" + DicomSeriesScreeningFinding.class.getName() + ">"), genericComponents(DicomSeriesCandidate.class));
        assertEquals(List.of("java.util.List<" + DicomSeriesCandidate.class.getName() + ">",
                "java.util.List<" + DicomMetadataInspection.class.getName() + ">"), genericComponents(DicomSeriesDiscovery.class));
        assertEquals(List.of("AMBIGUOUS_SERIES", "MULTIDIMENSIONAL_SERIES"), Arrays.stream(Code.values()).map(Enum::name).toList());
        assertEquals(List.of("DUPLICATE_SOP_INSTANCE", "SOP_INSTANCE_REUSED_ACROSS_SERIES", "MIXED_MODALITY", "MIXED_SOP_CLASS",
                "MIXED_FRAME_OF_REFERENCE", "MIXED_MATRIX", "MULTIFRAME_MEMBER", "MIXED_IMAGE_TYPE",
                "MULTIPLE_ACQUISITION_NUMBERS", "MULTIPLE_TEMPORAL_POSITIONS", "MULTIPLE_ECHO_NUMBERS"),
                Arrays.stream(Reason.values()).map(Enum::name).toList());
        assertTrue(Modifier.isFinal(DicomSeriesDiscoveryService.class.getModifiers()));
        assertEquals(1, DicomSeriesDiscoveryService.class.getConstructors().length);
        assertEquals(0, DicomSeriesDiscoveryService.class.getConstructors()[0].getParameterCount());
        assertEquals(DicomSeriesDiscovery.class, DicomSeriesDiscoveryService.class.getMethod("discover", DicomMetadataCatalog.class).getReturnType());
        assertEquals(List.of("discover"), Arrays.stream(DicomSeriesDiscoveryService.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers())).map(m -> m.getName()).toList());
    }

    // T02
    @Test void constructorsRejectInvalidStatesAndCopyCollections() {
        for (String uid : Arrays.asList(null, "", "1..2", "1.02", "1.2.", ".1", " 1.2", "1.2 ", "1.2\u0000", "patient-sentinel", "é", "1".repeat(65))) {
            invalid(() -> new DicomSeriesKey(uid, "1.2"));
            invalid(() -> new DicomSeriesKey("1.2", uid));
        }
        assertEquals("1".repeat(64), new DicomSeriesKey("1".repeat(64), "0.1").studyInstanceUid());
        var metadata = facts("1.2", "1.3", "1.4").build();
        var member = new DicomSeriesMember(source("a"), metadata);
        invalid(() -> new DicomSeriesMember(null, metadata));
        invalid(() -> new DicomSeriesMember(source("a"), null));
        invalid(() -> new DicomSeriesMember(new RelativePath(RelativePath.Root.OUTPUT, "a"), metadata));
        invalid(() -> new DicomSeriesScreeningFinding(null, Reason.MIXED_MATRIX));
        invalid(() -> new DicomSeriesScreeningFinding(Code.AMBIGUOUS_SERIES, null));
        for (var reason : Reason.values()) {
            Code expected = reason.ordinal() < 8 ? Code.AMBIGUOUS_SERIES : Code.MULTIDIMENSIONAL_SERIES;
            assertEquals(expected, new DicomSeriesScreeningFinding(expected, reason).code());
            invalid(() -> new DicomSeriesScreeningFinding(expected == Code.AMBIGUOUS_SERIES ? Code.MULTIDIMENSIONAL_SERIES : Code.AMBIGUOUS_SERIES, reason));
        }
        var key = new DicomSeriesKey("1.2", "1.3"); var finding = finding(Reason.MIXED_MATRIX);
        invalid(() -> new DicomSeriesCandidate(null, List.of(member), List.of()));
        invalid(() -> new DicomSeriesCandidate(key, null, List.of()));
        invalid(() -> new DicomSeriesCandidate(key, List.of(member), null));
        invalid(() -> new DicomSeriesCandidate(key, List.of(), List.of()));
        invalid(() -> new DicomSeriesCandidate(key, Arrays.asList((DicomSeriesMember)null), List.of()));
        invalid(() -> new DicomSeriesCandidate(key, List.of(member), Arrays.asList((DicomSeriesScreeningFinding)null)));
        invalid(() -> new DicomSeriesCandidate(key, List.of(member, member), List.of()));
        invalid(() -> new DicomSeriesCandidate(new DicomSeriesKey("1.9", "1.3"), List.of(member), List.of()));
        invalid(() -> new DicomSeriesCandidate(new DicomSeriesKey("1.2", "1.9"), List.of(member), List.of()));
        invalid(() -> new DicomSeriesCandidate(key, List.of(member), List.of(finding, finding)));
        var members = new ArrayList<>(List.of(member)); var findings = new ArrayList<>(List.of(finding));
        var candidate = new DicomSeriesCandidate(key, members, findings); members.clear(); findings.clear();
        assertEquals(List.of(member), candidate.members()); assertEquals(List.of(finding), candidate.findings());
        assertThrows(UnsupportedOperationException.class, () -> candidate.members().clear());
        assertThrows(UnsupportedOperationException.class, () -> candidate.findings().clear());
        var failed = failed("b", DicomMetadataInspection.FailureCode.METADATA_READ_FAILED);
        invalid(() -> new DicomSeriesDiscovery(null, List.of()));
        invalid(() -> new DicomSeriesDiscovery(List.of(), null));
        invalid(() -> new DicomSeriesDiscovery(Arrays.asList((DicomSeriesCandidate)null), List.of()));
        invalid(() -> new DicomSeriesDiscovery(List.of(), Arrays.asList((DicomMetadataInspection)null)));
        invalid(() -> new DicomSeriesDiscovery(List.of(candidate, candidate), List.of()));
        var otherFacts = facts("1.9", "1.3", "1.8").build();
        var other = new DicomSeriesCandidate(new DicomSeriesKey("1.9", "1.3"), List.of(new DicomSeriesMember(source("a"), otherFacts)), List.of());
        invalid(() -> new DicomSeriesDiscovery(List.of(candidate, other), List.of()));
        invalid(() -> new DicomSeriesDiscovery(List.of(), List.of(success("a", metadata))));
        invalid(() -> new DicomSeriesDiscovery(List.of(), List.of(failed, failed)));
        invalid(() -> new DicomSeriesDiscovery(List.of(candidate), List.of(failed("a", DicomMetadataInspection.FailureCode.INCOMPLETE_METADATA))));
        var candidates = new ArrayList<>(List.of(candidate)); var failures = new ArrayList<>(List.of(failed));
        var discovery = new DicomSeriesDiscovery(candidates, failures); candidates.clear(); failures.clear();
        assertEquals(List.of(candidate), discovery.candidates()); assertSame(failed, discovery.unassigned().getFirst());
        assertSame(metadata, discovery.candidates().getFirst().members().getFirst().metadata());
        assertThrows(UnsupportedOperationException.class, () -> discovery.candidates().clear());
        assertThrows(UnsupportedOperationException.class, () -> discovery.unassigned().clear());
        invalid(() -> new DicomSeriesDiscoveryService().discover(null));
    }

    // T03
    @Test void collectionsUseCanonicalOrdering() {
        var metadata = facts("1.2.10", "1.3", "1.4").build();
        var laterSop = facts("1.2.10", "1.3", "1.5").build();
        var candidate = new DicomSeriesCandidate(new DicomSeriesKey("1.2.10", "1.3"), List.of(
                new DicomSeriesMember(source("a"), laterSop), new DicomSeriesMember(source("𐀀"), metadata),
                new DicomSeriesMember(source("\ue000"), metadata)), List.of(finding(Reason.MULTIPLE_ECHO_NUMBERS),
                finding(Reason.MIXED_IMAGE_TYPE), finding(Reason.DUPLICATE_SOP_INSTANCE)));
        assertEquals(List.of("\ue000", "𐀀", "a"), candidate.members().stream().map(m -> m.source().path()).toList());
        assertEquals(List.of(Reason.DUPLICATE_SOP_INSTANCE, Reason.MIXED_IMAGE_TYPE, Reason.MULTIPLE_ECHO_NUMBERS), reasons(candidate));
        var series10 = candidate("1.2.10", "1.3.10", "b");
        var series2 = candidate("1.2.10", "1.3.2", "c");
        var study2 = candidate("1.2.2", "1.3", "d");
        var discovery = new DicomSeriesDiscovery(List.of(study2, series2, series10, candidate), List.of(
                failed("failed-𐀀", DicomMetadataInspection.FailureCode.METADATA_READ_FAILED), failed("failed-\ue000", DicomMetadataInspection.FailureCode.INCOMPLETE_METADATA)));
        assertEquals(List.of(candidate.key(), series10.key(), series2.key(), study2.key()), discovery.candidates().stream().map(DicomSeriesCandidate::key).toList());
        assertEquals(List.of("failed-\ue000", "failed-𐀀"), discovery.unassigned().stream().map(i -> i.source().path()).toList());
    }

    private static void components(Class<?> type, String... names) {
        assertTrue(type.isRecord()); assertTrue(Modifier.isPublic(type.getModifiers()));
        assertEquals(List.of(names), Arrays.stream(type.getRecordComponents()).map(c -> c.getName()).toList());
    }
    private static List<String> genericComponents(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(c -> c.getGenericType().getTypeName()).toList();
    }
    private static void invalid(org.junit.jupiter.api.function.Executable action) {
        var error = assertThrows(IllegalArgumentException.class, action);
        assertNull(error.getCause()); assertNotNull(error.getMessage());
        assertFalse(error.getMessage().contains("patient-sentinel")); assertFalse(error.getMessage().contains("1.9"));
    }
    private static DicomSeriesCandidate candidate(String study, String series, String path) {
        return new DicomSeriesCandidate(new DicomSeriesKey(study, series), List.of(new DicomSeriesMember(source(path), facts(study, series, "1.4").build())), List.of());
    }
    public static RelativePath source(String path) { return new RelativePath(RelativePath.Root.SOURCE, path); }
    public static DicomMetadataInspection success(String path, DicomDiscoveryMetadata metadata) {
        return new DicomMetadataInspection(source(path), Optional.of(metadata), Optional.empty());
    }
    public static DicomMetadataInspection failed(String path, DicomMetadataInspection.FailureCode code) {
        return new DicomMetadataInspection(source(path), Optional.empty(), Optional.of(code));
    }
    public static DicomSeriesScreeningFinding finding(Reason reason) {
        return new DicomSeriesScreeningFinding(reason.ordinal() < 8 ? Code.AMBIGUOUS_SERIES : Code.MULTIDIMENSIONAL_SERIES, reason);
    }
    public static List<Reason> reasons(DicomSeriesCandidate candidate) { return candidate.findings().stream().map(DicomSeriesScreeningFinding::reason).toList(); }
    public static Facts facts(String study, String series, String sop) { return new Facts(study, series, sop); }
    public static final class Facts {
        public String study, series, sop, modality = "MR", sopClass = "1.2.840.10008.5.1.4.1.1.4", syntax = "1.2.840.10008.1.2.1", frame;
        public Integer rows, columns, frames, acquisition, temporal;
        public List<Integer> echoes = List.of(); public List<String> imageType = List.of();
        private Facts(String study, String series, String sop) { this.study = study; this.series = series; this.sop = sop; }
        public DicomDiscoveryMetadata build() {
            return new DicomDiscoveryMetadata(study, series, sop, modality, sopClass, syntax,
                    Optional.ofNullable(frame), Optional.ofNullable(rows), Optional.ofNullable(columns), Optional.ofNullable(frames),
                    Optional.ofNullable(acquisition), Optional.ofNullable(temporal), echoes, imageType);
        }
    }
}
