package org.cbihi.mrinormalizer.infrastructure.dicom;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntConsumer;

import org.cbihi.mrinormalizer.application.dataset.dicom.DicomDiscoveryMetadata;
import org.cbihi.mrinormalizer.application.dataset.dicom.DicomMetadataInspection;
import org.cbihi.mrinormalizer.application.dataset.dicom.DicomMetadataInspection.FailureCode;
import org.cbihi.mrinormalizer.application.dataset.dicom.DicomMetadataLimits;
import org.cbihi.mrinormalizer.application.port.out.DicomMetadataReader;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;

/**
 * Closed, read-only metadata inspection. Private iterative frames replace default
 * dataset/sequence loading. Transport read-ahead (including uninterpreted pixel
 * bytes) is bounded below all buffering. Pixel values are never entered, retained
 * or decoded. The same explicit byte policy independently bounds raw reads and
 * consumed prefix plus expanded dataset bytes. No default policy is introduced.
 *
 * Observations do not establish F2/F6 content identity or snapshot isolation.
 * Stable non-null keys strengthen substitution checks; null keys are not fabricated
 * from pathnames. Hostile swaps between checks/open cannot be fully eliminated.
 */
public final class Dcm4cheMetadataReader implements DicomMetadataReader {
    private final Path root;
    private final BasicFileAttributes rootIdentity;
    private final DicomMetadataLimits limits;
    private final Observation observation;

    public Dcm4cheMetadataReader(Path inputRoot, DicomMetadataLimits limits) {
        this(inputRoot, limits, new Observation());
    }

    // Mechanical fault-injection seam only; no alternate public reader/configuration.
    Dcm4cheMetadataReader(Path inputRoot, DicomMetadataLimits limits, Observation observation) {
        if (inputRoot == null || limits == null || observation == null) {
            throw new IllegalArgumentException("Invalid metadata reader configuration");
        }
        try {
            root = inputRoot.toRealPath();
            rootIdentity = observation.attributes(root);
            if (!rootIdentity.isDirectory() || rootIdentity.isSymbolicLink()) throw new IOException();
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Invalid metadata reader configuration");
        }
        this.limits = limits; this.observation = observation;
    }

    @Override public DicomMetadataInspection inspect(RelativePath source) {
        requireSource(source);
        try {
            Path file = root.resolve(source.path()).normalize();
            if (file.equals(root) || !file.startsWith(root)) throw new IOException();
            var before = observe(file);
            DicomMetadataInspection result = inspectStream(source, observation.open(file), limits, observation::value);
            observation.afterRead(file);
            var after = observe(file);
            if (before.size() != after.size()) throw new IOException();
            for (int i = 0; i < before.size(); i++) {
                if (!stable(before.get(i).attributes(), after.get(i).attributes())) throw new IOException();
            }
            return result;
        } catch (IOException | RuntimeException e) {
            return failed(source, FailureCode.METADATA_READ_FAILED);
        }
    }

    private List<Observed> observe(Path file) throws IOException {
        var facts = new ArrayList<Observed>(); Path current = root;
        var a = observation.attributes(current);
        if (!a.isDirectory() || a.isSymbolicLink() || !sameIdentity(rootIdentity, a)
                || !current.toRealPath().equals(root)) throw new IOException();
        facts.add(new Observed(current, a));
        Path relative = root.relativize(file);
        for (int i = 0; i < relative.getNameCount(); i++) {
            current = current.resolve(relative.getName(i)); a = observation.attributes(current);
            if (a.isSymbolicLink() || (i + 1 == relative.getNameCount() ? !a.isRegularFile() : !a.isDirectory())) throw new IOException();
            Path real = current.toRealPath();
            if (!real.startsWith(root) || !real.equals(current)) throw new IOException();
            facts.add(new Observed(current, a));
        }
        return facts;
    }

    private static boolean sameIdentity(BasicFileAttributes a, BasicFileAttributes b) {
        if (a.fileKey() != null || b.fileKey() != null) return a.fileKey() != null && a.fileKey().equals(b.fileKey());
        // Birth/type observations are weaker substitution evidence, never identity proof.
        return a.creationTime().equals(b.creationTime()) && a.isDirectory() == b.isDirectory()
                && a.isRegularFile() == b.isRegularFile();
    }
    private static boolean stable(BasicFileAttributes a, BasicFileAttributes b) {
        return sameIdentity(a, b) && a.size() == b.size() && a.lastModifiedTime().equals(b.lastModifiedTime());
    }
    private record Observed(Path path, BasicFileAttributes attributes) {}

