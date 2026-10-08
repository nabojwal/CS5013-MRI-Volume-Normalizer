package org.cbihi.mrinormalizer;

import static org.cbihi.mrinormalizer.DicomSeriesDiscoveryModelTest.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.cbihi.mrinormalizer.application.dataset.dicom.*;
import org.cbihi.mrinormalizer.application.dataset.dicom.DicomSeriesScreeningFinding.Reason;
import org.cbihi.mrinormalizer.application.service.DicomSeriesDiscoveryService;
import org.junit.jupiter.api.Test;

class DicomSeriesDiscoveryServiceTest {
    // T04
    @Test void groupsOnlyByStudyAndSeries() {
        var a = facts("1.2", "1.3", "1.4").build(); var b = facts("1.2", "1.3", "1.5").build();
        var c = facts("1.2", "1.9", "1.6").build();
        var result = discover(success("z", a), success("b", b), success("c", c));
        assertEquals(2, result.candidates().size());
        assertEquals(List.of("z", "b"), result.candidates().getFirst().members().stream().map(m -> m.source().path()).toList());
        assertEquals(new DicomSeriesKey("1.2", "1.3"), result.candidates().getFirst().key());
        assertSame(a, result.candidates().getFirst().members().getFirst().metadata());
        assertEquals(3, count(result));
    }

    // T05
    @Test void sameSeriesUidInDifferentStudiesStaysSeparate() {
        var result = discover(success("a", facts("1.2", "1.3", "1.4").build()), success("b", facts("1.9", "1.3", "1.5").build()));
        assertEquals(2, result.candidates().size());
        assertEquals(List.of("1.2", "1.9"), result.candidates().stream().map(c -> c.key().studyInstanceUid()).toList());
        assertTrue(result.candidates().stream().allMatch(c -> c.findings().isEmpty()));
    }

    // T06
    @Test void sourceNamesDoNotControlMembership() {
        var a = facts("1.2", "1.3", "1.4").build(); var b = facts("1.2", "1.3", "1.5").build();
        var c = facts("1.9", "1.3", "1.6").build();
        var result = discover(success("study-other/repeated.dcm", a), success("unrelated/repeated.nii", b), success("study-other/no-extension", c));
        assertEquals(List.of(2, 1), result.candidates().stream().map(x -> x.members().size()).toList());
        assertEquals(List.of("1.4", "1.5"), result.candidates().getFirst().members().stream().map(m -> m.metadata().sopInstanceUid()).toList());
    }

    // T07
    @Test void incompleteCatalogPreservesAllFailureCodes() {
        var inspections = new ArrayList<DicomMetadataInspection>();
        for (var code : DicomMetadataInspection.FailureCode.values()) inspections.add(failed("failure-" + code.ordinal(), code));
        var failureOnly = new DicomMetadataCatalog(inspections);
        assertFalse(failureOnly.complete()); var result = new DicomSeriesDiscoveryService().discover(failureOnly);
        assertTrue(result.candidates().isEmpty()); assertEquals(failureOnly.inspections(), result.unassigned());
        for (int i = 0; i < inspections.size(); i++) assertSame(failureOnly.inspections().get(i), result.unassigned().get(i));
        inspections.add(success("success", facts("1.2", "1.3", "1.4").build()));
        result = new DicomSeriesDiscoveryService().discover(new DicomMetadataCatalog(inspections));
        assertEquals(1, result.candidates().size()); assertEquals(3, result.unassigned().size()); assertEquals(4, count(result));
    }

    // T08
    @Test void duplicateSopRetainsEveryMember() {
        var metadata = facts("1.2", "1.3", "1.4").build();
        var result = discover(success("c", metadata), success("a", metadata), success("b", metadata));
        var candidate = result.candidates().getFirst();
        assertEquals(List.of("a", "b", "c"), candidate.members().stream().map(m -> m.source().path()).toList());
        assertEquals(List.of(Reason.DUPLICATE_SOP_INSTANCE), reasons(candidate));
        assertTrue(candidate.members().stream().allMatch(m -> m.metadata() == metadata));
    }

