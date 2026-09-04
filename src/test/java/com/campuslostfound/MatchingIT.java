package com.campuslostfound;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.campuslostfound.support.AbstractApiIT;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Auto-suggested matches, the explanation payload, and the confirm / reject / unconfirm flow. */
class MatchingIT extends AbstractApiIT {

    private long lostLaptop(String token) {
        return createListing(token, Map.of(
                "kind", "LOST", "category", "LAPTOP", "title", "Lost silver Dell laptop",
                "description", "Silver Dell XPS 13 with a rainbow vinyl sticker on the lid",
                "building", "Main Library", "eventDate", "2026-03-10",
                "privateDetails", "Cracked lower-left corner"));
    }

    private long foundLaptop(String token, String date) {
        return createListing(token, Map.of(
                "kind", "FOUND", "category", "LAPTOP", "title", "Found a Dell laptop",
                "description", "Dell laptop, rainbow sticker, left on a desk in the library",
                "building", "main library", "eventDate", date));
    }

    @Test
    void creatingAnOppositeListingAutoSuggestsAScoredExplainedMatch() throws Exception {
        String finder = newVerifiedUser("finder@campus.edu");
        String seeker = newVerifiedUser("seeker@campus.edu");

        foundLaptop(finder, "2026-03-12");
        long lostId = lostLaptop(seeker);

        MvcResult res = mvc.perform(authGet("/api/v1/listings/" + lostId + "/matches", seeker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("SUGGESTED"))
                .andExpect(jsonPath("$[0].disclaimer").exists())
                .andExpect(jsonPath("$[0].otherListing.kind").value("FOUND"))
                .andReturn();

        JsonNode m = readTree(res).get(0);
        int score = m.get("score").asInt();
        int reasonSum = 0;
        for (JsonNode r : m.get("reasons")) {
            reasonSum += r.get("contribution").asInt();
        }
        org.assertj.core.api.Assertions.assertThat(score).isEqualTo(reasonSum);
        org.assertj.core.api.Assertions.assertThat(score).isGreaterThanOrEqualTo(45);
        // the counterparty's raw private details must never appear in the explanation
        org.assertj.core.api.Assertions.assertThat(res.getResponse().getContentAsString())
                .doesNotContain("Cracked lower-left corner");
    }

    @Test
    void aWeakPairDoesNotProduceASuggestion() throws Exception {
        String a = newVerifiedUser("weak-a@campus.edu");
        String b = newVerifiedUser("weak-b@campus.edu");

        createListing(a, Map.of("kind", "FOUND", "category", "UMBRELLA", "title", "Found umbrella",
                "description", "A plain black folding umbrella", "eventDate", "2026-01-01"));
        long lostId = createListing(b, Map.of("kind", "LOST", "category", "KEYS", "title", "Lost keys",
                "description", "A single house key on a plain ring", "eventDate", "2026-03-30"));

        mvc.perform(authGet("/api/v1/listings/" + lostId + "/matches", b))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void matchesEndpointIsNotVisibleToStrangers() throws Exception {
        String finder = newVerifiedUser("mf@campus.edu");
        String seeker = newVerifiedUser("ms@campus.edu");
        String stranger = newVerifiedUser("mx@campus.edu");
        foundLaptop(finder, "2026-03-12");
        long lostId = lostLaptop(seeker);

        mvc.perform(authGet("/api/v1/listings/" + lostId + "/matches", stranger))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/listings/" + lostId + "/matches"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirmMovesBothListingsToMatchedAndUnconfirmReverts() throws Exception {
        String finder = newVerifiedUser("cf@campus.edu");
        String seeker = newVerifiedUser("cs@campus.edu");
        long foundId = foundLaptop(finder, "2026-03-12");
        long lostId = lostLaptop(seeker);

        long candidateId = firstCandidateId(seeker, lostId);

        mvc.perform(authPost("/api/v1/matches/" + candidateId + "/confirm", seeker, Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mvc.perform(authGet("/api/v1/listings/" + lostId, seeker))
                .andExpect(jsonPath("$.status").value("MATCHED"));
        mvc.perform(authGet("/api/v1/listings/" + foundId, finder))
                .andExpect(jsonPath("$.status").value("MATCHED"));

        mvc.perform(authPost("/api/v1/matches/" + candidateId + "/unconfirm", finder, Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUGGESTED"));
        mvc.perform(authGet("/api/v1/listings/" + lostId, seeker))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void rejectedMatchIsNotRecreatedByARescan() throws Exception {
        String finder = newVerifiedUser("rf@campus.edu");
        String seeker = newVerifiedUser("rs@campus.edu");
        foundLaptop(finder, "2026-03-12");
        long lostId = lostLaptop(seeker);

        long candidateId = firstCandidateId(seeker, lostId);
        mvc.perform(authPost("/api/v1/matches/" + candidateId + "/reject", seeker, Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        MvcResult rescan = mvc.perform(authPost("/api/v1/listings/" + lostId + "/matches/rescan", seeker, Map.of()))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode c : readTree(rescan)) {
            org.assertj.core.api.Assertions.assertThat(c.get("status").asText()).isEqualTo("REJECTED");
        }
    }

    private long firstCandidateId(String token, long listingId) throws Exception {
        MvcResult res = mvc.perform(authGet("/api/v1/listings/" + listingId + "/matches", token))
                .andExpect(status().isOk())
                .andReturn();
        return readTree(res).get(0).get("candidateId").asLong();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder get(String u) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(u);
    }
}