    static class Observation {
        BasicFileAttributes attributes(Path p) throws IOException { return Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
        InputStream open(Path p) throws IOException { return Files.newInputStream(p, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS); }
        void afterRead(Path p) throws IOException {}
        void value(int tag) {}
    }

    // Package-private to exercise the actual guarded path with instrumented streams.
    // This owns/closes input even when probing or decoder construction fails.
    static DicomMetadataInspection inspectStream(RelativePath source, InputStream input,
            DicomMetadataLimits limits, IntConsumer semanticValue) {
        requireSource(source);
        if (input == null || limits == null || semanticValue == null) throw new IllegalArgumentException("Invalid metadata stream configuration");
        try (var raw = new Budget(input, limits.maxMetadataBytes(), 0);
                var buffered = new BufferedInputStream(raw, Math.min(limits.maxMetadataBytes(), 8192))) {
            buffered.mark(132);
            byte[] probe = buffered.readNBytes(132);
            buffered.reset();
            boolean part10 = probe.length == 132 && probe[128] == 'D' && probe[129] == 'I' && probe[130] == 'C' && probe[131] == 'M';
            DicomDiscoveryMetadata metadata;
            if (part10) {
                if (buffered.readNBytes(132).length != 132) throw new EOFException();
                try (var metaDecoder = new DicomInputStream(buffered, UID.ExplicitVRLittleEndian)) {
                    Meta meta = fileMeta(metaDecoder, limits.maxMetadataBytes(), semanticValue);
                    if (meta.syntax == null) throw new Incomplete();
                    establishSyntax(meta.syntax);
                    long prefix = Math.addExact(132L, metaDecoder.getPosition());
                    try (var decoder = new GuardedDecoder(metaDecoder, meta.syntax)) {
                        Budget logical = decoder.guard(limits.maxMetadataBytes(), prefix);
                        metadata = new Parser(decoder, meta.syntax, logical, limits, semanticValue).inspect(meta);
                    }
                }
            } else {
                try (var decoder = new GuardedDecoder(buffered)) {
                    String syntax = decoder.getTransferSyntax();
                    if (!nativeSyntax(syntax)) throw new IOException();
                    Budget logical = decoder.guard(limits.maxMetadataBytes(), 0);
                    metadata = new Parser(decoder, syntax, logical, limits, semanticValue).inspect(new Meta(syntax, null, null));
                }
            }
            return new DicomMetadataInspection(source, Optional.of(metadata), Optional.empty());
        } catch (Limit e) {
            return failed(source, FailureCode.RESOURCE_LIMIT_EXCEEDED);
        } catch (Incomplete e) {
            return failed(source, FailureCode.INCOMPLETE_METADATA);
        } catch (IOException | RuntimeException e) {
            return failed(source, FailureCode.METADATA_READ_FAILED);
        }
    }

    private static void requireSource(RelativePath source) {
        if (source == null || source.root() != RelativePath.Root.SOURCE) throw new IllegalArgumentException("SOURCE reference is required");
    }
    private static DicomMetadataInspection failed(RelativePath source, FailureCode code) {
        return new DicomMetadataInspection(source, Optional.empty(), Optional.of(code));
    }

    private static boolean nativeSyntax(String s) {
        return UID.ImplicitVRLittleEndian.equals(s) || UID.ExplicitVRLittleEndian.equals(s) || UID.ExplicitVRBigEndian.equals(s);
    }
    private static void establishSyntax(String s) throws IOException {
        // Registered standard transfer syntaxes in the pinned toolkit's UID registry.
        // A private/unknown UID must not acquire an assumed explicit-LE interpretation.
        boolean explicitMetadata = s.startsWith("1.2.840.10008.1.2.4.")
                || s.equals("1.2.840.10008.1.2.5") || s.equals("1.2.840.10008.1.2.1.98")
                || UID.DeflatedExplicitVRLittleEndian.equals(s);
        if (!nativeSyntax(s) && !(explicitMetadata && !"?".equals(UID.nameOf(s)))) throw new IOException();
    }