    // T09
    @Test void crossCandidateSopReuseFlagsEveryAffectedKey() {
        var result = discover(success("a", facts("1.2", "1.3", "1.4").build()), success("b", facts("1.2", "1.3", "1.4").build()),
                success("c", facts("1.2", "1.9", "1.4").build()), success("d", facts("1.9", "1.3", "1.4").build()),
                success("e", facts("1.9", "1.9", "1.5").build()));
        assertEquals(4, result.candidates().size()); assertEquals(5, count(result));
        assertEquals(List.of(Reason.DUPLICATE_SOP_INSTANCE, Reason.SOP_INSTANCE_REUSED_ACROSS_SERIES), reasons(result.candidates().getFirst()));
        for (int i : new int[] {1, 2}) assertEquals(List.of(Reason.SOP_INSTANCE_REUSED_ACROSS_SERIES), reasons(result.candidates().get(i)));
        assertTrue(result.candidates().getLast().findings().isEmpty());
    }

    // T10
    @Test void mixedModalityAndSopClassAnnotateWithoutSplitting() {
        var a = base(1); var b = base(2); b.modality = "CT"; b.sopClass = "1.2.840.10008.5.1.4.1.1.2";
        var candidate = single(a, b);
        assertEquals(List.of(Reason.MIXED_MODALITY, Reason.MIXED_SOP_CLASS), reasons(candidate)); assertEquals(2, candidate.members().size());
    }

    // T11
    @Test void screensObservedFramesAndCompleteMatrixPairs() {
        var a = base(1); a.frame = "1.8"; a.rows = 256; a.columns = 256;
        var b = base(2); b.frame = "1.9"; b.rows = 512; b.columns = 512;
        assertEquals(List.of(Reason.MIXED_FRAME_OF_REFERENCE, Reason.MIXED_MATRIX), reasons(single(a, b, base(3))));
        b.frame = "1.8"; b.rows = 256; b.columns = 256;
        assertTrue(single(a, b, base(3)).findings().isEmpty());
        b.rows = 512; b.columns = null;
        var c = base(3); c.columns = 512;
        assertTrue(single(a, b, c).findings().isEmpty());
        a.columns = null;
        assertTrue(single(a, b, c).findings().isEmpty());
        a.rows = 0; a.columns = 0; b.rows = 0; b.columns = 0;
        assertTrue(single(a, b).findings().isEmpty()); // no support/validity verdict from equal zero facts
    }

    // T12
    @Test void multipleAcquisitionNumbersProduceFinding() {
        var a = base(1); a.acquisition = -2; var b = base(2); b.acquisition = 3;
        assertEquals(List.of(Reason.MULTIPLE_ACQUISITION_NUMBERS), reasons(single(a, b, base(3))));
        b.acquisition = -2; assertTrue(single(a, b, base(3)).findings().isEmpty());
    }

    // T13
    @Test void multipleTemporalPositionsProduceFinding() {
        var a = base(1); a.temporal = 0; var b = base(2); b.temporal = 2;
        assertEquals(List.of(Reason.MULTIPLE_TEMPORAL_POSITIONS), reasons(single(a, b, base(3))));
        b.temporal = 0; assertTrue(single(a, b, base(3)).findings().isEmpty());
    }

    // T14
    @Test void echoUnionDetectsWithinAndAcrossMemberVariation() {
        var a = base(1); a.echoes = List.of(1); var b = base(2); b.echoes = List.of(2);
        assertEquals(List.of(Reason.MULTIPLE_ECHO_NUMBERS), reasons(single(a, b)));
        a.echoes = List.of(1, 2); assertEquals(List.of(Reason.MULTIPLE_ECHO_NUMBERS), reasons(single(a)));
        a.echoes = List.of(1, 1); b.echoes = List.of(); assertTrue(single(a, b).findings().isEmpty());
        a.echoes = List.of(Integer.MIN_VALUE, Integer.MAX_VALUE);
        assertEquals(List.of(Reason.MULTIPLE_ECHO_NUMBERS), reasons(single(a)));
    }

    // T15
    @Test void multiframeIsAmbiguityOnly() {
        var a = base(1); a.frames = 1; assertTrue(single(a, base(2)).findings().isEmpty());
        a.frames = 2; assertEquals(List.of(finding(Reason.MULTIFRAME_MEMBER)), single(a, base(2)).findings());
    }

    // T16
    @Test void imageTypeComparisonUsesExactObservedVectors() {
        var a = base(1); a.imageType = List.of("ORIGINAL", "PRIMARY", "VENDOR");
        var b = base(2); b.imageType = List.of("ORIGINAL", "PRIMARY", "OTHER");
        assertEquals(List.of(Reason.MIXED_IMAGE_TYPE), reasons(single(a, b, base(3))));
        b.imageType = a.imageType; assertTrue(single(a, b, base(3)).findings().isEmpty());
        b.imageType = List.of("PRIMARY", "ORIGINAL", "VENDOR");
        assertEquals(List.of(Reason.MIXED_IMAGE_TYPE), reasons(single(a, b)));
        a.imageType = List.of("ORIGINAL", "PRIMARY"); b.imageType = List.of("ORIGINAL", "PRIMARY", "");
        assertEquals(List.of(Reason.MIXED_IMAGE_TYPE), reasons(single(a, b)));
    }

