package com.campuslostfound.web.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Renders filter-chain 401/403 (i.e. failures that never reach a controller) as the same
 * {@code application/problem+json} shape the {@link GlobalExceptionHandler} produces.
 */
public final class ProblemJsonHandlers {

    private ProblemJsonHandlers() {
    }

    private static void write(HttpServletResponse response, HttpStatus status, String type,
                              String detail, ObjectMapper mapper) throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("https://campus-lost-and-found/problems/" + type));
        pd.setTitle(type);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), pd);
    }

    @Component
    public static class EntryPoint implements AuthenticationEntryPoint {
        private final ObjectMapper mapper;

        public EntryPoint(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public void commence(HttpServletRequest request, HttpServletResponse response,
                             AuthenticationException ex) throws IOException {
            write(response, HttpStatus.UNAUTHORIZED, "unauthorized",
                    "Authentication is required to access this resource.", mapper);
        }
    }

    @Component
    public static class DeniedHandler implements AccessDeniedHandler {
        private final ObjectMapper mapper;

        public DeniedHandler(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                           AccessDeniedException ex) throws IOException {
            write(response, HttpStatus.FORBIDDEN, "forbidden",
                    "You do not have permission to perform this action.", mapper);
        }
    }
}
