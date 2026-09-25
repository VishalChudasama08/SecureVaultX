package com.securevaultx.backend.services;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A fully validated response that is ready to be streamed. Everything that can fail cleanly (ownership,
 * authentication of the whole ciphertext, size policy) has already happened before an instance exists, so
 * the controller can still answer with a proper JSON error. Only genuine I/O failures remain for the body.
 */
public final class StreamedDownload {

    /** Writes the body. Must not close the output stream. */
    @FunctionalInterface
    public interface Body {
        void writeTo(OutputStream out) throws IOException;
    }

    private final String filename;
    private final long contentLength;
    private final Body body;
    private final Runnable onFailure;
    private final Runnable onClose;

    public StreamedDownload(String filename, long contentLength, Body body, Runnable onFailure, Runnable onClose) {
        this.filename = filename;
        this.contentLength = contentLength;
        this.body = body;
        this.onFailure = onFailure;
        this.onClose = onClose;
    }

    public static StreamedDownload of(String filename, long contentLength, Body body) {
        return new StreamedDownload(filename, contentLength, body, () -> { }, () -> { });
    }

    public String filename() {
        return filename;
    }

    /** Exact body length in bytes, or -1 if unknown. */
    public long contentLength() {
        return contentLength;
    }

    public void writeTo(OutputStream out) throws IOException {
        body.writeTo(out);
    }

    /** Called when streaming failed: compensate (e.g. remove a record for an .enc the client never received). */
    public void failed() {
        onFailure.run();
    }

    /** Always called last: release temp files. */
    public void close() {
        onClose.run();
    }
}
