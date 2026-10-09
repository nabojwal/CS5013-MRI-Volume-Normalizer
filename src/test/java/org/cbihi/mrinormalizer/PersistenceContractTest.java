package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.cbihi.mrinormalizer.application.dataset.model.FormatAssessment;
import org.cbihi.mrinormalizer.application.port.out.ManifestStore;
import org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord;
import org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.Disposition;
import org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.Kind;
import org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.Observation;
import org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.State;
import org.cbihi.mrinormalizer.application.provenance.manifest.ContentDigest;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Code;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Phase;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestOperation;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestReceipt;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestState;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestState.JobState;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestState.OperationState;
import org.cbihi.mrinormalizer.application.provenance.manifest.ProcessingEvidence;
import org.cbihi.mrinormalizer.application.provenance.manifest.ProcessingEvidence.Scope;
import org.cbihi.mrinormalizer.application.provenance.manifest.ProvenanceManifest;
import org.cbihi.mrinormalizer.application.provenance.manifest.ProvenancePersistenceException;
import org.cbihi.mrinormalizer.application.provenance.manifest.ProvenancePersistenceException.PublicationOutcome;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;
import org.cbihi.mrinormalizer.application.provenance.manifest.SourceFileRecord;
import org.cbihi.mrinormalizer.domain.error.DicomProcessingError;
import org.cbihi.mrinormalizer.domain.error.DicomToNiftiError;
import org.cbihi.mrinormalizer.domain.model.AffineMatrix4;
import org.cbihi.mrinormalizer.domain.model.DetectionOutcome;
import org.cbihi.mrinormalizer.domain.model.DetectionResult;
import org.cbihi.mrinormalizer.domain.model.IntensityTransform;
import org.cbihi.mrinormalizer.domain.model.ScalarType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** C01-C03 protocol values and declarations only: no store, replay or I/O fixture. */
class PersistenceContractTest {
    private static final UUID JOB = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant CREATED = Instant.parse("2026-10-04T00:00:00Z");
    private static final Instant START = CREATED.plusSeconds(1);
    private static final Instant FINISH = CREATED.plusSeconds(2);
    private static final Instant RECORDED = CREATED.plusSeconds(3);
    private static final String PLAN_HASH = "a".repeat(64);
    private static final String HEAD_HASH = "b".repeat(64);
    private static final ContentDigest DIGEST = new ContentDigest(1, "c".repeat(64));
    private static final ManifestFailure FAILURE = new ManifestFailure(Phase.HASHING, Code.HASH_FAILED);
    private static final ManifestFailure RECOVERY = new ManifestFailure(Phase.EXECUTION, Code.RECOVERY_REQUIRED);

    // C01
    @Test
    void recordKindAndObservationMatrixIsExhaustive() {
        var dispositions = new ArrayList<Optional<Disposition>>();
        dispositions.add(Optional.empty());
        for (var d : Disposition.values()) dispositions.add(Optional.of(d));
        var evidence = Arrays.asList(null, phase(Scope.RECONSTRUCTION, true),
                phase(Scope.RECONSTRUCTION, false), phase(Scope.CONVERSION, true), phase(Scope.CONVERSION, false));
        for (var state : State.values()) {
            for (int flags = 0; flags < 8; flags++) {
                boolean start = (flags & 1) != 0;
                boolean finish = (flags & 2) != 0;
                boolean digest = (flags & 4) != 0;
                for (int count : new int[] {0, 1, 100000}) {
                    for (var disposition : dispositions) {
                        for (var supplied : evidence) {
                            boolean expected = switch (state) {
                                case NOT_STARTED -> !start && !finish && !digest && count == 0
                                        && disposition.isEmpty() && supplied == null;
                                case IN_PROGRESS -> start && !finish && !digest && count > 0
                                        && disposition.isEmpty() && supplied == null;
                                case COMPLETED, IDENTICAL_EXISTING -> start && finish && digest && count > 0
                                        && disposition.isEmpty() && supplied == null;
                                case WRITTEN_UNVERIFIED -> start && finish && digest && count > 0
                                        && disposition.isEmpty() && supplied != null
                                        && supplied.scope() == Scope.CONVERSION && supplied.phaseSuccessful();
                                case SKIPPED_POLICY -> finish && !digest && count == 0 && supplied == null
                                        && disposition.equals(Optional.of(Disposition.NOT_REQUESTED_BY_POLICY));
                                case BLOCKED -> finish && !digest && count == 0 && disposition.isPresent()
                                        && List.of(Disposition.VALIDATION_REQUIRED, Disposition.UNSUPPORTED_INPUT,
                                                Disposition.INPUT_UNAVAILABLE).contains(disposition.orElseThrow());
                                case FAILED -> finish && !digest && count == 0 && disposition.isEmpty();
                                case RECOVERY_REQUIRED -> disposition.equals(
                                        Optional.of(Disposition.RECOVERY_RECONCILIATION_REQUIRED));
                            };
                            Executable construct = () -> new Observation(state, start ? Optional.of(START) : Optional.empty(),
                                    finish ? Optional.of(FINISH) : Optional.empty(), digest ? Optional.of(DIGEST) : Optional.empty(),
                                    count, disposition, Optional.ofNullable(supplied));
                            if (expected) assertDoesNotThrow(construct);
                            else reject("Observation facts do not match state", construct);
                        }
                    }
                }
            }
        }
    }

