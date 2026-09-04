package com.campuslostfound;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.campuslostfound.support.AbstractApiIT;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Cross-cutting security behaviour: headers, problem+json, CSRF-off, error hygiene. */
class SecurityIT extends AbstractApiIT {

    @Test
    void defensiveResponseHeadersArePresent() throws Exception {
        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"));
    }

    @Test
    void writeRequestsSucceedWithoutAnyCsrfToken() throws Exception {
        // Stateless bearer auth => CSRF protection is intentionally disabled. A POST with
        // only an Authorization header (no cookie, no _csrf) must be accepted.
        String token = newVerifiedUser("csrf@campus.edu");
        mvc.perform(authPost("/api/v1/listings", token, Map.of(
                        "kind", "LOST", "title", "No CSRF needed",
                        "description", "This create call carries no CSRF token at all",
                        "category", "OTHER", "eventDate", "2026-03-01")))
                .andExpect(status().isCreated());
    }

    @Test
    void notFoundIsProblemJsonWithNoStackTrace() throws Exception {
        mvc.perform(authGet("/api/v1/listings/999999", newVerifiedUser("nf@campus.edu")))
                .andExpect(status().isNotFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("Listing not found."))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void malformedJsonBodyIsA400() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{ not json "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("invalid-request"));
    }

    @Test
    void wrongHttpMethodIs405NotAServerError() throws Exception {
        String token = newVerifiedUser("method@campus.edu");
        long id = createListing(token, Map.of());
        // /listings/{id} supports GET and PATCH, not PUT -> 405 problem+json, never a 500
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/listings/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("method-not-allowed"));
    }

    @Test
    void unknownApiRouteIs404NotAServerError() throws Exception {
        mvc.perform(authGet("/api/v1/nope/nothing/here", newVerifiedUser("route@campus.edu")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void listingsMineRequiresAuth() throws Exception {
        mvc.perform(get("/api/v1/listings/mine")).andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder post(String u) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(u);
    }
}
