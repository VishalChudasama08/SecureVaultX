package com.securevaultx.backend.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes RFC 9457 problem+json bodies from servlet filters, where MVC message converters are not available.
 * Only compile-time constants are ever interpolated, so no escaping is required.
 */
final class SecurityProblemWriter {

    private SecurityProblemWriter() {
    }

    static void write(HttpServletResponse response, int status, String title, String code, String detail)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status
                + ",\"detail\":\"" + detail + "\",\"code\":\"" + code + "\"}");
    }
}