    // T17
    @Test void unobservedOptionalValuesCreateNoFindings() {
        var a = base(1); a.frame = "1.8"; a.rows = 256; a.columns = 256; a.frames = 1;
        a.acquisition = 7; a.temporal = 2; a.echoes = List.of(1); a.imageType = List.of("ORIGINAL", "PRIMARY");
        assertTrue(single(a, base(2)).findings().isEmpty());
        assertTrue(single(base(1), base(2)).findings().isEmpty());
    }

    // T18
    @Test void transferSyntaxDifferencesCreateNoF4Finding() {
        var a = base(1); var b = base(2); b.syntax = "1.2.840.10008.1.2.4.90";
        var c = base(3); c.syntax = "1.2.840.10008.1.2.1.99";
        var candidate = single(a, b, c); assertTrue(candidate.findings().isEmpty()); assertEquals(3, candidate.members().size());
        assertEquals(b.syntax, candidate.members().get(1).metadata().transferSyntaxUid());
    }

    // T19
    @Test void uniformNonMrAndUnsupportedObjectsRemainCandidates() {
        for (String sopClass : List.of("1.2.840.10008.5.1.4.1.1.2", "1.2.840.10008.5.1.4.1.1.2.1", "1.2.3.999")) {
            var a = base(1); a.modality = "CT"; a.sopClass = sopClass;
            var b = base(2); b.modality = "CT"; b.sopClass = sopClass;
            assertTrue(single(a, b).findings().isEmpty());
        }
    }

    // T20
    @Test void inputPermutationsProduceEqualDiscovery() {
        var a = base(1); a.acquisition = 7; var b = base(2); b.acquisition = 8; b.rows = 3; b.columns = 4;
        var observations = new ArrayList<>(List.of(success("𐀀", a.build()), success("\ue000", b.build()),
                success("z", facts("1.9", "1.3", "1.4.1").build()), failed("missing", DicomMetadataInspection.FailureCode.INCOMPLETE_METADATA)));
        var service = new DicomSeriesDiscoveryService(); var expected = service.discover(new DicomMetadataCatalog(observations));
        for (int seed = 0; seed < 20; seed++) {
            Collections.shuffle(observations, new Random(seed));
            assertEquals(expected, service.discover(new DicomMetadataCatalog(observations)));
            assertEquals(expected, new DicomSeriesDiscoveryService().discover(new DicomMetadataCatalog(observations)));
        }
        var sources = new HashSet<>(expected.candidates().stream().flatMap(c -> c.members().stream()).map(DicomSeriesMember::source).toList());
        sources.addAll(expected.unassigned().stream().map(DicomMetadataInspection::source).toList());
        assertEquals(new HashSet<>(observations.stream().map(DicomMetadataInspection::source).toList()), sources);
        assertEquals(observations.size(), count(expected));
    }

    // T21
    @Test void emptyCatalogProducesEmptyDiscovery() {
        assertEquals(new DicomSeriesDiscovery(List.of(), List.of()), new DicomSeriesDiscoveryService().discover(new DicomMetadataCatalog(List.of())));
    }

    private static Facts base(int sop) { return facts("1.2", "1.3", "1.4." + sop); }
    private static DicomSeriesCandidate single(Facts... facts) {
        var inspections = new ArrayList<DicomMetadataInspection>();
        for (int i = 0; i < facts.length; i++) inspections.add(success("member-" + i, facts[i].build()));
        var result = new DicomSeriesDiscoveryService().discover(new DicomMetadataCatalog(inspections));
        assertEquals(1, result.candidates().size()); assertEquals(facts.length, count(result)); assertTrue(result.unassigned().isEmpty());
        return result.candidates().getFirst();
    }
    private static DicomSeriesDiscovery discover(DicomMetadataInspection... inspections) {
        return new DicomSeriesDiscoveryService().discover(new DicomMetadataCatalog(List.of(inspections)));
    }
    private static int count(DicomSeriesDiscovery discovery) {
        return discovery.candidates().stream().mapToInt(c -> c.members().size()).sum() + discovery.unassigned().size();
    }
}
