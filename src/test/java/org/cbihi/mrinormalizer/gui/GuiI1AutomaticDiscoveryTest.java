package org.cbihi.mrinormalizer.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.cbihi.mrinormalizer.presentation.gui.GuiI1DicomDiscovery;
import org.cbihi.mrinormalizer.presentation.gui.GuiI1Execution;
import org.cbihi.mrinormalizer.presentation.gui.GuiI1Scanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Controlled metadata reader isolates discovery and routing from pixel decoding. */
final class GuiI1AutomaticDiscoveryTest {
    @TempDir Path tmp;
    private GuiI1Scanner scanner() {
        return new GuiI1Scanner(new GuiI1DicomDiscovery(path -> {
            String filename = path.getFileName().toString();
            if (!filename.endsWith(".dcm")) throw new IOException("Not DICOM");
            // No assumed DICOM extension in production; this fake parser is for deterministic tests.
            String series = filename.substring(0, filename.indexOf('-'));
            String instance = filename.substring(filename.indexOf('-') + 1, filename.length() - 4);
            return new GuiI1DicomDiscovery.Metadata("1.2.3", "1.2." + (series.equals("A") ? "4" : "5"),
                    "1.2.9." + Integer.parseInt(instance), "MR", 1);
        }));
    }
    private static void create(Path folder, String series, int count) throws Exception {
        for (int i = 1; i <= count; i++) Files.write(folder.resolve(series + "-" + i + ".dcm"), new byte[]{42});
    }
    @Test void discovers155InstancesWithoutTruncationAndOffersBackendPreflight() throws Exception {
        Path series = Files.createDirectory(tmp.resolve("one"));
        create(series, "A", 155);
        var r = scanner().scan(GuiI1Scanner.Direction.DICOM_TO_NIFTI, series);
        assertEquals(1, r.candidates().size());
        assertEquals(155, r.candidates().getFirst().instanceCount());
        assertTrue(r.candidates().getFirst().eligible());
        assertEquals(GuiI1Scanner.InputKind.DICOM_DIRECT, r.kind());
        assertEquals(GuiI1Execution.Route.F9_ONE_DICOM_SERIES, GuiI1Execution.plan(r, List.of(0)));
    }
    @Test void preparedParentCountsAndRoutesTwoDistinctSeries() throws Exception {
        Path a = Files.createDirectory(tmp.resolve("a")), b = Files.createDirectory(tmp.resolve("b"));
        create(a, "A", 3); create(b, "B", 4);
        var r = scanner().scan(GuiI1Scanner.Direction.DICOM_TO_NIFTI, tmp);
        assertEquals(GuiI1Scanner.InputKind.DICOM_PREPARED_PARENT, r.kind());
        assertEquals(List.of(3, 4), r.candidates().stream().map(GuiI1Scanner.Candidate::instanceCount).toList());
        assertTrue(r.candidates().stream().allMatch(GuiI1Scanner.Candidate::eligible));
        assertEquals(GuiI1Execution.Route.F9_ONE_DICOM_SERIES, GuiI1Execution.plan(r, List.of(0)));
        assertEquals(GuiI1Execution.Route.FBO_SEQUENTIAL_DICOM_BATCH, GuiI1Execution.plan(r, List.of(0, 1)));
    }
    @Test void mixedFlatFolderOffersExplicitSelectionForAutomaticPreparation() throws Exception {
        create(tmp, "A", 2); create(tmp, "B", 3);
        var r = scanner().scan(GuiI1Scanner.Direction.DICOM_TO_NIFTI, tmp);
        assertEquals(2, r.candidates().size());
        assertEquals(GuiI1Scanner.InputKind.DICOM_DISCOVERY_ONLY, r.kind());
        assertEquals(List.of(2, 3), r.candidates().stream().map(GuiI1Scanner.Candidate::instanceCount).toList());
        assertEquals(GuiI1Execution.Route.F9_ONE_DICOM_SERIES, GuiI1Execution.plan(r, List.of(0)));
    }
    @Test void nestedFolderIsEligibleForAutomaticPreparation() throws Exception {
        Path sub = Files.createDirectories(tmp.resolve("study").resolve("series"));
        create(sub, "A", 2);
        var r = scanner().scan(GuiI1Scanner.Direction.DICOM_TO_NIFTI, tmp);
        assertEquals(2, r.candidates().getFirst().instanceCount());
        assertTrue(r.candidates().getFirst().eligible());
    }
    @Test void nonDicomFilesAreNotSelectedForReconstruction() throws Exception {
        create(tmp, "A", 3);
        Files.writeString(tmp.resolve("notes.txt"), "not a DICOM file");
        var found = new GuiI1DicomDiscovery(path -> {
            if (!path.getFileName().toString().endsWith(".dcm")) throw new IOException("non DICOM");
            return new GuiI1DicomDiscovery.Metadata("1.2.3", "1.2.4", "1.2.9." +
                    path.getFileName().toString().replaceAll("\\D", ""), "MR", 1);
        }).discover(tmp);
        assertEquals(1, found.unidentifiedFiles());
        assertEquals(3, found.series().getFirst().instances());
        assertTrue(found.series().getFirst().eligible());
    }
    @Test void discoveryStopsRatherThanPartiallyReportingBeyondFileBudget() throws Exception {
        Path nested = tmp;
        for (int i = 0; i < GuiI1DicomDiscovery.MAX_DIRECTORY_DEPTH + 1; i++)
            nested = Files.createDirectory(nested.resolve("nest"));
        Path source = tmp;
        assertThrows(IOException.class, () -> scanner().scan(GuiI1Scanner.Direction.DICOM_TO_NIFTI, source));
    }
    @Test void exactly128PreparedInstancesRemainSelectableForBackendPreflight() throws Exception {
        create(tmp, "A", 128);
        var r = scanner().scan(GuiI1Scanner.Direction.DICOM_TO_NIFTI, tmp);
        assertEquals(128, r.candidates().getFirst().instanceCount());
        assertTrue(r.candidates().getFirst().eligible());
        assertEquals(GuiI1Scanner.InputKind.DICOM_DIRECT, r.kind());
    }
    @Test void sameSeriesScatteredAcrossFoldersIsCountedButNotRouted() throws Exception {
        Path one = Files.createDirectory(tmp.resolve("one"));
        Path two = Files.createDirectory(tmp.resolve("two"));
        create(one, "A", 2); create(two, "A", 2);
        var r = scanner().scan(GuiI1Scanner.Direction.DICOM_TO_NIFTI, tmp);
        assertEquals(1, r.candidates().size());
        assertEquals(2, r.candidates().getFirst().instanceCount());
        assertFalse(r.candidates().getFirst().eligible());
    }
    @Test void duplicateSopInstanceUidIsNotEligible() throws Exception {
        Files.write(tmp.resolve("A-1.dcm"), new byte[]{42});
        Files.write(tmp.resolve("A-01.dcm"), new byte[]{42});
        var r = scanner().scan(GuiI1Scanner.Direction.DICOM_TO_NIFTI, tmp);
        assertEquals(1, r.candidates().getFirst().instanceCount());
        assertFalse(r.candidates().getFirst().eligible());
        assertTrue(r.candidates().getFirst().finding().contains("Duplicate"));
    }
    @Test void multiFrameDicomIsDetectedAsAnInstanceButNotDispatchedToSingleFrameBackend() throws Exception {
        Files.write(tmp.resolve("volume.dcm"), new byte[]{42});
        var discovery = new GuiI1DicomDiscovery(p ->
                new GuiI1DicomDiscovery.Metadata("1.2.3", "1.2.4", "1.2.5", "MR", 155));
        var r = new GuiI1Scanner(discovery).scan(GuiI1Scanner.Direction.DICOM_TO_NIFTI, tmp);
        assertEquals(1, r.candidates().getFirst().instanceCount());
        assertFalse(r.candidates().getFirst().eligible());
        assertTrue(r.candidates().getFirst().finding().contains("multiframe"));
    }
    @Test void sourceFilesRemainUnchangedByMetadataGrouping() throws Exception {
        create(tmp, "A", 3);
        var before = Files.readAllBytes(tmp.resolve("A-1.dcm"));
        scanner().scan(GuiI1Scanner.Direction.DICOM_TO_NIFTI, tmp);
        assertArrayEquals(before, Files.readAllBytes(tmp.resolve("A-1.dcm")));
    }
}
