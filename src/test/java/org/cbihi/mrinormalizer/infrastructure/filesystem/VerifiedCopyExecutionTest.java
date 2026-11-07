package org.cbihi.mrinormalizer.infrastructure.filesystem;

import static org.junit.jupiter.api.Assertions.*;
import static org.cbihi.mrinormalizer.infrastructure.filesystem.SourceDigestAcquisitionTest.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.cbihi.mrinormalizer.application.dataset.copy.*;
import org.cbihi.mrinormalizer.application.port.out.VerifiedCopyAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VerifiedCopyExecutionTest {
    @TempDir Path temporary;
    // C37/T41: a parent creation fault is a write failure; later observation is still read activity.
    @Test void parentCreationFailureKeepsWriteReadAndTypedActivitiesDistinctInRealJournal() throws Exception {
        for (String mode : List.of("create", "observe", "typed", "interrupt")) {
            var f = new Fixture(privateDirectory(temporary.resolve(mode)));
            var parent = f.output.resolve("layout-v1");
            if (mode.equals("typed")) privateFile(parent, new byte[] {31});
            var reached = new java.util.concurrent.atomic.AtomicBoolean();
            var hooks = new NioVerifiedCopyAccess.Hooks() {
                public void at(NioVerifiedCopyAccess.Event event, Path path) {}
                public void createDirectory(Path path, java.nio.file.attribute.FileAttribute<?>... attributes) throws IOException {
                    if (path.equals(parent)) {
                        reached.set(true);
                        if (mode.equals("create")) throw new IOException("injected-parent-create-sentinel");
                        if (mode.equals("interrupt")) throw new java.nio.channels.ClosedByInterruptException();
                    }
                    Files.createDirectory(path, attributes);
                }
                public java.nio.file.attribute.UserPrincipal owner(Path path) throws IOException {
                    if (mode.equals("observe") && path.equals(parent)) throw new IOException("injected-parent-read-sentinel");
                    return Files.getOwner(path, LinkOption.NOFOLLOW_LINKS);
                }
            };
            var access = new NioVerifiedCopyAccess(f.source, f.output, f.namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector(), hooks);
            var batch = access.acquire(f.inventory, f.discovery); var plan = f.plan(batch);
            CopyExecutionResult result;
            try {
                result = f.execute(access, batch, UUID.randomUUID());
                if (mode.equals("interrupt")) assertTrue(Thread.currentThread().isInterrupted());
            } finally { if (mode.equals("interrupt")) Thread.interrupted(); }
            assertTrue(reached.get());
            var expected = mode.equals("create") ? CopyFailure.Code.WRITE_FAILED
                    : mode.equals("observe") ? CopyFailure.Code.READ_FAILED
                    : mode.equals("interrupt") ? CopyFailure.Code.INTERRUPTED : CopyFailure.Code.CONTAINMENT_UNPROVEN;
            assertEquals(CopyExecutionResult.Status.FAILED, result.status()); code(result.failures(), expected);
            assertTrue(result.failures().stream().allMatch(x -> x.stage() == CopyFailure.Stage.COPY && x.code() == expected));
            var durable = new org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure(
                    org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Phase.EXECUTION,
                    org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Code.valueOf(expected.name()));
            var journal = result.journal().orElseThrow();
            assertEquals(org.cbihi.mrinormalizer.application.provenance.manifest.ManifestState.JobState.FAILED, journal.state());
            assertEquals(List.of(durable), journal.jobFailures()); assertEquals(List.of(durable), journal.operations().getFirst().failures());
            assertFalse(Files.exists(f.output.resolve(plan.copies().getFirst().destination().path())));
            assertTrue(f.outputs().stream().noneMatch(x -> x.contains(".f6-stage-") || x.contains(".f6-probe-")));
            if (mode.equals("create") || mode.equals("interrupt")) assertFalse(Files.exists(parent));
            if (mode.equals("typed")) assertArrayEquals(new byte[] {31}, Files.readAllBytes(parent));
            assertFalse(result.toString().contains("sentinel"));
            System.out.println("F6 parent-boundary mode=" + mode + " injected-io=" + !mode.equals("typed") + " expected=" + expected);
        }
    }
    // T21/T30/T34: actual F5 identities, actual F1 ACKs and complete exact original bytes.
    @Test void exactPlanPublishesViaOwnedStageAndLeavesNoTemporaryLink() throws Exception {
        var f = new Fixture(temporary); var access = f.access(); var b = access.acquire(f.inventory, f.discovery);
        var plan = f.plan(b); var result = f.execute(access, b, UUID.randomUUID());
        assertEquals(CopyExecutionResult.Status.JOURNAL_COMPLETED, result.status());
        assertEquals(plan.copies().getFirst().operationId(), result.operations().getFirst().operationId());
        var target = f.output.resolve(plan.copies().getFirst().destination().path());
        assertArrayEquals(Files.readAllBytes(f.source.resolve("synthetic.nii")), Files.readAllBytes(target));
        assertFalse(Files.isSameFile(target, f.source.resolve("synthetic.nii")));
        assertTrue(f.outputs().stream().noneMatch(p -> p.contains(".f6-stage-") || p.contains(".f6-probe-")));
        assertTrue(Files.isRegularFile(f.output.resolve(".f6-copy.lease")));
    }
    // T23/T25: byte-identical logical aliases stay separate operations, including hard-linked sources.
    @Test void hardLinkedSourcesRetainSeparateIdsAndOneCompatibleDestination() throws Exception {
        var f = new Fixture(temporary); Files.createLink(f.source.resolve("second.nii"), f.source.resolve("synthetic.nii")); f.observe();
        Object originalLinkCount = Files.getFileAttributeView(f.source, java.nio.file.attribute.PosixFileAttributeView.class) == null ? null : Files.getAttribute(f.source.resolve("synthetic.nii"), "unix:nlink");
        var access = f.access(); var b = access.acquire(f.inventory, f.discovery); var plan = f.plan(b);
        assertEquals(2, plan.copies().size()); assertEquals(plan.copies().getFirst().destination(), plan.copies().getLast().destination());
        var result = f.execute(access, b, UUID.randomUUID()); assertEquals(CopyExecutionResult.Status.JOURNAL_COMPLETED, result.status());
        assertEquals(Set.of(CopyExecutionResult.Outcome.COMPLETED, CopyExecutionResult.Outcome.IDENTICAL_EXISTING),
                new HashSet<>(result.operations().stream().map(CopyExecutionResult.OperationResult::outcome).toList()));
        assertTrue(Files.isSameFile(f.source.resolve("second.nii"), f.source.resolve("synthetic.nii")));
        if (originalLinkCount != null) assertEquals(originalLinkCount, Files.getAttribute(f.source.resolve("synthetic.nii"), "unix:nlink"));
    }
    // T24/T26/T27: independently validate winners; preserve differing bytes and reject permissions without repair.
    @Test void identicalTargetIsVerifiedAndDifferentTargetRemainsUntouched() throws Exception {
        var f = new Fixture(temporary); var access = f.access(); var b = access.acquire(f.inventory, f.discovery); var p = f.plan(b);
        var target = f.output.resolve(p.copies().getFirst().destination().path());
        privateDirectory(f.output.resolve("layout-v1")); privateDirectory(f.output.resolve("layout-v1/nifti"));
        var wrong = nifti(4096); wrong[1000] ^= 1; privateFile(target, wrong);
        var result = f.execute(access, b, UUID.randomUUID());
        assertEquals(CopyExecutionResult.Status.FAILED, result.status()); code(result.failures(), CopyFailure.Code.OUTPUT_CONFLICT);
        assertArrayEquals(wrong, Files.readAllBytes(target));
        Files.write(target, nifti(4096)); var next = f.access(); var fresh = next.acquire(f.inventory, f.discovery);
        var good = f.execute(next, fresh, UUID.randomUUID());
        assertEquals(CopyExecutionResult.Status.JOURNAL_COMPLETED, good.status());
        assertEquals(CopyExecutionResult.Outcome.IDENTICAL_EXISTING, good.operations().getFirst().outcome());
    }
    @Test void finalSourceAliasNeverCountsAsIdenticalExisting() throws Exception {
        var f = new Fixture(temporary); var access = f.access(); var b = access.acquire(f.inventory, f.discovery); var p = f.plan(b);
        privateDirectory(f.output.resolve("layout-v1")); privateDirectory(f.output.resolve("layout-v1/nifti"));
        var target = f.output.resolve(p.copies().getFirst().destination().path()); Files.createLink(target, f.source.resolve("synthetic.nii"));
        var result = f.execute(access, b, UUID.randomUUID()); assertEquals(CopyExecutionResult.Status.FAILED, result.status());
        code(result.failures(), CopyFailure.Code.CONTAINMENT_UNPROVEN); assertTrue(Files.isSameFile(target, f.source.resolve("synthetic.nii")));
    }
    @Test void discoveredOutputSymlinkIsRefusedWithoutRemovingIt() throws Exception {
        var f = new Fixture(temporary); var access = f.access(); var b = access.acquire(f.inventory, f.discovery);
        var elsewhere = privateDirectory(temporary.resolve("elsewhere")); Files.createSymbolicLink(f.output.resolve("layout-v1"), elsewhere);
        var result = f.execute(access, b, UUID.randomUUID()); assertEquals(CopyExecutionResult.Status.FAILED, result.status());
        assertTrue(Files.isSymbolicLink(f.output.resolve("layout-v1"))); assertEquals(0, Files.list(elsewhere).count());
    }
    // T28/T29: mechanical faults supplement native publication. Force failure never publishes.
    @Test void forceFailureCleansOwnedStageAndRecordsKnownFailure() throws Exception {
        var f = new Fixture(temporary);
        var access = new NioVerifiedCopyAccess(f.source, f.output, f.namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS,
                detector(), (event, path) -> { if (event == NioVerifiedCopyAccess.Event.FORCE) throw new IOException("private-provider-sentinel"); });
        var b = access.acquire(f.inventory, f.discovery); var target = f.output.resolve(f.plan(b).copies().getFirst().destination().path());
        var result = f.execute(access, b, UUID.randomUUID()); assertEquals(CopyExecutionResult.Status.FAILED, result.status());
        code(result.failures(), CopyFailure.Code.WRITE_FAILED); assertFalse(Files.exists(target));
        assertFalse(result.toString().contains("private-provider-sentinel"));
        assertTrue(f.outputs().stream().noneMatch(p -> p.contains(".f6-stage-")));
    }
    @Test void corruptionAtIndependentStageReopenPreventsPublication() throws Exception {
        var f = new Fixture(temporary);
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        var injected = new java.util.concurrent.atomic.AtomicBoolean();
        var verification = new java.util.concurrent.atomic.AtomicBoolean();
        var hooks = new NioVerifiedCopyAccess.Hooks() {
            public void at(NioVerifiedCopyAccess.Event event, Path path) throws IOException {
                if (event == NioVerifiedCopyAccess.Event.STAGE_REOPEN) {
                    if (windows) {
                        // Real provider denial remains required. Inject bad read evidence separately;
                        // do not weaken the overlapping Windows write/delete-sharing pins.
                        assertThrows(IOException.class, () -> Files.write(path, new byte[] {99}));
                        verification.set(true);
                    } else { Files.write(path, new byte[] {99}); injected.set(true); }
                }
            }
            public int read(NioVerifiedCopyAccess.Event event, java.nio.channels.SeekableByteChannel channel,
                    java.nio.ByteBuffer buffer) throws IOException {
                int start = buffer.position(); int count = channel.read(buffer);
                if (verification.get() && count > 0 && injected.compareAndSet(false, true))
                    buffer.put(start, (byte)(buffer.get(start) ^ 1));
                return count;
            }
        };
        var access = new NioVerifiedCopyAccess(f.source, f.output, f.namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector(), hooks);
        var b = access.acquire(f.inventory, f.discovery); var target = f.output.resolve(f.plan(b).copies().getFirst().destination().path());
        var result = f.execute(access, b, UUID.randomUUID()); assertEquals(CopyExecutionResult.Status.FAILED, result.status());
        code(result.failures(), CopyFailure.Code.VERIFICATION_FAILED); assertFalse(Files.exists(target)); assertTrue(injected.get());
    }
    // T31: a concurrent creator wins unchanged; matching winners require independent verification.
    @Test void competingCreatorIsClassifiedWithoutReplacement() throws Exception {
        var f = new Fixture(temporary);
        var access = new NioVerifiedCopyAccess(f.source, f.output, f.namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS,
                detector(), (event, path) -> { if (event == NioVerifiedCopyAccess.Event.BEFORE_LINK) try { privateFile(path, new byte[] {9}); }
                    catch (Exception error) { throw new IOException(); } });
        var b = access.acquire(f.inventory, f.discovery); var target = f.output.resolve(f.plan(b).copies().getFirst().destination().path());
        var result = f.execute(access, b, UUID.randomUUID()); assertEquals(CopyExecutionResult.Status.FAILED, result.status());
        code(result.failures(), CopyFailure.Code.OUTPUT_CONFLICT); assertArrayEquals(new byte[] {9}, Files.readAllBytes(target));
    }
    // T32/T33: final corruption never deletes final; uncertain stage replacement remains.
    @Test void finalVerificationFailurePreservesVisibleFinal() throws Exception {
        var f = new Fixture(temporary);
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        var injected = new java.util.concurrent.atomic.AtomicBoolean();
        var verification = new java.util.concurrent.atomic.AtomicBoolean();
        var hooks = new NioVerifiedCopyAccess.Hooks() {
            public void at(NioVerifiedCopyAccess.Event event, Path path) throws IOException {
                if (event == NioVerifiedCopyAccess.Event.FINAL_READ) {
                    if (windows) {
                        // Real provider denial remains required. Inject bad read evidence separately;
                        // do not weaken the overlapping Windows write/delete-sharing pins.
                        assertThrows(IOException.class, () -> Files.write(path, new byte[] {7}));
                        verification.set(true);
                    } else { Files.write(path, new byte[] {7}); injected.set(true); }
                }
            }
            public int read(NioVerifiedCopyAccess.Event event, java.nio.channels.SeekableByteChannel channel,
                    java.nio.ByteBuffer buffer) throws IOException {
                int start = buffer.position(); int count = channel.read(buffer);
                if (verification.get() && count > 0 && injected.compareAndSet(false, true))
                    buffer.put(start, (byte)(buffer.get(start) ^ 1));
                return count;
            }
        };
        var access = new NioVerifiedCopyAccess(f.source, f.output, f.namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector(), hooks);
        var b = access.acquire(f.inventory, f.discovery); var target = f.output.resolve(f.plan(b).copies().getFirst().destination().path());
        var result = f.execute(access, b, UUID.randomUUID()); assertNotEquals(CopyExecutionResult.Status.JOURNAL_COMPLETED, result.status());
        code(result.failures(), CopyFailure.Code.VERIFICATION_FAILED); assertTrue(injected.get());
        assertArrayEquals(windows ? nifti(4096) : new byte[] {7}, Files.readAllBytes(target));
    }
    @Test void uncertainCleanupRetainsReplacementAndStopsNextOperation() throws Exception {
        var f = new Fixture(temporary); privateFile(f.source.resolve("second.nii"), nifti(8192)); f.observe();
        var replacements = new ArrayList<Path>();
        var access = new NioVerifiedCopyAccess(f.source, f.output, f.namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS,
                detector(), (event, path) -> { if (event == NioVerifiedCopyAccess.Event.CLEANUP) {
                    var before = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class);
                    Files.delete(path); try { privateFile(path, new byte[] {8}); } catch (Exception error) { throw new IOException(); }
                    Files.getFileAttributeView(path, java.nio.file.attribute.BasicFileAttributeView.class)
                            .setTimes(before.lastModifiedTime(), null, before.creationTime());
                    var after = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class);
                    assertEquals(before.lastModifiedTime(), after.lastModifiedTime());
                    if (before.fileKey() == null) assertEquals(before.creationTime(), after.creationTime());
                    assertNotEquals(before.size(), after.size());
                    replacements.add(path);
                } });
        var b = access.acquire(f.inventory, f.discovery); var result = f.execute(access, b, UUID.randomUUID());
        assertEquals(CopyExecutionResult.Status.RECOVERY_REQUIRED, result.status()); code(result.failures(), CopyFailure.Code.CLEANUP_FAILED);
        assertEquals(1, replacements.size()); assertArrayEquals(new byte[] {8}, Files.readAllBytes(replacements.getFirst()));
        assertEquals(1, result.operations().stream().filter(o -> o.outcome() == CopyExecutionResult.Outcome.NOT_STARTED).count());
    }
    // T34: interruption is factual and preserved rather than silently retried.
    @Test void interruptedAcquisitionPreservesFlagAndProducesNoWrites() throws Exception {
        var f = new Fixture(temporary); Thread.currentThread().interrupt();
        try (var access = f.access()) {
            var b = access.acquire(f.inventory, f.discovery); assertEquals(SourceDigestBatch.Status.BLOCKED, b.status());
            code(b.failures(), CopyFailure.Code.INTERRUPTED); assertTrue(Thread.currentThread().isInterrupted()); assertTrue(f.outputs().isEmpty());
        } finally { Thread.interrupted(); }
    }

    // T28: short progress is legal; zero progress and actual disk/quota errors are terminal.
    @Test void shortReadsAndWritesPreserveExactBytesAndBoundedBuffer() throws Exception {
        var f = new Fixture(temporary); var maximum = new java.util.concurrent.atomic.AtomicInteger();
        var hooks = new NioVerifiedCopyAccess.Hooks() {
            public void at(NioVerifiedCopyAccess.Event event, Path path) {}
            public int read(NioVerifiedCopyAccess.Event event, java.nio.channels.SeekableByteChannel channel, java.nio.ByteBuffer buffer) throws IOException {
                maximum.accumulateAndGet(buffer.capacity(), Math::max); buffer.limit(Math.min(buffer.limit(), 37)); return channel.read(buffer);
            }
            public int write(java.nio.channels.FileChannel channel, java.nio.ByteBuffer buffer) throws IOException {
                int limit = buffer.limit(); buffer.limit(Math.min(limit, buffer.position() + 11));
                try { return channel.write(buffer); } finally { buffer.limit(limit); }
            }
        };
        var access = new NioVerifiedCopyAccess(f.source, f.output, f.namespace, new CopyLimits(100000, 1000000, 127, 20), INVENTORY_LIMITS, METADATA_LIMITS, detector(), hooks);
        var b = access.acquire(f.inventory, f.discovery); var plan = f.plan(b); var result = f.execute(access, b, UUID.randomUUID());
        assertEquals(CopyExecutionResult.Status.JOURNAL_COMPLETED, result.status()); assertEquals(127, maximum.get());
        assertArrayEquals(nifti(4096), Files.readAllBytes(f.output.resolve(plan.copies().getFirst().destination().path())));
    }
    @Test void zeroProgressAndDiskFullNeverPublishOrRetry() throws Exception {
        for (String mode : List.of("read", "write", "disk")) {
            var f = new Fixture(privateDirectory(temporary.resolve(mode))); var calls = new java.util.concurrent.atomic.AtomicInteger();
            var hooks = new NioVerifiedCopyAccess.Hooks() {
                public void at(NioVerifiedCopyAccess.Event event, Path path) {}
                public int read(NioVerifiedCopyAccess.Event event, java.nio.channels.SeekableByteChannel channel, java.nio.ByteBuffer buffer) throws IOException {
                    if (event == NioVerifiedCopyAccess.Event.COPY_READ && mode.equals("read")) { calls.incrementAndGet(); return 0; }
                    return channel.read(buffer);
                }
                public int write(java.nio.channels.FileChannel channel, java.nio.ByteBuffer buffer) throws IOException {
                    calls.incrementAndGet(); if (mode.equals("disk")) throw new IOException("quota-private-sentinel"); return 0;
                }
            };
            var access = new NioVerifiedCopyAccess(f.source, f.output, f.namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector(), hooks);
            var b = access.acquire(f.inventory, f.discovery); var target = f.output.resolve(f.plan(b).copies().getFirst().destination().path());
            var r = f.execute(access, b, UUID.randomUUID()); assertEquals(CopyExecutionResult.Status.FAILED, r.status());
            assertEquals(1, calls.get()); assertFalse(Files.exists(target)); code(r.failures(), mode.equals("read") ? CopyFailure.Code.READ_FAILED : CopyFailure.Code.WRITE_FAILED);
            assertTrue(r.journal().orElseThrow().jobFailures().stream().noneMatch(x -> x.phase() == org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Phase.PERSISTENCE));
        }
    }
    @Test void knownInterruptIsAcknowledgedFailedAndFlagRemainsSet() throws Exception {
        var f = new Fixture(temporary); var count = new java.util.concurrent.atomic.AtomicInteger();
        var access = new NioVerifiedCopyAccess(f.source, f.output, f.namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector(),
                (event, path) -> { if (event == NioVerifiedCopyAccess.Event.WRITE) { count.incrementAndGet(); Thread.currentThread().interrupt(); } });
        var b = access.acquire(f.inventory, f.discovery); var plan = f.plan(b);
        try {
            var r = f.execute(access, b, UUID.randomUUID()); assertEquals(CopyExecutionResult.Status.FAILED, r.status());
            code(r.failures(), CopyFailure.Code.INTERRUPTED); assertTrue(Thread.currentThread().isInterrupted()); assertEquals(1, count.get());
            assertEquals(org.cbihi.mrinormalizer.application.provenance.manifest.ManifestState.JobState.FAILED, r.journal().orElseThrow().state());
            assertFalse(Files.exists(f.output.resolve(plan.copies().getFirst().destination().path())));
        } finally { Thread.interrupted(); }
    }
    @Test void stageCloseFailureAndUnsupportedPublicationHaveNoFallback() throws Exception {
        for (var event : List.of(NioVerifiedCopyAccess.Event.STAGE_CLOSE, NioVerifiedCopyAccess.Event.BEFORE_LINK)) {
            var f = new Fixture(privateDirectory(temporary.resolve(event.name())));
            var access = new NioVerifiedCopyAccess(f.source, f.output, f.namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector(),
                    (e, p) -> { if (e == event) { if (e == NioVerifiedCopyAccess.Event.BEFORE_LINK) throw new UnsupportedOperationException("private-link-sentinel"); throw new IOException("private-close-sentinel"); } });
            var b = access.acquire(f.inventory, f.discovery); var target = f.output.resolve(f.plan(b).copies().getFirst().destination().path());
            var r = f.execute(access, b, UUID.randomUUID()); assertEquals(CopyExecutionResult.Status.FAILED, r.status()); assertFalse(Files.exists(target));
            code(r.failures(), event == NioVerifiedCopyAccess.Event.STAGE_CLOSE ? CopyFailure.Code.WRITE_FAILED : CopyFailure.Code.PUBLICATION_UNPROVEN);
        }
    }
    @Test void unsafeExistingParentsRemainUnrepaired() throws Exception {
        for (String mode : List.of("file", "broad")) {
            var f = new Fixture(privateDirectory(temporary.resolve(mode))); var access = f.access(); var b = access.acquire(f.inventory, f.discovery);
            if (mode.equals("file")) privateFile(f.output.resolve("layout-v1"), new byte[] {23});
            else {
                privateDirectory(f.output.resolve("layout-v1"));
                if (Files.getFileAttributeView(f.output, java.nio.file.attribute.PosixFileAttributeView.class) != null)
                    Files.setPosixFilePermissions(f.output.resolve("layout-v1"), PosixFilePermissions.fromString("rwxr-xr-x"));
                else {
                    var view = Files.getFileAttributeView(f.output.resolve("layout-v1"), java.nio.file.attribute.AclFileAttributeView.class);
                    var acl = new ArrayList<>(view.getAcl()); acl.add(java.nio.file.attribute.AclEntry.newBuilder().setType(java.nio.file.attribute.AclEntryType.ALLOW)
                        .setPrincipal(f.output.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("Everyone"))
                        .setPermissions(java.nio.file.attribute.AclEntryPermission.READ_DATA).build()); view.setAcl(acl);
                }
            }
            var before = Files.readAttributes(f.output.resolve("layout-v1"), java.nio.file.attribute.BasicFileAttributes.class);
            var r = f.execute(access, b, UUID.randomUUID()); assertEquals(CopyExecutionResult.Status.FAILED, r.status());
            var after = Files.readAttributes(f.output.resolve("layout-v1"), java.nio.file.attribute.BasicFileAttributes.class);
            assertEquals(before.fileKey(), after.fileKey()); assertEquals(before.isDirectory(), after.isDirectory());
            if (mode.equals("file")) assertArrayEquals(new byte[] {23}, Files.readAllBytes(f.output.resolve("layout-v1")));
        }
    }
    @Test void earlierVerifiedFinalSurvivesLaterForceFailure() throws Exception {
        var f = new Fixture(temporary); privateFile(f.source.resolve("second.nii"), nifti(8192)); f.observe(); var count = new java.util.concurrent.atomic.AtomicInteger();
        var access = new NioVerifiedCopyAccess(f.source, f.output, f.namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector(),
                (e, p) -> { if (e == NioVerifiedCopyAccess.Event.FORCE && count.incrementAndGet() == 2) throw new IOException("second-stage-sentinel"); });
        var b = access.acquire(f.inventory, f.discovery); var plan = f.plan(b); var r = f.execute(access, b, UUID.randomUUID());
        assertEquals(CopyExecutionResult.Status.FAILED, r.status()); assertEquals(CopyExecutionResult.Outcome.COMPLETED, r.operations().getFirst().outcome());
        assertArrayEquals(Files.readAllBytes(f.source.resolve(plan.copies().getFirst().source().path())), Files.readAllBytes(f.output.resolve(plan.copies().getFirst().destination().path())));
        assertFalse(Files.exists(f.output.resolve(plan.copies().getLast().destination().path())));
    }
    @Test void exactCumulativeReadBudgetChargesEveryVerificationRead() throws Exception {
        for (long bound : new long[] {6L * 4096 + 1, 6L * 4096}) {
            var f = new Fixture(privateDirectory(temporary.resolve(Long.toString(bound))));
            var access = f.access(new CopyLimits(4096, bound, 257, 20)); var b = access.acquire(f.inventory, f.discovery); var r = f.execute(access, b, UUID.randomUUID());
            if (bound == 6L * 4096 + 1) assertEquals(CopyExecutionResult.Status.JOURNAL_COMPLETED, r.status());
            else { assertNotEquals(CopyExecutionResult.Status.JOURNAL_COMPLETED, r.status()); code(r.failures(), CopyFailure.Code.RESOURCE_LIMIT); }
        }
    }


    @Test void realConcurrentCreatorWinsAndMatchingWinnerIsIndependentlyVerified() throws Exception {
        for (String winner : List.of("winner-identical", "winner-different")) {
            var base = privateDirectory(temporary.resolve(winner)); var f = new Fixture(base); var reads = new java.util.concurrent.atomic.AtomicLong();
            var access = new NioVerifiedCopyAccess(f.source, f.output, f.namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector(),
                    (e, p) -> { if (e == NioVerifiedCopyAccess.Event.BEFORE_LINK) try {
                        var ready = base.resolve("ready"); var process = VerifiedCopyProviderTest.child(winner, p, ready, base.resolve("unused"));
                        VerifiedCopyProviderTest.awaitFile(ready, process); assertTrue(process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)); assertEquals(0, process.exitValue());
                    } catch (Exception error) { throw new IOException(); } });
            var b = access.acquire(f.inventory, f.discovery); var p = f.plan(b); var result = f.execute(access, b, UUID.randomUUID());
            var target = f.output.resolve(p.copies().getFirst().destination().path());
            if (winner.equals("winner-identical")) { assertEquals(CopyExecutionResult.Status.JOURNAL_COMPLETED, result.status()); assertEquals(CopyExecutionResult.Outcome.IDENTICAL_EXISTING, result.operations().getFirst().outcome()); assertArrayEquals(nifti(4096), Files.readAllBytes(target)); }
            else { assertEquals(CopyExecutionResult.Status.FAILED, result.status()); code(result.failures(), CopyFailure.Code.OUTPUT_CONFLICT); assertArrayEquals(new byte[] {91}, Files.readAllBytes(target)); }
        }
    }


    @Test void privateStageAndParentsExistRestrictivelyBeforeFirstWrite() throws Exception {
        var f = new Fixture(temporary); var observed = new java.util.concurrent.atomic.AtomicBoolean();
        var access = new NioVerifiedCopyAccess(f.source, f.output, f.namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector(), (e, p) -> {
            if (e == NioVerifiedCopyAccess.Event.WRITE && observed.compareAndSet(false, true)) {
                assertTrue(Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)); assertEquals(Files.getFileStore(f.output), Files.getFileStore(p));
                if (Files.getFileAttributeView(p, java.nio.file.attribute.PosixFileAttributeView.class) != null) {
                    assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(p));
                    assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(p.getParent()));
                } else {
                    var owner = Files.getOwner(p); assertTrue(Files.getFileAttributeView(p, java.nio.file.attribute.AclFileAttributeView.class).getAcl().stream()
                        .filter(x -> x.type() == java.nio.file.attribute.AclEntryType.ALLOW).allMatch(x -> x.principal().equals(owner)));
                }
            }
        });
        var b = access.acquire(f.inventory, f.discovery); assertEquals(CopyExecutionResult.Status.JOURNAL_COMPLETED, f.execute(access, b, UUID.randomUUID()).status()); assertTrue(observed.get());
    }
    @Test void probeReplacementAndFailureExhaustionRetainUncertainArtifacts() throws Exception {
        var f = new Fixture(temporary); var replaced = new ArrayList<Path>();
        var access = new NioVerifiedCopyAccess(f.source, f.output, f.namespace, new CopyLimits(100000, 1000000, 128, 1), INVENTORY_LIMITS, METADATA_LIMITS, detector(), (e, p) -> {
            if (e == NioVerifiedCopyAccess.Event.PROBE_CLEANUP && replaced.isEmpty()) {
                Files.delete(p); try { privateFile(p, new byte[] {83}); } catch (Exception error) { throw new IOException(); } replaced.add(p);
            }
        });
        var b = access.acquire(f.inventory, f.discovery); var result = f.execute(access, b, UUID.randomUUID());
        assertEquals(CopyExecutionResult.Status.RECOVERY_REQUIRED, result.status()); code(result.failures(), CopyFailure.Code.CLEANUP_FAILED);
        code(result.failures(), CopyFailure.Code.RESOURCE_LIMIT); assertEquals(2, result.failures().size()); assertEquals(1, replaced.size());
        assertArrayEquals(new byte[] {83}, Files.readAllBytes(replaced.getFirst())); assertTrue(f.outputs().stream().anyMatch(p -> p.contains(".f6-probe-")));
    }


    @Test void competingStageNameIsPreservedWithoutTruncationOrCleanup() throws Exception {
        var f = new Fixture(temporary); var competitor = new ArrayList<Path>();
        var adapter = new NioVerifiedCopyAccess(f.source, f.output, f.namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector(), (e, p) -> {
            if (e == NioVerifiedCopyAccess.Event.BEFORE_STAGE) { try { privateFile(p, new byte[] {51}); } catch (Exception error) { throw new IOException(); } competitor.add(p); }
        });
        var b = adapter.acquire(f.inventory, f.discovery); var r = f.execute(adapter, b, UUID.randomUUID());
        assertEquals(CopyExecutionResult.Status.FAILED, r.status()); code(r.failures(), CopyFailure.Code.WRITE_FAILED);
        assertEquals(1, competitor.size()); assertArrayEquals(new byte[] {51}, Files.readAllBytes(competitor.getFirst()));
    }
    @Test void uncertainStageCloseRetainsArtifactAndStopsWithRecovery() throws Exception {
        var f = new Fixture(temporary); var stages = new ArrayList<Path>();
        var adapter = new NioVerifiedCopyAccess(f.source, f.output, f.namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector(), (e, p) -> {
            if (e == NioVerifiedCopyAccess.Event.AFTER_STAGE_CLOSE) { stages.add(p); throw new IOException("close-effects-unproved-sentinel"); }
        });
        var b = adapter.acquire(f.inventory, f.discovery); var plan = f.plan(b); var r = f.execute(adapter, b, UUID.randomUUID());
        assertEquals(CopyExecutionResult.Status.RECOVERY_REQUIRED, r.status()); code(r.failures(), CopyFailure.Code.CLEANUP_FAILED);
        assertEquals(1, stages.size()); assertTrue(Files.exists(stages.getFirst())); assertFalse(Files.exists(f.output.resolve(plan.copies().getFirst().destination().path())));
    }


    @Test void thrownCopyReadAndWriteErrorsRetainTheirActivityInRealJournal() throws Exception {
        for (boolean reading : new boolean[] {true, false}) {
            var f = new Fixture(privateDirectory(temporary.resolve(reading ? "read" : "write")));
            privateFile(f.source.resolve("second.nii"), nifti(8192)); f.observe();
            var hits = new java.util.concurrent.atomic.AtomicInteger();
            var hooks = new NioVerifiedCopyAccess.Hooks() {
                public void at(NioVerifiedCopyAccess.Event event, Path path) {}
                public int read(NioVerifiedCopyAccess.Event event, java.nio.channels.SeekableByteChannel channel, java.nio.ByteBuffer buffer) throws IOException {
                    if (reading && event == NioVerifiedCopyAccess.Event.COPY_READ) { hits.incrementAndGet(); throw new IOException("private-read-sentinel"); }
                    return channel.read(buffer);
                }
                public int write(java.nio.channels.FileChannel channel, java.nio.ByteBuffer buffer) throws IOException {
                    if (!reading) { hits.incrementAndGet(); throw new IOException("private-write-sentinel"); }
                    return channel.write(buffer);
                }
            };
            var access = new NioVerifiedCopyAccess(f.source, f.output, f.namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector(), hooks);
            var batch = access.acquire(f.inventory, f.discovery); var plan = f.plan(batch);
            var result = f.execute(access, batch, UUID.randomUUID());
            assertEquals(CopyExecutionResult.Status.FAILED, result.status()); assertEquals(1, hits.get());
            code(result.failures(), reading ? CopyFailure.Code.READ_FAILED : CopyFailure.Code.WRITE_FAILED);
            assertFalse(result.failures().stream().anyMatch(x -> x.code() == (reading ? CopyFailure.Code.WRITE_FAILED : CopyFailure.Code.READ_FAILED)));
            var journal = result.journal().orElseThrow();
            var expected = new org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure(
                    org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Phase.EXECUTION,
                    reading ? org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Code.READ_FAILED
                            : org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Code.WRITE_FAILED);
            assertTrue(journal.jobFailures().contains(expected)); assertTrue(journal.operations().getFirst().failures().contains(expected));
            assertEquals(CopyExecutionResult.Outcome.FAILED, result.operations().getFirst().outcome());
            assertEquals(CopyExecutionResult.Outcome.NOT_STARTED, result.operations().getLast().outcome());
            for (var operation : plan.copies()) assertFalse(Files.exists(f.output.resolve(operation.destination().path())));
            assertTrue(f.outputs().stream().noneMatch(x -> x.contains(".f6-stage-")));
        }
    }

    @Test void qualificationPrimaryAndCleanupFailuresAreBothRetainedWithinBounds() throws Exception {
        for (int bound : new int[] {20, 1}) {
            var f = new Fixture(privateDirectory(temporary.resolve("bound-" + bound)));
            privateFile(f.source.resolve("second.nii"), nifti(8192)); f.observe();
            var cleanup = new ArrayList<Path>(); var corrupted = new java.util.concurrent.atomic.AtomicBoolean();
            var hooks = new NioVerifiedCopyAccess.Hooks() {
                public void at(NioVerifiedCopyAccess.Event event, Path path) throws IOException {
                    if (event == NioVerifiedCopyAccess.Event.PROBE_CLEANUP) { cleanup.add(path); throw new IOException("cleanup-private-sentinel"); }
                }
                public int read(NioVerifiedCopyAccess.Event event, java.nio.channels.SeekableByteChannel channel, java.nio.ByteBuffer buffer) throws IOException {
                    int start = buffer.position(); int n = channel.read(buffer);
                    if (event == null && n == 1 && corrupted.compareAndSet(false, true)) buffer.put(start, (byte)38);
                    return n;
                }
            };
            var limits = new CopyLimits(LIMITS.maxFileBytes(), LIMITS.maxReadBytes(), LIMITS.bufferBytes(), bound);
            var access = new NioVerifiedCopyAccess(f.source, f.output, f.namespace, limits, INVENTORY_LIMITS, METADATA_LIMITS, detector(), hooks);
            var b = access.acquire(f.inventory, f.discovery); var plan = f.plan(b); var result = f.execute(access, b, UUID.randomUUID());
            assertTrue(corrupted.get()); assertEquals(2, cleanup.size()); assertEquals(2, new HashSet<>(cleanup).size());
            assertEquals(CopyExecutionResult.Status.RECOVERY_REQUIRED, result.status());
            code(result.failures(), CopyFailure.Code.PROVIDER_UNQUALIFIED);
            if (bound == 20) { code(result.failures(), CopyFailure.Code.CLEANUP_FAILED); code(result.failures(), CopyFailure.Code.RECOVERY_REQUIRED); }
            else { assertEquals(2, result.failures().size()); code(result.failures(), CopyFailure.Code.RESOURCE_LIMIT); }
            for (var path : cleanup) assertTrue(Files.exists(path));
            for (var operation : plan.copies()) assertFalse(Files.exists(f.output.resolve(operation.destination().path())));
            assertEquals(CopyExecutionResult.Outcome.RECOVERY_REQUIRED, result.operations().getFirst().outcome());
            assertEquals(CopyExecutionResult.Outcome.NOT_STARTED, result.operations().getLast().outcome());
        }
    }

}
