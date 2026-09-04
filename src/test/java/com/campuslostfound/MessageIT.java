package com.campuslostfound;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.campuslostfound.support.AbstractApiIT;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** In-app contact messaging: delivery, inbox/sent, read state, and self-message guard. */
class MessageIT extends AbstractApiIT {

    @Test
    void messageIsDeliveredToTheReporterInboxAndAppearsInSenderSent() throws Exception {
        String owner = newVerifiedUser("mo@campus.edu");
        String sender = newVerifiedUser("msender@campus.edu");
        long id = createListing(owner, Map.of("title", "Lost notebook"));

        mvc.perform(authPost("/api/v1/listings/" + id + "/contact", sender,
                        Map.of("message", "I think I saw your notebook in the cafe.")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direction").value("SENT"))
                .andExpect(jsonPath("$.body").value("I think I saw your notebook in the cafe."));

        mvc.perform(authGet("/api/v1/messages?box=inbox", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].direction").value("INBOX"))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.items[0].counterpartyDisplayName").exists());

        mvc.perform(authGet("/api/v1/messages?box=sent", sender))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void readingAMessageMarksItReadForTheRecipientOnly() throws Exception {
        String owner = newVerifiedUser("mo2@campus.edu");
        String sender = newVerifiedUser("ms2@campus.edu");
        long id = createListing(owner, Map.of());
        var sent = mvc.perform(authPost("/api/v1/listings/" + id + "/contact", sender,
                Map.of("message", "hello there"))).andReturn();
        long messageId = readTree(sent).get("id").asLong();

        mvc.perform(authPost("/api/v1/messages/" + messageId + "/read", owner, Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        // the sender may not mark it read
        mvc.perform(authPost("/api/v1/messages/" + messageId + "/read", sender, Map.of()))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotMessageYourOwnListing() throws Exception {
        String owner = newVerifiedUser("mo3@campus.edu");
        long id = createListing(owner, Map.of());
        mvc.perform(authPost("/api/v1/listings/" + id + "/contact", owner,
                        Map.of("message", "talking to myself")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aThirdPartyCannotReadSomeoneElsesMessage() throws Exception {
        String owner = newVerifiedUser("mo4@campus.edu");
        String sender = newVerifiedUser("ms4@campus.edu");
        String nosy = newVerifiedUser("mn4@campus.edu");
        long id = createListing(owner, Map.of());
        var sent = mvc.perform(authPost("/api/v1/listings/" + id + "/contact", sender,
                Map.of("message", "private message body"))).andReturn();
        long messageId = readTree(sent).get("id").asLong();

        mvc.perform(authGet("/api/v1/messages/" + messageId, nosy))
                .andExpect(status().isForbidden());
    }
}
