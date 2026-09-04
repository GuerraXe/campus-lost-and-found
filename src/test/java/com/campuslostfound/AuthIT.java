package com.campuslostfound;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.campuslostfound.support.AbstractApiIT;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Registration, email verification, login, token invalidation. */
class AuthIT extends AbstractApiIT {

    private static final String PW = "correct horse battery"; // matches AbstractApiIT helper

    @Test
    void registerReturnsCreatedAndDoesNotEchoPassword() throws Exception {
        MvcResult res = mvc.perform(jsonPost("/api/v1/auth/register", Map.of(
                        "email", "Sam@Campus.edu", "password", PW, "displayName", "Sam")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.email").value("sam@campus.edu"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        // no mailer configured in tests -> the verification token is returned
        org.assertj.core.api.Assertions.assertThat(readTree(res).get("verificationToken").asText())
                .isNotBlank();
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        mvc.perform(jsonPost("/api/v1/auth/register", Map.of(
                "email", "dup@campus.edu", "password", PW, "displayName", "First"))).andExpect(status().isCreated());
        mvc.perform(jsonPost("/api/v1/auth/register", Map.of(
                        "email", "dup@campus.edu", "password", PW, "displayName", "Second")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("conflict"));
    }

    @Test
    void shortPasswordIsRejectedWithFieldError() throws Exception {
        mvc.perform(jsonPost("/api/v1/auth/register", Map.of(
                        "email", "weak@campus.edu", "password", "short", "displayName", "Weak")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void unknownJsonFieldIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@campus.edu\",\"password\":\"" + PW
                                + "\",\"displayName\":\"X\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginSucceedsAfterVerificationAndReturnsBearerToken() throws Exception {
        String token = newVerifiedUser("login@campus.edu");
        mvc.perform(authGet("/api/v1/users/me", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("login@campus.edu"))
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void loginWithWrongPasswordIsUniform401() throws Exception {
        newVerifiedUser("real@campus.edu");
        mvc.perform(jsonPost("/api/v1/auth/login", Map.of("email", "real@campus.edu", "password", "nope wrong pass")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
        // same message for an account that does not exist -> no user enumeration
        mvc.perform(jsonPost("/api/v1/auth/login", Map.of("email", "ghost@campus.edu", "password", "nope wrong pass")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }

    @Test
    void repeatedFailedLoginsLockTheAccount() throws Exception {
        newVerifiedUser("locked@campus.edu");
        for (int i = 0; i < 5; i++) {
            mvc.perform(jsonPost("/api/v1/auth/login",
                    Map.of("email", "locked@campus.edu", "password", "bad guess here")));
        }
        mvc.perform(jsonPost("/api/v1/auth/login", Map.of("email", "locked@campus.edu", "password", PW)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void logoutEverywhereInvalidatesTheExistingToken() throws Exception {
        String token = newVerifiedUser("logout@campus.edu");
        mvc.perform(authGet("/api/v1/users/me", token)).andExpect(status().isOk());
        mvc.perform(authPost("/api/v1/auth/logout", token, Map.of())).andExpect(status().isNoContent());
        mvc.perform(authGet("/api/v1/users/me", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithoutTokenIs401AsProblemJson() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().contentTypeCompatibleWith("application/problem+json"));
    }

    // static imports used above
    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder get(String u) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(u);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder post(String u) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(u);
    }
}
