package org.cbihi.mrinormalizer.fbo;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.cbihi.mrinormalizer.application.request.F9HeadlessRequest;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.cbihi.mrinormalizer.application.fbo.*;
import org.junit.jupiter.api.Test;

class FboRequestResultTest {
    private static FboRequest.SeriesSelection s(String name, String uid) {
        return new FboRequest.SeriesSelection(name, uid);
    }
    private static FboRequest request(List<FboRequest.SeriesSelection> selections) {
        return new FboRequest(Path.of("source"), Path.of("output"), selections);
    }
    @Test void selectedSeriesOf155And256MembersIsRepresentableWithoutTruncation() {
        for (int count : List.of(155, 256)) {
            List<RelativePath> members = new ArrayList<>();
            List<FboBatchController.PreparedFile> prepared = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String name = "instance-" + i + ".dcm";
                members.add(new RelativePath(RelativePath.Root.SOURCE, name));
                prepared.add(new FboBatchController.PreparedFile(name, 1024L, FileTime.fromMillis(0),
                        FileTime.fromMillis(0), null, "a".repeat(64)));
            }
            var request = new F9HeadlessRequest(UUID.randomUUID(), "1.2.3", members,
                    new RelativePath(RelativePath.Root.OUTPUT, "selected.nii"));
            assertEquals(count, request.selectedMembers().size());
            var series = new FboBatchController.PreparedSeries(1, Path.of("source"), "1.2.3",
                    new FboBatchController.DirectoryObservation(null, FileTime.fromMillis(0),
                            FileTime.fromMillis(0)), prepared);
            assertEquals(count, series.members().size());
        }
    }
    @Test void t01_exactBoundsAndImmutableSelection() {
        assertThrows(IllegalArgumentException.class, () -> request(List.of()));
        List<FboRequest.SeriesSelection> items = new ArrayList<>();
        for (int i = 1; i <= 16; i++) items.add(s("series" + i, "1.2." + i));
        FboRequest good = request(items);
        assertEquals(16, good.selections().size());
        items.add(s("series17", "1.2.17"));
        assertEquals(16, good.selections().size());
        assertThrows(IllegalArgumentException.class, () -> request(items));
        assertThrows(IllegalArgumentException.class, () -> request(null));
        assertThrows(NullPointerException.class, () -> new FboRequest(null,Path.of("out"),List.of(s("a","1.2"))));
    }
    @Test void t02_restrictedChildAndCanonicalUid() {
        for (String invalid : List.of("", "..", "../a", "a/b", "a\\b", "C:", "CON", "COM1.txt", "file. ", " a")) {
            assertThrows(IllegalArgumentException.class, () -> s(invalid,"1.2"), invalid);
        }
        for (String bad : List.of("", "abc", "1..2", "1.02", "1.2.", "a.3")) {
            assertThrows(IllegalArgumentException.class, () -> s("series",bad),bad);
        }
        assertThrows(IllegalArgumentException.class, () -> request(List.of(s("A","1.2"),s("a","2.3"))));
        assertEquals(List.of("B", "A"), request(List.of(s("B","1.2"),s("A","1.3")))
                .selections().stream().map(FboRequest.SeriesSelection::childDirectory).toList());
    }
    @Test void t22_resultInvariantAndCounts() {
        UUID batch=UUID.randomUUID(), first=UUID.randomUUID(), second=UUID.randomUUID();
        var entries=List.of(FboResult.Entry.verified(1,first),
                FboResult.Entry.failed(2,second,FboResult.Reason.F9_FAILED),FboResult.Entry.unattempted(3));
        var result=new FboResult(batch,FboResult.Status.PARTIAL_SUCCESS,entries,Optional.empty());
        assertEquals(3,result.selectedCount()); assertEquals(2,result.attemptedCount());
        assertEquals(1,result.verifiedCount()); assertEquals(1,result.failedCount());
        assertEquals(1,result.notAttemptedCount());
        assertThrows(IllegalArgumentException.class, () -> new FboResult(batch,FboResult.Status.SUCCESS,entries,Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new FboResult(batch,FboResult.Status.FAILED,
                List.of(FboResult.Entry.unattempted(2)),Optional.empty()));
    }
}
