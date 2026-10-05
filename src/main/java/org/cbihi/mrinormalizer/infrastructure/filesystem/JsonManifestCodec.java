package org.cbihi.mrinormalizer.infrastructure.filesystem;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.cbihi.mrinormalizer.application.dataset.model.AssessmentReason;
import org.cbihi.mrinormalizer.application.dataset.model.ConversionReadiness;
import org.cbihi.mrinormalizer.application.dataset.model.FormatAssessment;
import org.cbihi.mrinormalizer.application.dataset.model.FormatVariant;
import org.cbihi.mrinormalizer.application.dataset.model.SupportStatus;
import org.cbihi.mrinormalizer.application.dataset.model.ValidityStatus;
import org.cbihi.mrinormalizer.application.provenance.manifest.CheckpointRecord;
import org.cbihi.mrinormalizer.application.provenance.manifest.ContentDigest;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestFailure;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestOperation;
import org.cbihi.mrinormalizer.application.provenance.manifest.ManifestState;
import org.cbihi.mrinormalizer.application.provenance.manifest.ProcessingEvidence;
import org.cbihi.mrinormalizer.application.provenance.manifest.ProvenanceManifest;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;
import org.cbihi.mrinormalizer.application.provenance.manifest.SourceFileRecord;
import org.cbihi.mrinormalizer.domain.error.DicomProcessingError;
import org.cbihi.mrinormalizer.domain.error.DicomToNiftiError;
import org.cbihi.mrinormalizer.domain.model.AffineMatrix4;
import org.cbihi.mrinormalizer.domain.model.DetectionDiagnostic;
import org.cbihi.mrinormalizer.domain.model.DetectionOutcome;
import org.cbihi.mrinormalizer.domain.model.DetectionResult;
import org.cbihi.mrinormalizer.domain.model.ImagingFormat;
import org.cbihi.mrinormalizer.domain.model.IntensityTransform;
import org.cbihi.mrinormalizer.domain.model.ScalarType;

/** Fixed internal v1 wire language only; no arbitrary JSON values or filesystem behavior. */
final class JsonManifestCodec {
    private static final int PLAN_BYTES = 64 * 1024 * 1024;
    private static final int CHECKPOINT_BYTES = 16 * 1024;
    private static final String PLAN_START = "{\"schema\":\"org.cbihi.mrinormalizer.provenance-plan\",\"schemaVersion\":";
    private static final String CHECKPOINT_START = "{\"schema\":\"org.cbihi.mrinormalizer.provenance-checkpoint\",\"schemaVersion\":";

    JsonManifestCodec() { }

    byte[] encode(ProvenanceManifest plan) {
        require(plan != null);
        var writer = new Writer(PLAN_BYTES);
        writer.plan(plan);
        return writer.bytes();
    }

    ProvenanceManifest decodePlan(byte[] canonicalUtf8) {
        try {
            var decoder = decoder(canonicalUtf8, PLAN_BYTES);
            var plan = decoder.plan();
            decoder.finish();
            require(Arrays.equals(canonicalUtf8, encode(plan)));
            return plan;
        } catch (IllegalArgumentException | DateTimeException ignored) {
            throw invalid();
        }
    }

    byte[] encode(CheckpointRecord checkpoint) {
        require(checkpoint != null);
        var writer = new Writer(CHECKPOINT_BYTES);
        writer.checkpoint(checkpoint);
        return writer.bytes();
    }

    CheckpointRecord decodeCheckpoint(byte[] canonicalUtf8) {
        try {
            var decoder = decoder(canonicalUtf8, CHECKPOINT_BYTES);
            var record = decoder.checkpoint();
            decoder.finish();
            require(Arrays.equals(canonicalUtf8, encode(record)));
            return record;
        } catch (IllegalArgumentException | DateTimeException ignored) {
            throw invalid();
        }
    }

    private static Decoder decoder(byte[] bytes, int limit) {
        require(bytes != null && bytes.length > 0 && bytes.length <= limit);
        try {
            var text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            return new Decoder(text);
        } catch (CharacterCodingException ignored) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid canonical provenance JSON");
    }

    private static void require(boolean valid) { if (!valid) throw invalid(); }

    /** Bounded emitter with explicit typed shapes and literal field ordering. */
    private static final class Writer {
        private final StringBuilder text = new StringBuilder();
        private final int limit;
        private int byteCount;

