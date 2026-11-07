package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.*;
import static org.cbihi.mrinormalizer.infrastructure.filesystem.SourceDigestAcquisitionTest.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.cbihi.mrinormalizer.application.dataset.copy.*;
import org.cbihi.mrinormalizer.application.dataset.organization.*;
import org.cbihi.mrinormalizer.application.port.out.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.application.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;

class CopyJournalSequenceTest {
    @TempDir Path temporary;
    // T35/T36/T40/T42: actual F5 -> real F1 plan/RUNNING/intent/outcome/COMPLETED sequence.
    @Test void realStoreRecordsExactF5MappingsAndFactualVerifiedCopyOnly() throws Exception {
        var f = new Fixture(temporary); var access = f.access(); var batch = access.acquire(f.inventory, f.discovery);
        var plan = f.plan(batch); var job = UUID.randomUUID(); var result = f.execute(access, batch, job);
        assertEquals(CopyExecutionResult.Status.JOURNAL_COMPLETED, result.status()); var state = result.journal().orElseThrow();
        assertEquals(4, state.receipt().sequence()); assertEquals(ManifestState.JobState.COMPLETED, state.state());
        assertEquals(job, state.plan().jobId()); assertEquals(plan.copies().getFirst().operationId(), state.operations().getFirst().operationId());
        var op = state.operations().getFirst().observation(); assertEquals(CheckpointRecord.State.COMPLETED, op.state());
        assertEquals(1, op.matchedExpectedSourceCount()); assertEquals(Optional.of(plan.copies().getFirst().expectedDigest()), op.outputDigest());
        assertTrue(op.processingEvidence().isEmpty()); assertEquals(Optional.of(TIME), op.startedAt()); assertEquals(Optional.of(TIME), op.finishedAt());
        assertEquals(f.inventory.entries().getFirst().assessment(), state.plan().sources().getFirst().assessment());
    }
    // T37: no caller can manufacture a usable capability, and null publish creates no artifacts.
    @Test void preparedHandleWithoutAcknowledgedIntentCannotMutate() throws Exception {
        var f = new Fixture(temporary);
        try (var access = f.access()) {
            var batch = access.acquire(f.inventory, f.discovery); var plan = f.plan(batch); assertTrue(access.revalidate(plan, batch).isEmpty());
            try (var prepared = access.prepare(plan.copies().getFirst())) {
                assertThrows(IllegalArgumentException.class, () -> prepared.publish(null));
            }
            assertTrue(f.outputs().isEmpty());
        }
    }
    // T38/T39: even an actually published intent with acknowledgment failure yields no token/COPY.
    @Test void publishedButUnacknowledgedIntentNeverAuthorizesMutation() throws Exception {
        var f = new Fixture(temporary); var access = f.access(); var batch = access.acquire(f.inventory, f.discovery); var job = UUID.randomUUID();
        var real = f.store(job); var decorated = new ManifestStore() {
            public ManifestReceipt create(ProvenanceManifest p) { return real.create(p); }
            public ManifestReceipt append(CheckpointRecord r, ManifestReceipt h) {
                var result = real.append(r, h);
                if (r.kind() == CheckpointRecord.Kind.OPERATION_OBSERVED && r.observation().orElseThrow().state() == CheckpointRecord.State.IN_PROGRESS)
                    throw new ProvenancePersistenceException(List.of(new ManifestFailure(ManifestFailure.Phase.PERSISTENCE,
                            ManifestFailure.Code.READ_FAILED)), ProvenancePersistenceException.PublicationOutcome.PUBLISHED, Optional.of(result));
                return result;
            }
            public Optional<ManifestState> replay(Optional<ManifestReceipt> floor) { return real.replay(floor); }
            public void close() { real.close(); }
        };
        try (var service = new CopyExecutionService(access, decorated, Clock.fixed(TIME, ZoneOffset.UTC))) {
            var result = service.execute(f.plan(batch), batch, job); assertEquals(CopyExecutionResult.Status.RECOVERY_REQUIRED, result.status());
            assertFalse(Files.exists(f.output.resolve("layout-v1"))); assertFalse(Files.exists(f.output.resolve(".f6-copy.lease")));
            assertEquals(CheckpointRecord.State.IN_PROGRESS, real.replay(Optional.empty()).orElseThrow().operations().getFirst().observation().state());
        }
    }
    // T39/T46: outcome acknowledgment failure prevents the next operation, preserving already visible output.
    @Test void unacknowledgedOutcomeStopsBeforeNextPreparation() throws Exception {
        var f = new Fixture(temporary); privateFile(f.source.resolve("second.nii"), nifti(8192)); f.observe();
        var access = f.access(); var batch = access.acquire(f.inventory, f.discovery); var job = UUID.randomUUID(); var real = f.store(job);
        var done = new AtomicInteger(); var decorated = new ManifestStore() {
            public ManifestReceipt create(ProvenanceManifest p) { return real.create(p); }
            public ManifestReceipt append(CheckpointRecord r, ManifestReceipt h) {
                var receipt = real.append(r, h);
                if (r.observation().map(o -> o.state() == CheckpointRecord.State.COMPLETED).orElse(false)) {
                    done.incrementAndGet(); throw new ProvenancePersistenceException(List.of(new ManifestFailure(ManifestFailure.Phase.PERSISTENCE,
                            ManifestFailure.Code.READ_FAILED)), ProvenancePersistenceException.PublicationOutcome.PUBLISHED, Optional.of(receipt));
                }
                return receipt;
            }
            public Optional<ManifestState> replay(Optional<ManifestReceipt> floor) { return real.replay(floor); }
            public void close() { real.close(); }
        };
        try (var service = new CopyExecutionService(access, decorated, Clock.fixed(TIME, ZoneOffset.UTC))) {
            var result = service.execute(f.plan(batch), batch, job); assertEquals(CopyExecutionResult.Status.RECOVERY_REQUIRED, result.status());
            assertEquals(1, done.get()); assertEquals(1, real.replay(Optional.empty()).orElseThrow().operations().stream()
                    .filter(o -> o.observation().state() == CheckpointRecord.State.NOT_STARTED).count());
        }
    }
    // T41/T43: all original sources remain in the real journal; skips never masquerade as COPY outcomes.
    @Test void unusedNonMriSourceRemainsInCompletedCopyJournal() throws Exception {
        var f = new Fixture(temporary); privateFile(f.source.resolve("policy-text"), new byte[] {1, 2, 3}); f.observe();
        var access = f.access(); var batch = access.acquire(f.inventory, f.discovery); var result = f.execute(access, batch, UUID.randomUUID());
        assertEquals(CopyExecutionResult.Status.JOURNAL_COMPLETED, result.status()); assertEquals(2, result.journal().orElseThrow().plan().sources().size());
        assertEquals(1, result.operations().size()); assertEquals(1, result.journal().orElseThrow().plan().sources().stream().filter(s -> s.digest().isEmpty()).count());
    }
    // T45: backward clocks stop before the next affected fact or output mutation.
    @Test void backwardClockStopsWithoutFabricatedChronology() throws Exception {
        var f = new Fixture(temporary); var access = f.access(); var batch = access.acquire(f.inventory, f.discovery); var calls = new AtomicInteger();
        var clock = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; } public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return calls.getAndIncrement() == 0 ? TIME : TIME.minusSeconds(1); }
        };
        try (var service = new CopyExecutionService(access, f.store(UUID.fromString("00000000-0000-0000-0000-000000000005")), clock)) {
            var result = service.execute(f.plan(batch), batch, UUID.fromString("00000000-0000-0000-0000-000000000005"));
            assertEquals(CopyExecutionResult.Status.RECOVERY_REQUIRED, result.status()); assertFalse(Files.exists(f.output.resolve("layout-v1")));
            assertEquals(TIME, result.journal().orElseThrow().recordedAt());
        }
    }
    @Test void staleActualHeadCannotUseOlderAcknowledgedIntentToMutate() throws Exception {
        var f = new Fixture(temporary); var access = f.access(); var batch = access.acquire(f.inventory, f.discovery); var job = UUID.randomUUID(); var real = f.store(job);
        var inspections = new AtomicInteger(); var decorated = new ManifestStore() {
            public ManifestReceipt create(ProvenanceManifest p) { return real.create(p); }
            public ManifestReceipt append(CheckpointRecord r, ManifestReceipt h) { return real.append(r, h); }
            public Optional<ManifestState> replay(Optional<ManifestReceipt> floor) {
                if (floor.filter(h -> h.sequence() == 2).isPresent() && inspections.incrementAndGet() == 2) {
                    var head = real.replay(floor).orElseThrow().receipt();
                    real.append(new CheckpointRecord(1, job, 3, head.headSha256(), TIME, CheckpointRecord.Kind.JOB_OBSERVED,
                            Optional.empty(), Optional.empty(), Optional.of(ManifestState.JobState.RECOVERY_REQUIRED),
                            List.of(new ManifestFailure(ManifestFailure.Phase.EXECUTION, ManifestFailure.Code.RECOVERY_REQUIRED))), head);
                }
                return real.replay(floor);
            }
            public void close() { real.close(); }
        };
        try (var service = new CopyExecutionService(access, decorated, Clock.fixed(TIME, ZoneOffset.UTC))) {
            var r = service.execute(f.plan(batch), batch, job); assertEquals(CopyExecutionResult.Status.RECOVERY_REQUIRED, r.status());
            assertEquals(2, r.journal().orElseThrow().receipt().sequence()); assertEquals(3, real.replay(Optional.empty()).orElseThrow().receipt().sequence());
            assertFalse(Files.exists(f.output.resolve("layout-v1"))); assertFalse(Files.exists(f.output.resolve(".f6-copy.lease")));
        }
    }
    @Test void fabricatedAndForeignCapabilitiesCannotAuthorizeRealPreparedHandle() throws Exception {
        var f = new Fixture(temporary);
        var constructor = CopyExecutionService.AcknowledgedCopyIntent.class.getDeclaredConstructor(); constructor.setAccessible(true);
        var fabricated = constructor.newInstance();
        try (var access = f.access()) {
            var b = access.acquire(f.inventory, f.discovery); var p = f.plan(b); assertTrue(access.revalidate(p, b).isEmpty());
            try (var prepared = access.prepare(p.copies().getFirst())) {
                assertThrows(IllegalArgumentException.class, () -> fabricated.checkLive(prepared));
                assertThrows(IllegalArgumentException.class, () -> prepared.publish(fabricated));
                assertTrue(f.outputs().isEmpty());
            }
        }
    }
    @Test void everyAdmittedFailureMapsToLegalControlledF1PairWithoutPersistenceImagingError() throws Exception {
        var method = CopyExecutionService.class.getDeclaredMethod("map", CopyFailure.class, boolean.class); method.setAccessible(true);
        for (var stage : CopyFailure.Stage.values()) for (var code : CopyFailure.Code.values()) {
            CopyFailure fact;
            try { fact = new CopyFailure(stage, code, Optional.empty(), Optional.empty()); }
            catch (IllegalArgumentException illegalPair) { continue; }
            if (code == CopyFailure.Code.JOURNAL_BLOCKED) { assertThrows(java.lang.reflect.InvocationTargetException.class, () -> method.invoke(null, fact, false)); continue; }
            var mapped = (ManifestFailure)method.invoke(null, fact, false); assertNotEquals(ManifestFailure.Phase.PERSISTENCE, mapped.phase());
            assertEquals(mapped, new ManifestFailure(mapped.phase(), mapped.code()));
            var expected = switch (code) {
                case DISCOVERY_BINDING_UNPROVEN -> ManifestFailure.Code.SOURCE_CHANGED;
                case PROVIDER_UNQUALIFIED -> stage == CopyFailure.Stage.DIGEST_ACQUISITION || stage == CopyFailure.Stage.REVALIDATION ? ManifestFailure.Code.ACCESS_CONTROL_UNAVAILABLE : ManifestFailure.Code.PUBLICATION_UNAVAILABLE;
                case PUBLICATION_UNPROVEN -> ManifestFailure.Code.PUBLICATION_UNAVAILABLE;
                default -> ManifestFailure.Code.valueOf(code.name());
            };
            assertEquals(expected, mapped.code());
        }
    }
    @Test void callerCannotReuseSessionOrExecuteForeignSubsetPlan() throws Exception {
        var f = new Fixture(temporary); var access = f.access(); var b = access.acquire(f.inventory, f.discovery); var job = UUID.randomUUID(); var plan = f.plan(b);
        try (var service = new CopyExecutionService(access, f.store(job), Clock.fixed(TIME, ZoneOffset.UTC))) {
            var before = f.outputs(); var blocked = new OrganizationPlanningService().plan(f.inventory, f.discovery, Map.of());
            assertThrows(IllegalArgumentException.class, () -> service.execute(blocked, b, job)); assertEquals(before, f.outputs());
            assertEquals(CopyExecutionResult.Status.JOURNAL_COMPLETED, service.execute(plan, b, job).status());
            assertThrows(IllegalStateException.class, () -> service.execute(plan, b, job));
            assertThrows(IllegalStateException.class, () -> service.inspectRecovery(plan, Optional.empty()));
        }
    }

    @Test void conversionBlockedButCopyEligibleAssessmentCompletesUnchangedThroughRealReplay() throws Exception {
        var f = new Fixture(temporary); var original = f.inventory.entries().getFirst(); var old = original.assessment();
        var blocked = new org.cbihi.mrinormalizer.application.dataset.model.FormatAssessment(old.initialDetection(), old.format(), old.variant(), old.validity(),
                org.cbihi.mrinormalizer.application.dataset.model.SupportStatus.UNSUPPORTED,
                org.cbihi.mrinormalizer.application.dataset.model.ConversionReadiness.BLOCKED,
                List.of(org.cbihi.mrinormalizer.application.dataset.model.AssessmentReason.UNSUPPORTED_PROFILE));
        var inventory = new org.cbihi.mrinormalizer.application.dataset.model.DatasetInventory(List.of(new org.cbihi.mrinormalizer.application.dataset.model.InventoryEntry(original.source(), blocked)), List.of());
        var digest = digest(nifti(4096)); var batch = new SourceDigestBatch(SourceDigestBatch.Status.COMPLETE, inventory, f.discovery, List.of(new SourceDigestBatch.DigestFact(original.source(), digest)), List.of());
        var plan = new OrganizationPlanningService().plan(inventory, f.discovery, batch.suppliedDigests());
        // Deterministic port fixture isolates the amended real F1 completion semantics; native publication is covered separately.
        var access = new VerifiedCopyAccess() {
            public SourceDigestBatch acquire(org.cbihi.mrinormalizer.application.dataset.model.DatasetInventory i, org.cbihi.mrinormalizer.application.dataset.dicom.DicomSeriesDiscovery d) { return batch; }
            public List<CopyFailure> revalidate(OrganizationPlan p, SourceDigestBatch b) { return List.of(); }
            public PreparedCopy prepare(OrganizationCopyOperation op) { return new PreparedCopy() {
                public ContentDigest verifiedSourceDigest() { return digest; }
                public CopyOutcome publish(CopyExecutionService.AcknowledgedCopyIntent intent) {
                    intent.claim(this, op); intent.checkLive(this);
                    return new CopyOutcome(new CopyExecutionResult.OperationResult(op.operationId(), CopyExecutionResult.Outcome.COMPLETED, Optional.of(digest)), Publication.VERIFIED, List.of());
                }
                public void close() {}
            }; }
            public CopyExecutionResult inspect(OrganizationPlan p, ManifestState s) { throw new IllegalStateException("Fixture does not inspect"); }
            public void close() {}
        };
        var job = UUID.randomUUID();
        try (var service = new CopyExecutionService(access, f.store(job), Clock.fixed(TIME, ZoneOffset.UTC))) {
            var result = service.execute(plan, batch, job); assertEquals(CopyExecutionResult.Status.JOURNAL_COMPLETED, result.status());
            assertEquals(blocked, result.journal().orElseThrow().plan().sources().getFirst().assessment());
            assertEquals(org.cbihi.mrinormalizer.application.dataset.model.ConversionReadiness.BLOCKED, result.journal().orElseThrow().plan().sources().getFirst().assessment().readiness());
        }
    }


    @Test void foreignPlanReceiptAndReusedClaimNeverGrantAdditionalMutation() throws Exception {
        for (String mode : List.of("foreign-anchor", "reused-claim")) {
            var f = new Fixture(privateDirectory(temporary.resolve(mode))); var access = f.access(); var b = access.acquire(f.inventory, f.discovery); var job = UUID.randomUUID(); var real = f.store(job);
            var inspections = new AtomicInteger(); var reference = new java.util.concurrent.atomic.AtomicReference<CopyExecutionService>();
            var captured = new java.util.concurrent.atomic.AtomicReference<CopyExecutionService.AcknowledgedCopyIntent>();
            var capturedPrepared = new java.util.concurrent.atomic.AtomicReference<VerifiedCopyAccess.PreparedCopy>();
            var store = new ManifestStore() {
                public ManifestReceipt create(ProvenanceManifest p) { var receipt = real.create(p); return mode.equals("foreign-anchor") ? new ManifestReceipt(UUID.randomUUID(), receipt.sequence(), receipt.planSha256(), receipt.headSha256()) : receipt; }
                public ManifestReceipt append(CheckpointRecord r, ManifestReceipt h) { return real.append(r, h); }
                public Optional<ManifestState> replay(Optional<ManifestReceipt> floor) {
                    if (mode.equals("reused-claim") && floor.filter(h -> h.sequence() == 2).isPresent() && inspections.incrementAndGet() == 2) try {
                        var live = CopyExecutionService.class.getDeclaredField("live"); live.setAccessible(true); var token = (CopyExecutionService.AcknowledgedCopyIntent)live.get(reference.get()); captured.set(token);
                        var prepared = token.getClass().getDeclaredField("prepared"); prepared.setAccessible(true); capturedPrepared.set((VerifiedCopyAccess.PreparedCopy)prepared.get(token));
                        assertThrows(IllegalArgumentException.class, () -> token.checkLive(capturedPrepared.get()));
                    } catch (ReflectiveOperationException error) { throw new IllegalStateException("Fixture inspection failed"); }
                    return real.replay(floor);
                }
                public void close() { real.close(); }
            };
            try (var service = new CopyExecutionService(access, store, Clock.fixed(TIME, ZoneOffset.UTC))) {
                reference.set(service); var result = service.execute(f.plan(b), b, job);
                if (mode.equals("foreign-anchor")) { assertEquals(CopyExecutionResult.Status.RECOVERY_REQUIRED, result.status()); assertFalse(Files.exists(f.output.resolve("layout-v1"))); }
                else {
                    assertEquals(CopyExecutionResult.Status.JOURNAL_COMPLETED, result.status()); var before = f.outputs();
                    assertThrows(IllegalArgumentException.class, () -> captured.get().claim(capturedPrepared.get(), f.plan(b).copies().getFirst()));
                    assertThrows(IllegalArgumentException.class, () -> captured.get().checkLive(capturedPrepared.get()));
                    assertEquals(before, f.outputs());
                }
            }
        }
    }

    @Test void liveIntentRejectsForeignRealHandleAndDifferentOperationBeforeAnyMutation() throws Exception {
        for (String mode : List.of("handle", "operation")) {
            var base = privateDirectory(temporary.resolve(mode)); var f = new Fixture(base); var foreign = new Fixture(privateDirectory(base.resolve("foreign")));
            privateFile(foreign.source.resolve("other.nii"), nifti(4096)); foreign.observe();
            var access = f.access(); var b = access.acquire(f.inventory, f.discovery); var job = UUID.randomUUID(); var real = f.store(job);
            try (var foreignAccess = foreign.access()) {
                var foreignBatch = foreignAccess.acquire(foreign.inventory, foreign.discovery); var foreignPlan = foreign.plan(foreignBatch); assertTrue(foreignAccess.revalidate(foreignPlan, foreignBatch).isEmpty());
                try (var foreignPrepared = foreignAccess.prepare(foreignPlan.copies().getFirst())) {
                    var inspections = new AtomicInteger(); var reference = new java.util.concurrent.atomic.AtomicReference<CopyExecutionService>();
                    var decorated = new ManifestStore() {
                        public ManifestReceipt create(ProvenanceManifest p) { return real.create(p); }
                        public ManifestReceipt append(CheckpointRecord r, ManifestReceipt h) { return real.append(r, h); }
                        public Optional<ManifestState> replay(Optional<ManifestReceipt> floor) {
                            if (floor.filter(h -> h.sequence() == 2).isPresent() && inspections.incrementAndGet() == 2) try {
                                var live = CopyExecutionService.class.getDeclaredField("live"); live.setAccessible(true); var token = (CopyExecutionService.AcknowledgedCopyIntent)live.get(reference.get());
                                var preparedField = token.getClass().getDeclaredField("prepared"); preparedField.setAccessible(true); var current = (VerifiedCopyAccess.PreparedCopy)preparedField.get(token);
                                if (mode.equals("handle")) assertThrows(IllegalArgumentException.class, () -> foreignPrepared.publish(token));
                                else { var wrong = foreignPlan.copies().stream().filter(op -> !op.operationId().equals(f.plan(b).copies().getFirst().operationId())).findFirst().orElseThrow();
                                    assertThrows(IllegalArgumentException.class, () -> token.claim(current, wrong)); }
                            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Fixture authority inspection failed"); }
                            return real.replay(floor);
                        }
                        public void close() { real.close(); }
                    };
                    try (var service = new CopyExecutionService(access, decorated, Clock.fixed(TIME, ZoneOffset.UTC))) {
                        reference.set(service); assertThrows(IllegalArgumentException.class, () -> service.execute(f.plan(b), b, job));
                        assertFalse(Files.exists(f.output.resolve("layout-v1"))); assertFalse(Files.exists(f.output.resolve(".f6-copy.lease"))); assertTrue(foreign.outputs().isEmpty());
                        assertEquals(CheckpointRecord.State.IN_PROGRESS, real.replay(Optional.empty()).orElseThrow().operations().getFirst().observation().state());
                    }
                }
            }
        }
    }
    @Test void exhaustiveDurableClassificationTraversesActualIntentTerminalAndJobReplay() throws Exception {
        var f = new Fixture(temporary); SourceDigestBatch batch;
        try (var access = f.access()) { batch = access.acquire(f.inventory, f.discovery); }
        var plan = f.plan(batch); var operation = plan.copies().getFirst(); var entry = f.inventory.entries().getFirst();
        var mapping = CopyExecutionService.class.getDeclaredMethod("map", CopyFailure.class, boolean.class); mapping.setAccessible(true);
        int checked = 0;
        for (var stage : CopyFailure.Stage.values()) for (var code : CopyFailure.Code.values()) {
            CopyFailure fact;
            try { fact = new CopyFailure(stage, code, Optional.of(operation.source()), Optional.of(operation.operationId())); }
            catch (IllegalArgumentException invalidPair) { continue; }
            if (code == CopyFailure.Code.JOURNAL_BLOCKED) continue; // Separate original typed persistence tests cover this surface.
            var mapped = (ManifestFailure)mapping.invoke(null, fact, false);
            for (boolean injectedUnknown : new boolean[] {false, true}) {
                boolean uncertain = injectedUnknown || code == CopyFailure.Code.CLEANUP_FAILED || code == CopyFailure.Code.RECOVERY_REQUIRED;
                var failures = new LinkedHashSet<ManifestFailure>(); failures.add(mapped);
                if (uncertain) failures.add(new ManifestFailure(ManifestFailure.Phase.EXECUTION, ManifestFailure.Code.RECOVERY_REQUIRED));
                var durable = List.copyOf(failures); var job = UUID.randomUUID();
                // Controlled unit observations prove real F1 legality/replay, not native imaging-fault qualification.
                try (var store = f.store(job)) {
                    var manifest = new ProvenanceManifest(1, job, TIME, List.of(new SourceFileRecord(entry.source(), Optional.of(operation.expectedDigest()), entry.assessment(), List.of())),
                        List.of(new ManifestOperation(operation.operationId(), ManifestOperation.Kind.COPY, List.of(operation.source()), operation.destination())));
                    var head = store.create(manifest);
                    head = store.append(new CheckpointRecord(1, job, 1, head.headSha256(), TIME, CheckpointRecord.Kind.JOB_OBSERVED, Optional.empty(), Optional.empty(), Optional.of(ManifestState.JobState.RUNNING), List.of()), head);
                    head = store.append(new CheckpointRecord(1, job, 2, head.headSha256(), TIME, CheckpointRecord.Kind.OPERATION_OBSERVED, Optional.of(operation.operationId()),
                        Optional.of(new CheckpointRecord.Observation(CheckpointRecord.State.IN_PROGRESS, Optional.of(TIME), Optional.empty(), Optional.empty(), 1, Optional.empty(), Optional.empty())), Optional.empty(), List.of()), head);
                    var terminal = new CheckpointRecord.Observation(uncertain ? CheckpointRecord.State.RECOVERY_REQUIRED : CheckpointRecord.State.FAILED,
                        Optional.of(TIME), Optional.of(TIME), Optional.empty(), uncertain ? 1 : 0, uncertain ? Optional.of(CheckpointRecord.Disposition.RECOVERY_RECONCILIATION_REQUIRED) : Optional.empty(), Optional.empty());
                    head = store.append(new CheckpointRecord(1, job, 3, head.headSha256(), TIME, CheckpointRecord.Kind.OPERATION_OBSERVED, Optional.of(operation.operationId()), Optional.of(terminal), Optional.empty(), durable), head);
                    var jobState = uncertain ? ManifestState.JobState.RECOVERY_REQUIRED : ManifestState.JobState.FAILED;
                    head = store.append(new CheckpointRecord(1, job, 4, head.headSha256(), TIME, CheckpointRecord.Kind.JOB_OBSERVED, Optional.empty(), Optional.empty(), Optional.of(jobState), durable), head);
                    var replayed = store.replay(Optional.of(head)).orElseThrow(); assertEquals(jobState, replayed.state()); assertEquals(terminal, replayed.operations().getFirst().observation());
                    assertEquals(new HashSet<>(durable), new HashSet<>(replayed.operations().getFirst().failures())); assertTrue(replayed.jobFailures().stream().noneMatch(x -> x.phase() == ManifestFailure.Phase.PERSISTENCE)); checked++;
                }
            }
        }
        assertTrue(checked > 100); assertFalse(Files.exists(f.output.resolve("layout-v1")));
    }

    @Test void failedOutcomeAcknowledgmentRequiresRecoveryButPreservesMeasuredDigestAndEarlierAcks() throws Exception {
        for (var certainty : ProvenancePersistenceException.PublicationOutcome.values()) {
            for (boolean identical : new boolean[] {false, true}) {
                var f = new Fixture(privateDirectory(temporary.resolve(certainty.name() + identical)));
                privateFile(f.source.resolve("second.nii"), nifti(8192)); privateFile(f.source.resolve("third.nii"), nifti(12288)); f.observe();
                var access = f.access(); var batch = access.acquire(f.inventory, f.discovery); var plan = f.plan(batch);
                var second = plan.copies().get(1);
                if (identical) {
                    privateDirectory(f.output.resolve("layout-v1")); privateDirectory(f.output.resolve("layout-v1/nifti"));
                    privateFile(f.output.resolve(second.destination().path()), Files.readAllBytes(f.source.resolve(second.source().path())));
                }
                var job = UUID.randomUUID(); var real = f.store(job); var terminals = new AtomicInteger();
                var decorated = new ManifestStore() {
                    public ManifestReceipt create(ProvenanceManifest p) { return real.create(p); }
                    public ManifestReceipt append(CheckpointRecord r, ManifestReceipt h) {
                        boolean terminal = r.observation().map(o -> o.state() == CheckpointRecord.State.COMPLETED || o.state() == CheckpointRecord.State.IDENTICAL_EXISTING).orElse(false);
                        if (terminal && terminals.incrementAndGet() == 2) {
                            Optional<ManifestReceipt> published = certainty == ProvenancePersistenceException.PublicationOutcome.NOT_PUBLISHED ? Optional.empty() : Optional.of(real.append(r, h));
                            throw new ProvenancePersistenceException(List.of(new ManifestFailure(ManifestFailure.Phase.PERSISTENCE, ManifestFailure.Code.READ_FAILED)), certainty,
                                    certainty == ProvenancePersistenceException.PublicationOutcome.PUBLISHED ? published : Optional.empty());
                        }
                        return real.append(r, h);
                    }
                    public Optional<ManifestState> replay(Optional<ManifestReceipt> floor) { return real.replay(floor); }
                    public void close() { real.close(); }
                };
                try (var owner = new CopyExecutionService(access, decorated, Clock.fixed(TIME, ZoneOffset.UTC))) {
                    var result = owner.execute(plan, batch, job);
                    assertEquals(CopyExecutionResult.Status.RECOVERY_REQUIRED, result.status()); assertEquals(2, terminals.get());
                    assertEquals(CopyExecutionResult.Outcome.COMPLETED, result.operations().getFirst().outcome());
                    assertEquals(CopyExecutionResult.Outcome.RECOVERY_REQUIRED, result.operations().get(1).outcome());
                    assertEquals(Optional.of(second.expectedDigest()), result.operations().get(1).outputDigest());
                    assertEquals(CopyExecutionResult.Outcome.NOT_STARTED, result.operations().getLast().outcome());
                    assertEquals(CheckpointRecord.State.IN_PROGRESS, result.journal().orElseThrow().operations().get(1).observation().state());
                    var actual = real.replay(Optional.empty()).orElseThrow();
                    assertEquals(CheckpointRecord.State.COMPLETED, actual.operations().getFirst().observation().state());
                    assertEquals(certainty == ProvenancePersistenceException.PublicationOutcome.NOT_PUBLISHED ? CheckpointRecord.State.IN_PROGRESS
                            : identical ? CheckpointRecord.State.IDENTICAL_EXISTING : CheckpointRecord.State.COMPLETED, actual.operations().get(1).observation().state());
                    assertTrue(Files.exists(f.output.resolve(second.destination().path())));
                    assertFalse(Files.exists(f.output.resolve(plan.copies().getLast().destination().path())));
                }
            }
        }
    }

}
