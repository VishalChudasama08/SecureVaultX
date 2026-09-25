package com.securevaultx.backend.exception;

import com.securevaultx.backend.crypto.AuthenticationFailedException;
import com.securevaultx.backend.crypto.InvalidEncryptedFileException;
import com.securevaultx.backend.crypto.UnsupportedFormatVersionException;
import com.securevaultx.backend.storage.StorageException;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Single place that turns exceptions into RFC 9457 problem+json. Responses carry a stable {@code code} property
 * that the Flutter app maps to friendly messages. Nothing internal (stack traces, SQL, paths, keys) is exposed.
 * If the response is already committed (a stream failed half-way) nothing more is written.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException ex, HttpServletResponse response) {
        if (ex.status().is5xxServerError()) {
            log.error("Request failed: {} ({})", ex.code(), ex.getMessage(), ex.getCause());
        }
        return respond(response, ex.status(), ex.code(), ex.getMessage());
    }

    @ExceptionHandler(UnsupportedFormatVersionException.class)
    ResponseEntity<ProblemDetail> handleUnsupportedVersion(UnsupportedFormatVersionException ex,
                                                           HttpServletResponse response) {
        return respond(response, HttpStatus.BAD_REQUEST, "UNSUPPORTED_FORMAT_VERSION",
                "This encrypted file uses a format version that this server does not support.");
    }

    @ExceptionHandler(InvalidEncryptedFileException.class)
    ResponseEntity<ProblemDetail> handleInvalidEncrypted(InvalidEncryptedFileException ex,
                                                         HttpServletResponse response) {
        return respond(response, HttpStatus.BAD_REQUEST, "ENCRYPTED_FILE_INVALID",
                "The uploaded file is not a valid SecureVaultX encrypted file or is incomplete.");
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    ResponseEntity<ProblemDetail> handleTampered(AuthenticationFailedException ex, HttpServletResponse response) {
        return respond(response, HttpStatus.BAD_REQUEST, "INTEGRITY_CHECK_FAILED",
                "The encrypted file is corrupted or has been modified. Nothing was decrypted.");
    }

    @ExceptionHandler(StorageException.class)
    ResponseEntity<ProblemDetail> handleStorage(StorageException ex, HttpServletResponse response) {
        log.error("Storage failure", ex);
        return respond(response, HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR",
                "The server could not access the file storage.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        pd.setProperty("code", "VALIDATION_FAILED");
        pd.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(pd);
    }

    /** Last resort. Security exceptions are re-thrown so Spring Security answers 401/403 itself. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletResponse response) throws Exception {
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        log.error("Unexpected error", ex);
        return respond(response, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred.");
    }

    private static ResponseEntity<ProblemDetail> respond(HttpServletResponse response, HttpStatusCode status,
                                                         String code, String detail) {
        if (response.isCommitted()) {
            return null; // too late to change anything; the aborted transfer is the error signal
        }
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setProperty("code", code);
        return ResponseEntity.status(status).contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
