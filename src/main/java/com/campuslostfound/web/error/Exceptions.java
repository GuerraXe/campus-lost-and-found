package com.campuslostfound.web.error;

import org.springframework.http.HttpStatus;

/** Concrete {@link ApiException}s, grouped for brevity. */
public final class Exceptions {

    private Exceptions() {
    }

    /** A malformed request (bad parameter value, unsupported sort field, etc.). */
    public static class BadRequestException extends ApiException {
        public BadRequestException(String message) {
            super(HttpStatus.BAD_REQUEST, "invalid-request", message);
        }
    }

    public static class NotFoundException extends ApiException {
        public NotFoundException(String message) {
            super(HttpStatus.NOT_FOUND, "not-found", message);
        }
    }

    public static class ForbiddenException extends ApiException {
        public ForbiddenException(String message) {
            super(HttpStatus.FORBIDDEN, "forbidden", message);
        }
    }

    public static class ConflictException extends ApiException {
        public ConflictException(String message) {
            super(HttpStatus.CONFLICT, "conflict", message);
        }
    }

    /** A semantically invalid request that passed bean validation (bad state transition, etc.). */
    public static class ValidationException extends ApiException {
        public ValidationException(String message) {
            super(HttpStatus.UNPROCESSABLE_ENTITY, "validation", message);
        }
    }

    public static class UnauthorizedException extends ApiException {
        public UnauthorizedException(String message) {
            super(HttpStatus.UNAUTHORIZED, "unauthorized", message);
        }
    }

    public static class EmailNotVerifiedException extends ApiException {
        public EmailNotVerifiedException() {
            super(HttpStatus.FORBIDDEN, "email-not-verified",
                    "Verify your email address before performing this action.");
        }
    }

    public static class RateLimitedException extends ApiException {
        private final long retryAfterSeconds;

        public RateLimitedException(long retryAfterSeconds) {
            super(HttpStatus.TOO_MANY_REQUESTS, "rate-limited",
                    "Too many requests. Try again later.");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
