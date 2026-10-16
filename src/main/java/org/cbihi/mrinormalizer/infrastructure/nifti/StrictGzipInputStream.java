package org.cbihi.mrinormalizer.infrastructure.nifti;

import static org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadFailure.*;
import static org.cbihi.mrinormalizer.infrastructure.nifti.Nifti1VolumeReader.require;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Exactly one gzip member. Owns its inflater; the adapter owns the underlying stream. */
final class StrictGzipInputStream extends InputStream {
    private final PushbackInputStream input;
    private final Inflater inflater;
    private final CRC32 crc = new CRC32();
    private final byte[] encoded = new byte[8192];
    private final byte[] single = new byte[1];
    private long size;
    private int supplied;
    private boolean finished, closed;

    StrictGzipInputStream(InputStream input, long maxHeader) throws IOException {
        this.input = new PushbackInputStream(input, encoded.length);
        // No native inflater exists until optional header validation succeeds.
        CRC32 headerCrc = new CRC32();
        Header header = new Header(this.input, maxHeader, headerCrc);
        require(header.next() == 31 && header.next() == 139 && header.next() == 8, GZIP_INTEGRITY_FAILED);
        int flags = header.next();
        require((flags & 0xe0) == 0, GZIP_INTEGRITY_FAILED);
        for (int i = 0; i < 6; i++) header.next();
        if ((flags & 4) != 0) {
            int n = header.next() | header.next() << 8;
            for (int i = 0; i < n; i++) header.next();
        }
        if ((flags & 8) != 0) while (header.next() != 0) { /* bounded, no text retained */ }
        if ((flags & 16) != 0) while (header.next() != 0) { /* bounded, no text retained */ }
        if ((flags & 2) != 0) {
            int expected = (int) headerCrc.getValue() & 65535;
            int actual = header.raw() | header.raw() << 8;
            require(actual == expected, GZIP_INTEGRITY_FAILED);
        }
        inflater = new Inflater(true);
    }

    @Override public int read() throws IOException {
        return read(single, 0, 1) == -1 ? -1 : Byte.toUnsignedInt(single[0]);
    }

    @Override public int read(byte[] b, int off, int len) throws IOException {
        java.util.Objects.checkFromIndexSize(off, len, b.length);
        if (len == 0) return 0;
        if (finished) return -1;
        require(!closed, READ_FAILED);
        int stalls = 0;
        while (true) {
            Nifti1VolumeReader.interrupted();
            final int n;
            try { n = inflater.inflate(b, off, len); }
            catch (DataFormatException e) { throw new Nifti1VolumeReader.Fault(GZIP_INTEGRITY_FAILED); }
            if (n > 0) { crc.update(b, off, n); size = (size + n) & 0xffff_ffffL; return n; }
            if (inflater.finished()) { trailer(); finished = true; return -1; }
            require(!inflater.needsDictionary(), GZIP_INTEGRITY_FAILED);
            if (inflater.needsInput()) {
                supplied = Nifti1VolumeReader.progressRead(input, encoded, 0, encoded.length);
                require(supplied != -1, GZIP_INTEGRITY_FAILED);
                inflater.setInput(encoded, 0, supplied);
                stalls = 0;
            } else require(++stalls <= 16, GZIP_INTEGRITY_FAILED);
        }
    }

    private void trailer() throws IOException {
        int remaining = inflater.getRemaining();
        if (remaining > 0) input.unread(encoded, supplied - remaining, remaining);
        long expectedCrc = word(), expectedSize = word();
        require(expectedCrc == crc.getValue() && expectedSize == size, GZIP_INTEGRITY_FAILED);
        // One bounded physical lookahead rejects even empty members and zero padding.
        require(input.read() == -1, TRAILING_DATA);
    }

    private long word() throws IOException {
        long value = 0;
        for (int i = 0; i < 4; i++) {
            Nifti1VolumeReader.interrupted();
            int b = input.read(); require(b >= 0, GZIP_INTEGRITY_FAILED); value |= (long) b << (8 * i);
        }
        return value;
    }

    @Override public void close() {
        if (!closed) { closed = true; inflater.end(); }
    }

    private static final class Header {
        private final InputStream input;
        private final long limit;
        private final CRC32 crc;
        private long count;
        Header(InputStream input, long limit, CRC32 crc) { this.input = input; this.limit = limit; this.crc = crc; }
        int raw() throws IOException {
            Nifti1VolumeReader.interrupted(); require(count < limit, RESOURCE_LIMIT);
            int b = input.read(); require(b >= 0, GZIP_INTEGRITY_FAILED); count++; return b;
        }
        int next() throws IOException { int b = raw(); crc.update(b); return b; }
    }
}
