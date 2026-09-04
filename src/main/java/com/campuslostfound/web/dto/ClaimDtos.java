package com.campuslostfound.web.dto;

import com.campuslostfound.domain.ClaimStatus;
import com.campuslostfound.web.validation.SafeText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Request/response shapes for the ownership-claim workflow. */
public final class ClaimDtos {

    private ClaimDtos() {
    }

    public record CreateRequest(
            @NotBlank @SafeText @Size(min = 10, max = 1000) String answer,
            Long matchCandidateId) {
    }

    public enum Decision { APPROVE, REJECT }

    public record DecisionRequest(
            @NotNull Decision decision,
            @SafeText @Size(max = 1000) String note) {
    }

    public record ClaimResponse(
            Long id,
            Long listingId,
            Long claimantId,
            String claimantDisplayName,
            String answer,
            ClaimStatus status,
            String decisionNote,
            Instant decidedAt,
            Instant createdAt) {
    }
}