    private static Meta fileMeta(DicomInputStream decoder, int maximum, IntConsumer semantic) throws IOException {
        String syntax = null, sopClass = null, sopInstance = null;
        long end = -1; int previous = 0; boolean any = false;
        for (;;) {
            long position = decoder.getPosition();
            if (end >= 0 && position == end) break;
            if (end >= 0 && (position > end || end - position < 8)) throw new IOException();
            if (end < 0) {
                decoder.mark(4); byte[] peek = decoder.readNBytes(4); decoder.reset();
                if (peek.length == 0) break;
                if (peek.length != 4) throw new EOFException();
                if ((peek[0] & 255) != 2 || peek[1] != 0) break;
            }
            decoder.readHeader(); int tag = decoder.tag(); long length = decoder.unsignedLength();
            if ((tag >>> 16) != 2 || any && Integer.compareUnsigned(tag, previous) <= 0) throw new IOException();
            any = true; previous = tag;
            if (end >= 0 && decoder.getPosition() > end) throw new IOException();
            capacity(length, (long)maximum - 132 - decoder.getPosition());
            if (end >= 0 && length > end - decoder.getPosition()) throw new IOException();
            if ((length & 1) != 0 || decoder.vr() == VR.SQ || decoder.vr() == VR.UN || length < 0) throw new IOException();
            semantic.accept(tag);
            if (tag == Tag.FileMetaInformationGroupLength) {
                if (decoder.vr() != VR.UL || length != 4) throw new IOException();
                byte[] b = new byte[4]; decoder.readFully(b);
                long groupLength = (b[0] & 255L) | (b[1] & 255L) << 8 | (b[2] & 255L) << 16 | (b[3] & 255L) << 24;
                end = Math.addExact(decoder.getPosition(), groupLength);
                if (end > (long)maximum - 132) throw new Limit();
            } else if (tag == Tag.TransferSyntaxUID || tag == Tag.MediaStorageSOPClassUID || tag == Tag.MediaStorageSOPInstanceUID) {
                if (decoder.vr() != VR.UI) throw new IOException();
                String value = readUid(decoder, length);
                if (tag == Tag.TransferSyntaxUID) syntax = value;
                else if (tag == Tag.MediaStorageSOPClassUID) sopClass = value;
                else sopInstance = value;
            } else discard(decoder, length);
        }
        return new Meta(syntax, sopClass, sopInstance);
    }
    private record Meta(String syntax, String sopClass, String sopInstance) {}

    /** No overridden final decoder reads; the guard wraps inherited transport input. */
    private static final class GuardedDecoder extends DicomInputStream {
        GuardedDecoder(InputStream input) throws IOException { super(input); }
        GuardedDecoder(InputStream input, String syntax) throws IOException { super(input, syntax); }
        Budget guard(int maximum, long prefix) throws IOException {
            if (prefix < 0 || prefix > maximum) throw new Limit();
            var guarded = new Budget(in, maximum, prefix); in = guarded; return guarded;
        }
    }

    /** Monotonic byte accounting; zero remaining never becomes fabricated EOF. */
    private static final class Budget extends FilterInputStream {
        private final long maximum; private long used; private boolean eof;
        Budget(InputStream input, long maximum, long used) { super(input); this.maximum = maximum; this.used = used; }
        long remaining() { return maximum - used; }
        @Override public int read() throws IOException {
            if (eof) return -1;
            if (remaining() == 0) throw new Limit();
            int value = in.read(); if (value < 0) eof = true; else used++; return value;
        }
        @Override public int read(byte[] b, int offset, int length) throws IOException {
            java.util.Objects.checkFromIndexSize(offset, length, b.length);
            if (length == 0) return 0;
            if (eof) return -1;
            if (remaining() == 0) throw new Limit();
            int n = in.read(b, offset, (int)Math.min(length, remaining()));
            if (n < 0) eof = true; else used += n; return n;
        }
        @Override public long skip(long count) throws IOException {
            long done = 0; byte[] scratch = new byte[512];
            while (done < count) { int n = read(scratch, 0, (int)Math.min(scratch.length, count - done)); if (n < 0) break; done += n; }
            return done;
        }
        @Override public boolean markSupported() { return false; }
        @Override public void mark(int n) {}
        @Override public void reset() throws IOException { throw new IOException("Metadata accounting cannot be reset"); }
    }