        private Writer(int limit) { this.limit = limit; }

        private void append(String value) {
            int size = value.getBytes(StandardCharsets.UTF_8).length;
            require(size <= limit - byteCount);
            text.append(value);
            byteCount += size;
        }

        private void quoted(String value) {
            var escaped = new StringBuilder(value.length() + 2);
            escaped.append('"');
            for (int i = 0; i < value.length();) {
                char unit = value.charAt(i);
                require(!Character.isLowSurrogate(unit) && (!Character.isHighSurrogate(unit)
                        || i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1))));
                int scalar = value.codePointAt(i);
                require(!Character.isISOControl(scalar));
                if (scalar == '"' || scalar == '\\') escaped.append('\\');
                escaped.appendCodePoint(scalar);
                i += Character.charCount(scalar);
            }
            escaped.append('"');
            append(escaped.toString());
        }

        private void number(long value) { append(Long.toString(value)); }
        private void number(double value) { require(Double.isFinite(value)); append(Double.toString(value)); }
        private void bool(boolean value) { append(value ? "true" : "false"); }
        private void instant(Optional<Instant> value) { if (value.isPresent()) quoted(value.orElseThrow().toString()); else append("null"); }

        private void plan(ProvenanceManifest value) {
            append(PLAN_START); number(value.schemaVersion()); append(",\"jobId\":"); quoted(value.jobId().toString());
            append(",\"createdAt\":"); quoted(value.createdAt().toString());
            append(",\"roots\":[\"SOURCE\",\"OUTPUT\"],\"sources\":[");
            boolean first = true;
            for (var source : value.sources()) { if (!first) append(","); first = false; source(source); }
            append("],\"operations\":["); first = true;
            for (var operation : value.operations()) { if (!first) append(","); first = false; operation(operation); }
            append("]}");
        }

        private void source(SourceFileRecord value) {
            append("{\"source\":"); path(value.source()); append(",\"digest\":"); digest(value.digest());
            append(",\"assessment\":"); assessment(value.assessment()); append(",\"failures\":"); failures(value.failures()); append("}");
        }

        private void path(RelativePath value) {
            append("{\"root\":"); quoted(value.root().name()); append(",\"path\":"); quoted(value.path()); append("}");
        }

        private void digest(Optional<ContentDigest> value) {
            if (value.isEmpty()) { append("null"); return; }
            var actual = value.orElseThrow();
            append("{\"sizeBytes\":"); number(actual.sizeBytes()); append(",\"sha256\":"); quoted(actual.sha256()); append("}");
        }

        private void assessment(FormatAssessment value) {
            var raw = value.initialDetection();
            append("{\"initialDetection\":{\"outcome\":"); quoted(raw.outcome().name()); append(",\"diagnostic\":"); quoted(raw.diagnostic().name());
            append(",\"extensionMismatch\":"); bool(raw.extensionMismatch()); append("},\"format\":"); quoted(value.format().name());
            append(",\"variant\":"); quoted(value.variant().name()); append(",\"validity\":"); quoted(value.validity().name());
            append(",\"support\":"); quoted(value.support().name()); append(",\"readiness\":"); quoted(value.readiness().name());
            append(",\"reasons\":["); boolean first = true;
            for (var reason : value.reasons()) { if (!first) append(","); first = false; quoted(reason.name()); }
            append("]}");
        }

        private void operation(ManifestOperation value) {
            append("{\"operationId\":"); quoted(value.operationId()); append(",\"kind\":"); quoted(value.kind().name()); append(",\"sources\":[");
            boolean first = true;
            for (var source : value.sources()) { if (!first) append(","); first = false; path(source); }
            append("],\"destination\":"); path(value.destination()); append("}");
        }

        private void failures(List<ManifestFailure> values) {
            append("["); boolean first = true;
            for (var failure : values) {
                if (!first) append(","); first = false;
                append("{\"phase\":"); quoted(failure.phase().name()); append(",\"code\":"); quoted(failure.code().name()); append("}");
            }
            append("]");
        }

        private void checkpoint(CheckpointRecord value) {
            append(CHECKPOINT_START); number(value.schemaVersion()); append(",\"jobId\":"); quoted(value.jobId().toString());
            append(",\"sequence\":"); number(value.sequence()); append(",\"previousRecordSha256\":"); quoted(value.previousRecordSha256());
            append(",\"recordedAt\":"); quoted(value.recordedAt().toString()); append(",\"kind\":"); quoted(value.kind().name());
            append(",\"operationId\":"); if (value.operationId().isPresent()) quoted(value.operationId().orElseThrow()); else append("null");
            append(",\"observation\":"); if (value.observation().isPresent()) observation(value.observation().orElseThrow()); else append("null");
            append(",\"jobState\":"); if (value.jobState().isPresent()) quoted(value.jobState().orElseThrow().name()); else append("null");
            append(",\"failures\":"); failures(value.failures()); append("}");
        }

        private void observation(CheckpointRecord.Observation value) {
            append("{\"state\":"); quoted(value.state().name()); append(",\"startedAt\":"); instant(value.startedAt());
            append(",\"finishedAt\":"); instant(value.finishedAt()); append(",\"outputDigest\":"); digest(value.outputDigest());
            append(",\"matchedExpectedSourceCount\":"); number(value.matchedExpectedSourceCount()); append(",\"disposition\":");
            if (value.disposition().isPresent()) quoted(value.disposition().orElseThrow().name()); else append("null");
            append(",\"processingEvidence\":"); if (value.processingEvidence().isPresent()) processing(value.processingEvidence().orElseThrow()); else append("null");
            append("}");
        }

        private void processing(ProcessingEvidence value) {
            append("{\"scope\":"); quoted(value.scope().name()); append(",\"phaseSuccessful\":"); bool(value.phaseSuccessful());
            append(",\"sourceSummary\":"); if (value.sourceSummary().isPresent()) summary(value.sourceSummary().orElseThrow()); else append("null");
            append(",\"reconstructionErrors\":["); boolean first = true;
            for (var error : value.reconstructionErrors()) { if (!first) append(","); first = false; quoted(error.name()); }
            append("],\"conversionErrors\":["); first = true;
            for (var error : value.conversionErrors()) { if (!first) append(","); first = false; quoted(error.name()); }
            append("],\"conversionFacts\":"); if (value.conversionFacts().isPresent()) facts(value.conversionFacts().orElseThrow()); else append("null");
            append("}");
        }

        private void summary(ProcessingEvidence.SourceSummary value) {
            append("{\"selectedSourceFingerprint\":");
            if (value.selectedSourceFingerprint().isPresent()) {
                append("{\"kind\":\"SELECTED_SERIES_SHA256\",\"sha256\":"); quoted(value.selectedSourceFingerprint().orElseThrow()); append("}");
            } else append("null");
            append(",\"softwareVersion\":"); quoted(value.softwareVersion()); append(",\"dcm4cheVersion\":"); quoted(value.dcm4cheVersion());
            append(",\"completedAt\":"); quoted(value.completedAt().toString()); append(",\"provenanceSuccessful\":"); bool(value.provenanceSuccessful());
            append(",\"inputCount\":"); number(value.inputCount()); append(",\"acceptedSlices\":"); number(value.acceptedSlices()); append("}");
        }

        private void facts(ProcessingEvidence.ConversionFacts value) {
            append("{\"width\":"); number(value.width()); append(",\"height\":"); number(value.height()); append(",\"depth\":"); number(value.depth());
            append(",\"scalarType\":"); quoted(value.scalarType().name()); append(",\"voxelCount\":"); number(value.voxelCount());
            append(",\"rowSpacingMm\":"); number(value.rowSpacingMm()); append(",\"columnSpacingMm\":"); number(value.columnSpacingMm());
            append(",\"sliceSpacingMm\":"); number(value.sliceSpacingMm()); append(",\"niftiRasVoxelToWorld\":[");
            for (int row = 0; row < 4; row++) {
                if (row > 0) append(","); append("[");
                for (int column = 0; column < 4; column++) { if (column > 0) append(","); number(value.niftiRasVoxelToWorld().get(row, column)); }
                append("]");
            }
            append("],\"intensityTransform\":{\"declared\":"); bool(value.intensityTransform().declared());
            append(",\"slope\":"); number(value.intensityTransform().slope()); append(",\"intercept\":"); number(value.intensityTransform().intercept());
            append("},\"storedVoxelValuesPreserved\":"); bool(value.storedVoxelValuesPreserved()); append(",\"resampled\":"); bool(value.resampled());
            append(",\"interpolated\":"); bool(value.interpolated()); append(",\"voxelOrderChanged\":"); bool(value.voxelOrderChanged());
            append(",\"postWriteValidation\":\"NOT_PERFORMED\"}");
        }

        private byte[] bytes() { append("\n"); return text.toString().getBytes(StandardCharsets.UTF_8); }
    }

    /** Closed typed cursor: every object/array is consumed by its owning schema routine. */
    private static final class Decoder {
        private final String text;
        private int position;
        private int referenceCount;

        private Decoder(String text) { this.text = text; }

        private void expect(String literal) {
            require(text.startsWith(literal, position));
            position += literal.length();
        }

        private boolean take(char value) {
            if (position < text.length() && text.charAt(position) == value) { position++; return true; }
            return false;
        }

        private boolean nil() {
            if (text.startsWith("null", position)) { position += 4; return true; }
            return false;
        }

        private String string(int maxBytes) {
            expect("\"");
            var value = new StringBuilder();
            int bytes = 0;
            while (position < text.length()) {
                if (take('"')) return value.toString();
                int scalar;
                if (take('\\')) {
                    require(position < text.length());
                    scalar = text.charAt(position++);
                    require(scalar == '"' || scalar == '\\');
                } else {
                    scalar = text.codePointAt(position);
                    position += Character.charCount(scalar);
                    require(!Character.isISOControl(scalar));
                }
                bytes += scalar <= 0x7f ? 1 : scalar <= 0x7ff ? 2 : scalar <= 0xffff ? 3 : 4;
                require(bytes <= maxBytes);
                value.appendCodePoint(scalar);
            }
            throw invalid();
        }

        private long integer() {
            int start = position;
            take('-');
            int digits = position;
            while (position < text.length() && text.charAt(position) >= '0' && text.charAt(position) <= '9') {
                position++; require(position - start <= 20);
            }
            require(position > digits);
            var token = text.substring(start, position);
            long value = Long.parseLong(token);
            require(Long.toString(value).equals(token));
            return value;
        }

        private int smallInteger() {
            long value = integer(); require(value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE); return (int) value;
        }

        private double decimal() {
            int start = position;
            while (position < text.length()) {
                char unit = text.charAt(position);
                if (!(unit >= '0' && unit <= '9' || unit == '-' || unit == '+' || unit == '.' || unit == 'E' || unit == 'e')) break;
                position++; require(position - start <= 32);
            }
            require(position > start);
            var token = text.substring(start, position);
            double value = Double.parseDouble(token);
            require(Double.isFinite(value) && Double.toString(value).equals(token));
            return value;
        }

        private boolean bool() {
            if (text.startsWith("true", position)) { position += 4; return true; }
            expect("false"); return false;
        }

        private <E extends Enum<E>> E enumeration(Class<E> type) { return Enum.valueOf(type, string(64)); }
        private Instant instant() { var token = string(40); var value = Instant.parse(token); require(value.toString().equals(token)); return value; }
        private Optional<Instant> optionalInstant() { return nil() ? Optional.empty() : Optional.of(instant()); }
        private UUID jobId() { var token = string(36); var value = UUID.fromString(token); require(value.toString().equals(token)); return value; }
        private void capacity(int size, int limit) { require(size < limit); }
        private void version() { require(smallInteger() == 1); }
        private void finish() { expect("\n"); require(position == text.length()); }

        private ProvenanceManifest plan() {
            expect(PLAN_START); version(); expect(",\"jobId\":"); var job = jobId(); expect(",\"createdAt\":"); var time = instant();
            expect(",\"roots\":[\"SOURCE\",\"OUTPUT\"],\"sources\":[");
            var sources = new ArrayList<SourceFileRecord>();
            if (!take(']')) {
                do { capacity(sources.size(), 100000); sources.add(source()); } while (take(','));
                expect("]");
            }
            expect(",\"operations\":["); var operations = new ArrayList<ManifestOperation>();
            if (!take(']')) {
                do { capacity(operations.size(), 100000); operations.add(operation()); } while (take(','));
                expect("]");
            }
            expect("}"); return new ProvenanceManifest(1, job, time, sources, operations);
        }

        private RelativePath path() {
            expect("{\"root\":"); var root = enumeration(RelativePath.Root.class); expect(",\"path\":"); var path = string(4096);
            expect("}"); return new RelativePath(root, path);
        }

        private Optional<ContentDigest> digest() {
            if (nil()) return Optional.empty();
            expect("{\"sizeBytes\":"); long size = integer(); expect(",\"sha256\":"); var hash = string(64); expect("}");
            return Optional.of(new ContentDigest(size, hash));
        }

        private SourceFileRecord source() {
            expect("{\"source\":"); var source = path(); expect(",\"digest\":"); var digest = digest();
            expect(",\"assessment\":"); var assessment = assessment(); expect(",\"failures\":"); var failures = failures(); expect("}");
            return new SourceFileRecord(source, digest, assessment, failures);
        }

        private FormatAssessment assessment() {
            expect("{\"initialDetection\":{\"outcome\":"); var outcome = enumeration(DetectionOutcome.class);
            expect(",\"diagnostic\":"); var diagnostic = enumeration(DetectionDiagnostic.class); expect(",\"extensionMismatch\":"); boolean mismatch = bool();
            expect("},\"format\":"); var format = enumeration(ImagingFormat.class); expect(",\"variant\":"); var variant = enumeration(FormatVariant.class);
            expect(",\"validity\":"); var validity = enumeration(ValidityStatus.class); expect(",\"support\":"); var support = enumeration(SupportStatus.class);
            expect(",\"readiness\":"); var readiness = enumeration(ConversionReadiness.class); expect(",\"reasons\":[");
            var reasons = new ArrayList<AssessmentReason>();
            if (!take(']')) {
                do { capacity(reasons.size(), 8); reasons.add(enumeration(AssessmentReason.class)); } while (take(','));
                expect("]");
            }
            expect("}"); return new FormatAssessment(new DetectionResult(outcome, diagnostic, mismatch), format, variant, validity, support, readiness, reasons);
        }

        private ManifestOperation operation() {
            expect("{\"operationId\":"); var id = string(64); expect(",\"kind\":"); var kind = enumeration(ManifestOperation.Kind.class);
            expect(",\"sources\":["); var sources = new ArrayList<RelativePath>();
            if (!take(']')) {
                do { capacity(sources.size(), 100000); require(referenceCount < 200000); referenceCount++; sources.add(path()); } while (take(','));
                expect("]");
            }
            expect(",\"destination\":"); var destination = path(); expect("}"); return new ManifestOperation(id, kind, sources, destination);
        }

        private List<ManifestFailure> failures() {
            expect("["); var failures = new ArrayList<ManifestFailure>();
            if (!take(']')) {
                do {
                    capacity(failures.size(), 64); expect("{\"phase\":"); var phase = enumeration(ManifestFailure.Phase.class);
                    expect(",\"code\":"); var code = enumeration(ManifestFailure.Code.class); expect("}"); failures.add(new ManifestFailure(phase, code));
                } while (take(','));
                expect("]");
            }
            return failures;
        }

        private CheckpointRecord checkpoint() {
            expect(CHECKPOINT_START); version(); expect(",\"jobId\":"); var job = jobId(); expect(",\"sequence\":"); long sequence = integer();
            expect(",\"previousRecordSha256\":"); var previous = string(64); expect(",\"recordedAt\":"); var time = instant();
            expect(",\"kind\":"); var kind = enumeration(CheckpointRecord.Kind.class); expect(",\"operationId\":"); var id = nil() ? Optional.<String>empty() : Optional.of(string(64));
            expect(",\"observation\":"); var observed = nil() ? Optional.<CheckpointRecord.Observation>empty() : Optional.of(observation());
            expect(",\"jobState\":"); var state = nil() ? Optional.<ManifestState.JobState>empty() : Optional.of(enumeration(ManifestState.JobState.class));
            expect(",\"failures\":"); var failures = failures(); expect("}");
            return new CheckpointRecord(1, job, sequence, previous, time, kind, id, observed, state, failures);
        }

        private CheckpointRecord.Observation observation() {
            expect("{\"state\":"); var state = enumeration(CheckpointRecord.State.class); expect(",\"startedAt\":"); var start = optionalInstant();
            expect(",\"finishedAt\":"); var finish = optionalInstant(); expect(",\"outputDigest\":"); var digest = digest();
            expect(",\"matchedExpectedSourceCount\":"); int count = smallInteger(); expect(",\"disposition\":");
            var disposition = nil() ? Optional.<CheckpointRecord.Disposition>empty() : Optional.of(enumeration(CheckpointRecord.Disposition.class));
            expect(",\"processingEvidence\":"); var evidence = nil() ? Optional.<ProcessingEvidence>empty() : Optional.of(processing()); expect("}");
            return new CheckpointRecord.Observation(state, start, finish, digest, count, disposition, evidence);
        }

        private ProcessingEvidence processing() {
            expect("{\"scope\":"); var scope = enumeration(ProcessingEvidence.Scope.class); expect(",\"phaseSuccessful\":"); boolean successful = bool();
            expect(",\"sourceSummary\":"); var summary = nil() ? Optional.<ProcessingEvidence.SourceSummary>empty() : Optional.of(summary());
            expect(",\"reconstructionErrors\":["); var reconstruction = new ArrayList<DicomProcessingError>();
            if (!take(']')) {
                do { capacity(reconstruction.size(), 20); reconstruction.add(enumeration(DicomProcessingError.class)); } while (take(',')); expect("]");
            }
            expect(",\"conversionErrors\":["); var conversion = new ArrayList<DicomToNiftiError>();
            if (!take(']')) {
                do { capacity(conversion.size(), 3); conversion.add(enumeration(DicomToNiftiError.class)); } while (take(',')); expect("]");
            }
            expect(",\"conversionFacts\":"); var facts = nil() ? Optional.<ProcessingEvidence.ConversionFacts>empty() : Optional.of(facts()); expect("}");
            return new ProcessingEvidence(scope, successful, summary, reconstruction, conversion, facts);
        }

        private ProcessingEvidence.SourceSummary summary() {
            expect("{\"selectedSourceFingerprint\":"); Optional<String> fingerprint;
            if (nil()) fingerprint = Optional.empty();
            else { expect("{\"kind\":\"SELECTED_SERIES_SHA256\",\"sha256\":"); fingerprint = Optional.of(string(64)); expect("}"); }
            expect(",\"softwareVersion\":"); var software = string(64); expect(",\"dcm4cheVersion\":"); var library = string(64);
            expect(",\"completedAt\":"); var time = instant(); expect(",\"provenanceSuccessful\":"); boolean successful = bool();
            expect(",\"inputCount\":"); int count = smallInteger(); expect(",\"acceptedSlices\":"); int slices = smallInteger(); expect("}");
            return new ProcessingEvidence.SourceSummary(fingerprint, software, library, time, successful, count, slices);
        }

        private ProcessingEvidence.ConversionFacts facts() {
            expect("{\"width\":"); int width = smallInteger(); expect(",\"height\":"); int height = smallInteger(); expect(",\"depth\":"); int depth = smallInteger();
            expect(",\"scalarType\":"); var scalar = enumeration(ScalarType.class); expect(",\"voxelCount\":"); long voxels = integer();
            expect(",\"rowSpacingMm\":"); double rowSpacing = decimal(); expect(",\"columnSpacingMm\":"); double columnSpacing = decimal();
            expect(",\"sliceSpacingMm\":"); double sliceSpacing = decimal(); expect(",\"niftiRasVoxelToWorld\":[");
            double[][] matrix = new double[4][4];
            for (int row = 0; row < 4; row++) {
                if (row > 0) expect(","); expect("[");
                for (int column = 0; column < 4; column++) { if (column > 0) expect(","); matrix[row][column] = decimal(); }
                expect("]");
            }
            expect("],\"intensityTransform\":{\"declared\":"); boolean declared = bool(); expect(",\"slope\":"); double slope = decimal();
            expect(",\"intercept\":"); double intercept = decimal(); expect("},\"storedVoxelValuesPreserved\":"); boolean preserved = bool();
            expect(",\"resampled\":"); boolean resampled = bool(); expect(",\"interpolated\":"); boolean interpolated = bool();
            expect(",\"voxelOrderChanged\":"); boolean reordered = bool(); expect(",\"postWriteValidation\":\"NOT_PERFORMED\"}");
            return new ProcessingEvidence.ConversionFacts(width, height, depth, scalar, voxels, rowSpacing, columnSpacing, sliceSpacing,
                    new AffineMatrix4(matrix), new IntensityTransform(declared, slope, intercept), preserved, resampled, interpolated, reordered);
        }
    }
}
