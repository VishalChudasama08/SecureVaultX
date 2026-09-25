package com.securevaultx.backend.controllers;

import com.securevaultx.backend.util.FilenameSanitizer;
import java.nio.charset.StandardCharsets;
import org.springframework.web.util.UriUtils;

/** The client sends the original name percent-encoded (UTF-8) in the X-Filename header. Untrusted input. */
final class FilenameHeader {

    static final String NAME = "X-Filename";

    private FilenameHeader() {
    }

    static String decode(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return FilenameSanitizer.FALLBACK;
        }
        try {
            return FilenameSanitizer.sanitize(UriUtils.decode(headerValue, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return FilenameSanitizer.FALLBACK;
        }
    }
}
