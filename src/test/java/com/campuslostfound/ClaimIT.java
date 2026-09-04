package com.campuslostfound;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.campuslostfound.support.AbstractApiIT;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** The ownership-claim workflow and its gate on marking a listing RECOVERED. */
class ClaimIT extends AbstractApiIT {

    private long foundListing(String finder) {
        return createListing(finder, Map.of(
                "kind", "FOUND", "category", "PHONE", "title", "Found a phone",
                "description", "A phone found on a bench near the quad",
                "eventDate", "2026-03-05", "privateDetails", "Lock screen is a photo of a corgi"));
    }

    @Test
    void claimSubmitDecisionAndRecoveredGate() throws Exception {
        String finder = newVerifiedUser("cf1@campus.edu");
        String claimant = newVerifiedUser("cc1@campus.edu");
        long id = foundListing(finder);

        // claimant submits a claim describing the withheld detail
        MvcResult submitted = mvc.perform(authPost("/api/v1/listings/" + id + "/claims", claimant,
                        Map.of("answer", "The lock screen shows a corgi puppy")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        long claimId = readTree(submitted).get("id").asLong();

        // a stranger cannot see the claim list; the finder can
        String stranger = newVerifiedUser("cx1@campus.edu");
        mvc.perform(authGet("/api/v1/listings/" + id + "/claims", stranger))
                .andExpect(status().isForbidden());
        mvc.perform(authGet("/api/v1/listings/" + id + "/claims", finder))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // finder cannot mark recovered while the claim is only pending
        mvc.perform(authPost("/api/v1/listings/" + id + "/status", finder, Map.of("status", "RECOVERED")))
                .andExpect(status().isUnprocessableEntity());

        // finder approves the claim
        mvc.perform(authPost("/api/v1/claims/" + claimId + "/decision", finder,
                        Map.of("decision", "APPROVE", "note", "Matches the photo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // now RECOVERED is allowed
        mvc.perform(authPost("/api/v1/listings/" + id + "/status", finder, Map.of("status", "RECOVERED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECOVERED"));
    }

    @Test
    void cannotClaimYourOwnListingOrALostListing() throws Exception {
        String finder = newVerifiedUser("cf2@campus.edu");
        long foundId = foundListing(finder);
        mvc.perform(authPost("/api/v1/listings/" + foundId + "/claims", finder,
                        Map.of("answer", "it is definitely mine i promise")))
                .andExpect(status().isForbidden());

        String seeker = newVerifiedUser("cs2@campus.edu");
        long lostId = createListing(seeker, Map.of("kind", "LOST", "category", "PHONE",
                "title", "Lost phone", "description", "Lost my phone somewhere on campus",
                "eventDate", "2026-03-05"));
        String other = newVerifiedUser("co2@campus.edu");
        mvc.perform(authPost("/api/v1/listings/" + lostId + "/claims", other,
                        Map.of("answer", "trying to claim a lost listing which is not allowed")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void duplicatePendingClaimIsRejected() throws Exception {
        String finder = newVerifiedUser("cf3@campus.edu");
        String claimant = newVerifiedUser("cc3@campus.edu");
        long id = foundListing(finder);

        mvc.perform(authPost("/api/v1/listings/" + id + "/claims", claimant,
                Map.of("answer", "first claim with enough characters"))).andExpect(status().isCreated());
        mvc.perform(authPost("/api/v1/listings/" + id + "/claims", claimant,
                        Map.of("answer", "second claim while first still pending")))
                .andExpect(status().isConflict());
    }
}
