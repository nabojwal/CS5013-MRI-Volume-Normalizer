package org.cbihi.mrinormalizer.infrastructure.filesystem;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.cbihi.mrinormalizer.application.dataset.model.FormatAssessment;
import org.cbihi.mrinormalizer.application.port.out.ManifestStore;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord.*;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Code;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure.Phase;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestState.JobState;
import org.cbihi.mrinormalizer.application.provenance.manifest.ProvenancePersistenceException.PublicationOutcome;
import org.cbihi.mrinormalizer.domain.model.DetectionOutcome;
import org.cbihi.mrinormalizer.domain.model.DetectionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.function.Executable;

/** S01-S09. Authored before the adapter; real local-provider failures are not skipped. */
class ManifestStoreTest {
    @TempDir Path temporary;
    private static final UUID JOB = new UUID(0, 1);
    private static final Instant TIME = Instant.parse("2026-10-05T00:00:00Z");
    private static final ContentDigest DIGEST = new ContentDigest(1, "a".repeat(64));
    private static final ManifestFailure FAILURE = new ManifestFailure(Phase.PERSISTENCE, Code.WRITE_FAILED);
    private static final JsonManifestStore.Hooks NO_FAULT = (event, path) -> { };
    private final JsonManifestCodec codec = new JsonManifestCodec();

    // S01
    @Test void onePlanAndSmallRecordsPublishWithExactReceipts() throws Exception {
        var f = fixture("exact"); var plan = plan(1);
        try (var store = f.open()) {
            var anchor = store.create(plan);
            assertEquals(new ManifestReceipt(JOB, 0, hash(codec.encode(plan)), hash(codec.encode(plan))), anchor);
            var planBytes = Files.readAllBytes(f.job().resolve("plan.json"));
            var started = store.append(job(anchor, JobState.RUNNING, List.of()), anchor);
            var intent = operation(started, 1, State.IN_PROGRESS);
            var acknowledgedIntent = store.append(intent, started);
            // The owner fixture simulates mutation only after acknowledged plan and intent.
            assertEquals(2, acknowledgedIntent.sequence());
            assertTrue(Files.isRegularFile(f.record(2)));
            Path syntheticOutput = f.output().resolve("owner-simulated-output");
            Files.write(syntheticOutput, new byte[] {7});
            var outcome = operation(acknowledgedIntent, 1, State.COMPLETED);
            var done = store.append(outcome, acknowledgedIntent);
            assertEquals(hash(codec.encode(outcome)), done.headSha256());
            assertEquals(anchor.planSha256(), done.planSha256());
            var finish = store.append(job(done, JobState.COMPLETED, List.of()), done);
            assertEquals(4, finish.sequence());
            assertEquals(JobState.COMPLETED, store.replay(Optional.of(anchor)).orElseThrow().state());
            assertArrayEquals(planBytes, Files.readAllBytes(f.job().resolve("plan.json")));
            assertEquals(1, store.metrics().planEncodes());
            assertFalse(Files.exists(f.job().resolve("latest.json")));
            assertFalse(Files.exists(f.source().resolve("synthetic/nonexistent/source")));
        }
    }

    @Test void createRequiresMatchingJobAndValidBoundedPlan() throws Exception {
        var f = fixture("invalid-plan");
        try (var store = f.open()) {
            failure(Code.INVALID_MANIFEST, PublicationOutcome.NOT_PUBLISHED, () -> store.create(null));
            failure(Code.INVALID_REFERENCE, PublicationOutcome.NOT_PUBLISHED,
                    () -> store.create(new ProvenanceManifest(1, UUID.randomUUID(), TIME, List.of(), List.of())));
            assertTrue(store.replay(Optional.empty()).isEmpty());
            assertEquals(0, store.create(plan(0)).sequence());
        }
    }

    // S02
    @Test void planAndAppendIdempotenceNeverOverwrite() throws Exception {
        var f = fixture("idempotence");
        try (var store = f.open()) {
            var anchor = store.create(plan(1));
            assertEquals(anchor, store.create(plan(1)));
            var first = job(anchor, JobState.RUNNING, List.of());
            var head = store.append(first, anchor);
            var bytes = Files.readAllBytes(f.record(1));
            assertEquals(head, store.append(first, anchor));
            var second = operation(head, 1, State.IN_PROGRESS);
            var later = store.append(second, head);
            assertEquals(head, store.append(first, anchor));
            assertEquals(later, store.replay(Optional.empty()).orElseThrow().receipt());
            assertArrayEquals(bytes, Files.readAllBytes(f.record(1)));
            assertEquals(2, store.metrics().publishedRecords());
            var changedPlan = new ProvenanceManifest(1, JOB, TIME.plusSeconds(1), plan(1).sources(), plan(1).operations());
            failure(Code.OUTPUT_CONFLICT, PublicationOutcome.NOT_PUBLISHED, () -> store.create(changedPlan));
        }
    }

    @Test void staleWrongAndChangedSameSequenceHeadsConflict() throws Exception {
        var f = fixture("conflict");
        try (var store = f.open()) {
            var anchor = store.create(plan(1));
            var head = store.append(job(anchor, JobState.RUNNING, List.of()), anchor);
            var changed = new CheckpointRecord(1, JOB, 1, anchor.headSha256(), TIME.plusSeconds(1),
                    Kind.JOB_OBSERVED, Optional.empty(), Optional.empty(), Optional.of(JobState.RUNNING), List.of());
            failure(Code.CHECKPOINT_CONFLICT, PublicationOutcome.NOT_PUBLISHED, () -> store.append(changed, anchor));
            failure(Code.CHECKPOINT_CONFLICT, PublicationOutcome.NOT_PUBLISHED,
                    () -> store.append(operation(head, 1, State.IN_PROGRESS), anchor));
            var wrong = new ManifestReceipt(UUID.randomUUID(), 1, head.planSha256(), head.headSha256());
            failure(Code.CHECKPOINT_CONFLICT, PublicationOutcome.NOT_PUBLISHED,
                    () -> store.append(operation(head, 1, State.IN_PROGRESS), wrong));
            var wrongHash = new ManifestReceipt(JOB, 1, head.planSha256(), "b".repeat(64));
            failure(Code.CHECKPOINT_CONFLICT, PublicationOutcome.NOT_PUBLISHED,
                    () -> store.append(operation(head, 1, State.IN_PROGRESS), wrongHash));
            var wrongPlan = new ManifestReceipt(JOB, 1, "c".repeat(64), head.headSha256());
            failure(Code.CHECKPOINT_CONFLICT, PublicationOutcome.NOT_PUBLISHED,
                    () -> store.append(operation(head, 1, State.IN_PROGRESS), wrongPlan));
            var badLink = new CheckpointRecord(1, JOB, 2, "d".repeat(64), TIME, Kind.JOB_OBSERVED,
                    Optional.empty(), Optional.empty(), Optional.of(JobState.FAILED), List.of(FAILURE));
            failure(Code.CHECKPOINT_CONFLICT, PublicationOutcome.NOT_PUBLISHED, () -> store.append(badLink, head));
            assertEquals(head, store.replay(Optional.empty()).orElseThrow().receipt());
        }
    }