    @Test
    void recordKindsRequireExclusiveFields() {
        for (var kind : Kind.values()) {
            for (int flags = 0; flags < 8; flags++) {
                var operation = (flags & 1) != 0 ? Optional.of(id(1)) : Optional.<String>empty();
                var observation = (flags & 2) != 0 ? Optional.of(observation(State.IN_PROGRESS)) : Optional.<Observation>empty();
                var jobState = (flags & 4) != 0 ? Optional.of(JobState.RUNNING) : Optional.<JobState>empty();
                boolean valid = kind == Kind.OPERATION_OBSERVED ? flags == 3 : flags == 4;
                Executable construct = () -> new CheckpointRecord(1, JOB, 1, PLAN_HASH, RECORDED, kind,
                        operation, observation, jobState, List.of());
                if (valid) assertDoesNotThrow(construct);
                else reject("Checkpoint fields do not match kind", construct);
            }
        }
    }

    @Test
    void checkpointRequiredComponentsVersionSequenceAndHashesReject() {
        reject("Checkpoint components must be non-null", () -> checkpoint(null, State.IN_PROGRESS, List.of()));
        reject("Checkpoint components must be non-null", () -> new CheckpointRecord(1, JOB, 1, null, RECORDED,
                Kind.JOB_OBSERVED, Optional.empty(), Optional.empty(), Optional.of(JobState.RUNNING), List.of()));
        reject("Checkpoint components must be non-null", () -> new CheckpointRecord(1, JOB, 1, PLAN_HASH, null,
                Kind.JOB_OBSERVED, Optional.empty(), Optional.empty(), Optional.of(JobState.RUNNING), List.of()));
        reject("Checkpoint components must be non-null", () -> new CheckpointRecord(1, JOB, 1, PLAN_HASH, RECORDED,
                null, Optional.empty(), Optional.empty(), Optional.of(JobState.RUNNING), List.of()));
        for (int missing = 0; missing < 4; missing++) {
            int index = missing;
            reject("Checkpoint components must be non-null", () -> new CheckpointRecord(1, JOB, 1, PLAN_HASH, RECORDED,
                    Kind.JOB_OBSERVED, index == 0 ? null : Optional.empty(), index == 1 ? null : Optional.empty(),
                    index == 2 ? null : Optional.of(JobState.RUNNING), index == 3 ? null : List.of()));
        }
        for (int version : new int[] {Integer.MIN_VALUE, 0, 2, Integer.MAX_VALUE}) {
            reject("Unsupported checkpoint schema version", () -> new CheckpointRecord(version, JOB, 1, PLAN_HASH,
                    RECORDED, Kind.JOB_OBSERVED, Optional.empty(), Optional.empty(), Optional.of(JobState.RUNNING), List.of()));
        }
        for (long sequence : new long[] {Long.MIN_VALUE, -1, 0}) {
            reject("Checkpoint sequence must be positive", () -> jobRecord(sequence, PLAN_HASH, JobState.RUNNING, List.of()));
        }
        for (var hash : badHashes()) {
            reject("Checkpoint hash must be lowercase SHA-256", () -> jobRecord(1, hash, JobState.RUNNING, List.of()));
            reject("Checkpoint operation ID must be lowercase SHA-256", () -> new CheckpointRecord(1, JOB, 1, PLAN_HASH,
                    RECORDED, Kind.OPERATION_OBSERVED, Optional.of(hash), Optional.of(observation(State.IN_PROGRESS)),
                    Optional.empty(), List.of()));
        }
    }

    @Test
    void observationNullabilityAndCountBoundsReject() {
        for (int missing = 0; missing < 6; missing++) {
            int index = missing;
            reject("Observation components must be non-null", () -> new Observation(index == 0 ? null : State.NOT_STARTED,
                    index == 1 ? null : Optional.empty(), index == 2 ? null : Optional.empty(),
                    index == 3 ? null : Optional.empty(), 0, index == 4 ? null : Optional.empty(),
                    index == 5 ? null : Optional.empty()));
        }
        for (int count : new int[] {Integer.MIN_VALUE, -1, 100001, Integer.MAX_VALUE}) {
            reject("Observation source count is invalid", () -> new Observation(State.RECOVERY_REQUIRED,
                    Optional.empty(), Optional.empty(), Optional.empty(), count,
                    Optional.of(Disposition.RECOVERY_RECONCILIATION_REQUIRED), Optional.empty()));
        }
    }

    @Test
    void initialStatesAreDerivedAndNeverJournalRecords() {
        assertDoesNotThrow(() -> observation(State.NOT_STARTED));
        reject("Initial operation state cannot be checkpointed", () -> checkpoint(JOB, State.NOT_STARTED, List.of()));
        reject("Initial job state cannot be checkpointed", () -> jobRecord(1, PLAN_HASH, JobState.PLANNED, List.of()));
    }

    @Test
    void checkpointFailureApplicabilityIsExhaustive() {
        for (var state : State.values()) {
            if (state == State.NOT_STARTED) continue;
            for (var failures : List.of(List.<ManifestFailure>of(), List.of(FAILURE), List.of(RECOVERY))) {
                boolean valid = switch (state) {
                    case IN_PROGRESS, COMPLETED, IDENTICAL_EXISTING, WRITTEN_UNVERIFIED, SKIPPED_POLICY -> failures.isEmpty();
                    case BLOCKED -> true;
                    case FAILED -> !failures.isEmpty();
                    case RECOVERY_REQUIRED -> failures.contains(RECOVERY);
                    default -> false;
                };
                Executable construct = () -> checkpoint(JOB, state, failures);
                if (valid) assertDoesNotThrow(construct);
                else reject("Checkpoint failures do not match observation", construct);
            }
        }
        for (var state : JobState.values()) {
            if (state == JobState.PLANNED) continue;
            for (var failures : List.of(List.<ManifestFailure>of(), List.of(FAILURE), List.of(RECOVERY))) {
                boolean valid = switch (state) {
                    case COMPLETED -> failures.isEmpty();
                    case RUNNING, FAILED -> true; // Prior evidence/transition applicability is checked by S2B.
                    case RECOVERY_REQUIRED -> failures.contains(RECOVERY);
                    default -> false;
                };
                Executable construct = () -> jobRecord(1, PLAN_HASH, state, failures);
                if (valid) assertDoesNotThrow(construct);
                else reject("Checkpoint failures do not match job state", construct);
            }
        }
    }

