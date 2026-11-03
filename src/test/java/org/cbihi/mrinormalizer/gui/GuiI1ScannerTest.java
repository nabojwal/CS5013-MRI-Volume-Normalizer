package org.cbihi.mrinormalizer.gui;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import org.cbihi.mrinormalizer.presentation.gui.GuiI1Scanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class GuiI1ScannerTest {
    @TempDir Path root;
    private static byte[] minimalNifti() {
        byte[] bytes = new byte[352];
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0, 348); b.putShort(40, (short)3);
        b.putShort(42, (short)1); b.putShort(44, (short)1); b.putShort(46, (short)1);
        bytes[344] = 'n'; bytes[345] = '+'; bytes[346] = '1';
        return bytes;
    }
    @Test void plainAndGzipSignaturesAreRecognizedWithoutMutation() throws Exception {
        Path plain = Files.write(root.resolve("a.nii"), minimalNifti());
        Path gzip = root.resolve("b.nii.gz");
        try (var output = new GZIPOutputStream(Files.newOutputStream(gzip))) { output.write(minimalNifti()); }
        byte[] before = Files.readAllBytes(plain);
        var result = new GuiI1Scanner().scan(GuiI1Scanner.Direction.NIFTI_TO_DICOM, root);
        assertEquals(GuiI1Scanner.InputKind.NIFTI_DIRECTORY, result.kind());
        assertEquals(2, result.candidates().size());
        assertArrayEquals(before, Files.readAllBytes(plain));
    }
    @Test void refusesMixedFolderWithCorruptNiftiCandidateRatherThanSilentlySkipping() throws Exception {
        Files.write(root.resolve("a.nii"), minimalNifti());
        Files.writeString(root.resolve("b.nii"), "bad header");
        assertThrows(IOException.class, () -> new GuiI1Scanner().scan(
                GuiI1Scanner.Direction.NIFTI_TO_DICOM, root));
    }
    @Test void rejectsSymlinkedCandidateAndUnsupportedNiftiDimensions() throws Exception {
        byte[] wrong = minimalNifti(); wrong[40] = 4;
        Files.write(root.resolve("wrong.nii"), wrong);
        assertThrows(IOException.class, () -> new GuiI1Scanner().scan(
                GuiI1Scanner.Direction.NIFTI_TO_DICOM, root.resolve("wrong.nii")));
        Path regular = Files.write(root.resolve("good.nii"), minimalNifti());
        Path link = root.resolve("alias.nii");
        try { Files.createSymbolicLink(link, regular.getFileName()); }
        catch (UnsupportedOperationException | IOException nativeLinkUnavailable) { return; }
        assertThrows(IOException.class, () -> new GuiI1Scanner().scan(
                GuiI1Scanner.Direction.NIFTI_TO_DICOM, link));
    }
}
