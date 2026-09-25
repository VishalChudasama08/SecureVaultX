package com.securevaultx.backend.controllers;

import com.securevaultx.backend.services.StreamedDownload;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** Sends a {@link StreamedDownload} as a binary attachment and handles mid-stream failure honestly. */
final class StreamedDownloadWriter {

    private static final Logger log = LoggerFactory.getLogger(StreamedDownloadWriter.class);

    private StreamedDownloadWriter() {
    }

    static void write(HttpServletResponse response, StreamedDownload download) throws IOException {
        try {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                    .filename(download.filename(), StandardCharsets.UTF_8).build().toString());
            if (download.contentLength() >= 0) {
                response.setContentLengthLong(download.contentLength());
            }
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            // If the body stops short, the connection must not be reused: the client then sees a broken transfer
            // (fewer bytes than Content-Length) instead of a file that looks complete.
            response.setHeader(HttpHeaders.CONNECTION, "close");

            OutputStream out = response.getOutputStream();
            download.writeTo(out);
            out.flush();
        } catch (IOException | RuntimeException e) {
            download.failed();
            if (!response.isCommitted()) {
                response.reset(); // nothing was sent yet: let the exception handler answer with a JSON error
                throw e;
            }
            // Headers already sent: cannot change the status. Aborting the transfer is the signal.
            log.warn("Download aborted after the response was committed: {}", e.getClass().getSimpleName());
        } finally {
            download.close();
        }
    }
}
