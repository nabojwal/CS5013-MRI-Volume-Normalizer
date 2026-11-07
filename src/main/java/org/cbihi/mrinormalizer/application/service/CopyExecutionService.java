package org.cbihi.mrinormalizer.application.service;

import java.time.*;
import java.util.*;
import org.cbihi.mrinormalizer.application.dataset.copy.*;
import org.cbihi.mrinormalizer.application.dataset.organization.*;
import org.cbihi.mrinormalizer.application.port.out.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestState.JobState;

/** Sole session sequencing owner. Transfers exclusive runtime use of the supplied real job store/access. */
public final class CopyExecutionService implements AutoCloseable {
    private final VerifiedCopyAccess access;
    private final ManifestStore store;
    private final Clock clock;
    private final Object gate = new Object();
    private boolean used, revoked, closed, accessClosed;
    private ManifestState acknowledged;
    private ProvenanceManifest composed;
    private AcknowledgedCopyIntent live;
    // Original typed persistence facts/outcome/known receipt remain intact, separately from imaging failures.
    private final List<ProvenancePersistenceException> persistenceFailures = new ArrayList<>();

    public CopyExecutionService(VerifiedCopyAccess access, ManifestStore store, Clock clock) {
        if (access == null || store == null || clock == null) invalid();
        this.access = access; this.store = store; this.clock = clock;
    }
    public CopyExecutionResult execute(OrganizationPlan plan, SourceDigestBatch evidence, UUID jobId) {
        synchronized (gate) {
            if (used || closed || revoked) throw new IllegalStateException("COPY execution session unavailable");
            if (plan == null || evidence == null || jobId == null || plan.status() != OrganizationPlan.Status.COMPLETE
                    || evidence.status() != SourceDigestBatch.Status.COMPLETE || !plan.inventory().equals(evidence.inventory())
                    || !plan.dicomDiscovery().equals(evidence.discovery())
                    || !new OrganizationPlanningService().plan(evidence.inventory(), evidence.discovery(), evidence.suppliedDigests()).equals(plan)) invalid();
            used = true;
            var results = new LinkedHashMap<String, CopyExecutionResult.OperationResult>();
            for (var operation : plan.copies()) results.put(operation.operationId(), result(operation, CopyExecutionResult.Outcome.NOT_STARTED, Optional.empty()));
            var failures = new ArrayList<CopyFailure>();
            OrganizationCopyOperation current = null; Instant started = null; boolean intentAcknowledged = false;
            VerifiedCopyAccess.PreparedCopy prepared = null;
            try {
                failures.addAll(access.revalidate(plan, evidence));
                if (!failures.isEmpty()) { revoke(); return finish(CopyExecutionResult.Status.FAILED, results, failures); }
                var sources = new ArrayList<SourceFileRecord>();
                var digests = evidence.suppliedDigests();
                for (var entry : plan.inventory().entries()) sources.add(new SourceFileRecord(entry.source(),
                        Optional.ofNullable(digests.get(entry.source())), entry.assessment(), List.of()));
                var mappings = plan.copies().stream().map(c -> new ManifestOperation(c.operationId(), ManifestOperation.Kind.COPY,
                        List.of(c.source()), c.destination())).toList();
                composed = new ProvenanceManifest(1, jobId, clock.instant(), sources, mappings);
                var anchor = store.create(composed);
                if (anchor.sequence() != 0 || !anchor.jobId().equals(jobId)) throw new JournalMismatch();
                acknowledge(anchor);
                if (plan.copies().isEmpty()) return finish(CopyExecutionResult.Status.NOTHING_TO_COPY, results, failures);
                appendJob(JobState.RUNNING, List.of());
                for (var operation : plan.copies()) {
                    current = operation; started = null; intentAcknowledged = false;
                    prepared = access.prepare(operation);
                    if (!operation.expectedDigest().equals(prepared.verifiedSourceDigest())) throw new VerifiedCopyAccess.AccessFailure(
                            List.of(failure(CopyFailure.Stage.COPY, CopyFailure.Code.SOURCE_CHANGED, operation)));
                    started = now();
                    appendOperation(operation, new Observation(State.IN_PROGRESS, Optional.of(started), Optional.empty(),
                            Optional.empty(), 1, Optional.empty(), Optional.empty()), List.of(), started);
                    intentAcknowledged = true;
                    var token = new AcknowledgedCopyIntent(); token.owner = this; token.prepared = prepared; token.operation = operation;
                    token.intentHead = acknowledged.receipt(); token.started = started; live = token;
                    var outcome = prepared.publish(token);
                    prepared.close(); prepared = null; token.active = false; live = null;
                    validateOutcome(operation, outcome);
                    // Measured bytes are known; operation success is not exposed until its actual ACK.
                    results.put(operation.operationId(), outcome.failures().isEmpty()
                            ? result(operation, CopyExecutionResult.Outcome.RECOVERY_REQUIRED, outcome.operation().outputDigest())
                            : outcome.operation());
                    failures.addAll(outcome.failures());
                    if (!outcome.failures().isEmpty()) {
                        revoke(); var uncertain = outcome.publication() == VerifiedCopyAccess.Publication.UNKNOWN
                                || outcome.operation().outcome() == CopyExecutionResult.Outcome.RECOVERY_REQUIRED;
                        recordFailure(operation, started, intentAcknowledged, failures, uncertain, false, outcome.operation().outputDigest());
                        return finish(uncertain ? CopyExecutionResult.Status.RECOVERY_REQUIRED : CopyExecutionResult.Status.FAILED, results, failures);
                    }
                    Instant finished = now();
                    var state = outcome.operation().outcome() == CopyExecutionResult.Outcome.COMPLETED ? State.COMPLETED : State.IDENTICAL_EXISTING;
                    appendOperation(operation, new Observation(state, Optional.of(started), Optional.of(finished),
                            outcome.operation().outputDigest(), 1, Optional.empty(), Optional.empty()), List.of(), finished);
                    results.put(operation.operationId(), outcome.operation());
                    current = null; intentAcknowledged = false;
                }
                // Releasing the output namespace lease is part of success, before JOB COMPLETED ACK.
                access.close(); accessClosed = true;
                appendJob(JobState.COMPLETED, List.of()); revoke();
                return finish(CopyExecutionResult.Status.JOURNAL_COMPLETED, results, failures);
            } catch (ProvenancePersistenceException error) {
                preserve(error); revoke(); closePrepared(prepared, failures, current);
                if (current != null && intentAcknowledged) results.put(current.operationId(), result(current,
                        CopyExecutionResult.Outcome.RECOVERY_REQUIRED, results.get(current.operationId()).outputDigest()));
                if (failures.isEmpty()) failures.add(failure(CopyFailure.Stage.JOURNAL, CopyFailure.Code.JOURNAL_BLOCKED, current));
                return finish(CopyExecutionResult.Status.RECOVERY_REQUIRED, results, failures);
            } catch (VerifiedCopyAccess.AccessFailure error) {
                failures.addAll(error.failures()); revoke(); closePrepared(prepared, failures, current);
                boolean uncertain = failures.stream().anyMatch(f -> f.code() == CopyFailure.Code.RECOVERY_REQUIRED || f.code() == CopyFailure.Code.CLEANUP_FAILED);
                if (current != null && intentAcknowledged) results.put(current.operationId(), result(current,
                        uncertain ? CopyExecutionResult.Outcome.RECOVERY_REQUIRED : CopyExecutionResult.Outcome.FAILED, Optional.empty()));
                if (acknowledged != null) {
                    try { recordFailure(current, started, intentAcknowledged, failures, uncertain, !intentAcknowledged, Optional.empty()); }
                    catch (ProvenancePersistenceException e) { preserve(e); uncertain = true; if (failures.isEmpty()) failures.add(failure(CopyFailure.Stage.JOURNAL, CopyFailure.Code.JOURNAL_BLOCKED, current)); }
                    catch (JournalMismatch e) { uncertain = true; if (failures.isEmpty()) failures.add(failure(CopyFailure.Stage.JOURNAL, CopyFailure.Code.RECOVERY_REQUIRED, current)); }
                }
                return finish(uncertain ? CopyExecutionResult.Status.RECOVERY_REQUIRED : CopyExecutionResult.Status.FAILED, results, failures);
            } catch (JournalMismatch error) {
                revoke(); closePrepared(prepared, failures, current);
                if (failures.isEmpty()) failures.add(failure(CopyFailure.Stage.JOURNAL, CopyFailure.Code.RECOVERY_REQUIRED, current));
                return finish(CopyExecutionResult.Status.RECOVERY_REQUIRED, results, failures);
            }
        }
    }
    public CopyExecutionResult inspectRecovery(OrganizationPlan plan, Optional<ManifestReceipt> floor) {
        synchronized (gate) {
            if (plan == null || floor == null) invalid();
            if (closed || used) throw new IllegalStateException("COPY execution session unavailable");
            used = true; revoke();
            try {
                var replayed = store.replay(floor);
                if (replayed.isEmpty()) return new CopyExecutionResult(CopyExecutionResult.Status.RECOVERY_REQUIRED,
                        Optional.empty(), plan.copies().stream().map(c -> result(c, CopyExecutionResult.Outcome.NOT_STARTED, Optional.empty())).toList(),
                        List.of(failure(CopyFailure.Stage.RECOVERY, CopyFailure.Code.RECOVERY_REQUIRED, null)));
                acknowledged = replayed.get(); return access.inspect(plan, acknowledged);
            } catch (ProvenancePersistenceException error) { preserve(error); throw error; }
        }
    }
    public void close() {
        synchronized (gate) {
            if (closed) return; closed = true; revoke();
            VerifiedCopyAccess.AccessFailure copyError = null; ProvenancePersistenceException journalError = null;
            try { if (!accessClosed) access.close(); } catch (VerifiedCopyAccess.AccessFailure e) { copyError = e; }
            finally { accessClosed = true; }
            try { store.close(); } catch (ProvenancePersistenceException e) { preserve(e); journalError = e; }
            if (journalError != null) throw journalError;
            if (copyError != null) throw copyError;
        }
    }
    public static final class AcknowledgedCopyIntent {
        private CopyExecutionService owner;
        private VerifiedCopyAccess.PreparedCopy prepared;
        private OrganizationCopyOperation operation;
        private ManifestReceipt intentHead;
        private Instant started;
        private boolean claimed, active = true;
        private AcknowledgedCopyIntent() {}
        public void claim(VerifiedCopyAccess.PreparedCopy prepared, OrganizationCopyOperation operation) {
            if (owner == null) invalid();
            synchronized (owner.gate) {
                if (claimed || !active || owner.closed || owner.revoked || owner.live != this
                        || this.prepared != prepared || !this.operation.equals(operation)
                        || !operation.expectedDigest().equals(prepared.verifiedSourceDigest())) { owner.revoke(); invalid(); }
                try {
                    var state = owner.store.replay(Optional.of(intentHead)).orElseThrow(JournalMismatch::new);
                    if (!state.receipt().equals(intentHead) || !state.plan().equals(owner.composed) || state.state() != JobState.RUNNING
                            || state.operations().stream().filter(o -> o.observation().state() == State.IN_PROGRESS).count() != 1) throw new JournalMismatch();
                    var observed = state.operations().stream().filter(o -> o.operationId().equals(operation.operationId())).findFirst().orElseThrow(JournalMismatch::new);
                    if (observed.observation().state() != State.IN_PROGRESS || observed.observation().matchedExpectedSourceCount() != 1
                            || !observed.observation().startedAt().equals(Optional.of(started)) || !observed.failures().isEmpty()) throw new JournalMismatch();
                    claimed = true;
                } catch (ProvenancePersistenceException e) { owner.preserve(e); owner.revoke(); throw e; }
                catch (JournalMismatch e) { owner.revoke(); throw e; }
            }
        }
        public void checkLive(VerifiedCopyAccess.PreparedCopy prepared) {
            if (owner == null) invalid();
            synchronized (owner.gate) {
                if (!claimed || !active || owner.closed || owner.revoked || owner.live != this || this.prepared != prepared) invalid();
            }
        }
    }
    private static void validateOutcome(OrganizationCopyOperation op, VerifiedCopyAccess.CopyOutcome outcome) {
        if (outcome == null || !outcome.operation().operationId().equals(op.operationId())
                || outcome.operation().outputDigest().filter(d -> !d.equals(op.expectedDigest())).isPresent()) throw new JournalMismatch();
        for (var failure : outcome.failures()) if (failure.operationId().filter(id -> !id.equals(op.operationId())).isPresent()
                || failure.source().filter(s -> !s.equals(op.source())).isPresent()) throw new JournalMismatch();
    }
    private void recordFailure(OrganizationCopyOperation op, Instant started, boolean intent, List<CopyFailure> facts,
            boolean uncertain, boolean hashing, Optional<ContentDigest> digest) {
        // Mask solely for factual failure journaling: never retry imaging I/O, restore before returning.
        boolean interrupted = Thread.interrupted();
        try {
        var durable = new LinkedHashSet<ManifestFailure>(); for (var f : facts) if (f.stage() != CopyFailure.Stage.JOURNAL) durable.add(map(f, hashing));
        if (uncertain) durable.add(new ManifestFailure(ManifestFailure.Phase.EXECUTION, ManifestFailure.Code.RECOVERY_REQUIRED));
        if (durable.isEmpty()) throw new JournalMismatch();
        if (intent) {
            var at = now(); var observation = uncertain
                    ? new Observation(State.RECOVERY_REQUIRED, Optional.of(started), Optional.of(at), digest, 1,
                            Optional.of(Disposition.RECOVERY_RECONCILIATION_REQUIRED), Optional.empty())
                    : new Observation(State.FAILED, Optional.of(started), Optional.of(at), Optional.empty(), 0, Optional.empty(), Optional.empty());
            appendOperation(op, observation, List.copyOf(durable), at);
        }
        appendJob(uncertain ? JobState.RECOVERY_REQUIRED : JobState.FAILED, List.copyOf(durable));
        } finally { if (interrupted) Thread.currentThread().interrupt(); }
    }
    private static ManifestFailure map(CopyFailure f, boolean hashing) {
        var phase = hashing || f.stage() == CopyFailure.Stage.DIGEST_ACQUISITION || f.stage() == CopyFailure.Stage.REVALIDATION
                || f.code() == CopyFailure.Code.HASH_FAILED || f.code() == CopyFailure.Code.SOURCE_CHANGED
                || f.code() == CopyFailure.Code.DISCOVERY_BINDING_UNPROVEN ? ManifestFailure.Phase.HASHING : ManifestFailure.Phase.EXECUTION;
        var code = switch (f.code()) {
            case DISCOVERY_BINDING_UNPROVEN -> ManifestFailure.Code.SOURCE_CHANGED;
            case PROVIDER_UNQUALIFIED -> phase == ManifestFailure.Phase.HASHING ? ManifestFailure.Code.ACCESS_CONTROL_UNAVAILABLE : ManifestFailure.Code.PUBLICATION_UNAVAILABLE;
            case PUBLICATION_UNPROVEN -> ManifestFailure.Code.PUBLICATION_UNAVAILABLE;
            case CLEANUP_FAILED -> { phase = ManifestFailure.Phase.EXECUTION; yield ManifestFailure.Code.CLEANUP_FAILED; }
            case RECOVERY_REQUIRED -> { phase = ManifestFailure.Phase.EXECUTION; yield ManifestFailure.Code.RECOVERY_REQUIRED; }
            case JOURNAL_BLOCKED -> throw new JournalMismatch();
            default -> ManifestFailure.Code.valueOf(f.code().name());
        };
        return new ManifestFailure(phase, code);
    }
    private void appendJob(JobState state, List<ManifestFailure> failures) {
        var at = now(); var head = acknowledged.receipt();
        acknowledge(store.append(new CheckpointRecord(1, composed.jobId(), Math.addExact(head.sequence(), 1), head.headSha256(),
                at, Kind.JOB_OBSERVED, Optional.empty(), Optional.empty(), Optional.of(state), failures), head));
    }
    private void appendOperation(OrganizationCopyOperation op, Observation observation, List<ManifestFailure> failures, Instant time) {
        var head = acknowledged.receipt();
        acknowledge(store.append(new CheckpointRecord(1, composed.jobId(), Math.addExact(head.sequence(), 1), head.headSha256(),
                time, Kind.OPERATION_OBSERVED, Optional.of(op.operationId()), Optional.of(observation), Optional.empty(), failures), head));
    }
    private void acknowledge(ManifestReceipt receipt) {
        var replayed = store.replay(Optional.of(receipt)).orElseThrow(JournalMismatch::new);
        if (!replayed.receipt().equals(receipt) || !replayed.plan().equals(composed)) throw new JournalMismatch();
        acknowledged = replayed;
    }
    private Instant now() { var at = clock.instant(); if (acknowledged != null && at.isBefore(acknowledged.recordedAt())) throw new JournalMismatch(); return at; }
    private void preserve(ProvenancePersistenceException error) {
        // execute/inspect are one-shot and close is idempotent: at most one work error plus one close error.
        if (!persistenceFailures.contains(error)) persistenceFailures.add(error);
    }
    private void revoke() { revoked = true; if (live != null) live.active = false; }
    private void closePrepared(VerifiedCopyAccess.PreparedCopy prepared, List<CopyFailure> failures, OrganizationCopyOperation op) {
        if (prepared == null) return;
        try { prepared.close(); } catch (VerifiedCopyAccess.AccessFailure error) { failures.addAll(error.failures()); }
    }
    private CopyExecutionResult finish(CopyExecutionResult.Status status, Map<String, CopyExecutionResult.OperationResult> results, List<CopyFailure> failures) {
        return new CopyExecutionResult(status, Optional.ofNullable(acknowledged), List.copyOf(results.values()), List.copyOf(new LinkedHashSet<>(failures)));
    }
    private static CopyExecutionResult.OperationResult result(OrganizationCopyOperation op, CopyExecutionResult.Outcome outcome, Optional<ContentDigest> digest) {
        return new CopyExecutionResult.OperationResult(op.operationId(), outcome, digest);
    }
    private static CopyFailure failure(CopyFailure.Stage stage, CopyFailure.Code code, OrganizationCopyOperation op) {
        return new CopyFailure(stage, code, op == null ? Optional.empty() : Optional.of(op.source()), op == null ? Optional.empty() : Optional.of(op.operationId()));
    }
    private static final class JournalMismatch extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private JournalMismatch() { super("COPY journal continuity unproven", null, false, false); }
    }
    private static void invalid() { throw new IllegalArgumentException("Invalid COPY execution contract"); }
}