    @Test
    void originalPhaseErrorsSatisfyFailureWithoutDuplication() {
        for (var scope : Scope.values()) {
            var evidence = phase(scope, false);
            var observed = withEvidence(State.FAILED, evidence);
            var record = operationRecord(1, PLAN_HASH, RECORDED, observed, List.of());
            assertSame(evidence, record.observation().orElseThrow().processingEvidence().orElseThrow());
            assertTrue(record.failures().isEmpty());
        }
        reject("Checkpoint failures do not match observation", () -> operationRecord(1, PLAN_HASH, RECORDED,
                withEvidence(State.FAILED, phase(Scope.RECONSTRUCTION, true)), List.of()));
        var successfulPhase = phase(Scope.CONVERSION, true);
        var record = operationRecord(1, PLAN_HASH, RECORDED, withEvidence(State.FAILED, successfulPhase), List.of(FAILURE));
        assertSame(successfulPhase, record.observation().orElseThrow().processingEvidence().orElseThrow());
        assertEquals(State.FAILED, record.observation().orElseThrow().state());
    }

    @Test
    void writtenUnverifiedRequiresSuccessfulConversionFacts() {
        var written = observation(State.WRITTEN_UNVERIFIED);
        var record = operationRecord(1, PLAN_HASH, RECORDED, written, List.of());
        assertEquals(State.WRITTEN_UNVERIFIED, record.observation().orElseThrow().state());
        assertTrue(record.observation().orElseThrow().processingEvidence().orElseThrow().conversionFacts().isPresent());
        assertTrue(record.failures().isEmpty());
        assertFalse(Arrays.stream(State.values()).anyMatch(s -> s.name().contains("VERIFIED") && s != State.WRITTEN_UNVERIFIED));
    }

    @Test
    void suppliedChronologyRejectsReversedAndFutureFacts() {
        reject("Observation chronology is invalid", () -> new Observation(State.COMPLETED, Optional.of(FINISH),
                Optional.of(START), Optional.of(DIGEST), 1, Optional.empty(), Optional.empty()));
        assertDoesNotThrow(() -> new Observation(State.COMPLETED, Optional.of(FINISH), Optional.of(FINISH),
                Optional.of(DIGEST), 1, Optional.empty(), Optional.empty()));
        reject("Checkpoint chronology is invalid", () -> operationRecord(1, PLAN_HASH, START.minusNanos(1),
                observation(State.IN_PROGRESS), List.of()));
        reject("Checkpoint chronology is invalid", () -> operationRecord(1, PLAN_HASH, FINISH.minusNanos(1),
                observation(State.COMPLETED), List.of()));
        var summary = new ProcessingEvidence.SourceSummary(Optional.empty(), "1", "1", RECORDED.plusNanos(1), true, 1, 1);
        var evidence = new ProcessingEvidence(Scope.RECONSTRUCTION, true, Optional.of(summary), List.of(), List.of(), Optional.empty());
        reject("Checkpoint chronology is invalid", () -> operationRecord(1, PLAN_HASH, RECORDED,
                new Observation(State.RECOVERY_REQUIRED, Optional.of(START), Optional.empty(), Optional.empty(), 1,
                        Optional.of(Disposition.RECOVERY_RECONCILIATION_REQUIRED), Optional.of(evidence)), List.of(RECOVERY)));
        reject("Observation chronology is invalid", () -> withEvidence(State.FAILED, evidence));
    }

    @Test
    void recoveryRetainsSuppliedFactsWithoutPromotingOrExecuting() {
        var evidence = phase(Scope.CONVERSION, true);
        var observed = new Observation(State.RECOVERY_REQUIRED, Optional.of(START), Optional.of(FINISH),
                Optional.of(DIGEST), 1, Optional.of(Disposition.RECOVERY_RECONCILIATION_REQUIRED), Optional.of(evidence));
        var record = operationRecord(1, PLAN_HASH, RECORDED, observed, List.of(FAILURE, RECOVERY));
        assertSame(DIGEST, record.observation().orElseThrow().outputDigest().orElseThrow());
        assertSame(evidence, record.observation().orElseThrow().processingEvidence().orElseThrow());
        assertEquals(State.RECOVERY_REQUIRED, record.observation().orElseThrow().state());
        assertDoesNotThrow(() -> operationRecord(1, PLAN_HASH, RECORDED, observation(State.RECOVERY_REQUIRED), List.of(RECOVERY)));
    }

    @Test
    void checkpointFailuresAreCopiedCanonicalUniqueAndBounded() {
        var supplied = new ArrayList<>(List.of(RECOVERY, FAILURE));
        var record = operationRecord(1, PLAN_HASH, RECORDED, observation(State.RECOVERY_REQUIRED), supplied);
        supplied.clear();
        assertEquals(List.of(FAILURE, RECOVERY), record.failures());
        assertThrowsExactly(UnsupportedOperationException.class, () -> record.failures().clear());
        reject("Checkpoint failures must be non-null and unique", () -> checkpoint(JOB, State.BLOCKED, Arrays.asList((ManifestFailure) null)));
        reject("Checkpoint failures must be non-null and unique", () -> checkpoint(JOB, State.BLOCKED, List.of(FAILURE, FAILURE)));
        reject("Checkpoint failure limit exceeded", () -> checkpoint(JOB, State.BLOCKED, Collections.nCopies(65, FAILURE)));
    }