    private enum Kind { ROOT, SEQUENCE, ITEM }
    private static final class Frame {
        final Kind kind; final long end; final long bound; final int depth; final DicomInputStream decoder; final String syntax;
        int previous; boolean any;
        Frame(Kind kind, long end, long enclosingBound, int depth, DicomInputStream decoder, String syntax) {
            this.kind = kind; this.end = end; this.bound = end >= 0 ? end : enclosingBound;
            this.depth = depth; this.decoder = decoder; this.syntax = syntax;
        }
    }

    private static final class Parser {
        private final ArrayDeque<Frame> frames = new ArrayDeque<>();
        private final Budget logical; private final DicomMetadataLimits limits; private final IntConsumer semantic;
        private final Values values = new Values();
        Parser(DicomInputStream decoder, String syntax, Budget logical, DicomMetadataLimits limits, IntConsumer semantic) {
            this.logical = logical; this.limits = limits; this.semantic = semantic;
            frames.push(new Frame(Kind.ROOT, -1, -1, 0, decoder, syntax));
        }
        DicomDiscoveryMetadata inspect(Meta meta) throws IOException {
            while (!frames.isEmpty()) {
                Frame frame = frames.peek();
                if (frame.end >= 0) {
                    if (logical.used > frame.end) throw new IOException();
                    if (logical.used == frame.end) { frames.pop(); continue; }
                }
                if (frame.bound >= 0 && frame.bound - logical.used < 8) throw new IOException();
                long before = logical.used;
                try { frame.decoder.readHeader(); }
                catch (EOFException e) {
                    if (frame.kind == Kind.ROOT && frames.size() == 1 && logical.used == before) break;
                    throw e;
                }
                int tag = frame.decoder.tag(); long length = frame.decoder.unsignedLength();
                if (frame.bound >= 0 && logical.used > frame.bound) throw new IOException();
                if (pixel(tag)) {
                    if (frame.kind != Kind.ROOT || frames.size() != 1) throw new IOException();
                    if (frame.any && Integer.compareUnsigned(tag, frame.previous) <= 0) throw new IOException();
                    break; // Do not validate or enter the value, including malformed/undefined lengths.
                }
                if (tag == Tag.Item) {
                    if (frame.kind != Kind.SEQUENCE) throw new IOException();
                    capacity(length, logical.remaining());
                    long end = endpoint(length); checkEnclosing(end);
                    if (length >= 0 && (length & 1) != 0) throw new IOException();
                    frames.push(new Frame(Kind.ITEM, end, frame.bound, frame.depth, frame.decoder, frame.syntax)); continue;
                }
                if (tag == Tag.ItemDelimitationItem || tag == Tag.SequenceDelimitationItem) {
                    if (length != 0 || frame.end >= 0 || (tag == Tag.ItemDelimitationItem ? frame.kind != Kind.ITEM : frame.kind != Kind.SEQUENCE)) throw new IOException();
                    frames.pop(); continue;
                }
                if (frame.kind == Kind.SEQUENCE || frame.any && Integer.compareUnsigned(tag, frame.previous) <= 0 || (tag >>> 16) == 2) throw new IOException();
                frame.any = true; frame.previous = tag;
                boolean implicit = UID.ImplicitVRLittleEndian.equals(frame.syntax);
                VR encoded = frame.decoder.vr();
                VR vr = implicit ? ElementDictionary.vrOf(tag, null) : encoded;
                boolean un = !implicit && vr == VR.UN;
                if (vr == VR.UN) vr = ElementDictionary.vrOf(tag, null);
                if (length == -1 && vr == VR.UN) vr = VR.SQ;
                if (vr == VR.SQ) {
                    capacity(length, logical.remaining());
                    if (length >= 0 && (length & 1) != 0) throw new IOException();
                    if (length == 0) continue;
                    long depth = (long)frame.depth + 1;
                    if (depth > limits.maxNestingDepth()) throw new Limit();
                    long end = endpoint(length); checkEnclosing(end);
                    DicomInputStream decoder = frame.decoder; String syntax = frame.syntax;
                    if (un) {
                        // UN sequence values are implicit little endian, independently of outer syntax.
                        // All contexts read the shared guarded stream directly: no
                        // depth-dependent chain of decoder-to-decoder read calls.
                        decoder = new DicomInputStream(new NonClosing(logical), UID.ImplicitVRLittleEndian);
                        syntax = UID.ImplicitVRLittleEndian;
                    }
                    frames.push(new Frame(Kind.SEQUENCE, end, frame.bound, (int)depth, decoder, syntax)); continue;
                }
                if (length < 0) throw new IOException();
                capacity(length, logical.remaining());
                long end = endpoint(length); checkEnclosing(end);
                if ((length & 1) != 0) throw new IOException();
                semantic.accept(tag);
                if (frame.kind == Kind.ROOT && expected(tag) != null) {
                    if (vr != expected(tag)) throw new IOException();
                    values.read(tag, frame.decoder, length, un || !UID.ExplicitVRBigEndian.equals(frame.syntax));
                } else discard(frame.decoder, length);
            }
            return values.finish(meta);
        }
        private long endpoint(long length) throws IOException {
            return length == -1 ? -1 : Math.addExact(logical.used, length);
        }
        private void checkEnclosing(long end) throws IOException {
            if (end < 0) return;
            long enclosing = frames.peek().bound;
            if (enclosing >= 0 && end > enclosing) throw new IOException();
        }
    }

