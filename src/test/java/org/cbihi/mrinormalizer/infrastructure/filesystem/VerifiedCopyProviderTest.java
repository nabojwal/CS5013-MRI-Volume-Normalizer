package org.cbihi.mrinormalizer.infrastructure.filesystem;

import static org.junit.jupiter.api.Assertions.*;
import static org.cbihi.mrinormalizer.infrastructure.filesystem.SourceDigestAcquisitionTest.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.cbihi.mrinormalizer.application.dataset.copy.*;
import org.cbihi.mrinormalizer.application.service.CopyExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The running native profile is measured; this class never labels an unavailable OS qualified. */
public class VerifiedCopyProviderTest {
    @TempDir Path temporary;
    // T50: mutate only synthetic, newly created lease fixtures; never repair them to obtain success.
    @Test void outputLeaseWithBroadNativeAccessIsRejectedAndPreserved() throws Exception {
        var f = new Fixture(temporary); var path = f.output.resolve(".f6-copy.lease"); privateFile(path, new byte[] {67});
        broadenLeaseAccess(path); var before = leaseAccess(path); var owner = Files.getOwner(path);
        var access = f.access(); var batch = access.acquire(f.inventory, f.discovery);
        var result = f.execute(access, batch, UUID.randomUUID());
        assertEquals(CopyExecutionResult.Status.FAILED, result.status()); code(result.failures(), CopyFailure.Code.ACCESS_CONTROL_UNAVAILABLE);
        assertEquals(before, leaseAccess(path)); assertEquals(owner, Files.getOwner(path)); assertArrayEquals(new byte[] {67}, Files.readAllBytes(path));
        assertFalse(Files.exists(f.output.resolve("layout-v1"))); assertLeaseFailure(result, CopyFailure.Code.ACCESS_CONTROL_UNAVAILABLE);
        System.out.println("F6 T50 native existing-lease-access-anomaly=REFUSED preserved=PASS");
    }
    @Test void outputLeaseNativeAccessChangeRetainsStageAndStopsNextOperation() throws Exception {
        var f = new Fixture(temporary); privateFile(f.source.resolve("second.nii"), nifti(8192)); f.observe();
        var path = f.output.resolve(".f6-copy.lease"); var reached = new java.util.concurrent.atomic.AtomicBoolean();
        var access = new NioVerifiedCopyAccess(f.source, f.output, f.namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector(), (event, ignored) -> {
            if (event == NioVerifiedCopyAccess.Event.STAGE_REOPEN && reached.compareAndSet(false, true)) broadenLeaseAccess(path);
        });
        var batch = access.acquire(f.inventory, f.discovery); var plan = f.plan(batch);
        var job = UUID.randomUUID(); var service = new CopyExecutionService(access, f.store(job), Clock.fixed(TIME, ZoneOffset.UTC));
        CopyExecutionResult result;
        try { result = service.execute(plan, batch, job); }
        finally { code(assertThrows(org.cbihi.mrinormalizer.application.port.out.VerifiedCopyAccess.AccessFailure.class, service::close).failures(), CopyFailure.Code.CLEANUP_FAILED); }
        assertTrue(reached.get()); assertEquals(CopyExecutionResult.Status.RECOVERY_REQUIRED, result.status());
        code(result.failures(), CopyFailure.Code.ACCESS_CONTROL_UNAVAILABLE); code(result.failures(), CopyFailure.Code.CLEANUP_FAILED);
        assertEquals(1, result.operations().stream().filter(x -> x.outcome() == CopyExecutionResult.Outcome.NOT_STARTED).count());
        assertEquals(1, f.outputs().stream().filter(x -> x.contains(".f6-stage-")).count());
        for (var operation : plan.copies()) assertFalse(Files.exists(f.output.resolve(operation.destination().path())));
        assertTrue(Files.exists(path)); assertTrue(leaseAccessIsBroad(path)); assertLeaseFailure(result, CopyFailure.Code.ACCESS_CONTROL_UNAVAILABLE);
        System.out.println("F6 T50 native live-lease-access-anomaly=REFUSED uncertain-stage=RETAINED");
    }
    @Test void injectedOutputLeaseOwnerAnomaliesExerciseAdapterRefusalWithoutChangingNativeOwner() throws Exception {
        for (String moment : List.of("existing", "open", "stage")) {
            var f = new Fixture(privateDirectory(temporary.resolve(moment))); var path = f.output.resolve(".f6-copy.lease"); privateFile(path, new byte[0]);
            var nativeOwner = Files.getOwner(path); var nativeAccess = leaseAccess(path);
            var injected = new java.util.concurrent.atomic.AtomicBoolean(moment.equals("existing"));
            var observed = new java.util.concurrent.atomic.AtomicInteger(); UserPrincipal foreign = () -> "injected-f6-foreign-owner";
            var hooks = new NioVerifiedCopyAccess.Hooks() {
                public void at(NioVerifiedCopyAccess.Event event, Path ignored) {
                    if (moment.equals("open") && event == NioVerifiedCopyAccess.Event.LEASE_OPEN
                            || moment.equals("stage") && event == NioVerifiedCopyAccess.Event.STAGE_REOPEN) injected.set(true);
                }
                public UserPrincipal owner(Path current) throws java.io.IOException {
                    if (current.equals(path) && injected.get()) { observed.incrementAndGet(); return foreign; }
                    return Files.getOwner(current, LinkOption.NOFOLLOW_LINKS);
                }
            };
            var access = new NioVerifiedCopyAccess(f.source, f.output, f.namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector(), hooks);
            var batch = access.acquire(f.inventory, f.discovery); var plan = f.plan(batch); var job = UUID.randomUUID();
            var service = new CopyExecutionService(access, f.store(job), Clock.fixed(TIME, ZoneOffset.UTC)); CopyExecutionResult result;
            try { result = service.execute(plan, batch, job); }
            finally {
                if (moment.equals("stage")) code(assertThrows(org.cbihi.mrinormalizer.application.port.out.VerifiedCopyAccess.AccessFailure.class, service::close).failures(), CopyFailure.Code.CLEANUP_FAILED);
                else service.close();
            }
            assertTrue(observed.get() > 0); assertEquals(moment.equals("stage") ? CopyExecutionResult.Status.RECOVERY_REQUIRED : CopyExecutionResult.Status.FAILED, result.status());
            code(result.failures(), CopyFailure.Code.ACCESS_CONTROL_UNAVAILABLE); assertLeaseFailure(result, CopyFailure.Code.ACCESS_CONTROL_UNAVAILABLE);
            assertEquals(nativeOwner, Files.getOwner(path)); assertEquals(nativeAccess, leaseAccess(path)); assertEquals(0, Files.size(path));
            for (var operation : plan.copies()) assertFalse(Files.exists(f.output.resolve(operation.destination().path())));
            if (moment.equals("stage")) { code(result.failures(), CopyFailure.Code.CLEANUP_FAILED); assertTrue(f.outputs().stream().anyMatch(x -> x.contains(".f6-stage-"))); }
            else assertFalse(Files.exists(f.output.resolve("layout-v1")));
            assertFalse(result.toString().contains("injected-f6-foreign-owner"));
            System.out.println("F6 T50 injected lease-owner-observation moment=" + moment + " adapter-refusal=PASS native-owner-unchanged=PASS native-owner-change=NOT RUN");
        }
    }
    @Test void injectedUnavailableRequiredOpenOptionsRefuseBeforeImagingMutation() throws Exception {
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        List<OpenOption> required = windows ? List.of(com.sun.nio.file.ExtendedOpenOption.NOSHARE_WRITE, com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE)
                : List.of(LinkOption.NOFOLLOW_LINKS);
        for (var option : required) for (boolean existing : List.of(false, true)) {
            var f = new Fixture(privateDirectory(temporary.resolve(option + "-" + existing))); var lease = f.output.resolve(".f6-copy.lease");
            if (existing) privateFile(lease, new byte[] {71});
            var originalAccess = existing ? leaseAccess(lease) : null;
            var observed = new java.util.concurrent.atomic.AtomicInteger();
            var hooks = new NioVerifiedCopyAccess.Hooks() {
                public void at(NioVerifiedCopyAccess.Event event, Path path) {}
                public FileChannel openLease(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attributes) throws java.io.IOException {
                    if (path.equals(lease) && !(existing && options.contains(StandardOpenOption.CREATE_NEW))) {
                        assertTrue(options.contains(option));
                        assertEquals(windows, options.contains(com.sun.nio.file.ExtendedOpenOption.NOSHARE_WRITE));
                        assertEquals(windows, options.contains(com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE));
                        observed.incrementAndGet(); throw new UnsupportedOperationException("injected-required-open-option-sentinel");
                    }
                    return FileChannel.open(path, options, attributes);
                }
            };
            var access = new NioVerifiedCopyAccess(f.source, f.output, f.namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector(), hooks);
            var batch = access.acquire(f.inventory, f.discovery); var result = f.execute(access, batch, UUID.randomUUID());
            assertEquals(1, observed.get()); assertEquals(CopyExecutionResult.Status.FAILED, result.status()); code(result.failures(), CopyFailure.Code.PROVIDER_UNQUALIFIED);
            assertLeaseFailure(result, CopyFailure.Code.PROVIDER_UNQUALIFIED); assertFalse(Files.exists(f.output.resolve("layout-v1")));
            if (existing) { assertArrayEquals(new byte[] {71}, Files.readAllBytes(lease)); assertEquals(originalAccess, leaseAccess(lease)); }
            else assertFalse(Files.exists(lease));
            assertFalse(result.toString().contains("sentinel"));
            System.out.println("F6 T48 injected unavailable-option=" + option + " existing-lease=" + existing + " refused=PASS native-unavailable-sharing-options=NOT RUN Windows-sharing-option-injection=" + windows);
        }
    }
    static void assertLeaseFailure(CopyExecutionResult result, CopyFailure.Code code) {
        var durable = new org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure(
                org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Phase.EXECUTION,
                code == CopyFailure.Code.PROVIDER_UNQUALIFIED ? org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Code.PUBLICATION_UNAVAILABLE
                        : org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Code.ACCESS_CONTROL_UNAVAILABLE);
        assertTrue(result.journal().orElseThrow().jobFailures().contains(durable));
        assertTrue(result.journal().orElseThrow().operations().getFirst().failures().contains(durable));
        assertTrue(result.journal().orElseThrow().jobFailures().stream().allMatch(x -> x.phase() == org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Phase.EXECUTION));
    }
    static Object leaseAccess(Path path) throws java.io.IOException {
        return Files.getFileAttributeView(path, PosixFileAttributeView.class) != null ? Files.getPosixFilePermissions(path)
                : List.copyOf(Files.getFileAttributeView(path, AclFileAttributeView.class).getAcl());
    }
    static void broadenLeaseAccess(Path path) throws java.io.IOException {
        if (Files.getFileAttributeView(path, PosixFileAttributeView.class) != null) Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-r--r--"));
        else {
            var view = Files.getFileAttributeView(path, AclFileAttributeView.class); var acl = new ArrayList<>(view.getAcl());
            acl.add(AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                    .setPrincipal(path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("Everyone"))
                    .setPermissions(AclEntryPermission.READ_DATA).build()); view.setAcl(acl);
        }
    }
    static boolean leaseAccessIsBroad(Path path) throws java.io.IOException {
        if (Files.getFileAttributeView(path, PosixFileAttributeView.class) != null) return Files.getPosixFilePermissions(path).contains(PosixFilePermission.OTHERS_READ);
        var owner = Files.getOwner(path);
        return Files.getFileAttributeView(path, AclFileAttributeView.class).getAcl().stream().anyMatch(x -> x.type() == AclEntryType.ALLOW && !x.principal().equals(owner));
    }
    // T47/T48/T52: actual key/provider/store/private creation/no-replace qualification.
    @Test void nativeProfileUsesActualOwnershipAndHardLinkEvidence() throws Exception {
        var f = new Fixture(temporary); var access = f.access(); var b = access.acquire(f.inventory, f.discovery); var p = f.plan(b);
        var result = f.execute(access, b, UUID.randomUUID()); assertEquals(CopyExecutionResult.Status.JOURNAL_COMPLETED, result.status());
        var target = f.output.resolve(p.copies().getFirst().destination().path()); var attrs = Files.readAttributes(target, BasicFileAttributes.class);
        String provider = target.getFileSystem().provider().getClass().getName(); String type = Files.getFileStore(target).type();
        if (provider.equals("sun.nio.fs.LinuxFileSystemProvider")) {
            assertNotNull(attrs.fileKey()); assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(target));
            assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(target.getParent()));
        } else {
            assertEquals("sun.nio.fs.WindowsFileSystemProvider", provider); assertEquals("NTFS", type.toUpperCase(Locale.ROOT));
            assertNull(attrs.fileKey()); assertNotNull(Files.getFileAttributeView(target, AclFileAttributeView.class));
            assertEquals(System.getenv("SystemDrive") + "\\", target.getRoot().toString());
            assertNull(Files.readAttributes(f.output, BasicFileAttributes.class).fileKey());
            var probe = temporary.resolve("native-null-key"); privateFile(probe, new byte[] {17}); var link = temporary.resolve("native-null-key-link"); Files.createLink(link, probe);
            assertNull(Files.readAttributes(probe, BasicFileAttributes.class).fileKey()); assertNull(Files.readAttributes(link, BasicFileAttributes.class).fileKey());
            assertTrue(Files.isSameFile(probe, link)); assertNotEquals(probe, link);
        }
        assertFalse(Files.isSameFile(f.source.resolve("synthetic.nii"), target));
        System.out.println("F6 native os=" + System.getProperty("os.name") + " version=" + System.getProperty("os.version") + " provider=" + provider + " store=" + type + " primitive-checks=PASS full-dual-platform-qualification=PENDING other-platform=NOT RUN power-loss/directory-durability=UNPROVED");
    }
    // T49: prepared handles retain SOURCE; Linux cooperative profile detects changed bytes, Windows must deny writers.
    @Test void preparedSourceHasNativeContinuityAndRejectsChangedBytes() throws Exception {
        var f = new Fixture(temporary);
        try (var access = f.access()) {
            var b = access.acquire(f.inventory, f.discovery); var p = f.plan(b); assertTrue(access.revalidate(p, b).isEmpty());
            try (var prepared = access.prepare(p.copies().getFirst())) {
                assertEquals(p.copies().getFirst().expectedDigest(), prepared.verifiedSourceDigest());
                if (System.getProperty("os.name").startsWith("Windows")) {
                    assertThrows(java.io.IOException.class, () -> Files.write(f.source.resolve("synthetic.nii"), new byte[] {8}));
                    assertThrows(java.io.IOException.class, () -> Files.delete(f.source.resolve("synthetic.nii")));
                    assertEquals(org.cbihi.mrinormalizer.domain.model.DetectionOutcome.NIFTI, detector().detect(
                        new org.cbihi.mrinormalizer.domain.model.InputSource(f.source.resolve("synthetic.nii").toString())).outcome());
                }
                else { var bytes = nifti(4096); bytes[400] ^= 1; Files.write(f.source.resolve("synthetic.nii"), bytes); }
            }
            assertTrue(f.outputs().isEmpty());
        }
    }
    // T50: a real second JVM contends on the fixed persistent lease; no shared-JVM shortcut.
    @Test void secondProcessOutputLeasePreventsCopyAndLeaseIsNeverUnlinked() throws Exception {
        var f = new Fixture(temporary); var lease = f.output.resolve(".f6-copy.lease"); privateFile(lease, new byte[0]);
        var ready = temporary.resolve("ready"); var release = temporary.resolve("release");
        var process = child("lease", lease, ready, release);
        try {
            awaitFile(ready, process); var access = f.access(); var b = access.acquire(f.inventory, f.discovery);
            var result = f.execute(access, b, UUID.randomUUID()); assertEquals(CopyExecutionResult.Status.FAILED, result.status());
            code(result.failures(), CopyFailure.Code.OUTPUT_CONFLICT); assertFalse(Files.exists(f.output.resolve("layout-v1"))); assertTrue(Files.exists(lease));
        } finally { Files.write(release, new byte[0]); assertTrue(process.waitFor(20, TimeUnit.SECONDS)); }
        assertEquals(0, process.exitValue()); assertTrue(Files.exists(lease));
    }
    public static Process child(String action, Path first, Path second, Path third) throws Exception {
        return new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp",
                System.getProperty("java.class.path"), VerifiedCopyProviderTest.class.getName(), action,
                first.toString(), second.toString(), third.toString()).redirectErrorStream(true).redirectOutput(second.resolveSibling(second.getFileName().toString() + ".child.log").toFile()).start();
    }
    public static void awaitFile(Path ready, Process process) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!Files.exists(ready) && process.isAlive() && System.nanoTime() < end) Thread.sleep(10);
        assertTrue(Files.exists(ready), "Second-process barrier was not established");
    }
    public static void main(String[] args) throws Exception {
        if (args[0].equals("ordinary")) { runOrdinaryChecks(); } else if (args[0].equals("job")) {
            var base = Path.of(args[1]); var source = base.resolve("source"); var output = base.resolve("output"); var namespace = output.resolve("journal");
            var access = new NioVerifiedCopyAccess(source, output, namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector(),
                    (e, p) -> { if (e == NioVerifiedCopyAccess.Event.STAGE_REOPEN) {
                        Files.write(Path.of(args[2]), new byte[0]); long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
                        while (!Files.exists(Path.of(args[3])) && System.nanoTime() < end) try { Thread.sleep(10); } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new java.io.IOException(); }
                        if (!Files.exists(Path.of(args[3]))) throw new java.io.IOException("Process barrier expired");
                    }});
            var inventory = new NioDatasetScanner(source, output, INVENTORY_LIMITS, detector()).scan();
            var discovery = new org.cbihi.mrinormalizer.application.dataset.dicom.DicomSeriesDiscovery(List.of(), List.of());
            var b = access.acquire(inventory, discovery); var plan = new org.cbihi.mrinormalizer.application.service.OrganizationPlanningService().plan(inventory, discovery, b.suppliedDigests());
            var job = UUID.fromString("00000000-0000-0000-0000-000000000066");
            try (var owner = new CopyExecutionService(access, new JsonManifestStore(source, output, namespace, job), Clock.fixed(TIME, ZoneOffset.UTC))) {
                assertEquals(CopyExecutionResult.Status.JOURNAL_COMPLETED, owner.execute(plan, b, job).status());
            }
        } else if (args[0].startsWith("winner")) {
            privateFile(Path.of(args[1]), args[0].equals("winner-identical") ? nifti(4096) : new byte[] {91}); Files.write(Path.of(args[2]), new byte[0]);
        } else if (args[0].equals("lease")) {
            try (var channel = FileChannel.open(Path.of(args[1]), StandardOpenOption.READ, StandardOpenOption.WRITE); var lock = channel.lock()) {
                Files.write(Path.of(args[2]), new byte[0]);
                while (!Files.exists(Path.of(args[3]))) Thread.sleep(10);
            }
        } else throw new IllegalArgumentException("Invalid test process action");
    }

    @Test void twoActualJobProcessesObserveF1ThenPersistentOutputLease() throws Exception {
        var f = new Fixture(temporary); var ready = temporary.resolve("job-ready"); var release = temporary.resolve("job-release");
        var process = child("job", temporary, ready, release);
        try {
            awaitFile(ready, process); assertTrue(Files.isRegularFile(f.output.resolve(".f6-copy.lease")));
            var access = f.access(); var b = access.acquire(f.inventory, f.discovery); var r = f.execute(access, b, UUID.randomUUID());
            assertEquals(CopyExecutionResult.Status.FAILED, r.status()); code(r.failures(), CopyFailure.Code.OUTPUT_CONFLICT);
            assertEquals(org.cbihi.mrinormalizer.application.provenance.manifest.ManifestState.JobState.FAILED, r.journal().orElseThrow().state());
            assertTrue(f.outputs().stream().anyMatch(p -> p.contains(".f6-stage-")));
        } finally { Files.write(release, new byte[0]); assertTrue(process.waitFor(20, TimeUnit.SECONDS)); }
        assertEquals(0, process.exitValue());
        try (var old = f.store(UUID.fromString("00000000-0000-0000-0000-000000000066"))) {
            assertEquals(org.cbihi.mrinormalizer.application.provenance.manifest.ManifestState.JobState.COMPLETED, old.replay(Optional.empty()).orElseThrow().state());
        }
        assertTrue(Files.isRegularFile(f.output.resolve(".f6-copy.lease")));
    }
    @Test void leaseReplacementRetainsStageAndPreventsCompletion() throws Exception {
        var changed = new java.util.concurrent.atomic.AtomicBoolean(); var sharingDenied = new java.util.concurrent.atomic.AtomicBoolean();
        var fresh = new Fixture(privateDirectory(temporary.resolve("actual"))); changed.set(false);
        var job = UUID.randomUUID(); var actual = new NioVerifiedCopyAccess(fresh.source, fresh.output, fresh.namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector(),
            (e, p) -> { if (e == NioVerifiedCopyAccess.Event.STAGE_REOPEN && changed.compareAndSet(false, true)) {
                var lease = fresh.output.resolve(".f6-copy.lease");
                try { Files.move(lease, temporary.resolve("actual-held-lease")); }
                catch (FileSystemException denied) {
                    assertTrue(System.getProperty("os.name").startsWith("Windows"));
                    assertTrue(Files.isRegularFile(lease)); assertFalse(Files.exists(temporary.resolve("actual-held-lease")));
                    assertEquals(0, Files.size(lease));
                    System.out.println("F6 lease rename denied=" + denied.getClass().getSimpleName() + " original-retained=PASS");
                    sharingDenied.set(true); return;
                }
                try { privateFile(lease, new byte[] {44}); } catch (Exception error) { throw new java.io.IOException(); }
            }});
        var evidence = actual.acquire(fresh.inventory, fresh.discovery); var owner = new CopyExecutionService(actual, fresh.store(job), Clock.fixed(TIME, ZoneOffset.UTC));
        var result = owner.execute(fresh.plan(evidence), evidence, job);
        if (sharingDenied.get()) {
            assertEquals(CopyExecutionResult.Status.JOURNAL_COMPLETED, result.status()); assertTrue(Files.exists(fresh.output.resolve(".f6-copy.lease"))); owner.close(); return;
        }
        assertEquals(CopyExecutionResult.Status.RECOVERY_REQUIRED, result.status());
        code(result.failures(), CopyFailure.Code.CLEANUP_FAILED); assertTrue(fresh.outputs().stream().anyMatch(p -> p.contains(".f6-stage-")));
        assertThrows(org.cbihi.mrinormalizer.application.port.out.VerifiedCopyAccess.AccessFailure.class, owner::close);
        assertArrayEquals(new byte[] {44}, Files.readAllBytes(fresh.output.resolve(".f6-copy.lease")));
    }
    @Test void outputLeaseSymlinkAndReleaseFailureAreNeverSuccess() throws Exception {
        var f = new Fixture(temporary); privateFile(temporary.resolve("external"), new byte[] {18});
        Files.createSymbolicLink(f.output.resolve(".f6-copy.lease"), temporary.resolve("external"));
        var access = f.access(); var b = access.acquire(f.inventory, f.discovery); var r = f.execute(access, b, UUID.randomUUID());
        assertEquals(CopyExecutionResult.Status.FAILED, r.status()); assertTrue(Files.isSymbolicLink(f.output.resolve(".f6-copy.lease")));
        assertArrayEquals(new byte[] {18}, Files.readAllBytes(temporary.resolve("external")));
        var next = new Fixture(privateDirectory(temporary.resolve("release-case"))); var job = UUID.randomUUID();
        var adapter = new NioVerifiedCopyAccess(next.source, next.output, next.namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector(),
                (e, p) -> { if (e == NioVerifiedCopyAccess.Event.LEASE_RELEASE) throw new java.io.IOException("release-private-sentinel"); });
        var evidence = adapter.acquire(next.inventory, next.discovery);
        try (var owner = new CopyExecutionService(adapter, next.store(job), Clock.fixed(TIME, ZoneOffset.UTC))) {
            var failed = owner.execute(next.plan(evidence), evidence, job); assertEquals(CopyExecutionResult.Status.RECOVERY_REQUIRED, failed.status());
            assertNotEquals(org.cbihi.mrinormalizer.application.provenance.manifest.ManifestState.JobState.COMPLETED, failed.journal().orElseThrow().state());
            code(failed.failures(), CopyFailure.Code.CLEANUP_FAILED);
        }
    }
    @Test void unsupportedZipProviderIsRefusedAndCrossStoreSourceStreamsWhereNative() throws Exception {
        var f = new Fixture(temporary); var zip = temporary.resolve("fixture.zip");
        try (var fs = FileSystems.newFileSystem(java.net.URI.create("jar:" + zip.toUri()), Map.of("create", "true"))) {
            Files.createDirectory(fs.getPath("/source"));
            assertThrows(org.cbihi.mrinormalizer.application.port.out.VerifiedCopyAccess.AccessFailure.class,
                    () -> new NioVerifiedCopyAccess(fs.getPath("/source"), f.output, f.namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector()));
            assertTrue(f.outputs().isEmpty());
        }
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        Path crossStoreRoot = Path.of("/dev/shm");
        if (windows) {
            String configured = System.getProperty("f6.test.windowsCrossStoreRoot");
            assertNotNull(configured, "T51 requires a provisioned local NTFS mount under SystemDrive; no silent NOT RUN success");
            crossStoreRoot = Path.of(configured).toRealPath();
            assertEquals(System.getenv("SystemDrive") + "\\", crossStoreRoot.getRoot().toString());
            assertEquals("NTFS", Files.getFileStore(crossStoreRoot).type().toUpperCase(Locale.ROOT));
            for (String property : List.of("f6.test.windowsUncRoot", "f6.test.windowsMappedRoot")) {
                String value = System.getProperty(property);
                assertNotNull(value, "T51 requires accessible UNC and mapped-drive negative fixtures");
                Path rejected = Path.of(value); assertTrue(Files.isDirectory(rejected), "Native negative fixture must actually exist");
                if (property.endsWith("UncRoot")) assertTrue(value.startsWith("\\\\"));
                else {
                    assertNotNull(rejected.getRoot());
                    assertNotEquals((System.getenv("SystemDrive") + "\\").toLowerCase(Locale.ROOT), rejected.getRoot().toString().toLowerCase(Locale.ROOT));
                    var query = new ProcessBuilder("cmd", "/c", "net", "use", rejected.getRoot().toString().substring(0, 2)).redirectErrorStream(true).start();
                    assertTrue(query.waitFor(10, TimeUnit.SECONDS)); assertEquals(0, query.exitValue(), "Fixture must be an actual mapped network drive");
                }
                var refused = assertThrows(org.cbihi.mrinormalizer.application.port.out.VerifiedCopyAccess.AccessFailure.class,
                        () -> new NioVerifiedCopyAccess(rejected, f.output, f.namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector()));
                code(refused.failures(), CopyFailure.Code.PROVIDER_UNQUALIFIED); assertTrue(f.outputs().isEmpty());
            }
        }
        {
            var source = Files.createTempDirectory(crossStoreRoot, "f6-cross-store-");
            try {
                privateFile(source.resolve("synthetic.nii"), nifti(4096)); assertNotEquals(Files.getFileStore(source), Files.getFileStore(f.output));
                var inventory = new NioDatasetScanner(source, f.output, INVENTORY_LIMITS, detector()).scan();
                var discovery = new org.cbihi.mrinormalizer.application.dataset.dicom.DicomSeriesDiscovery(List.of(), List.of());
                var adapter = new NioVerifiedCopyAccess(source, f.output, f.namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector());
                var evidence = adapter.acquire(inventory, discovery); var plan = new org.cbihi.mrinormalizer.application.service.OrganizationPlanningService().plan(inventory, discovery, evidence.suppliedDigests());
                var job = UUID.randomUUID();
                try (var owner = new CopyExecutionService(adapter, new JsonManifestStore(source, f.output, f.namespace, job), Clock.fixed(TIME, ZoneOffset.UTC))) {
                    var result = owner.execute(plan, evidence, job); assertEquals(CopyExecutionResult.Status.JOURNAL_COMPLETED, result.status());
                    assertArrayEquals(nifti(4096), Files.readAllBytes(f.output.resolve(plan.copies().getFirst().destination().path())));
                }
                if (windows) {
                    // Actual destination ancestry crossing into another store through a junction must be refused.
                    var other = new Fixture(privateDirectory(temporary.resolve("cross-store-destination")));
                    Path junction = other.output.resolve("layout-v1");
                    var process = new ProcessBuilder("cmd", "/c", "mklink", "/J", junction.toString(), source.toString()).redirectErrorStream(true).start();
                    assertTrue(process.waitFor(10, TimeUnit.SECONDS)); assertEquals(0, process.exitValue(), "Native junction fixture creation failed");
                    try {
                        assertNotEquals(Files.getFileStore(other.output), Files.getFileStore(junction));
                        var blocked = other.access(); var batch = blocked.acquire(other.inventory, other.discovery);
                        var result = other.execute(blocked, batch, UUID.randomUUID());
                        assertEquals(CopyExecutionResult.Status.FAILED, result.status());
                        code(result.failures(), CopyFailure.Code.CONTAINMENT_UNPROVEN);
                        assertArrayEquals(nifti(4096), Files.readAllBytes(source.resolve("synthetic.nii")));
                        try (var children = Files.list(source)) { assertEquals(1, children.count()); }
                    } finally { Files.delete(junction); }
                }
                System.out.println("F6 T51 native cross-store=" + System.getProperty("os.name")
                        + " source=" + Files.getFileStore(source).type() + " output=" + Files.getFileStore(f.output).type()
                        + " distinct-stores=PASS exact-copy=PASS unsupported-native-fixtures=" + (windows ? "PASS" : "Windows NOT RUN"));
            } finally { Files.deleteIfExists(source.resolve("synthetic.nii")); Files.delete(source); }
        }
    }
    // Required F1 uncertainty evidence uses real publisher hooks, not a mock receipt.
    @Test void actualJournalPublicationFailuresKeepOriginalTypedEvidenceAndStopCopy() throws Exception {
        for (String certainty : List.of("NOT_PUBLISHED", "PUBLISHED", "UNKNOWN")) {
            var f = new Fixture(privateDirectory(temporary.resolve(certainty))); var job = UUID.randomUUID(); var access = f.access(); var b = access.acquire(f.inventory, f.discovery);
            var armed = new java.util.concurrent.atomic.AtomicBoolean();
            var store = new JsonManifestStore(f.source, f.output, f.namespace, job, (e, p) -> {
                boolean intent = p.getFileName().toString().equals("00000000000000000002.json");
                if (intent && e == JsonManifestStore.Event.BEFORE_PUBLISH) armed.set(true);
                if (armed.get() && (certainty.equals("NOT_PUBLISHED") && e == JsonManifestStore.Event.BEFORE_PUBLISH
                        || !certainty.equals("NOT_PUBLISHED") && e == JsonManifestStore.Event.AFTER_PUBLISH
                        || certainty.equals("UNKNOWN") && e == JsonManifestStore.Event.CLASSIFY)) throw new java.io.IOException("journal-private-sentinel");
            }, JsonManifestStore.Limits.DEFAULT);
            try (var owner = new CopyExecutionService(access, store, Clock.fixed(TIME, ZoneOffset.UTC))) {
                var result = owner.execute(f.plan(b), b, job); assertEquals(CopyExecutionResult.Status.RECOVERY_REQUIRED, result.status());
                assertFalse(Files.exists(f.output.resolve("layout-v1"))); assertFalse(Files.exists(f.output.resolve(".f6-copy.lease")));
                var field = CopyExecutionService.class.getDeclaredField("persistenceFailures"); field.setAccessible(true);
                var preserved = (List<?>)field.get(owner); assertEquals(1, preserved.size());
                var error = (org.cbihi.mrinormalizer.application.provenance.manifest.ProvenancePersistenceException)preserved.getFirst();
                assertEquals(org.cbihi.mrinormalizer.application.provenance.manifest.ProvenancePersistenceException.PublicationOutcome.valueOf(certainty), error.outcome());
                assertEquals(certainty.equals("PUBLISHED"), error.knownPublication().isPresent()); assertTrue(error.failures().stream().allMatch(x -> x.phase() == org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Phase.PERSISTENCE));
            }
        }
    }
    @Test void realStoreUnreservableIntentIsRefusedBeforeCopy() throws Exception {
        var f = new Fixture(temporary); var access = f.access(); var b = access.acquire(f.inventory, f.discovery); var job = UUID.randomUUID();
        var limits = new JsonManifestStore.Limits(1000000, 16384, 2, 1000000);
        try (var owner = new CopyExecutionService(access, new JsonManifestStore(f.source, f.output, f.namespace, job, (e, p) -> {}, limits), Clock.fixed(TIME, ZoneOffset.UTC))) {
            var r = owner.execute(f.plan(b), b, job); assertEquals(CopyExecutionResult.Status.RECOVERY_REQUIRED, r.status());
            assertFalse(Files.exists(f.output.resolve("layout-v1"))); assertEquals(1, r.journal().orElseThrow().receipt().sequence());
            assertEquals(org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.State.NOT_STARTED, r.journal().orElseThrow().operations().getFirst().observation().state());
        }
    }


    @Test void nativeCopyAndActualPermissionRefusalAreMeasuredUnderCurrentPrivilegeProfile() throws Exception {
        // Assert the real denial under the actual process profile; root UID is not assumed to bypass DAC.
        runOrdinaryChecks();
    }
    private static void runOrdinaryChecks() throws Exception {
        var base = Files.createTempDirectory("f6-ordinary-provider-");
        try {
            var f = new Fixture(base); var adapter = f.access(); var b = adapter.acquire(f.inventory, f.discovery);
            assertEquals(CopyExecutionResult.Status.JOURNAL_COMPLETED, f.execute(adapter, b, UUID.randomUUID()).status());
            if (Files.getFileAttributeView(f.source, PosixFileAttributeView.class) != null) {
                Files.setPosixFilePermissions(f.source.resolve("synthetic.nii"), Set.of());
                try (var refused = f.access()) { assertEquals(SourceDigestBatch.Status.BLOCKED, refused.acquire(f.inventory, f.discovery).status()); }
                finally { Files.setPosixFilePermissions(f.source.resolve("synthetic.nii"), PosixFilePermissions.fromString("rw-------")); }
            }
            System.out.println("F6 native-permission-checks=PASS effective-user=" + System.getProperty("user.name"));
        } finally { try (var paths = Files.walk(base)) { for (var p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); } }
    }


    @Test void actualNativeSpecialEntryOrJunctionIsRefusedWithoutTraversal() throws Exception {
        var f = new Fixture(temporary); var unsafe = f.source.resolve("unsafe-entry");
        Process process;
        if (System.getProperty("os.name").equals("Linux")) process = new ProcessBuilder("mkfifo", unsafe.toString()).redirectErrorStream(true).start();
        else {
            var external = privateDirectory(temporary.resolve("junction-destination"));
            process = new ProcessBuilder("cmd.exe", "/d", "/c", "mklink", "/J", unsafe.toString(), external.toString()).redirectErrorStream(true).start();
        }
        assertTrue(process.waitFor(20, TimeUnit.SECONDS)); assertEquals(0, process.exitValue()); assertTrue(Files.exists(unsafe, LinkOption.NOFOLLOW_LINKS));
        f.observe(); try (var access = f.access()) { var blocked = access.acquire(f.inventory, f.discovery); assertEquals(SourceDigestBatch.Status.BLOCKED, blocked.status()); assertThrows(IllegalStateException.class, blocked::suppliedDigests); }
        assertTrue(Files.exists(unsafe, LinkOption.NOFOLLOW_LINKS)); assertTrue(f.outputs().isEmpty());
    }

    @Test void replacementAcrossExistingLeaseOpenCannotSplitTheOutputLock() throws Exception {
        var f = new Fixture(temporary); privateFile(f.output.resolve(".f6-copy.lease"), new byte[0]); var changed = new java.util.concurrent.atomic.AtomicBoolean();
        var access = new NioVerifiedCopyAccess(f.source, f.output, f.namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector(), (event, path) -> {
            if (event == NioVerifiedCopyAccess.Event.LEASE_OPEN && changed.compareAndSet(false, true)) {
                if (System.getProperty("os.name").startsWith("Windows")) assertThrows(java.io.IOException.class, () -> Files.move(path, temporary.resolve("retained-old")));
                else { Files.move(path, temporary.resolve("retained-old")); try { privateFile(path, new byte[] {61}); } catch (Exception error) { throw new java.io.IOException(); } }
            }
        });
        var b = access.acquire(f.inventory, f.discovery); var result = f.execute(access, b, UUID.randomUUID()); assertTrue(changed.get());
        if (System.getProperty("os.name").startsWith("Windows")) assertEquals(CopyExecutionResult.Status.JOURNAL_COMPLETED, result.status());
        else { assertEquals(CopyExecutionResult.Status.FAILED, result.status()); code(result.failures(), CopyFailure.Code.CONTAINMENT_UNPROVEN); assertFalse(Files.exists(f.output.resolve("layout-v1"))); assertArrayEquals(new byte[] {61}, Files.readAllBytes(f.output.resolve(".f6-copy.lease"))); }
    }
}
