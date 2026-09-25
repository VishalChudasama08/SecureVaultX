package com.securevaultx.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/** A deliberate, client-safe failure. {@code code} is a stable machine-readable identifier for Flutter. */
public class ApiException extends RuntimeException {

    private final HttpStatusCode status;
    private final String code;

    public ApiException(HttpStatusCode status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public ApiException(HttpStatusCode status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatusCode status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RECORD_NOT_FOUND",
                "No such file was found for your account.");
    }

    public static ApiException vaultTooLarge(long maxBytes) {
        return new ApiException(HttpStatusCode.valueOf(413), "VAULT_SIZE_LIMIT_EXCEEDED",
                "Vault storage is limited to " + maxBytes + " bytes. Use Encrypt & Download for larger files.");
    }

    public static ApiException uploadTooLarge(long maxBytes) {
        return new ApiException(HttpStatusCode.valueOf(413), "UPLOAD_TOO_LARGE",
                "Upload exceeds the server limit of " + maxBytes + " bytes.");
    }

    public static ApiException lengthRequired() {
        return new ApiException(HttpStatus.LENGTH_REQUIRED, "LENGTH_REQUIRED",
                "A Content-Length header is required for this request.");
    }
}