    private static final class NonClosing extends FilterInputStream {
        NonClosing(InputStream input) { super(input); }
        @Override public void close() {}
    }
    private static boolean pixel(int tag) { return tag == Tag.PixelData || tag == Tag.FloatPixelData || tag == Tag.DoubleFloatPixelData; }
    private static VR expected(int tag) {
        return switch (tag) {
            case Tag.StudyInstanceUID, Tag.SeriesInstanceUID, Tag.SOPInstanceUID, Tag.SOPClassUID, Tag.FrameOfReferenceUID -> VR.UI;
            case Tag.Modality, Tag.ImageType -> VR.CS;
            case Tag.Rows, Tag.Columns -> VR.US;
            case Tag.NumberOfFrames, Tag.AcquisitionNumber, Tag.TemporalPositionIdentifier, Tag.EchoNumbers -> VR.IS;
            default -> null;
        };
    }
    private static void capacity(long length, long remaining) throws IOException {
        if (length < -1) throw new IOException();
        if (length >= 0 && length > remaining) throw new Limit();
    }
    private static void discard(DicomInputStream decoder, long length) throws IOException {
        byte[] scratch = new byte[(int)Math.min(length, 4096)];
        while (length > 0) { int n = (int)Math.min(length, scratch.length); decoder.readFully(scratch, 0, n); length -= n; }
    }

    private static String readUid(DicomInputStream decoder, long length) throws IOException {
        if (length == 0) return null;
        if (length > 64) throw new IOException();
        byte[] bytes = new byte[(int)length]; decoder.readFully(bytes);
        int count = bytes.length; if (bytes[count - 1] == 0) count--;
        if (count == 0) return null;
        String value = new String(bytes, 0, count, StandardCharsets.US_ASCII);
        int start = 0;
        for (int i = 0; i <= value.length(); i++) {
            if (i == value.length() || value.charAt(i) == '.') {
                if (i == start || i - start > 1 && value.charAt(start) == '0') throw new IOException(); start = i + 1;
            } else if (value.charAt(i) < '0' || value.charAt(i) > '9') throw new IOException();
        }
        return value;
    }