    @Test void appendBeforePlanInvalidTransitionAndClosedUseReject() throws Exception {
        var f = fixture("preconditions"); var store = f.open();
        var fake = new ManifestReceipt(JOB, 0, "a".repeat(64), "a".repeat(64));
        failure(Code.INVALID_MANIFEST, PublicationOutcome.NOT_PUBLISHED,
                () -> store.append(job(fake, JobState.RUNNING, List.of()), fake));
        var anchor = store.create(plan(1));
        failure(Code.INVALID_MANIFEST, PublicationOutcome.NOT_PUBLISHED,
                () -> store.append(job(anchor, JobState.COMPLETED, List.of()), anchor));
        failure(Code.INVALID_MANIFEST, PublicationOutcome.NOT_PUBLISHED, () -> store.append(null, anchor));
        failure(Code.INVALID_REFERENCE, PublicationOutcome.NOT_PUBLISHED,
                () -> store.append(job(anchor, JobState.RUNNING, List.of()), null));
        failure(Code.INVALID_REFERENCE, PublicationOutcome.NOT_PUBLISHED, () -> store.replay(null));
        assertFalse(Files.exists(f.record(1)));
        store.close(); store.close();
        failure(Code.RECOVERY_REQUIRED, PublicationOutcome.NOT_PUBLISHED, () -> store.create(plan(1)));
        failure(Code.RECOVERY_REQUIRED, PublicationOutcome.NOT_PUBLISHED, () -> store.replay(Optional.empty()));
    }

    // S03
    @Test void restartWithoutReceiptAndMinimumReceiptReplayExactly() throws Exception {
        var onlyPlan = fixture("plan-only-restart"); ManifestReceipt planAnchor;
        try (var store = onlyPlan.open()) { planAnchor = store.create(plan(1)); }
        try (var store = onlyPlan.open()) {
            assertEquals(JobState.PLANNED, store.replay(Optional.of(planAnchor)).orElseThrow().state());
        }
        var f = fixture("restart"); ManifestReceipt anchor, older, head;
        try (var store = f.open()) {
            assertTrue(store.replay(Optional.empty()).isEmpty());
            anchor = store.create(plan(1));
            assertEquals(JobState.PLANNED, store.replay(Optional.empty()).orElseThrow().state());
            older = store.append(job(anchor, JobState.RUNNING, List.of()), anchor);
            head = store.append(operation(older, 1, State.IN_PROGRESS), older);
        }
        try (var reopened = f.open()) {
            for (var minimum : List.of(Optional.<ManifestReceipt>empty(), Optional.of(anchor), Optional.of(older), Optional.of(head))) {
                var state = reopened.replay(minimum).orElseThrow();
                assertEquals(head, state.receipt());
                assertEquals(State.IN_PROGRESS, state.operations().get(0).observation().state());
            }
            assertEquals(1, reopened.metrics().prefixPasses());
        }
    }

    @Test void corruptionUnsupportedAndAcknowledgedLossNeverFallBack() throws Exception {
        for (String variant : List.of("unsupported", "whitespace", "broken-link", "wrong-job", "contradiction", "truncated", "wrong-schema")) {
            var f = fixture("corrupt-" + variant); var head = populated(f);
            String original = Files.readString(f.record(2));
            String bad = switch (variant) {
                case "unsupported" -> original.replace("\"schemaVersion\":1", "\"schemaVersion\":2");
                case "whitespace" -> " " + original;
                case "broken-link" -> original.replace(codec.decodeCheckpoint(original.getBytes(StandardCharsets.UTF_8)).previousRecordSha256(), "f".repeat(64));
                case "wrong-job" -> original.replace(JOB.toString(), new UUID(0, 2).toString());
                case "contradiction" -> original.replace("\"operationId\":\"" + id(1), "\"operationId\":\"" + id(2));
                case "wrong-schema" -> original.replace("provenance-checkpoint", "another-checkpoint");
                default -> original.substring(0, original.length() / 2);
            };
            Files.writeString(f.record(2), bad);
            Code expected = variant.equals("unsupported") || variant.equals("wrong-schema") ? Code.UNSUPPORTED_SCHEMA : Code.CORRUPT_CHECKPOINT;
            failure(expected, PublicationOutcome.NOT_PUBLISHED, () -> { try (var ignored = f.open()) { ignored.replay(Optional.of(head)); } });
            assertEquals(bad, Files.readString(f.record(2))); // No corrupt-evidence deletion/repair.
        }
    }

    @Test void minimumReceiptDetectsMissingChangedAndWrongAcknowledgments() throws Exception {
        var absentPlan = fixture("lost-plan"); ManifestReceipt missingAnchor;
        try (var store = absentPlan.open()) { missingAnchor = store.create(plan(1)); }
        Files.delete(absentPlan.job().resolve("plan.json"));
        try (var store = absentPlan.open()) {
            failure(Code.CHECKPOINT_CONFLICT, PublicationOutcome.NOT_PUBLISHED, () -> store.replay(Optional.of(missingAnchor)));
        }
        var f = fixture("floor"); var head = populated(f);
        Files.delete(f.record(2)); // Owner fixture deliberately loses acknowledged final evidence.
        try (var store = f.open()) {
            failure(Code.CHECKPOINT_CONFLICT, PublicationOutcome.NOT_PUBLISHED, () -> store.replay(Optional.of(head)));
            var anchor = store.replay(Optional.empty()).orElseThrow().receipt();
            var wrong = new ManifestReceipt(JOB, 1, anchor.planSha256(), "c".repeat(64));
            failure(Code.CHECKPOINT_CONFLICT, PublicationOutcome.NOT_PUBLISHED, () -> store.replay(Optional.of(wrong)));
            var foreign = new ManifestReceipt(UUID.randomUUID(), 0, anchor.planSha256(), anchor.planSha256());
            failure(Code.CHECKPOINT_CONFLICT, PublicationOutcome.NOT_PUBLISHED, () -> store.replay(Optional.of(foreign)));
        }
        var live = fixture("live-floor");
        try (var store = live.open()) {
            var anchor = store.create(plan(1));
            var first = store.append(job(anchor, JobState.RUNNING, List.of()), anchor);
            Files.writeString(live.record(1), "{}\n");
            failure(Code.CORRUPT_CHECKPOINT, PublicationOutcome.NOT_PUBLISHED, () -> store.replay(Optional.of(first)));
            failure(Code.RECOVERY_REQUIRED, PublicationOutcome.NOT_PUBLISHED,
                    () -> store.append(operation(first, 1, State.IN_PROGRESS), first));
        }
    }

