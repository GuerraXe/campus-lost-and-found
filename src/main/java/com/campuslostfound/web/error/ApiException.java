package com.campuslostfound.web.error;

import org.springframework.http.HttpStatus;

/**
 * Base class for expected, client-facing failures. Each carries the HTTP status and a
 * short machine-readable {@code type} slug that becomes the {@code type} URI of the
 * RFC 7807 problem response (see {@link GlobalExceptionHandler}).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String type;

    public ApiException(HttpStatus status, String type, String message) {
        super(message);
        this.status = status;
        this.type = type;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getType() {
        return type;
    }
}
