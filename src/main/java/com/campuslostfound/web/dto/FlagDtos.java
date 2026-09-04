package com.campuslostfound.web.dto;

import com.campuslostfound.domain.FlagReason;
import com.campuslostfound.domain.FlagStatus;
import com.campuslostfound.web.validation.SafeText;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Request/response shapes for flagging listings and the moderation queue. */
public final class FlagDtos {

    private FlagDtos() {
    }

    public record CreateRequest(
            @NotNull FlagReason reason,
            @SafeText @Size(max = 1000) String details) {
    }

    public enum Action { REVIEW, ACTION, DISMISS }

    public record ResolveRequest(
            @NotNull Action action,
            @SafeText @Size(max = 1000) String note) {
    }

    public record FlagResponse(
            Long id,
            Long listingId,
            String listingTitle,
            FlagReason reason,
            String details,
            FlagStatus status,
            Long reporterId,
            String resolutionNote,
            Instant resolvedAt,
            Instant createdAt) {
    }
}