    @Test void orphanGapsWrongBucketsNamesTypesAndUnexpectedEntriesReject() throws Exception {
        for (String variant : List.of("orphan", "gap", "bucket", "name", "type", "extra", "extra-job", "too-many-buckets")) {
            var f = fixture("layout-" + variant); populated(f);
            switch (variant) {
                case "orphan" -> Files.delete(f.job().resolve("plan.json"));
                case "gap" -> Files.delete(f.record(1));
                case "bucket" -> { Files.createDirectory(f.job().resolve("records/000001")); privateDirectory(f.job().resolve("records/000001")); Files.move(f.record(2), f.job().resolve("records/000001/00000000000000000002.json")); }
                case "name" -> Files.move(f.record(2), f.record(2).resolveSibling("2.json"));
                case "type" -> { Files.delete(f.record(2)); Files.createDirectory(f.record(2)); privateDirectory(f.record(2)); }
                case "extra" -> privateFile(f.record(2).resolveSibling("unexpected.json"), new byte[] {1});
                case "extra-job" -> privateFile(f.job().resolve("latest.json"), new byte[] {1});
                default -> { Files.createDirectory(f.job().resolve("records/999999")); privateDirectory(f.job().resolve("records/999999")); }
            }
            failure(Code.CORRUPT_CHECKPOINT, PublicationOutcome.NOT_PUBLISHED, () -> { try (var ignored = f.open()) { ignored.replay(Optional.empty()); } });
        }
    }

    @Test void unsafeFinalSymlinkIsNeverFollowedOrDeleted() throws Exception {
        var f = fixture("symlink"); populated(f);
        var target = temporary.resolve("outside-target"); Files.writeString(target, "leave untouched");
        Files.delete(f.record(2));
        Files.createSymbolicLink(f.record(2), target);
        failure(Code.CONTAINMENT_UNPROVEN, PublicationOutcome.NOT_PUBLISHED, () -> { try (var ignored = f.open()) { ignored.replay(Optional.empty()); } });
        assertEquals("leave untouched", Files.readString(target));
        assertTrue(Files.isSymbolicLink(f.record(2)));
    }

    // S04
    @Test void intentOutcomeAndAcknowledgmentCrashBoundariesAreFactual() throws Exception {
        var f = fixture("crash-outcome");
        var once = new OneFault(JsonManifestStore.Event.BEFORE_PUBLISH);
        ManifestReceipt intentHead;
        try (var store = f.open(once)) {
            once.enabled = false;
            var anchor = store.create(plan(1));
            var running = store.append(job(anchor, JobState.RUNNING, List.of()), anchor);
            intentHead = store.append(operation(running, 1, State.IN_PROGRESS), running);
            once.enabled = true;
            failure(Code.WRITE_FAILED, PublicationOutcome.NOT_PUBLISHED,
                    () -> store.append(operation(intentHead, 1, State.COMPLETED), intentHead));
            assertEquals(intentHead, store.replay(Optional.empty()).orElseThrow().receipt());
        }
        try (var reopened = f.open()) {
            assertEquals(State.IN_PROGRESS, reopened.replay(Optional.of(intentHead)).orElseThrow().operations().get(0).observation().state());
        }
    }

    @Test void prePublicationFaultsNeverPublishOrConsumeSequence() throws Exception {
        for (var event : List.of(JsonManifestStore.Event.BEFORE_STAGE, JsonManifestStore.Event.AFTER_STAGE,
                JsonManifestStore.Event.WRITE, JsonManifestStore.Event.BEFORE_FORCE, JsonManifestStore.Event.AFTER_FORCE,
                JsonManifestStore.Event.BEFORE_PUBLISH)) {
            var creation = fixture("create-fault-" + event); var creationFault = new OneFault(event);
            try (var store = creation.open(creationFault)) {
                failure(Code.WRITE_FAILED, PublicationOutcome.NOT_PUBLISHED, () -> store.create(plan(1)));
                assertTrue(store.replay(Optional.empty()).isEmpty());
                assertFalse(Files.exists(creation.job().resolve("plan.json")));
                assertEquals(0, store.create(plan(1)).sequence());
            }
            var f = fixture("fault-" + event); var fault = new OneFault(event);
            try (var store = f.open(fault)) {
                fault.enabled = false; var anchor = store.create(plan(1)); fault.enabled = true;
                var record = job(anchor, JobState.RUNNING, List.of());
                failure(Code.WRITE_FAILED, PublicationOutcome.NOT_PUBLISHED, () -> store.append(record, anchor));
                assertFalse(Files.exists(f.record(1)));
                assertEquals(anchor, store.replay(Optional.empty()).orElseThrow().receipt());
                assertEquals(1, store.append(record, anchor).sequence());
            }
        }
    }

    @Test void knownPublishedFaultsRetryIdempotentlyAfterAcknowledgmentLoss() throws Exception {
        for (var event : List.of(JsonManifestStore.Event.AFTER_PUBLISH, JsonManifestStore.Event.BEFORE_ACCEPT, JsonManifestStore.Event.AFTER_ACCEPT)) {
            var f = fixture("published-" + event); var fault = new OneFault(event);
            try (var store = f.open(fault)) {
                fault.enabled = false; var anchor = store.create(plan(1)); fault.enabled = true;
                var record = job(anchor, JobState.RUNNING, List.of());
                var error = failure(Code.WRITE_FAILED, PublicationOutcome.PUBLISHED, () -> store.append(record, anchor));
                var acknowledged = error.knownPublication().orElseThrow();
                assertEquals(acknowledged, store.append(record, anchor));
                assertEquals(acknowledged, store.replay(Optional.empty()).orElseThrow().receipt());
                assertEquals(1, store.metrics().publishedRecords());
            }
        }
    }

