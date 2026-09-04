package com.campuslostfound;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.campuslostfound.support.AbstractApiIT;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Creating, reading (with redaction), searching, filtering, patching, status changes. */
class ListingIT extends AbstractApiIT {

    @Test
    void createRequiresAVerifiedEmail() throws Exception {
        // register + login but do NOT verify
        mvc.perform(jsonPost("/api/v1/auth/register", Map.of(
                "email", "unv@campus.edu", "password", "correct horse battery", "displayName", "Unv")))
                .andExpect(status().isCreated());
        String token = login("unv@campus.edu", "correct horse battery");

        mvc.perform(authPost("/api/v1/listings", token, Map.of(
                        "kind", "LOST", "title", "My umbrella", "description", "A long black umbrella lost today",
                        "category", "UMBRELLA", "eventDate", "2026-03-01")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("email-not-verified"));
    }

    @Test
    void createReturnsDetailAndRejectsFutureDate() throws Exception {
        String token = newVerifiedUser("owner@campus.edu");
        mvc.perform(authPost("/api/v1/listings", token, Map.of(
                        "kind", "FOUND", "title", "Found keys", "description", "A set of keys on a blue lanyard",
                        "category", "KEYS", "eventDate", "2026-03-02", "building", "Library",
                        "privateDetails", "Small brass keyring shaped like a cat")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.categoryLabel").value("Keys"))
                .andExpect(jsonPath("$.privateDetails").value("Small brass keyring shaped like a cat"));

        mvc.perform(authPost("/api/v1/listings", token, Map.of(
                        "kind", "FOUND", "title", "Future item", "description", "This date has not happened yet",
                        "category", "OTHER", "eventDate", "2099-01-01")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void missingRequiredFieldsAreRejectedWithFieldErrors() throws Exception {
        String token = newVerifiedUser("v@campus.edu");
        mvc.perform(authPost("/api/v1/listings", token, Map.of("kind", "LOST")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.description").exists())
                .andExpect(jsonPath("$.errors.category").exists());
    }

    @Test
    void publicDetailHidesPrivateDetailsAndReporterFromStrangers() throws Exception {
        String owner = newVerifiedUser("owner2@campus.edu");
        long id = createListing(owner, Map.of("privateDetails", "serial ends 7788", "title", "Lost bag"));

        // anonymous
        mvc.perform(get("/api/v1/listings/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privateDetails").doesNotExist())
                .andExpect(jsonPath("$.reporter").doesNotExist());

        // a different logged-in user is still a stranger
        String other = newVerifiedUser("stranger@campus.edu");
        mvc.perform(authGet("/api/v1/listings/" + id, other))
                .andExpect(jsonPath("$.privateDetails").doesNotExist());

        // the owner sees everything
        mvc.perform(authGet("/api/v1/listings/" + id, owner))
                .andExpect(jsonPath("$.privateDetails").value("serial ends 7788"))
                .andExpect(jsonPath("$.reporter.displayName").exists());
    }

    @Test
    void searchByKeywordAndFilters() throws Exception {
        String token = newVerifiedUser("search@campus.edu");
        createListing(token, Map.of("kind", "LOST", "title", "Blue Hydroflask bottle",
                "description", "Dented blue insulated water bottle with stickers",
                "category", "WATER_BOTTLE", "building", "Rec Center", "eventDate", "2026-02-10"));
        createListing(token, Map.of("kind", "FOUND", "title", "Umbrella",
                "description", "Large golf umbrella, striped",
                "category", "UMBRELLA", "building", "Union", "eventDate", "2026-02-20"));

        mvc.perform(get("/api/v1/listings").param("q", "hydroflask"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Blue Hydroflask bottle"));

        mvc.perform(get("/api/v1/listings").param("kind", "FOUND"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].kind").value("FOUND"));

        mvc.perform(get("/api/v1/listings").param("category", "WATER_BOTTLE"))
                .andExpect(jsonPath("$.items.length()").value(1));

        mvc.perform(get("/api/v1/listings").param("building", "rec center"))
                .andExpect(jsonPath("$.items.length()").value(1));

        mvc.perform(get("/api/v1/listings")
                        .param("dateFrom", "2026-02-15").param("dateTo", "2026-02-25"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Umbrella"));

        mvc.perform(get("/api/v1/listings").param("q", "nonexistentword"))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void invalidSortFieldIsRejected() throws Exception {
        mvc.perform(get("/api/v1/listings").param("sort", "passwordHash,asc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyOwnerOrModeratorCanPatch() throws Exception {
        String owner = newVerifiedUser("po@campus.edu");
        String other = newVerifiedUser("pother@campus.edu");
        long id = createListing(owner, Map.of("title", "Original title"));

        mvc.perform(patch("/api/v1/listings/" + id, other, Map.of("title", "Hacked title")))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/v1/listings/" + id, owner, Map.of("title", "Edited by owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Edited by owner"));
    }

    @Test
    void statusTransitionsAreValidatedAndRecoveredIsGated() throws Exception {
        String owner = newVerifiedUser("so@campus.edu");
        long id = createListing(owner, Map.of("kind", "LOST"));

        // illegal jump
        mvc.perform(authPost("/api/v1/listings/" + id + "/status", owner, Map.of("status", "REMOVED")))
                .andExpect(status().isForbidden()); // REMOVED requires a moderator

        // OPEN -> CLOSED -> OPEN is fine
        mvc.perform(authPost("/api/v1/listings/" + id + "/status", owner, Map.of("status", "CLOSED")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        mvc.perform(authPost("/api/v1/listings/" + id + "/status", owner, Map.of("status", "OPEN")))
                .andExpect(status().isOk());

        // RECOVERED without an approved claim is refused
        mvc.perform(authPost("/api/v1/listings/" + id + "/status", owner, Map.of("status", "RECOVERED")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void attributesCanBeAddedAndRemovedByOwner() throws Exception {
        String owner = newVerifiedUser("ao@campus.edu");
        long id = createListing(owner, Map.of());

        var res = mvc.perform(authPost("/api/v1/listings/" + id + "/attributes", owner,
                        Map.of("key", "COLOR", "value", "black")))
                .andExpect(status().isCreated())
                .andReturn();
        long attrId = readTree(res).get("id").asLong();

        mvc.perform(authPost("/api/v1/listings/" + id + "/attributes", owner,
                        Map.of("key", "COLOR", "value", "black")))
                .andExpect(status().isConflict());

        mvc.perform(authGet("/api/v1/listings/" + id, owner))
                .andExpect(jsonPath("$.attributes.length()").value(1));

        mvc.perform(delete("/api/v1/listings/" + id + "/attributes/" + attrId, owner))
                .andExpect(status().isNoContent());
    }

    // helpers
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder patch(
            String url, String token, Map<String, Object> payload) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(url)
                .header("Authorization", "Bearer " + token)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(body(payload));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder delete(
            String url, String token) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url)
                .header("Authorization", "Bearer " + token);
    }
}
