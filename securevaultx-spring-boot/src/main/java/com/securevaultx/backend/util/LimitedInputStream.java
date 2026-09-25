package com.securevaultx.backend.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;

/**
 * Enforces a hard byte limit while streaming. Used so size policies are checked against the bytes
 * actually received, not only against a (forgeable) Content-Length header.
 */
public final class LimitedInputStream extends FilterInputStream {

    private final long limit;
    private final Supplier<? extends RuntimeException> onExceeded;
    private long count;

    public LimitedInputStream(InputStream in, long limit, Supplier<? extends RuntimeException> onExceeded) {
        super(in);
        this.limit = limit;
        this.onExceeded = onExceeded;
    }

    public long bytesRead() {
        return count;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b >= 0 && ++count > limit) {
            throw onExceeded.get();
        }
        return b;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        int n = super.read(buf, off, len);
        if (n > 0) {
            count += n;
            if (count > limit) {
                throw onExceeded.get();
            }
        }
        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        long skipped = super.skip(n);
        count += Math.max(0, skipped);
        if (count > limit) {
            throw onExceeded.get();
        }
        return skipped;
    }

    @Override
    public boolean markSupported() {
        return false;
    }
}
