package com.campuslostfound.web.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Single place that turns every exception into an {@code application/problem+json}
 * response (RFC 7807). No stack traces or SQL ever reach the client; unexpected errors
 * are logged with a reference id and reported as a bare 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String BASE_TYPE = "https://campus-lost-and-found/problems/";

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        ProblemDetail pd = base(ex.getStatus(), ex.getType(), ex.getMessage());
        HttpHeaders headers = new HttpHeaders();
        if (ex instanceof Exceptions.RateLimitedException rl) {
            headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(rl.getRetryAfterSeconds()));
        }
        return new ResponseEntity<>(pd, headers, ex.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleBodyValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ProblemDetail pd = base(HttpStatus.BAD_REQUEST, "invalid-request",
                "One or more fields are invalid.");
        pd.setProperty("errors", fields);
        return pd;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleParamValidation(ConstraintViolationException ex) {
        Map<String, String> fields = ex.getConstraintViolations().stream().collect(Collectors.toMap(
                v -> v.getPropertyPath().toString(),
                v -> v.getMessage(),
                (a, b) -> a, LinkedHashMap::new));
        ProblemDetail pd = base(HttpStatus.BAD_REQUEST, "invalid-request",
                "One or more parameters are invalid.");
        pd.setProperty("errors", fields);
        return pd;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    ProblemDetail handleMalformed(Exception ex) {
        return base(HttpStatus.BAD_REQUEST, "invalid-request",
                "The request body or parameters could not be read.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail handleNoResource(NoResourceFoundException ex) {
        return base(HttpStatus.NOT_FOUND, "not-found", "No such endpoint.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return base(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed",
                "That HTTP method is not supported on this endpoint.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return base(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-media-type",
                "The request content type is not supported; send application/json.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentUpdate(OptimisticLockingFailureException ex) {
        return base(HttpStatus.CONFLICT, "concurrent-update",
                "The resource was modified by someone else. Reload and try again.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return base(HttpStatus.CONFLICT, "conflict",
                "The request conflicts with the current state of the resource.");
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    ProblemDetail handleAccessDenied(RuntimeException ex) {
        return base(HttpStatus.FORBIDDEN, "forbidden",
                "You do not have permission to perform this action.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        String ref = Long.toHexString(System.nanoTime());
        log.error("Unhandled exception [ref={}] on {} {}", ref, request.getMethod(),
                request.getRequestURI(), ex);
        ProblemDetail pd = base(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "An unexpected error occurred.");
        pd.setProperty("reference", ref);
        return pd;
    }

    private static ProblemDetail base(HttpStatus status, String type, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(BASE_TYPE + type));
        pd.setTitle(type);
        return pd;
    }
}