    @Test
    void everyApprovedWorkflowFailureRemainsCanonicalWithoutTruncation() {
        var canonical = new ArrayList<ManifestFailure>();
        for (var phase : Phase.values()) {
            for (var code : Code.values()) {
                try {
                    canonical.add(new ManifestFailure(phase, code));
                } catch (IllegalArgumentException rejectedPair) {
                    // The frozen S1A pair matrix owns rejected pairs; this test retains all accepted supplied pairs.
                }
            }
        }
        assertEquals(38, canonical.size());
        var supplied = new ArrayList<>(canonical);
        Collections.reverse(supplied);
        assertEquals(canonical, checkpoint(JOB, State.BLOCKED, supplied).failures());
        assertEquals(canonical, new OperationState(id(1), observation(State.RECOVERY_REQUIRED), supplied).failures());
        assertEquals(canonical, new ManifestState(plan(), receipt(1), JobState.RUNNING, RECORDED, initialStates(), supplied).jobFailures());
        var persistence = canonical.stream().filter(f -> f.phase() == Phase.PERSISTENCE).toList();
        var reversed = new ArrayList<>(persistence);
        Collections.reverse(reversed);
        assertEquals(persistence, new ProvenancePersistenceException(reversed, PublicationOutcome.UNKNOWN, Optional.empty()).failures());
    }

    @Test
    void localRecordsDoNotValidateGlobalSequenceLinksOrEligibility() {
        var record = operationRecord(Long.MAX_VALUE, HEAD_HASH, RECORDED, observation(State.COMPLETED), List.of());
        assertEquals(Long.MAX_VALUE, record.sequence());
        assertEquals(HEAD_HASH, record.previousRecordSha256());
        assertEquals(id(1), record.operationId().orElseThrow());
        assertDoesNotThrow(() -> jobRecord(300002, HEAD_HASH, JobState.COMPLETED, List.of()));
        // Count caps, links, prior transitions, COPY/conversion kind and eligibility require a plan/chain in S2B/S4.
    }