    @Test void unknownPublicationPoisonsMutationsUntilCloseAndReopen() throws Exception {
        var f = fixture("unknown"); boolean[] enabled = {false};
        JsonManifestStore.Hooks hooks = (event, path) -> {
            if (enabled[0] && (event == JsonManifestStore.Event.AFTER_PUBLISH || event == JsonManifestStore.Event.CLASSIFY)) throw new IOException("private provider details");
        };
        ManifestReceipt anchor;
        try (var store = f.open(hooks)) {
            anchor = store.create(plan(1)); enabled[0] = true;
            var error = failure(Code.RECOVERY_REQUIRED, PublicationOutcome.UNKNOWN,
                    () -> store.append(job(anchor, JobState.RUNNING, List.of()), anchor));
            assertTrue(error.knownPublication().isEmpty());
            failure(Code.RECOVERY_REQUIRED, PublicationOutcome.NOT_PUBLISHED,
                    () -> store.append(job(anchor, JobState.RUNNING, List.of()), anchor));
            failure(Code.RECOVERY_REQUIRED, PublicationOutcome.NOT_PUBLISHED, () -> store.create(plan(1)));
            enabled[0] = false;
        }
        try (var reopened = f.open()) { assertEquals(1, reopened.replay(Optional.of(anchor)).orElseThrow().receipt().sequence()); }
    }

    // S05
    @Test void primaryCleanupAndCloseFailuresRemainExplicit() throws Exception {
        var f = fixture("cleanup"); boolean[] enabled = {false};
        JsonManifestStore.Hooks hooks = (event, path) -> {
            if (enabled[0] && (event == JsonManifestStore.Event.AFTER_PUBLISH || event == JsonManifestStore.Event.CLEANUP)) throw new IOException("confidential");
        };
        var store = f.open(hooks); var anchor = store.create(plan(1)); enabled[0] = true;
        var error = failure(Code.WRITE_FAILED, PublicationOutcome.PUBLISHED,
                () -> store.append(job(anchor, JobState.RUNNING, List.of()), anchor));
        assertTrue(error.failures().contains(new ManifestFailure(Phase.PERSISTENCE, Code.CLEANUP_FAILED)));
        assertTrue(Files.isRegularFile(f.record(1))); assertFalse(Files.exists(f.source().resolve("synthetic/nonexistent/source")));
        failure(Code.CLEANUP_FAILED, PublicationOutcome.NOT_PUBLISHED, store::close);
        enabled[0] = false;
        try (var reopened = f.open()) { assertEquals(1, reopened.replay(Optional.empty()).orElseThrow().receipt().sequence()); }
    }

    @Test void cleanupOnlyFailurePreservesPublishedReceiptAndCommittedBytes() throws Exception {
        var f = fixture("cleanup-only"); var fault = new OneFault(JsonManifestStore.Event.CLEANUP);
        try (var store = f.open(fault)) {
            fault.enabled = false; var anchor = store.create(plan(1)); fault.enabled = true;
            var record = job(anchor, JobState.RUNNING, List.of());
            var error = failure(Code.CLEANUP_FAILED, PublicationOutcome.PUBLISHED, () -> store.append(record, anchor));
            assertEquals(hash(codec.encode(record)), error.knownPublication().orElseThrow().headSha256());
            assertArrayEquals(codec.encode(record), Files.readAllBytes(f.record(1)));
        }
    }

    @Test void leaseReleaseAndChannelCloseFailuresAreVisibleAndBothAttempted() throws Exception {
        for (var event : List.of(JsonManifestStore.Event.LEASE_RELEASE, JsonManifestStore.Event.CHANNEL_CLOSE)) {
            var f = fixture("close-" + event); var fault = new OneFault(event);
            var store = f.open(fault); store.create(plan(1));
            var error = failure(Code.CLEANUP_FAILED, PublicationOutcome.NOT_PUBLISHED, store::close);
            assertEquals("Provenance persistence failed", error.getMessage());
            assertNull(error.getCause()); assertEquals(0, error.getStackTrace().length); assertEquals(0, error.getSuppressed().length);
            failure(Code.CLEANUP_FAILED, PublicationOutcome.NOT_PUBLISHED, store::close);
            try (var next = f.open()) { assertEquals(0, next.replay(Optional.empty()).orElseThrow().receipt().sequence()); }
        }
    }

    @Test void stageCloseFailureIsSeparateAndNeverPublishes() throws Exception {
        for (boolean alsoWriteFailure : List.of(false, true)) {
            var f = fixture("stage-close-" + alsoWriteFailure); boolean[] enabled = {false};
            JsonManifestStore.Hooks hooks = (event, path) -> {
                if (enabled[0] && (event == JsonManifestStore.Event.STAGE_CLOSE
                        || alsoWriteFailure && event == JsonManifestStore.Event.WRITE)) throw new IOException("controlled close failure");
            };
            try (var store = f.open(hooks)) {
                var anchor = store.create(plan(1)); enabled[0] = true;
                var error = failure(Code.CLEANUP_FAILED, PublicationOutcome.NOT_PUBLISHED,
                        () -> store.append(job(anchor, JobState.RUNNING, List.of()), anchor));
                if (alsoWriteFailure) assertTrue(error.failures().contains(FAILURE));
                assertFalse(Files.exists(f.record(1)));
                assertEquals(anchor, store.replay(Optional.empty()).orElseThrow().receipt());
            }
        }
    }

