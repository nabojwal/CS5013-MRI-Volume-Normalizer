package org.cbihi.mrinormalizer.gui;

import java.nio.file.Files;
import java.nio.file.Path;
import org.cbihi.mrinormalizer.presentation.gui.GuiI1Scanner;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real dcm4che Part-10 metadata parsing; no image data is required for preview. */
final class GuiI1DicomPart10IntegrationTest {
    @TempDir Path temp;
    private static void create(Path path, String series, int number) throws Exception {
        Attributes a = new Attributes();
        a.setString(Tag.SOPClassUID, VR.UI, UID.MRImageStorage);
        a.setString(Tag.SOPInstanceUID, VR.UI, "1.2.826.0.1.3680043.10.985." + series + "." + number);
        a.setString(Tag.StudyInstanceUID, VR.UI, "1.2.826.0.1.3680043.10.985.42");
        a.setString(Tag.SeriesInstanceUID, VR.UI, "1.2.826.0.1.3680043.10.985." + series);
        a.setString(Tag.Modality, VR.CS, "MR");
        var bytes = new java.io.ByteArrayOutputStream();
        try (DicomOutputStream out = new DicomOutputStream(bytes, UID.ExplicitVRLittleEndian)) {
            out.writeDataset(a.createFileMetaInformation(UID.ExplicitVRLittleEndian), a);
        }
        Files.write(path, bytes.toByteArray());
    }
    @Test void scansRealPart10MetadataWithUnusualFileExtensionAndDiscovers155Instances() throws Exception {
        Path dir = Files.createDirectory(temp.resolve("series"));
        for (int i = 1; i <= 155; i++) create(dir.resolve("instance_" + i + ".bin"), "7", i);
        var scan = new GuiI1Scanner().scan(GuiI1Scanner.Direction.DICOM_TO_NIFTI, dir);
        assertEquals(1, scan.candidates().size());
        assertEquals(155, scan.candidates().getFirst().instanceCount());
        assertFalse(scan.candidates().getFirst().eligible());
    }
    @Test void realPart10SeriesUidDiscoveryAllowsPreparedTwoSeriesWithoutTypingUID() throws Exception {
        Path a = Files.createDirectory(temp.resolve("seriesA"));
        Path b = Files.createDirectory(temp.resolve("seriesB"));
        create(a.resolve("slice1"), "7", 1);
        create(b.resolve("slice1"), "8", 1);
        var scan = new GuiI1Scanner().scan(GuiI1Scanner.Direction.DICOM_TO_NIFTI, temp);
        assertEquals(2, scan.candidates().size());
        assertTrue(scan.candidates().stream().allMatch(GuiI1Scanner.Candidate::eligible));
        assertEquals("1.2.826.0.1.3680043.10.985.7", scan.candidates().getFirst().seriesUid());
    }
}
