package com.campuslostfound.web.dto;

import com.campuslostfound.web.validation.SafeText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Request/response shapes for in-app contact messages. */
public final class MessageDtos {

    private MessageDtos() {
    }

    public record SendRequest(@NotBlank @SafeText @Size(min = 1, max = 2000) String message) {
    }

    public record MessageResponse(
            Long id,
            Long listingId,
            String listingTitle,
            String direction,        // "INBOX" or "SENT" relative to the caller
            String counterpartyDisplayName,
            String body,
            boolean read,
            Instant createdAt) {
    }
}