    // S06
    @Test void privateCreationAndJobLeaseAreQualified() throws Exception {
        var f = fixture("lease");
        boolean[] sawStage = {false};
        try (var store = f.open((event, path) -> {
            if (event == JsonManifestStore.Event.AFTER_STAGE) { assertPrivate(path, false); sawStage[0] = true; }
        })) {
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> failure(Code.CHECKPOINT_CONFLICT, PublicationOutcome.NOT_PUBLISHED, f::open));
            var anchor = store.create(plan(1)); store.append(job(anchor, JobState.RUNNING, List.of()), anchor);
            for (var path : List.of(f.namespace(), f.job(), f.job().resolve("records"), f.record(1).getParent())) assertPrivate(path, true);
            for (var path : List.of(f.job().resolve(".lease"), f.job().resolve("plan.json"), f.record(1))) assertPrivate(path, false);
            assertTrue(sawStage[0]);
            var anotherJob = new UUID(0, 2);
            try (var other = new JsonManifestStore(f.source(), f.output(), f.namespace(), anotherJob)) { assertTrue(other.replay(Optional.empty()).isEmpty()); }
        }
        try (var next = f.open()) { assertTrue(next.replay(Optional.empty()).isPresent()); }
    }

    @Test void canonicalContainmentOverlapAndUnsafeEntriesFailClosed() throws Exception {
        var f = fixture("containment");
        failure(Code.CONTAINMENT_UNPROVEN, PublicationOutcome.NOT_PUBLISHED,
                () -> new JsonManifestStore(f.source(), f.source(), f.source().resolve("manifest"), JOB));
        failure(Code.CONTAINMENT_UNPROVEN, PublicationOutcome.NOT_PUBLISHED,
                () -> new JsonManifestStore(f.source(), f.output(), temporary.resolve("outside"), JOB));
        var nested = Files.createDirectory(f.source().resolve("nested"));
        failure(Code.CONTAINMENT_UNPROVEN, PublicationOutcome.NOT_PUBLISHED,
                () -> new JsonManifestStore(f.source(), nested, nested.resolve("manifest"), JOB));
        try (var store = f.open()) { store.create(plan(1)); }
        Files.delete(f.job().resolve(".lease")); Files.createSymbolicLink(f.job().resolve(".lease"), temporary.resolve("external-lease"));
        failure(Code.CONTAINMENT_UNPROVEN, PublicationOutcome.NOT_PUBLISHED, f::open);
        assertTrue(Files.isSymbolicLink(f.job().resolve(".lease")));
    }

    @Test void configuredSymlinkAncestorIsCanonicalizedRatherThanBlanketRejected() throws Exception {
        var f = fixture("configured-link"); var alias = temporary.resolve("output-alias"); Files.createSymbolicLink(alias, f.output());
        try (var store = new JsonManifestStore(f.source(), alias, alias.resolve("manifest"), JOB)) {
            assertEquals(0, store.create(plan(1)).sequence());
        }
        assertTrue(Files.isRegularFile(f.job().resolve("plan.json")));
    }

    @Test void existingBroadAccessIsRejectedWithoutChangingParentPermissions() throws Exception {
        var f = fixture("broad"); Files.createDirectory(f.namespace());
        if (Files.getFileAttributeView(f.namespace(), java.nio.file.attribute.PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(f.namespace(), PosixFilePermissions.fromString("rwxr-xr-x"));
            var before = Files.getPosixFilePermissions(f.namespace());
            failure(Code.ACCESS_CONTROL_UNAVAILABLE, PublicationOutcome.NOT_PUBLISHED, f::open);
            assertEquals(before, Files.getPosixFilePermissions(f.namespace()));
        } else {
            var view = Files.getFileAttributeView(f.namespace(), AclFileAttributeView.class);
            assertNotNull(view, "S09 requires a supported ACL or POSIX provider");
            var before = view.getAcl();
            var broad = java.nio.file.attribute.AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                    .setPrincipal(f.namespace().getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("Everyone"))
                    .setPermissions(java.nio.file.attribute.AclEntryPermission.READ_DATA).build();
            var acl = new ArrayList<>(before); acl.add(broad); view.setAcl(acl);
            var exposed = view.getAcl(); failure(Code.ACCESS_CONTROL_UNAVAILABLE, PublicationOutcome.NOT_PUBLISHED, f::open);
            assertEquals(exposed, view.getAcl());
        }
    }

    // S07
    @Test void budgetReservationsSequenceAndBucketsAreBounded() {
        var limits = JsonManifestStore.Limits.DEFAULT;
        assertEquals(64 * 1024 * 1024, limits.planBytes()); assertEquals(16 * 1024, limits.recordBytes());
        assertEquals(300001, limits.recordCount()); assertEquals(256L * 1024 * 1024, limits.journalBytes());
        long cap = limits.journalBytes(); int record = 500;
        JsonManifestStore.checkCapacity(1, cap - record - 32768, record, true, false, limits);
        failure(Code.RESOURCE_LIMIT, PublicationOutcome.NOT_PUBLISHED,
                () -> JsonManifestStore.checkCapacity(1, cap - record - 32767, record, true, false, limits));
        JsonManifestStore.checkCapacity(1, cap - record - 16384, record, false, false, limits);
        failure(Code.RESOURCE_LIMIT, PublicationOutcome.NOT_PUBLISHED,
                () -> JsonManifestStore.checkCapacity(1, cap - record - 16383, record, false, false, limits));
        JsonManifestStore.checkCapacity(300000, cap - record, record, false, true, limits);
        failure(Code.RESOURCE_LIMIT, PublicationOutcome.NOT_PUBLISHED,
                () -> JsonManifestStore.checkCapacity(300001, 0, record, false, true, limits));
        failure(Code.RESOURCE_LIMIT, PublicationOutcome.NOT_PUBLISHED,
                () -> JsonManifestStore.checkCapacity(299999, 0, record, true, false, limits));
        assertEquals(300001, JsonManifestStore.checkedNext(300000));
        failure(Code.RESOURCE_LIMIT, PublicationOutcome.NOT_PUBLISHED, () -> JsonManifestStore.checkedNext(Long.MAX_VALUE));
        assertEquals("000000", JsonManifestStore.bucketName(999)); assertEquals("000000", JsonManifestStore.bucketName(1000));
        assertEquals("000001", JsonManifestStore.bucketName(1001));
    }

    @Test void resourceLimitOccursBeforePublishingUnreservableIntent() throws Exception {
        var f = fixture("reserve"); var limits = new JsonManifestStore.Limits(64 * 1024 * 1024, 16384, 3, 256L * 1024 * 1024);
        try (var store = f.open(NO_FAULT, limits)) {
            var anchor = store.create(plan(1)); var running = store.append(job(anchor, JobState.RUNNING, List.of()), anchor);
            failure(Code.RESOURCE_LIMIT, PublicationOutcome.NOT_PUBLISHED,
                    () -> store.append(operation(running, 1, State.IN_PROGRESS), running));
            assertFalse(Files.exists(f.record(2)));
            assertEquals(running, store.replay(Optional.empty()).orElseThrow().receipt());
        }
        var control = fixture("reserved-control");
        var controlLimits = new JsonManifestStore.Limits(64 * 1024 * 1024, 16384, 2, 256L * 1024 * 1024);
        try (var store = control.open(NO_FAULT, controlLimits)) {
            var anchor = store.create(plan(1)); var running = store.append(job(anchor, JobState.RUNNING, List.of()), anchor);
            var recovery = new ManifestFailure(Phase.PERSISTENCE, Code.RECOVERY_REQUIRED);
            assertEquals(2, store.append(job(running, JobState.RECOVERY_REQUIRED, List.of(recovery)), running).sequence());
        }
    }

    @Test void realBucketTransitionLimitsEachDirectoryToThousandRecords() throws Exception {
        var f = fixture("buckets");
        try (var store = f.open()) {
            var head = store.create(plan(500)); head = store.append(job(head, JobState.RUNNING, List.of()), head);
            for (int i = 1; i <= 500; i++) {
                head = store.append(operation(head, i, State.IN_PROGRESS), head);
                head = store.append(operation(head, i, State.COMPLETED), head);
            }
            assertEquals(1001, head.sequence());
            assertTrue(Files.exists(f.record(999))); assertTrue(Files.exists(f.record(1000))); assertTrue(Files.exists(f.record(1001)));
            try (var names = Files.list(f.record(1).getParent())) { assertEquals(1000, names.count()); }
            try (var names = Files.list(f.record(1001).getParent())) { assertEquals(1, names.count()); }
        }
        try (var reopened = f.open()) { assertEquals(1001, reopened.replay(Optional.empty()).orElseThrow().receipt().sequence()); }
    }

    @Test void boundedEncodingAndControlledDiskFullDoNotPublish() throws Exception {
        var f = fixture("small-plan"); var small = new JsonManifestStore.Limits(32, 16384, 300001, 256L * 1024 * 1024);
        try (var store = f.open(NO_FAULT, small)) {
            failure(Code.RESOURCE_LIMIT, PublicationOutcome.NOT_PUBLISHED, () -> store.create(plan(1)));
            assertFalse(Files.exists(f.job().resolve("plan.json")));
        }
        var recordLimit = fixture("small-record"); var tiny = new JsonManifestStore.Limits(64 * 1024 * 1024, 32, 300001, 256L * 1024 * 1024);
        try (var store = recordLimit.open(NO_FAULT, tiny)) {
            var anchor = store.create(plan(1));
            failure(Code.RESOURCE_LIMIT, PublicationOutcome.NOT_PUBLISHED,
                    () -> store.append(job(anchor, JobState.RUNNING, List.of()), anchor));
        }
        var disk = fixture("disk-full"); var fault = new OneFault(JsonManifestStore.Event.WRITE);
        try (var store = disk.open(fault)) {
            fault.enabled = false; var anchor = store.create(plan(1)); fault.enabled = true;
            failure(Code.WRITE_FAILED, PublicationOutcome.NOT_PUBLISHED,
                    () -> store.append(job(anchor, JobState.RUNNING, List.of()), anchor));
            assertEquals(0, store.metrics().publishedRecords());
        }
        var oversized = fixture("oversized"); populated(oversized);
        Files.write(oversized.record(2), new byte[16385]);
        failure(Code.RESOURCE_LIMIT, PublicationOutcome.NOT_PUBLISHED, oversized::open);
    }

    // S08
    @Test void appendDoesNotReencodeOrRereplayFullPlan() throws Exception {
        var f = fixture("linear"); var plan = plan(250);
        try (var store = f.open()) {
            var head = store.create(plan); head = store.append(job(head, JobState.RUNNING, List.of()), head);
            for (int i = 1; i <= 250; i++) {
                head = store.append(operation(head, i, State.IN_PROGRESS), head);
                head = store.append(operation(head, i, State.COMPLETED), head);
            }
            var metrics = store.metrics();
            assertEquals(1, metrics.planEncodes()); assertEquals(0, metrics.planDecodes());
            assertEquals(0, metrics.journalReads()); assertEquals(0, metrics.views());
            assertEquals(501, metrics.checkpointEncodes()); assertEquals(501, metrics.publishedRecords());
            assertEquals(502, metrics.retainedHashes()); // Compact integrity index, not record/history snapshots.
            store.replay(Optional.empty()); assertEquals(1, store.metrics().views());
        }
        try (var reopened = f.open()) {
            assertEquals(1, reopened.metrics().planDecodes()); assertEquals(501, reopened.metrics().journalReads());
            assertEquals(1, reopened.metrics().prefixPasses());
            reopened.replay(Optional.empty()); reopened.replay(Optional.empty());
            assertEquals(501, reopened.metrics().journalReads());
            var head = reopened.replay(Optional.empty()).orElseThrow().receipt();
            reopened.append(job(head, JobState.COMPLETED, List.of()), head);
            assertEquals(501, reopened.metrics().journalReads());
        }
        assertFalse(Arrays.stream(JsonManifestStore.class.getDeclaredFields())
                .anyMatch(field -> field.getGenericType().getTypeName().contains("List<org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord>")));
    }

    // S09: no assumptions/disabled/OS skips, and no unsupported-provider success branch.
    @Test void actualProviderIdentityEvidenceQualifiesPrivateCanonicalPublication() throws Exception {
        var f = fixture("provider-identity");
        boolean windows = f.output().getFileSystem().provider().getClass().getName().equals("sun.nio.fs.WindowsFileSystemProvider");
        var root = Files.readAttributes(f.output(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (windows) assertNull(root.fileKey(), "Authoritative Windows/NTFS provider exposes no basic file key");
        else assertNotNull(root.fileKey(), "Qualified POSIX provider must retain stable key evidence");
        Path[] stage = {null}; int[] publications = {0};
        JsonManifestStore.Hooks hooks = (event, path) -> {
            if (event == JsonManifestStore.Event.AFTER_STAGE) stage[0] = path;
            if (event == JsonManifestStore.Event.AFTER_PUBLISH) {
                assertNotNull(stage[0]);
                var staged = Files.readAttributes(stage[0], BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                var published = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (windows) { assertNull(staged.fileKey()); assertNull(published.fileKey()); }
                else { assertNotNull(staged.fileKey()); assertEquals(staged.fileKey(), published.fileKey()); }
                assertTrue(Files.isSameFile(stage[0], path), "Actual provider must prove hard-link identity");
                assertEquals(path, path.toRealPath()); assertEquals(stage[0], stage[0].toRealPath());
                assertPrivate(stage[0], false); assertPrivate(path, false);
                assertEquals(Files.getFileStore(f.output()), Files.getFileStore(path));
                assertEquals(Files.getOwner(f.output()), Files.getOwner(path));
                assertArrayEquals(Files.readAllBytes(stage[0]), Files.readAllBytes(path));
                publications[0]++;
            }
        };
        ManifestReceipt head;
        try (var store = f.open(hooks)) {
            var anchor = store.create(plan(1));
            head = store.append(job(anchor, JobState.RUNNING, List.of()), anchor);
            assertEquals(2, publications[0]); assertFalse(Files.exists(stage[0], LinkOption.NOFOLLOW_LINKS));
            assertEquals(head, store.replay(Optional.of(head)).orElseThrow().receipt());
        }
        try (var reopened = f.open()) { assertEquals(head, reopened.replay(Optional.of(head)).orElseThrow().receipt()); }
    }

    @Test void cleanupNeverDeletesAReplacementOfItsOwnedStage() throws Exception {
        var f = fixture("cleanup-replacement"); boolean[] enabled = {false}, replaced = {false}, prevented = {false};
        Path[] stage = {null}; byte[] replacement = "unowned replacement\n".getBytes(StandardCharsets.UTF_8);
        JsonManifestStore.Hooks hooks = (event, path) -> {
            if (!enabled[0] || event != JsonManifestStore.Event.CLEANUP) return;
            stage[0] = path;
            try { Files.delete(path); }
            catch (java.nio.file.FileSystemException denied) {
                // A real Windows no-delete-sharing ownership handle blocks substitution.
                assertEquals("sun.nio.fs.WindowsFileSystemProvider", path.getFileSystem().provider().getClass().getName());
                assertNull(Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).fileKey());
                assertTrue(Files.isSameFile(path, f.record(1))); prevented[0] = true; return;
            }
            privateFile(path, replacement); replaced[0] = true;
        };
        var store = f.open(hooks); var anchor = store.create(plan(1)); enabled[0] = true;
        var record = job(anchor, JobState.RUNNING, List.of());
        try {
            store.append(record, anchor);
            assertTrue(prevented[0], "Only a real provider preventing substitution permits successful cleanup");
            assertFalse(replaced[0]); assertFalse(Files.exists(stage[0], LinkOption.NOFOLLOW_LINKS));
        } catch (ProvenancePersistenceException error) {
            assertTrue(replaced[0]); assertFalse(prevented[0]);
            assertTrue(error.failures().contains(new ManifestFailure(Phase.PERSISTENCE, Code.CLEANUP_FAILED)));
            assertEquals(PublicationOutcome.PUBLISHED, error.outcome());
            assertArrayEquals(replacement, Files.readAllBytes(stage[0]));
        } finally {
            enabled[0] = false;
            if (replaced[0]) failure(Code.CLEANUP_FAILED, PublicationOutcome.NOT_PUBLISHED, store::close);
            else store.close();
        }
        assertArrayEquals(codec.encode(record), Files.readAllBytes(f.record(1)));
        if (replaced[0]) assertArrayEquals(replacement, Files.readAllBytes(stage[0]));
    }

    @Test void supportedLocalProviderPublicationIsExecutable() throws Exception {
        var f = fixture("real-provider"); var fs = Files.getFileStore(f.output());
        long before = fs.getUsableSpace(); long start = System.nanoTime();
        ManifestReceipt head;
        try (var store = f.open()) {
            var anchor = store.create(plan(1)); head = store.append(job(anchor, JobState.RUNNING, List.of()), anchor);
            assertArrayEquals(codec.encode(plan(1)), Files.readAllBytes(f.job().resolve("plan.json")));
            assertPrivate(f.record(1), false); assertPrivate(f.job().resolve(".lease"), false);
            assertEquals(head, store.replay(Optional.of(anchor)).orElseThrow().receipt());
        }
        try (var reopened = f.open()) { assertEquals(head, reopened.replay(Optional.of(head)).orElseThrow().receipt()); }
        long logical;
        try (var files = Files.walk(f.job())) {
            logical = files.filter(Files::isRegularFile).mapToLong(path -> { try { return Files.size(path); } catch (IOException error) { throw new java.io.UncheckedIOException(error); } }).sum();
        }
        long allocation = -1;
        long blockSize = fs.getBlockSize();
        assertTrue(blockSize > 0); assertTrue(logical > 0);
        try { allocation = ((Number) Files.getAttribute(f.job().resolve("plan.json"), "unix:blocks")).longValue() * 512; }
        catch (UnsupportedOperationException | IllegalArgumentException ignored) { /* Report unavailable measurement explicitly, not provider acceptance. */ }
        System.out.println("S09 actual platform=" + System.getProperty("os.name") + "; provider=" + f.output().getFileSystem().provider().getClass().getName()
                + "; filesystem=" + fs.type() + "; allocationBlockBytes=" + blockSize + "; logicalBytes=" + logical + "; planAllocatedBytes=" + allocation
                + "; usableSpaceDelta=" + (before - fs.getUsableSpace()) + "; elapsedNanos=" + (System.nanoTime() - start)
                + "; qualification=PASS; other-platform=NOT RUN; power-loss/directory-durability=UNPROVED");
    }

    @Test void concurrentFinalCreationNeverReplacesWinnerAndVisibilityIsComplete() throws Exception {
        var f = fixture("race");
        try (var store = f.open()) {
            var a = f.job().resolve(".stage-" + UUID.randomUUID() + ".tmp");
            var b = f.job().resolve(".stage-" + UUID.randomUUID() + ".tmp");
            var target = f.job().resolve("qualification-target");
            byte[] left = new byte[16384], right = new byte[16384]; Arrays.fill(left, (byte) 1); Arrays.fill(right, (byte) 2);
            privateFile(a, left); privateFile(b, right);
            var gate = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
            try {
                var first = pool.submit(() -> { gate.await(); try { Files.createLink(target, a); return true; } catch (java.nio.file.FileAlreadyExistsException exists) { return false; } });
                var second = pool.submit(() -> { gate.await(); try { Files.createLink(target, b); return true; } catch (java.nio.file.FileAlreadyExistsException exists) { return false; } });
                gate.countDown(); boolean won = first.get(5, TimeUnit.SECONDS); boolean otherWon = second.get(5, TimeUnit.SECONDS);
                assertNotEquals(won, otherWon); assertArrayEquals(won ? left : right, Files.readAllBytes(target));
                assertTrue(Files.isSameFile(target, won ? a : b));
                assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> Files.createLink(target, won ? b : a));
                assertArrayEquals(won ? left : right, Files.readAllBytes(target));
            } finally { pool.shutdownNow(); Files.deleteIfExists(target); Files.deleteIfExists(a); Files.deleteIfExists(b); }
        }
        // Race at the real adapter's BEFORE_PUBLISH boundary: a competing complete final is retained.
        var collision = fixture("adapter-collision"); boolean[] enabled = {false};
        JsonManifestStore.Hooks hooks = (event, path) -> {
            if (enabled[0] && event == JsonManifestStore.Event.BEFORE_PUBLISH) privateFile(path, "conflicting complete bytes\n".getBytes(StandardCharsets.UTF_8));
        };
        try (var store = collision.open(hooks)) {
            var anchor = store.create(plan(1)); enabled[0] = true;
            failure(Code.CHECKPOINT_CONFLICT, PublicationOutcome.NOT_PUBLISHED,
                    () -> store.append(job(anchor, JobState.RUNNING, List.of()), anchor));
            assertEquals("conflicting complete bytes\n", Files.readString(collision.record(1)));
        }
    }

    @Test void unsupportedProviderClassificationFailsClosedAndApiIsFrozen() throws Exception {
        assertFalse(JsonManifestStore.supportedLocalProvider("unknown.Provider", "NTFS", true));
        assertFalse(JsonManifestStore.supportedLocalProvider("sun.nio.fs.WindowsFileSystemProvider", "CIFS", true));
        assertFalse(JsonManifestStore.supportedLocalProvider("sun.nio.fs.LinuxFileSystemProvider", "nfs", false));
        assertFalse(JsonManifestStore.supportedLocalProvider("sun.nio.fs.LinuxFileSystemProvider", "fuse.sshfs", false));
        var f = fixture("foreign-provider");
        var runtimeFilesystem = java.nio.file.FileSystems.getFileSystem(java.net.URI.create("jrt:/"));
        failure(Code.PUBLICATION_UNAVAILABLE, PublicationOutcome.NOT_PUBLISHED,
                () -> new JsonManifestStore(runtimeFilesystem.getPath("/modules"), f.output(), f.namespace(), JOB));
        assertTrue(Modifier.isFinal(JsonManifestStore.class.getModifiers()));
        assertTrue(ManifestStore.class.isAssignableFrom(JsonManifestStore.class));
        var constructors = Arrays.stream(JsonManifestStore.class.getDeclaredConstructors()).filter(c -> Modifier.isPublic(c.getModifiers())).toList();
        assertEquals(1, constructors.size());
        assertArrayEquals(new Class<?>[] {Path.class, Path.class, Path.class, UUID.class}, constructors.get(0).getParameterTypes());
        assertEquals(List.of("append", "close", "create", "replay"), Arrays.stream(JsonManifestStore.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())).map(java.lang.reflect.Method::getName).sorted().toList());
    }

    private Fixture fixture(String name) throws IOException {
        Path base = Files.createDirectory(temporary.resolve(name));
        return new Fixture(Files.createDirectory(base.resolve("source")), Files.createDirectory(base.resolve("output")));
    }
    private record Fixture(Path source, Path output) {
        Path namespace() { return output.resolve("manifest"); }
        Path job() { return namespace().resolve(JOB.toString()); }
        Path record(long sequence) { return job().resolve("records").resolve(String.format(java.util.Locale.ROOT, "%06d", (sequence - 1) / 1000)).resolve(String.format(java.util.Locale.ROOT, "%020d.json", sequence)); }
        JsonManifestStore open() { return new JsonManifestStore(source, output, namespace(), JOB); }
        JsonManifestStore open(JsonManifestStore.Hooks hooks) { return open(hooks, JsonManifestStore.Limits.DEFAULT); }
        JsonManifestStore open(JsonManifestStore.Hooks hooks, JsonManifestStore.Limits limits) { return new JsonManifestStore(source, output, namespace(), JOB, hooks, limits); }
    }
    private ManifestReceipt populated(Fixture f) {
        try (var store = f.open()) {
            var anchor = store.create(plan(1)); var running = store.append(job(anchor, JobState.RUNNING, List.of()), anchor);
            return store.append(operation(running, 1, State.IN_PROGRESS), running);
        }
    }
    private static ProvenanceManifest plan(int operations) {
        var source = new SourceFileRecord(new RelativePath(RelativePath.Root.SOURCE, "synthetic/nonexistent/source"), Optional.of(DIGEST),
                FormatAssessment.fromDetection(DetectionResult.identified(DetectionOutcome.DICOM)), List.of());
        var ops = new ArrayList<ManifestOperation>();
        for (int i = 1; i <= operations; i++) ops.add(new ManifestOperation(id(i), ManifestOperation.Kind.COPY, List.of(source.source()),
                new RelativePath(RelativePath.Root.OUTPUT, "organized/out" + i)));
        return new ProvenanceManifest(1, JOB, TIME, List.of(source), ops);
    }
    private static CheckpointRecord job(ManifestReceipt head, JobState state, List<ManifestFailure> failures) {
        return new CheckpointRecord(1, JOB, head.sequence() + 1, head.headSha256(), TIME, Kind.JOB_OBSERVED,
                Optional.empty(), Optional.empty(), Optional.of(state), failures);
    }
    private static CheckpointRecord operation(ManifestReceipt head, int operation, State state) {
        var observation = state == State.IN_PROGRESS
                ? new Observation(state, Optional.of(TIME), Optional.empty(), Optional.empty(), 1, Optional.empty(), Optional.empty())
                : new Observation(state, Optional.of(TIME), Optional.of(TIME), Optional.of(DIGEST), 1, Optional.empty(), Optional.empty());
        return new CheckpointRecord(1, JOB, head.sequence() + 1, head.headSha256(), TIME, Kind.OPERATION_OBSERVED,
                Optional.of(id(operation)), Optional.of(observation), Optional.empty(), List.of());
    }
    private static String id(int value) { return String.format(java.util.Locale.ROOT, "%064x", value); }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static ProvenancePersistenceException failure(Code code, PublicationOutcome outcome, Executable action) {
        var error = assertThrowsExactly(ProvenancePersistenceException.class, action);
        assertTrue(error.failures().contains(new ManifestFailure(Phase.PERSISTENCE, code)), () -> "Missing " + code + " in " + error.failures());
        assertEquals(outcome, error.outcome()); assertEquals("Provenance persistence failed", error.getMessage());
        assertNull(error.getCause()); assertEquals(0, error.getSuppressed().length); assertEquals(0, error.getStackTrace().length);
        return error;
    }
    private static void assertPrivate(Path path, boolean directory) throws IOException {
        assertFalse(Files.isSymbolicLink(path));
        var posix = Files.getFileAttributeView(path, java.nio.file.attribute.PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix != null) assertEquals(PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"), posix.readAttributes().permissions());
        else {
            var acl = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            assertNotNull(acl); var owner = acl.getOwner();
            assertFalse(acl.getAcl().isEmpty());
            for (var entry : acl.getAcl()) if (entry.type() == AclEntryType.ALLOW) assertEquals(owner, entry.principal(), "Broad ACL grant");
        }
    }
    private static void privateDirectory(Path path) throws IOException {
        var posix = Files.getFileAttributeView(path, java.nio.file.attribute.PosixFileAttributeView.class);
        if (posix != null) Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
        // On Windows these test-local children inherit from the already qualified private job directory.
    }
    private static void privateFile(Path path, byte[] bytes) throws IOException {
        if (Files.getFileAttributeView(path.getParent(), java.nio.file.attribute.PosixFileAttributeView.class) != null)
            Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        else Files.createFile(path);
        Files.write(path, bytes); assertPrivate(path, false);
    }
    private static final class OneFault implements JsonManifestStore.Hooks {
        final JsonManifestStore.Event event; boolean enabled = true; boolean fired;
        OneFault(JsonManifestStore.Event event) { this.event = event; }
        @Override public void at(JsonManifestStore.Event actual, Path path) throws IOException {
            if (enabled && !fired && actual == event) { fired = true; throw new IOException("controlled disk/provider/close fault"); }
        }
    }
}