    // C02
    @Test
    void receiptsAndNeutralFailuresAreFrozen() {
        assertArrayEquals(new String[] {"jobId", "sequence", "planSha256", "headSha256"}, names(ManifestReceipt.class));
        assertArrayEquals(new Class<?>[] {UUID.class, long.class, String.class, String.class}, types(ManifestReceipt.class));
        assertArrayEquals(new String[] {"NOT_PUBLISHED", "PUBLISHED", "UNKNOWN"},
                Arrays.stream(PublicationOutcome.values()).map(Enum::name).toArray(String[]::new));
        assertTrue(Modifier.isFinal(ProvenancePersistenceException.class.getModifiers()));
        assertEquals(RuntimeException.class, ProvenancePersistenceException.class.getSuperclass());
        assertEquals(1, ProvenancePersistenceException.class.getDeclaredConstructors().length);
        assertArrayEquals(new Class<?>[] {List.class, PublicationOutcome.class, Optional.class},
                ProvenancePersistenceException.class.getDeclaredConstructors()[0].getParameterTypes());
        assertEquals("java.util.List<" + ManifestFailure.class.getName() + ">",
                ProvenancePersistenceException.class.getDeclaredConstructors()[0].getGenericParameterTypes()[0].getTypeName());
        assertEquals("java.util.Optional<" + ManifestReceipt.class.getName() + ">",
                ProvenancePersistenceException.class.getDeclaredConstructors()[0].getGenericParameterTypes()[2].getTypeName());
        assertArrayEquals(new String[] {"failures", "knownPublication", "outcome"},
                Arrays.stream(ProvenancePersistenceException.class.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers()))
                        .map(m -> m.getName()).sorted().toArray(String[]::new));
    }

    @Test
    void receiptSequenceAndHashShapeAreExact() {
        var anchor = receipt(0);
        assertEquals(anchor.planSha256(), anchor.headSha256());
        assertEquals(Long.MAX_VALUE, receipt(Long.MAX_VALUE).sequence());
        assertDoesNotThrow(() -> new ManifestReceipt(JOB, 1, PLAN_HASH, PLAN_HASH));
        reject("Receipt components must be non-null", () -> new ManifestReceipt(null, 0, PLAN_HASH, PLAN_HASH));
        reject("Receipt components must be non-null", () -> new ManifestReceipt(JOB, 0, null, PLAN_HASH));
        reject("Receipt components must be non-null", () -> new ManifestReceipt(JOB, 0, PLAN_HASH, null));
        for (long sequence : new long[] {Long.MIN_VALUE, -1}) {
            reject("Receipt sequence must be nonnegative", () -> receipt(sequence));
        }
        for (var hash : badHashes()) {
            reject("Receipt hashes must be lowercase SHA-256", () -> new ManifestReceipt(JOB, 1, hash, HEAD_HASH));
            reject("Receipt hashes must be lowercase SHA-256", () -> new ManifestReceipt(JOB, 1, PLAN_HASH, hash));
        }
        reject("Plan receipt head must equal plan hash", () -> new ManifestReceipt(JOB, 0, PLAN_HASH, HEAD_HASH));
    }

    @Test
    void publicationOutcomeAndKnownReceiptMatrixIsExhaustive() {
        var failure = new ManifestFailure(Phase.PERSISTENCE, Code.WRITE_FAILED);
        for (var outcome : PublicationOutcome.values()) {
            for (boolean known : new boolean[] {false, true}) {
                var publication = known ? Optional.of(receipt(1)) : Optional.<ManifestReceipt>empty();
                if (known && outcome != PublicationOutcome.PUBLISHED) {
                    reject("Known receipt requires published outcome", () -> new ProvenancePersistenceException(List.of(failure), outcome, publication));
                } else {
                    var exception = new ProvenancePersistenceException(List.of(failure), outcome, publication);
                    assertEquals(outcome, exception.outcome());
                    assertEquals(publication, exception.knownPublication());
                }
            }
        }
        // PUBLISHED without a job receipt is the approved public-export case, not a plan/checkpoint acknowledgement.
    }

    @Test
    void persistenceExceptionIsFixedAndCannotCarryCauseOrSuppressedPayload() {
        var exception = new ProvenancePersistenceException(List.of(new ManifestFailure(Phase.PERSISTENCE, Code.WRITE_FAILED)),
                PublicationOutcome.UNKNOWN, Optional.empty());
        assertEquals("Provenance persistence failed", exception.getMessage());
        assertNull(exception.getCause());
        var foreign = new RuntimeException("synthetic identifying path /private/subject-123");
        assertThrowsExactly(IllegalStateException.class, () -> exception.initCause(foreign));
        exception.addSuppressed(foreign);
        assertEquals(0, exception.getSuppressed().length);
        exception.setStackTrace(new StackTraceElement[] {new StackTraceElement("private", "path", "subject-123", 1)});
        assertEquals(0, exception.getStackTrace().length);
        assertFalse(exception.toString().contains("subject-123"));
    }

    @Test
    void persistenceExceptionFailuresAreRequiredPersistenceOnlyAndCanonical() {
        var first = new ManifestFailure(Phase.PERSISTENCE, Code.WRITE_FAILED);
        var second = new ManifestFailure(Phase.PERSISTENCE, Code.CLEANUP_FAILED);
        var supplied = new ArrayList<>(List.of(second, first));
        var exception = new ProvenancePersistenceException(supplied, PublicationOutcome.PUBLISHED, Optional.of(receipt(1)));
        supplied.clear();
        assertEquals(List.of(first, second), exception.failures());
        assertThrowsExactly(UnsupportedOperationException.class, () -> exception.failures().clear());
        for (int missing = 0; missing < 3; missing++) {
            int index = missing;
            reject("Persistence failure components must be non-null", () -> new ProvenancePersistenceException(
                    index == 0 ? null : List.of(first), index == 1 ? null : PublicationOutcome.NOT_PUBLISHED,
                    index == 2 ? null : Optional.empty()));
        }
        reject("Persistence failures must be nonempty and bounded", () -> new ProvenancePersistenceException(List.of(), PublicationOutcome.UNKNOWN, Optional.empty()));
        reject("Persistence failures must be nonempty and bounded", () -> new ProvenancePersistenceException(Collections.nCopies(65, first), PublicationOutcome.UNKNOWN, Optional.empty()));
        for (var failures : List.of(Arrays.asList((ManifestFailure) null), List.of(first, first), List.of(FAILURE))) {
            reject("Persistence failures must be unique persistence facts", () -> new ProvenancePersistenceException(failures, PublicationOutcome.UNKNOWN, Optional.empty()));
        }
    }

    @Test
    void storeIsOnlyTheFrozenApplicationOutputPort() throws Exception {
        assertTrue(ManifestStore.class.isInterface());
        assertArrayEquals(new Class<?>[] {AutoCloseable.class}, ManifestStore.class.getInterfaces());
        assertArrayEquals(new String[] {"append", "close", "create", "replay"},
                Arrays.stream(ManifestStore.class.getDeclaredMethods()).map(m -> m.getName()).sorted().toArray(String[]::new));
        assertEquals(ManifestReceipt.class, ManifestStore.class.getMethod("create", ProvenanceManifest.class).getReturnType());
        assertEquals(ManifestReceipt.class, ManifestStore.class.getMethod("append", CheckpointRecord.class, ManifestReceipt.class).getReturnType());
        var replay = ManifestStore.class.getMethod("replay", Optional.class);
        assertEquals("java.util.Optional<" + ManifestState.class.getName() + ">", replay.getGenericReturnType().getTypeName());
        assertEquals("java.util.Optional<" + ManifestReceipt.class.getName() + ">", replay.getGenericParameterTypes()[0].getTypeName());
        assertEquals(void.class, ManifestStore.class.getMethod("close").getReturnType());
        for (var method : ManifestStore.class.getDeclaredMethods()) {
            assertTrue(Modifier.isAbstract(method.getModifiers()));
            assertFalse(method.isDefault());
            assertEquals(0, method.getExceptionTypes().length);
        }
        assertEquals("org.cbihi.mrinormalizer.application.port.out", ManifestStore.class.getPackageName());
        // Receipt-free restart semantics are documented on the port; there is deliberately no fake store/replay here.
    }

    // C03
    @Test
    void currentViewCopiesAndRetainsExactPlan() {
        var plan = plan();
        var states = new ArrayList<>(List.of(new OperationState(id(2), observation(State.NOT_STARTED), List.of()),
                new OperationState(id(1), observation(State.IN_PROGRESS), List.of())));
        var failures = new ArrayList<>(List.of(RECOVERY, FAILURE));
        var view = new ManifestState(plan, receipt(1), JobState.RECOVERY_REQUIRED, RECORDED, states, failures);
        states.clear();
        failures.clear();
        assertSame(plan, view.plan());
        assertEquals(List.of(id(1), id(2)), view.operations().stream().map(OperationState::operationId).toList());
        assertEquals(List.of(FAILURE, RECOVERY), view.jobFailures());
        assertEquals(2, view.plan().sources().size());
        assertEquals(view.plan().operations().get(0).destination(), view.plan().operations().get(1).destination());
        assertThrowsExactly(UnsupportedOperationException.class, () -> view.operations().clear());
        assertThrowsExactly(UnsupportedOperationException.class, () -> view.jobFailures().clear());
    }

    @Test
    void currentViewRequiresNonNullComponentsAndMatchingJob() {
        for (int missing = 0; missing < 6; missing++) {
            int index = missing;
            reject("Manifest state components must be non-null", () -> new ManifestState(index == 0 ? null : plan(),
                    index == 1 ? null : receipt(1), index == 2 ? null : JobState.RUNNING, index == 3 ? null : RECORDED,
                    index == 4 ? null : initialStates(), index == 5 ? null : List.of()));
        }
        reject("Manifest state receipt belongs to another job", () -> new ManifestState(plan(),
                new ManifestReceipt(new UUID(0, 2), 1, PLAN_HASH, HEAD_HASH), JobState.RUNNING, RECORDED, initialStates(), List.of()));
    }

    @Test
    void currentViewRequiresUniqueCompletePlannedOperationKeys() {
        reject("Manifest state operation count does not match plan", () -> view(List.of()));
        reject("Manifest state operation count does not match plan", () -> view(Collections.nCopies(100001,
                new OperationState(id(1), observation(State.NOT_STARTED), List.of()))));
        reject("Manifest state operations must be non-null unique planned keys", () -> view(Arrays.asList(null,
                new OperationState(id(2), observation(State.NOT_STARTED), List.of()))));
        reject("Manifest state operations must be non-null unique planned keys", () -> view(List.of(
                new OperationState(id(1), observation(State.NOT_STARTED), List.of()),
                new OperationState(id(1), observation(State.NOT_STARTED), List.of()))));
        reject("Manifest state operations must be non-null unique planned keys", () -> view(List.of(
                new OperationState(id(1), observation(State.NOT_STARTED), List.of()),
                new OperationState(id(3), observation(State.NOT_STARTED), List.of()))));
    }

    @Test
    void currentViewChronologyAndPlanAnchorAreCoherent() {
        reject("Manifest state chronology is invalid", () -> new ManifestState(plan(), receipt(1), JobState.RUNNING,
                CREATED.minusNanos(1), initialStates(), List.of()));
        reject("Manifest state chronology is invalid", () -> new ManifestState(plan(), receipt(1), JobState.RUNNING, START,
                List.of(new OperationState(id(1), observation(State.COMPLETED), List.of()),
                        new OperationState(id(2), observation(State.NOT_STARTED), List.of())), List.of()));
        assertDoesNotThrow(() -> new ManifestState(plan(), receipt(0), JobState.PLANNED, CREATED, initialStates(), List.of()));
        for (var state : JobState.values()) {
            if (state != JobState.PLANNED) reject("Plan anchor state is invalid", () -> new ManifestState(plan(), receipt(0), state, CREATED, initialStates(), List.of()));
        }
        reject("Plan anchor state is invalid", () -> new ManifestState(plan(), receipt(0), JobState.PLANNED, RECORDED, initialStates(), List.of()));
        reject("Plan anchor state is invalid", () -> new ManifestState(plan(), receipt(0), JobState.PLANNED, CREATED, initialStates(), List.of(FAILURE)));
        var early = new Observation(State.IN_PROGRESS, Optional.of(CREATED), Optional.empty(), Optional.empty(), 1, Optional.empty(), Optional.empty());
        reject("Plan anchor state is invalid", () -> new ManifestState(plan(), receipt(0), JobState.PLANNED, CREATED,
                List.of(new OperationState(id(1), early, List.of()), new OperationState(id(2), observation(State.NOT_STARTED), List.of())), List.of()));
        assertDoesNotThrow(() -> new ManifestState(new ProvenanceManifest(1, JOB, CREATED, List.of(), List.of()),
                receipt(0), JobState.PLANNED, CREATED, List.of(), List.of()));
    }

    @Test
    void operationStateFailuresAreImmutableAndStateApplicable() {
        var supplied = new ArrayList<>(List.of(RECOVERY, FAILURE));
        var state = new OperationState(id(1), observation(State.RECOVERY_REQUIRED), supplied);
        supplied.clear();
        assertEquals(List.of(FAILURE, RECOVERY), state.failures());
        assertThrowsExactly(UnsupportedOperationException.class, () -> state.failures().clear());
        reject("Operation state components must be non-null", () -> new OperationState(null, observation(State.NOT_STARTED), List.of()));
        reject("Operation state components must be non-null", () -> new OperationState(id(1), null, List.of()));
        reject("Operation state components must be non-null", () -> new OperationState(id(1), observation(State.NOT_STARTED), null));
        for (var bad : badHashes()) reject("Operation state ID must be lowercase SHA-256", () -> new OperationState(bad, observation(State.NOT_STARTED), List.of()));
        reject("State failure limit exceeded", () -> new OperationState(id(1), observation(State.BLOCKED), Collections.nCopies(65, FAILURE)));
        reject("State failures must be non-null and unique", () -> new OperationState(id(1), observation(State.BLOCKED), Arrays.asList((ManifestFailure) null)));
        reject("State failures must be non-null and unique", () -> new OperationState(id(1), observation(State.BLOCKED), List.of(FAILURE, FAILURE)));
        for (var observed : State.values()) {
            for (var failures : List.of(List.<ManifestFailure>of(), List.of(FAILURE), List.of(RECOVERY))) {
                boolean valid = switch (observed) {
                    case NOT_STARTED, IN_PROGRESS, COMPLETED, IDENTICAL_EXISTING, WRITTEN_UNVERIFIED, SKIPPED_POLICY -> failures.isEmpty();
                    case BLOCKED -> true;
                    case FAILED -> !failures.isEmpty();
                    case RECOVERY_REQUIRED -> failures.contains(RECOVERY);
                };
                Executable construct = () -> new OperationState(id(1), observation(observed), failures);
                if (valid) assertDoesNotThrow(construct);
                else reject("Operation state failures do not match observation", construct);
            }
        }
    }

    @Test
    void jobFailureListsAreCanonicalUniqueBoundedAndCopied() {
        reject("State failure limit exceeded", () -> new ManifestState(plan(), receipt(1), JobState.RUNNING,
                RECORDED, initialStates(), Collections.nCopies(65, FAILURE)));
        reject("State failures must be non-null and unique", () -> new ManifestState(plan(), receipt(1), JobState.RUNNING,
                RECORDED, initialStates(), Arrays.asList((ManifestFailure) null)));
        reject("State failures must be non-null and unique", () -> new ManifestState(plan(), receipt(1), JobState.RUNNING,
                RECORDED, initialStates(), List.of(FAILURE, FAILURE)));
    }

    @Test
    void currentViewRetainsOriginalEvidenceAndDoesNotImplementReducer() {
        var evidence = phase(Scope.CONVERSION, false);
        var observed = withEvidence(State.FAILED, evidence);
        var view = new ManifestState(plan(), receipt(1), JobState.FAILED, RECORDED, List.of(
                new OperationState(id(1), observed, List.of()), new OperationState(id(2), observation(State.NOT_STARTED), List.of())), List.of());
        assertSame(evidence, view.operations().get(0).observation().processingEvidence().orElseThrow());
        assertTrue(view.operations().get(0).failures().isEmpty());
        assertEquals(List.of(DicomToNiftiError.OUTPUT_WRITE_FAILED), evidence.conversionErrors());
        // Positive-receipt global job eligibility and transitions are S2B's contract, not a second reducer here.
        assertDoesNotThrow(() -> new ManifestState(plan(), receipt(1), JobState.COMPLETED, RECORDED, initialStates(), List.of()));
    }

    @Test
    void allProtocolRecordShapesEnumsAndGenericsRemainFrozen() throws Exception {
        assertArrayEquals(new String[] {"schemaVersion", "jobId", "sequence", "previousRecordSha256", "recordedAt", "kind",
                "operationId", "observation", "jobState", "failures"}, names(CheckpointRecord.class));
        assertArrayEquals(new Class<?>[] {int.class, UUID.class, long.class, String.class, Instant.class, Kind.class,
                Optional.class, Optional.class, Optional.class, List.class}, types(CheckpointRecord.class));
        assertArrayEquals(new String[] {"state", "startedAt", "finishedAt", "outputDigest", "matchedExpectedSourceCount",
                "disposition", "processingEvidence"}, names(Observation.class));
        assertArrayEquals(new Class<?>[] {State.class, Optional.class, Optional.class, Optional.class, int.class,
                Optional.class, Optional.class}, types(Observation.class));
        assertArrayEquals(new String[] {"plan", "receipt", "state", "recordedAt", "operations", "jobFailures"}, names(ManifestState.class));
        assertArrayEquals(new Class<?>[] {ProvenanceManifest.class, ManifestReceipt.class, JobState.class, Instant.class,
                List.class, List.class}, types(ManifestState.class));
        assertArrayEquals(new String[] {"operationId", "observation", "failures"}, names(OperationState.class));
        assertArrayEquals(new Class<?>[] {String.class, Observation.class, List.class}, types(OperationState.class));
        assertArrayEquals(new String[] {"OPERATION_OBSERVED", "JOB_OBSERVED"}, Arrays.stream(Kind.values()).map(Enum::name).toArray(String[]::new));
        assertArrayEquals(new String[] {"NOT_STARTED", "IN_PROGRESS", "COMPLETED", "IDENTICAL_EXISTING", "WRITTEN_UNVERIFIED",
                "SKIPPED_POLICY", "BLOCKED", "FAILED", "RECOVERY_REQUIRED"}, Arrays.stream(State.values()).map(Enum::name).toArray(String[]::new));
        assertArrayEquals(new String[] {"NOT_REQUESTED_BY_POLICY", "VALIDATION_REQUIRED", "UNSUPPORTED_INPUT", "INPUT_UNAVAILABLE",
                "RECOVERY_RECONCILIATION_REQUIRED"}, Arrays.stream(Disposition.values()).map(Enum::name).toArray(String[]::new));
        assertArrayEquals(new String[] {"PLANNED", "RUNNING", "COMPLETED", "FAILED", "RECOVERY_REQUIRED"},
                Arrays.stream(JobState.values()).map(Enum::name).toArray(String[]::new));
        generic(CheckpointRecord.class, "operationId", Optional.class, String.class);
        generic(CheckpointRecord.class, "observation", Optional.class, Observation.class);
        generic(CheckpointRecord.class, "jobState", Optional.class, JobState.class);
        generic(CheckpointRecord.class, "failures", List.class, ManifestFailure.class);
        generic(Observation.class, "startedAt", Optional.class, Instant.class);
        generic(Observation.class, "finishedAt", Optional.class, Instant.class);
        generic(Observation.class, "outputDigest", Optional.class, ContentDigest.class);
        generic(Observation.class, "disposition", Optional.class, Disposition.class);
        generic(Observation.class, "processingEvidence", Optional.class, ProcessingEvidence.class);
        generic(ManifestState.class, "operations", List.class, OperationState.class);
        generic(ManifestState.class, "jobFailures", List.class, ManifestFailure.class);
        generic(OperationState.class, "failures", List.class, ManifestFailure.class);
        for (var type : List.of(CheckpointRecord.class, Observation.class, ManifestReceipt.class, ManifestState.class, OperationState.class)) {
            assertTrue(type.isRecord());
            assertEquals(1, type.getDeclaredConstructors().length);
            for (var method : type.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers())) {
                    assertFalse(Modifier.isStatic(method.getModifiers()));
                    assertTrue(List.of("equals", "hashCode", "toString").contains(method.getName())
                            || Arrays.asList(names(type)).contains(method.getName()));
                }
            }
        }
    }

    private static Observation observation(State state) {
        return switch (state) {
            case NOT_STARTED -> new Observation(state, Optional.empty(), Optional.empty(), Optional.empty(), 0, Optional.empty(), Optional.empty());
            case IN_PROGRESS -> new Observation(state, Optional.of(START), Optional.empty(), Optional.empty(), 1, Optional.empty(), Optional.empty());
            case COMPLETED, IDENTICAL_EXISTING -> new Observation(state, Optional.of(START), Optional.of(FINISH), Optional.of(DIGEST), 1, Optional.empty(), Optional.empty());
            case WRITTEN_UNVERIFIED -> new Observation(state, Optional.of(START), Optional.of(FINISH), Optional.of(DIGEST), 1, Optional.empty(), Optional.of(phase(Scope.CONVERSION, true)));
            case SKIPPED_POLICY -> new Observation(state, Optional.empty(), Optional.of(FINISH), Optional.empty(), 0, Optional.of(Disposition.NOT_REQUESTED_BY_POLICY), Optional.empty());
            case BLOCKED -> new Observation(state, Optional.empty(), Optional.of(FINISH), Optional.empty(), 0, Optional.of(Disposition.VALIDATION_REQUIRED), Optional.empty());
            case FAILED -> new Observation(state, Optional.of(START), Optional.of(FINISH), Optional.empty(), 0, Optional.empty(), Optional.empty());
            case RECOVERY_REQUIRED -> new Observation(state, Optional.empty(), Optional.empty(), Optional.empty(), 0, Optional.of(Disposition.RECOVERY_RECONCILIATION_REQUIRED), Optional.empty());
        };
    }

    private static Observation withEvidence(State state, ProcessingEvidence evidence) {
        return new Observation(state, Optional.of(START), Optional.of(FINISH), Optional.empty(), 0, Optional.empty(), Optional.of(evidence));
    }

    private static ProcessingEvidence phase(Scope scope, boolean successful) {
        var facts = new ProcessingEvidence.ConversionFacts(1, 1, 1, ScalarType.INT16, 1, 1, 1, 0,
                new AffineMatrix4(new double[][] {{1, 0, 0, 0}, {0, 1, 0, 0}, {0, 0, 1, 0}, {0, 0, 0, 1}}),
                new IntensityTransform(false, 1, 0), true, false, false, false);
        return new ProcessingEvidence(scope, successful, Optional.empty(),
                scope == Scope.RECONSTRUCTION && !successful ? List.of(DicomProcessingError.EMPTY_INPUT) : List.of(),
                scope == Scope.CONVERSION && !successful ? List.of(DicomToNiftiError.OUTPUT_WRITE_FAILED) : List.of(),
                scope == Scope.CONVERSION && successful ? Optional.of(facts) : Optional.empty());
    }

    private static CheckpointRecord checkpoint(UUID job, State state, List<ManifestFailure> failures) {
        return new CheckpointRecord(1, job, 1, PLAN_HASH, RECORDED, Kind.OPERATION_OBSERVED, Optional.of(id(1)),
                Optional.of(observation(state)), Optional.empty(), failures);
    }

    private static CheckpointRecord operationRecord(long sequence, String previous, Instant time, Observation observation, List<ManifestFailure> failures) {
        return new CheckpointRecord(1, JOB, sequence, previous, time, Kind.OPERATION_OBSERVED,
                Optional.of(id(1)), Optional.of(observation), Optional.empty(), failures);
    }

    private static CheckpointRecord jobRecord(long sequence, String previous, JobState state, List<ManifestFailure> failures) {
        return new CheckpointRecord(1, JOB, sequence, previous, RECORDED, Kind.JOB_OBSERVED,
                Optional.empty(), Optional.empty(), Optional.of(state), failures);
    }

    private static ManifestReceipt receipt(long sequence) {
        return new ManifestReceipt(JOB, sequence, PLAN_HASH, sequence == 0 ? PLAN_HASH : HEAD_HASH);
    }

    private static ProvenanceManifest plan() {
        var assessment = FormatAssessment.fromDetection(DetectionResult.identified(DetectionOutcome.DICOM));
        var a = new RelativePath(RelativePath.Root.SOURCE, "restricted/a");
        var b = new RelativePath(RelativePath.Root.SOURCE, "restricted/b");
        var destination = new RelativePath(RelativePath.Root.OUTPUT, "shared");
        return new ProvenanceManifest(1, JOB, CREATED, List.of(new SourceFileRecord(a, Optional.of(DIGEST), assessment, List.of()),
                new SourceFileRecord(b, Optional.of(DIGEST), assessment, List.of())),
                List.of(new ManifestOperation(id(1), ManifestOperation.Kind.COPY, List.of(a), destination),
                        new ManifestOperation(id(2), ManifestOperation.Kind.COPY, List.of(b), destination)));
    }

    private static List<OperationState> initialStates() {
        return List.of(new OperationState(id(1), observation(State.NOT_STARTED), List.of()),
                new OperationState(id(2), observation(State.NOT_STARTED), List.of()));
    }

    private static ManifestState view(List<OperationState> states) {
        return new ManifestState(plan(), receipt(1), JobState.RUNNING, RECORDED, states, List.of());
    }

    private static List<String> badHashes() {
        return List.of("", "a".repeat(63), "a".repeat(65), "A".repeat(64), "g".repeat(64), "a".repeat(63) + "\n", "/private/subject-123");
    }

    private static String id(int value) { return String.format("%064x", value); }

    private static String[] names(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(c -> c.getName()).toArray(String[]::new);
    }

    private static Class<?>[] types(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(c -> c.getType()).toArray(Class<?>[]::new);
    }

    private static void generic(Class<?> type, String name, Class<?> container, Class<?> value) throws Exception {
        assertEquals(container.getName() + "<" + value.getName() + ">", type.getMethod(name).getGenericReturnType().getTypeName());
    }

    private static void reject(String message, Executable action) {
        var error = assertThrowsExactly(IllegalArgumentException.class, action);
        assertEquals(message, error.getMessage());
        assertNull(error.getCause());
    }
}
