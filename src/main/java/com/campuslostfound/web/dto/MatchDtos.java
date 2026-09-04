package com.campuslostfound.web.dto;

import com.campuslostfound.domain.Category;
import com.campuslostfound.domain.ListingKind;
import com.campuslostfound.domain.ListingStatus;
import com.campuslostfound.domain.MatchSignal;
import com.campuslostfound.domain.MatchStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Response shapes for the matching endpoints. */
public final class MatchDtos {

    private MatchDtos() {
    }

    public record ReasonResponse(MatchSignal signal, String detail, int contribution) {
    }

    /** A neutral view of the listing on the other side of a candidate. No reporter identity. */
    public record OtherListingResponse(
            Long id, ListingKind kind, String title, Category category, String categoryLabel,
            String building, String area, LocalDate eventDate, ListingStatus status) {
    }

    public record CandidateResponse(
            Long candidateId,
            int score,
            MatchStatus status,
            String scorerVersion,
            String disclaimer,
            OtherListingResponse otherListing,
            List<ReasonResponse> reasons,
            Instant createdAt) {

        public static final String DISCLAIMER =
                "Suggested by the matching algorithm. This is not a confirmed match - "
                        + "a person must verify ownership before an item changes hands.";
    }
}