    private static List<String> components(DicomInputStream decoder, long length, boolean cs) throws IOException {
        if (length == 0) return List.of();
        int bound = cs ? 16 : 12; var values = new ArrayList<String>(); var component = new StringBuilder(bound);
        for (long i = 0; i < length; i++) {
            int b = decoder.read(); if (b < 0) throw new EOFException();
            if (b == '\\') {
                values.add(trim(component)); component.setLength(0);
            } else {
                boolean legal = b == ' ' || b >= '0' && b <= '9' || (cs ? b >= 'A' && b <= 'Z' || b == '_' : b == '+' || b == '-');
                if (!legal || component.length() >= bound && !(i == length - 1 && b == ' ' && component.length() == bound)) throw new IOException();
                component.append((char)b);
            }
        }
        values.add(trim(component));
        if (values.size() == 1 && values.get(0).isEmpty()) return List.of();
        return values;
    }
    private static String trim(StringBuilder value) {
        int start = 0, end = value.length();
        while (start < end && value.charAt(start) == ' ') start++;
        while (end > start && value.charAt(end - 1) == ' ') end--;
        return value.substring(start, end);
    }
    private static Integer integer(String value) throws IOException {
        if (value.isEmpty()) throw new IOException();
        int start = value.charAt(0) == '+' || value.charAt(0) == '-' ? 1 : 0;
        if (start == value.length()) throw new IOException();
        for (int i = start; i < value.length(); i++) if (value.charAt(i) < '0' || value.charAt(i) > '9') throw new IOException();
        try { return Integer.valueOf(value); } catch (NumberFormatException e) { throw new IOException("Invalid technical integer"); }
    }

    private static final class Values {
        String study, series, instance, modality, sop, frame;
        Integer rows, columns, frames, acquisition, temporal;
        List<Integer> echoes = List.of(); List<String> imageType = List.of();
        void read(int tag, DicomInputStream decoder, long length, boolean littleEndian) throws IOException {
            switch (expected(tag)) {
                case UI -> {
                    String value = readUid(decoder, length);
                    switch (tag) {
                        case Tag.StudyInstanceUID -> study = value;
                        case Tag.SeriesInstanceUID -> series = value;
                        case Tag.SOPInstanceUID -> instance = value;
                        case Tag.SOPClassUID -> sop = value;
                        case Tag.FrameOfReferenceUID -> frame = value;
                        default -> throw new IOException();
                    }
                }
                case CS -> {
                    var value = components(decoder, length, true);
                    if (tag == Tag.Modality) { if (value.size() > 1) throw new IOException(); modality = value.isEmpty() ? null : value.get(0); }
                    else {
                        if (!value.isEmpty() && (value.size() < 2 || value.get(0).isEmpty() || value.get(1).isEmpty())) throw new IOException();
                        imageType = List.copyOf(value);
                    }
                }
                case US -> {
                    if (length == 0) return;
                    if (length != 2) throw new IOException();
                    int first = decoder.read(), second = decoder.read(); if (first < 0 || second < 0) throw new EOFException();
                    int value = littleEndian ? first | second << 8 : first << 8 | second;
                    if (tag == Tag.Rows) rows = value; else columns = value;
                }
                case IS -> {
                    var value = components(decoder, length, false);
                    if (tag == Tag.EchoNumbers) {
                        var numbers = new ArrayList<Integer>(); for (String s : value) numbers.add(integer(s)); echoes = List.copyOf(numbers);
                    } else {
                        if (value.size() > 1) throw new IOException(); Integer number = value.isEmpty() ? null : integer(value.get(0));
                        if (tag == Tag.NumberOfFrames) { if (number != null && number <= 0) throw new IOException(); frames = number; }
                        else if (tag == Tag.AcquisitionNumber) acquisition = number;
                        else temporal = number;
                    }
                }
                default -> throw new IOException();
            }
        }
        DicomDiscoveryMetadata finish(Meta meta) throws IOException {
            if (meta.sopClass != null && sop != null && !meta.sopClass.equals(sop)
                    || meta.sopInstance != null && instance != null && !meta.sopInstance.equals(instance)) throw new IOException();
            if (study == null || series == null || instance == null || modality == null || sop == null || meta.syntax == null) throw new Incomplete();
            return new DicomDiscoveryMetadata(study, series, instance, modality, sop, meta.syntax,
                    Optional.ofNullable(frame), Optional.ofNullable(rows), Optional.ofNullable(columns), Optional.ofNullable(frames),
                    Optional.ofNullable(acquisition), Optional.ofNullable(temporal), echoes, imageType);
        }
    }

    private static final class Limit extends IOException { Limit() { super("Metadata policy exhausted"); } }
    private static final class Incomplete extends IOException { Incomplete() { super("Required metadata not observed"); } }
}
