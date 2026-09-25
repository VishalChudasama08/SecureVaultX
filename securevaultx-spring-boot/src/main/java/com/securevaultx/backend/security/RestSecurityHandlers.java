package com.securevaultx.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CsrfException;

/** JSON (never HTML redirect) answers for authentication events, suitable for a Flutter client. */
public final class RestSecurityHandlers {

    private RestSecurityHandlers() {
    }

    /** 401 for anonymous callers hitting a protected endpoint or an expired session. */
    public static final AuthenticationEntryPoint ENTRY_POINT = (HttpServletRequest req, HttpServletResponse res,
            AuthenticationException ex) -> SecurityProblemWriter.write(res, 401, "Unauthorized",
            "UNAUTHENTICATED", "Authentication is required or your session has expired.");

    /** 403; distinguishes a missing/invalid CSRF token so the client can refresh it and retry. */
    public static final AccessDeniedHandler ACCESS_DENIED = (HttpServletRequest req, HttpServletResponse res,
            AccessDeniedException ex) -> {
        if (ex instanceof CsrfException) {
            SecurityProblemWriter.write(res, 403, "Forbidden", "CSRF_INVALID",
                    "The CSRF token is missing or invalid.");
        } else {
            SecurityProblemWriter.write(res, 403, "Forbidden", "FORBIDDEN", "Access is denied.");
        }
    };

    /** Login succeeded: the session cookie is already set. 204; the client then calls GET /api/auth/me. */
    public static final AuthenticationSuccessHandler LOGIN_SUCCESS = (HttpServletRequest req,
            HttpServletResponse res, Authentication auth) -> {
        res.setStatus(HttpServletResponse.SC_NO_CONTENT);
        res.setHeader("Cache-Control", "no-store");
    };

    /** One uniform message for unknown user and wrong password, so accounts cannot be probed via login. */
    public static final AuthenticationFailureHandler LOGIN_FAILURE = (HttpServletRequest req,
            HttpServletResponse res, AuthenticationException ex) -> SecurityProblemWriter.write(res, 401,
            "Unauthorized", "INVALID_CREDENTIALS", "Invalid email or password.");

}
