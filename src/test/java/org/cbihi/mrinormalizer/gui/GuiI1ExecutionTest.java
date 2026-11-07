package org.cbihi.mrinormalizer.gui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.cbihi.mrinormalizer.presentation.gui.GuiI1Scanner;
import org.cbihi.mrinormalizer.presentation.gui.GuiI1Execution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class GuiI1ExecutionTest {
    @TempDir Path root;
    private GuiI1Scanner.ScanResult prepared(int count) throws Exception {
        Path input = Files.createDirectory(root.resolve("input"));
        List<GuiI1Scanner.Candidate> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Path dir = Files.createDirectory(input.resolve("s" + i));
            rows.add(new GuiI1Scanner.Candidate("Series " + (i+1), "s" + i, dir,
                    "1.2." + (i+1), List.of("image.dcm")));
        }
        return new GuiI1Scanner.ScanResult(GuiI1Scanner.Direction.DICOM_TO_NIFTI,
                GuiI1Scanner.InputKind.DICOM_PREPARED_PARENT, input, rows);
    }
    @Test void f9UsesInputDerivedFilenameWithoutUuidAndRejectsUnsafeFolderNames() {
        assertEquals("BraTS2021_01061.nii", GuiI1Execution.niftiFilename(Path.of("C:/input/BraTS2021_01061")));
        assertEquals("scan.nii", GuiI1Execution.niftiFilename(Path.of("C:/input/scan.nii")));
        assertThrows(IllegalArgumentException.class, () -> GuiI1Execution.niftiFilename(Path.of("C:/input/CON")));
        assertThrows(IllegalArgumentException.class, () -> GuiI1Execution.niftiFilename(Path.of("C:/input/trailing.")));
    }
    @Test void f9RefusesExistingNamedOutputBeforeBackendDispatch() throws Exception {
        var scan = prepared(1);
        Path output = Files.createDirectory(root.resolve("output"));
        Files.writeString(scan.candidates().getFirst().directory().resolve("image.dcm"), "fixture");
        Files.writeString(output.resolve("s0.nii"), "existing publication");
        AtomicInteger calls = new AtomicInteger();
        GuiI1Execution.Backend port = new GuiI1Execution.Backend() {
            public int ebo(String[] args) { throw new AssertionError(); }
            public int fbo(String[] args) { throw new AssertionError(); }
            public org.cbihi.mrinormalizer.application.result.F9HeadlessResult f9(Path p, Path o,
                    String uid, List<String> names) { calls.incrementAndGet(); throw new AssertionError(); }
        };
        var outcome = new GuiI1Execution(port).execute(scan, List.of(0), output);
        assertEquals(GuiI1Execution.State.REJECTED, outcome.state());
        assertEquals(0, calls.get());
        assertEquals("existing publication", Files.readString(output.resolve("s0.nii")));
    }
    @Test void oneSeriesUsesF9AndTwoSeriesUseFboExplicitly() throws Exception {
        var scan = prepared(2);
        assertEquals(GuiI1Execution.Route.F9_ONE_DICOM_SERIES,
                GuiI1Execution.plan(scan, List.of(0)));
        assertEquals(GuiI1Execution.Route.FBO_SEQUENTIAL_DICOM_BATCH,
                GuiI1Execution.plan(scan, List.of(1,0)));
        assertThrows(IllegalArgumentException.class, () -> GuiI1Execution.plan(scan, List.of(0,0)));
    }
    @Test void niftiDirectoryRequiresAllRowsBecauseEboDoesNotAcceptSubset() throws Exception {
        Path input = Files.createDirectory(root.resolve("input"));
        var list = List.of(new GuiI1Scanner.Candidate("One", null, input, null, List.of("one.nii")),
                new GuiI1Scanner.Candidate("Two", null, input, null, List.of("two.nii")));
        var scan = new GuiI1Scanner.ScanResult(GuiI1Scanner.Direction.NIFTI_TO_DICOM,
                GuiI1Scanner.InputKind.NIFTI_DIRECTORY, input, list);
        assertThrows(IllegalArgumentException.class, () -> GuiI1Execution.plan(scan, List.of(0)));
        assertEquals(GuiI1Execution.Route.EBO_NIFTI_DIRECTORY,
                GuiI1Execution.plan(scan, List.of(0,1)));
    }
    @Test void classicWindowKeepsTwoTabsWithoutToolbarAndRequiresReverseAcknowledgment() throws Exception {
        if (java.awt.GraphicsEnvironment.isHeadless()) return; // native desktop gate only
        java.util.concurrent.atomic.AtomicReference<Throwable> problem = new java.util.concurrent.atomic.AtomicReference<>();
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            org.cbihi.mrinormalizer.presentation.gui.GuiI1Window window = null;
            try {
                window = new org.cbihi.mrinormalizer.presentation.gui.GuiI1Window();
                var tabs = collect(window, javax.swing.JTabbedPane.class);
                assertEquals(1, tabs.size());
                assertEquals(2, tabs.getFirst().getTabCount());
                assertEquals("DICOM → NIfTI", tabs.getFirst().getTitleAt(0));
                assertEquals("NIfTI → DICOM", tabs.getFirst().getTitleAt(1));
                assertEquals(0, collect(window, javax.swing.JToolBar.class).size());
                assertEquals(0, collect(window, javax.swing.JComboBox.class).size());
                var buttons = collect(window, javax.swing.JButton.class);
                assertEquals(2L, buttons.stream().filter(b -> b.getText().equals("Browse...")).count());
                assertEquals(1L, buttons.stream().filter(b -> b.getText().equals("Convert")).count());
                assertEquals(1L, buttons.stream().filter(b -> b.getText().equals("Close")).count());
                var acknowledgments = collect(window, javax.swing.JCheckBox.class);
                assertEquals(1, acknowledgments.size());
                assertFalse(acknowledgments.getFirst().getParent().isVisible());
                tabs.getFirst().setSelectedIndex(1);
                assertTrue(acknowledgments.getFirst().getParent().isVisible());
                assertFalse(acknowledgments.getFirst().isSelected());
                assertFalse(buttons.stream().filter(b -> b.getText().equals("Convert")).findFirst().orElseThrow().isEnabled());
            } catch (Throwable fail) { problem.set(fail); }
            finally { if (window != null) window.dispose(); }
        });
        if (problem.get() != null) throw new AssertionError("Classic GUI desktop invariant failed", problem.get());
    }
    private static <T extends java.awt.Component> List<T> collect(java.awt.Component root, Class<T> type) {
        List<T> found = new ArrayList<>();
        if (type.isInstance(root)) found.add(type.cast(root));
        if (root instanceof java.awt.Container container)
            for (java.awt.Component child : container.getComponents()) found.addAll(collect(child, type));
        return found;
    }

    @Test void injectedEboStatusOnlyShowsVerifiedOnZeroAndRejectsOverlappingRoots() throws Exception {
        Path input = Files.createDirectory(root.resolve("input"));
        Path output = Files.createDirectory(root.resolve("output"));
        // E1 fingerprints source content before dispatch: the stub must supply a real file.
        Files.write(input.resolve("example.nii"), new byte[]{1, 2, 3, 4});
        var scan = new GuiI1Scanner.ScanResult(GuiI1Scanner.Direction.NIFTI_TO_DICOM,
                GuiI1Scanner.InputKind.NIFTI_FILE, input.resolve("example.nii"),
                List.of(new GuiI1Scanner.Candidate("One", null, input, null, List.of("example.nii"))));
        AtomicInteger calls = new AtomicInteger();
        GuiI1Execution.Backend port = new GuiI1Execution.Backend() {
            public int ebo(String[] args) { calls.incrementAndGet(); return 5; }
            public int fbo(String[] args) { throw new AssertionError("FBO called unexpectedly"); }
            public org.cbihi.mrinormalizer.application.result.F9HeadlessResult f9(Path p, Path o,
                    String uid, List<String> names) { throw new AssertionError("F9 called unexpectedly"); }
        };
        var execution = new GuiI1Execution(port);
        var outcome = execution.execute(scan, List.of(0), output);
        assertEquals(GuiI1Execution.State.RECOVERY_REQUIRED, outcome.state());
        assertEquals(1, calls.get());
        assertThrows(java.io.IOException.class, () -> execution.execute(scan, List.of(0), input));
        assertEquals(1, calls.get());
    }
}
