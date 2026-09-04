package com.campuslostfound.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.campuslostfound.domain.Role;
import com.campuslostfound.domain.User;
import com.campuslostfound.repo.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base for full-stack API tests: real Spring context, Flyway-migrated H2, MockMvc.
 * Every table is truncated before each test so cases are independent.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractApiIT {

    private static final String[] TABLES = {
            "match_reasons", "match_candidates", "claims", "contact_messages", "flags",
            "listing_attributes", "listings", "email_verification_tokens", "users"
    };

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UserRepository users;

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : TABLES) {
            jdbc.execute("TRUNCATE TABLE " + table + " RESTART IDENTITY");
        }
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    // --- helpers ------------------------------------------------------------

    protected String body(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected JsonNode readTree(MvcResult result) {
        try {
            return json.readTree(result.getResponse().getContentAsString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected MockHttpServletRequestBuilder jsonPost(String url, Object payload) {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(body(payload));
    }

    protected MockHttpServletRequestBuilder authGet(String url, String token) {
        return get(url).header("Authorization", "Bearer " + token);
    }

    protected MockHttpServletRequestBuilder authPost(String url, String token, Object payload) {
        return post(url).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body(payload));
    }

    /** Register a user, verify the email, log in, and return a usable bearer token. */
    protected String newVerifiedUser(String email) {
        try {
            MvcResult reg = mvc.perform(jsonPost("/api/v1/auth/register", Map.of(
                            "email", email, "password", "correct horse battery", "displayName", "Test User")))
                    .andReturn();
            String token = readTree(reg).get("verificationToken").asText();
            mvc.perform(jsonPost("/api/v1/auth/verify", Map.of("token", token)))
                    .andExpect(status().isNoContent());
            return login(email, "correct horse battery");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Register + verify + login, then promote to the given role directly in the DB. */
    protected String newUserWithRole(String email, Role role) {
        String token = newVerifiedUser(email);
        User u = users.findByEmail(email).orElseThrow();
        u.setRole(role);
        users.save(u);
        // role is read from the DB per request by the JWT filter, so the existing token
        // now carries the new authorities.
        return token;
    }

    protected String login(String email, String password) {
        try {
            MvcResult res = mvc.perform(jsonPost("/api/v1/auth/login",
                            Map.of("email", email, "password", password)))
                    .andExpect(status().isOk())
                    .andReturn();
            return readTree(res).get("accessToken").asText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected long createListing(String token, Map<String, Object> overrides) {
        try {
            var payload = new java.util.HashMap<String, Object>(Map.of(
                    "kind", "LOST",
                    "title", "Lost item title",
                    "description", "A reasonably detailed description of the lost item.",
                    "category", "OTHER",
                    "eventDate", "2026-03-01"));
            payload.putAll(overrides);
            MvcResult res = mvc.perform(authPost("/api/v1/listings", token, payload))
                    .andExpect(status().isCreated())
                    .andReturn();
            return readTree(res).get("id").asLong();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // static import shim so subclasses can use status() without importing it themselves
    protected static org.springframework.test.web.servlet.result.StatusResultMatchers status() {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.status();
    }
}
