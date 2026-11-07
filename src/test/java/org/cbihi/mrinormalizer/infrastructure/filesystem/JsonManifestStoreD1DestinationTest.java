package org.cbihi.mrinormalizer.infrastructure.filesystem;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** D1: destination drive identity must come from Windows, not SystemDrive equality. */
class JsonManifestStoreD1DestinationTest {

    @Test
    void nullAndRelativePathsDoNotQualify() {
        assertFalse(JsonManifestStore.isWindowsFixedLocalRoot(null));
        assertFalse(JsonManifestStore.isWindowsFixedLocalRoot(Path.of("relative-output")));
    }

    @Test
    void uncRootNeverQualifiesAsFixedLocal() {
        assumeTrue(isWindows());
        assertFalse(JsonManifestStore.isWindowsFixedLocalRoot(Path.of("\\\\server\\share\\output")));
    }

    @Test
    void systemDriveIsRecognizedWhenNativeWindowsReportsFixedNtfs() throws Exception {
        assumeTrue(isWindows());
        String drive = System.getenv("SystemDrive");
        assumeTrue(drive != null && drive.matches("(?i)^[a-z]:$"));
        Path root = Path.of(drive + "\\");
        assumeTrue(Files.getFileStore(root).type().equalsIgnoreCase("ntfs"));
        // This is a native Windows test: the host must report a fixed local disk.
        assertTrue(JsonManifestStore.isWindowsFixedLocalRoot(root));
    }

    @Test
    void independentFixedNtfsDriveIsEligibleWhenPresent() throws Exception {
        assumeTrue(isWindows());
        Path root = Path.of("D:\\");
        assumeTrue(Files.isDirectory(root));
        assumeTrue(Files.getFileStore(root).type().equalsIgnoreCase("ntfs"));
        // A removable, mapped or otherwise non-fixed D: is not an eligible fixture.
        assumeTrue(JsonManifestStore.isWindowsFixedLocalRoot(root));
        assertTrue(JsonManifestStore.isWindowsFixedLocalRoot(root));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }
}
