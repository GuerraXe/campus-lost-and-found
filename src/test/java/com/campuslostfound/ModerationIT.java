package com.campuslostfound;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.campuslostfound.domain.Role;
import com.campuslostfound.support.AbstractApiIT;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Flagging listings and the moderator queue / takedown. */
class ModerationIT extends AbstractApiIT {

    @Test
    void flagThenModeratorResolvesAndTakesDown() throws Exception {
        String owner = newVerifiedUser("mown@campus.edu");
        String reporter = newVerifiedUser("mrep@campus.edu");
        String mod = newUserWithRole("mmod@campus.edu", Role.MODERATOR);
        long id = createListing(owner, Map.of("title", "Suspicious listing"));

        mvc.perform(authPost("/api/v1/listings/" + id + "/flags", reporter,
                        Map.of("reason", "SCAM", "details", "Asks for payment to return the item")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"));

        // a second unresolved flag by the same user is rejected
        mvc.perform(authPost("/api/v1/listings/" + id + "/flags", reporter,
                        Map.of("reason", "SPAM", "details", "also spammy")))
                .andExpect(status().isConflict());

        // non-moderator cannot see the queue
        mvc.perform(authGet("/api/v1/moderation/flags", reporter))
                .andExpect(status().isForbidden());

        var queue = mvc.perform(authGet("/api/v1/moderation/flags?status=OPEN", mod))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn();
        long flagId = readTree(queue).get("items").get(0).get("id").asLong();

        mvc.perform(authPost("/api/v1/moderation/flags/" + flagId + "/resolve", mod,
                        Map.of("action", "ACTION", "note", "Confirmed scam")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIONED"));

        mvc.perform(authPost("/api/v1/moderation/listings/" + id + "/takedown", mod, Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REMOVED"));

        // a removed listing is invisible to the public
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/listings/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerCannotTakeDownTheirOwnListing() throws Exception {
        String owner = newVerifiedUser("mo5@campus.edu");
        long id = createListing(owner, Map.of());
        mvc.perform(authPost("/api/v1/moderation/listings/" + id + "/takedown", owner, Map.of()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanChangeAUsersRole() throws Exception {
        String admin = newUserWithRole("admin@campus.edu", Role.ADMIN);
        String target = newVerifiedUser("target@campus.edu");
        long targetId = users.findByEmail("target@campus.edu").orElseThrow().getId();

        mvc.perform(put("/api/v1/admin/users/" + targetId + "/role", admin,
                        Map.of("role", "MODERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MODERATOR"));

        // a non-admin cannot reach the endpoint at all
        String plain = newVerifiedUser("plain@campus.edu");
        mvc.perform(put("/api/v1/admin/users/" + targetId + "/role", plain, Map.of("role", "ADMIN")))
                .andExpect(status().isForbidden());

        // and the role change invalidated the target's old token (defence in depth)
        mvc.perform(authGet("/api/v1/users/me", target))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder put(
            String url, String token, Map<String, Object> payload) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url)
                .header("Authorization", "Bearer " + token)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(body(payload));
    }
}
