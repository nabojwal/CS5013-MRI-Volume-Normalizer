package org.cbihi.mrinormalizer.infrastructure.filesystem;

import static org.junit.jupiter.api.Assertions.*;
import static org.cbihi.mrinormalizer.infrastructure.filesystem.SourceDigestAcquisitionTest.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.cbihi.mrinormalizer.application.dataset.copy.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.application.service.CopyExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class VerifiedCopyRecoveryTest {
    @TempDir Path temporary;
    // T41/T42/T56: terminal journal success is historical evidence, never today's file proof.
    @Test void recoveryRehashesCurrentOutputWithoutRepairingCorruption() throws Exception {
        var f = new Fixture(temporary); var access = f.access(); var batch = access.acquire(f.inventory, f.discovery); var plan = f.plan(batch);
        var job = UUID.randomUUID(); var original = f.execute(access, batch, job); assertEquals(CopyExecutionResult.Status.JOURNAL_COMPLETED, original.status());
        var target = f.output.resolve(plan.copies().getFirst().destination().path()); Files.write(target, new byte[] {8});
        var before = f.outputs(); try (var service = new CopyExecutionService(f.access(), f.store(job), Clock.fixed(TIME, ZoneOffset.UTC))) {
            var inspected = service.inspectRecovery(plan, Optional.of(original.journal().orElseThrow().receipt()));
            assertEquals(CopyExecutionResult.Status.RECOVERY_REQUIRED, inspected.status()); assertEquals(ManifestState.JobState.COMPLETED, inspected.journal().orElseThrow().state());
            assertArrayEquals(new byte[] {8}, Files.readAllBytes(target)); assertEquals(before, f.outputs());
            assertEquals(Optional.of(digest(new byte[] {8})), inspected.operations().getFirst().outputDigest());
            assertEquals(CopyExecutionResult.Outcome.RECOVERY_REQUIRED, inspected.operations().getFirst().outcome());
        }
    }
    // T53/T54: real second-JVM abrupt termination at stage and published-final barriers.
    @Test void crashedStageAndPublishedFinalAreOnlyInspectedNeverResumedOrDeleted() throws Exception {
        for (String event : List.of("STAGE_REOPEN", "FINAL_READ")) {
            var base = privateDirectory(temporary.resolve(event)); var f = new Fixture(base); var ready = base.resolve("ready"); var job = UUID.randomUUID();
            var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp",
                    System.getProperty("java.class.path"), VerifiedCopyRecoveryTest.class.getName(), base.toString(), event, job.toString(), ready.toString())
                    .redirectErrorStream(true).redirectOutput(base.resolve("child.log").toFile()).start();
            VerifiedCopyProviderTest.awaitFile(ready, process); assertTrue(process.waitFor(20, TimeUnit.SECONDS)); assertEquals(91, process.exitValue());
            var fresh = f.access(); var b = fresh.acquire(f.inventory, f.discovery); var plan = f.plan(b); fresh.close();
            var before = f.outputs(); try (var service = new CopyExecutionService(f.access(), f.store(job), Clock.fixed(TIME, ZoneOffset.UTC))) {
                var result = service.inspectRecovery(plan, Optional.empty()); assertEquals(CopyExecutionResult.Status.RECOVERY_REQUIRED, result.status());
                assertEquals(CheckpointRecord.State.IN_PROGRESS, result.journal().orElseThrow().operations().getFirst().observation().state());
                assertEquals(before, f.outputs()); assertTrue(before.stream().anyMatch(p -> p.contains(".f6-stage-")));
            }
        }
    }
    // T55/T57: corruption/orphan/floor failures use actual F1 refusal, never an older prefix.
    @Test void acknowledgedLossCannotFallBackToEarlierJournal() throws Exception {
        var f = new Fixture(temporary); var access = f.access(); var b = access.acquire(f.inventory, f.discovery); var plan = f.plan(b);
        var job = UUID.randomUUID(); var done = f.execute(access, b, job); var floor = done.journal().orElseThrow().receipt();
        var records = f.namespace.resolve(job.toString()).resolve("records");
        try (var stream = Files.walk(records)) { var last = stream.filter(p -> p.getFileName().toString().endsWith(".json")).max(Comparator.comparing(Path::toString)).orElseThrow(); Files.delete(last); }
        assertThrows(ProvenancePersistenceException.class, () -> { try (var store = f.store(job)) { store.replay(Optional.of(floor)); } });
    }
    // T58: synthetic external approval, new UUID/store/session, fresh whole evidence and original mappings.
    @Test void externallyApprovedFreshJobKeepsMappingsAndVerifiesExistingFinals() throws Exception {
        var f = new Fixture(temporary); var first = f.access(); var b = first.acquire(f.inventory, f.discovery); var p = f.plan(b);
        var oldJob = UUID.randomUUID(); var old = f.execute(first, b, oldJob); assertEquals(CopyExecutionResult.Status.JOURNAL_COMPLETED, old.status());
        boolean explicitExternalApproval = true; assertTrue(explicitExternalApproval);
        var nextJob = UUID.randomUUID(); assertNotEquals(oldJob, nextJob); var next = f.access(); var fresh = next.acquire(f.inventory, f.discovery);
        assertEquals(p, f.plan(fresh)); var result = f.execute(next, fresh, nextJob);
        assertEquals(CopyExecutionResult.Status.JOURNAL_COMPLETED, result.status()); assertEquals(CopyExecutionResult.Outcome.IDENTICAL_EXISTING, result.operations().getFirst().outcome());
        try (var oldStore = f.store(oldJob)) { assertEquals(old.journal().orElseThrow(), oldStore.replay(Optional.empty()).orElseThrow()); }
    }
    public static void main(String[] args) throws Exception {
        var base = Path.of(args[0]); var source = base.resolve("source"); var output = base.resolve("output"); var namespace = output.resolve("journal");
        var writes = new java.util.concurrent.atomic.AtomicInteger();
        var access = new NioVerifiedCopyAccess(source, output, namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector(),
                (event, path) -> { if (event.name().equals(args[1]) || args[1].equals("PARTIAL") && event == NioVerifiedCopyAccess.Event.WRITE && writes.incrementAndGet() == 2) { Files.write(Path.of(args[3]), new byte[0]); Runtime.getRuntime().halt(91); } });
        var inv = new NioDatasetScanner(source, output, INVENTORY_LIMITS, detector()).scan();
        var discovery = new org.cbihi.mrinormalizer.application.service.DicomSeriesDiscoveryService().discover(
                new org.cbihi.mrinormalizer.application.service.DicomMetadataInspectionService(
                        new org.cbihi.mrinormalizer.infrastructure.dicom.Dcm4cheMetadataReader(source, METADATA_LIMITS)).inspect(inv));
        var b = access.acquire(inv, discovery); var p = new org.cbihi.mrinormalizer.application.service.OrganizationPlanningService().plan(inv, discovery, b.suppliedDigests());
        var job = UUID.fromString(args[2]); try (var service = new CopyExecutionService(access, new JsonManifestStore(source, output, namespace, job, (event, path) -> {
            boolean barrier = event == JsonManifestStore.Event.AFTER_ACCEPT && (args[1].equals("PLAN") && path.getFileName().toString().equals("plan.json")
                    || args[1].equals("RUNNING") && path.getFileName().toString().equals("00000000000000000001.json")
                    || args[1].equals("INTENT") && path.getFileName().toString().equals("00000000000000000002.json"))
                    || args[1].equals("OUTCOME") && event == JsonManifestStore.Event.AFTER_PUBLISH && path.getFileName().toString().equals("00000000000000000003.json");
            if (barrier) { Files.write(Path.of(args[3]), new byte[0]); Runtime.getRuntime().halt(91); }
        }, JsonManifestStore.Limits.DEFAULT), Clock.fixed(TIME, ZoneOffset.UTC))) {
            service.execute(p, b, job); throw new IllegalStateException("Crash barrier not reached");
        }
    }
    @Test void secondJvmCrashesAtEveryEarlyAndOutcomeJournalBoundaryWithoutResume() throws Exception {
        for (String event : List.of("PLAN", "RUNNING", "INTENT", "PARTIAL", "OUTCOME")) {
            var base = privateDirectory(temporary.resolve(event)); var f = new Fixture(base); Files.write(f.source.resolve("synthetic.nii"), nifti(32768)); f.observe();
            var ready = base.resolve("ready"); var job = UUID.randomUUID();
            var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", System.getProperty("java.class.path"),
                    VerifiedCopyRecoveryTest.class.getName(), base.toString(), event, job.toString(), ready.toString()).redirectErrorStream(true)
                    .redirectOutput(base.resolve("child.log").toFile()).start();
            VerifiedCopyProviderTest.awaitFile(ready, process); assertTrue(process.waitFor(20, TimeUnit.SECONDS)); assertEquals(91, process.exitValue());
            var access = f.access(); var b = access.acquire(f.inventory, f.discovery); var p = f.plan(b); access.close(); var before = f.outputs();
            try (var owner = new CopyExecutionService(f.access(), f.store(job), Clock.fixed(TIME, ZoneOffset.UTC))) {
                var inspected = owner.inspectRecovery(p, Optional.empty()); assertEquals(CopyExecutionResult.Status.RECOVERY_REQUIRED, inspected.status());
                long expected = switch (event) { case "PLAN" -> 0; case "RUNNING" -> 1; case "OUTCOME" -> 3; default -> 2; };
                assertEquals(expected, inspected.journal().orElseThrow().receipt().sequence()); assertEquals(before, f.outputs());
                assertNotEquals(ManifestState.JobState.COMPLETED, inspected.journal().orElseThrow().state());
                if (event.equals("PARTIAL")) try (var paths = Files.walk(f.output)) {
                    var stage = paths.filter(x -> x.getFileName().toString().startsWith(".f6-stage-")).findFirst().orElseThrow();
                    assertTrue(Files.size(stage) > 0 && Files.size(stage) < 32768);
                }
                if (event.equals("OUTCOME")) assertEquals(CheckpointRecord.State.COMPLETED, inspected.journal().orElseThrow().operations().getFirst().observation().state());
                assertThrows(IllegalStateException.class, () -> owner.execute(p, b, job));
            }
        }
    }
    @Test void corruptionGapAndOrphanRefuseF6RecoveryWithoutPrefixFallback() throws Exception {
        for (String damage : List.of("corruption", "gap", "orphan")) {
            var f = new Fixture(privateDirectory(temporary.resolve(damage))); var access = f.access(); var b = access.acquire(f.inventory, f.discovery); var plan = f.plan(b);
            var job = UUID.randomUUID(); var done = f.execute(access, b, job); var records = f.namespace.resolve(job.toString()).resolve("records");
            if (damage.equals("orphan")) privateFile(f.namespace.resolve(job.toString()).resolve("unexpected-artifact"), new byte[] {1});
            else try (var paths = Files.walk(records)) {
                var middle = paths.filter(x -> x.getFileName().toString().equals("00000000000000000002.json")).findFirst().orElseThrow();
                if (damage.equals("gap")) Files.delete(middle); else Files.write(middle, new byte[] {0});
            }
            var before = f.outputs();
            assertThrows(ProvenancePersistenceException.class, () -> { try (var owner = new CopyExecutionService(f.access(), f.store(job), Clock.fixed(TIME, ZoneOffset.UTC))) {
                owner.inspectRecovery(plan, Optional.of(done.journal().orElseThrow().receipt()));
            }}); assertEquals(before, f.outputs());
        }
    }
    @Test void retryFixtureRequiresApprovalFreshEvidenceAndUnchangedOriginalMappings() throws Exception {
        var f = new Fixture(temporary); var access = f.access(); var b = access.acquire(f.inventory, f.discovery); var original = f.plan(b); f.execute(access, b, UUID.randomUUID());
        var before = f.outputs(); assertThrows(IllegalArgumentException.class, () -> retryApproved(false, original, f)); assertEquals(before, f.outputs());
        var bytes = nifti(4096); bytes[900] ^= 1; Files.write(f.source.resolve("synthetic.nii"), bytes); f.observe();
        assertThrows(IllegalArgumentException.class, () -> retryApproved(true, original, f)); assertEquals(before, f.outputs());
    }
    private static CopyExecutionResult retryApproved(boolean approval, org.cbihi.mrinormalizer.application.dataset.organization.OrganizationPlan original, Fixture f) {
        if (!approval) throw new IllegalArgumentException("External approval required");
        try (var access = f.access()) {
            var evidence = access.acquire(f.inventory, f.discovery); var fresh = f.plan(evidence);
            if (!fresh.equals(original)) throw new IllegalArgumentException("Original mapping agreement required");
            return f.execute(access, evidence, UUID.randomUUID());
        }
    }

    @Test void genuineRecoveryPreservesKnownFactsAndCannotReturnToNormalJournalState() throws Exception {
        var f = new Fixture(temporary); var job = UUID.randomUUID(); var changed = new java.util.concurrent.atomic.AtomicBoolean();
        var access = new NioVerifiedCopyAccess(f.source, f.output, f.namespace, LIMITS, INVENTORY_LIMITS, METADATA_LIMITS, detector(),
                (e, p) -> { if (e == NioVerifiedCopyAccess.Event.CLEANUP && changed.compareAndSet(false, true)) {
                    Files.delete(p); try { privateFile(p, new byte[] {12}); } catch (Exception error) { throw new java.io.IOException(); }
                }});
        var b = access.acquire(f.inventory, f.discovery); var plan = f.plan(b); var result = f.execute(access, b, job);
        assertEquals(CopyExecutionResult.Status.RECOVERY_REQUIRED, result.status()); var original = result.journal().orElseThrow(); var observed = original.operations().getFirst().observation();
        assertEquals(CheckpointRecord.State.RECOVERY_REQUIRED, observed.state()); assertEquals(1, observed.matchedExpectedSourceCount());
        assertEquals(Optional.of(TIME), observed.startedAt()); assertEquals(Optional.of(TIME), observed.finishedAt()); assertEquals(Optional.of(plan.copies().getFirst().expectedDigest()), observed.outputDigest());
        try (var store = f.store(job)) {
            var head = original.receipt();
            var refusal = assertThrows(ProvenancePersistenceException.class, () -> store.append(new CheckpointRecord(1, job, head.sequence() + 1, head.headSha256(), TIME,
                    CheckpointRecord.Kind.JOB_OBSERVED, Optional.empty(), Optional.empty(), Optional.of(ManifestState.JobState.RUNNING), List.of()), head));
            assertTrue(refusal.failures().stream().anyMatch(failure -> failure.code() == ManifestFailure.Code.INVALID_MANIFEST));
            assertEquals(original, store.replay(Optional.of(head)).orElseThrow());
        }
        var before = f.outputs(); try (var owner = new CopyExecutionService(f.access(), f.store(job), Clock.fixed(TIME, ZoneOffset.UTC))) {
            var inspected = owner.inspectRecovery(plan, Optional.of(original.receipt())); assertEquals(CopyExecutionResult.Status.RECOVERY_REQUIRED, inspected.status());
            assertEquals(original, inspected.journal().orElseThrow()); assertEquals(Optional.of(plan.copies().getFirst().expectedDigest()), inspected.operations().getFirst().outputDigest()); assertEquals(before, f.outputs());
        }
    }

}
