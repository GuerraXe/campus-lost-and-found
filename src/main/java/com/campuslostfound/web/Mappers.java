package com.campuslostfound.web;

import com.campuslostfound.domain.Claim;
import com.campuslostfound.domain.ContactMessage;
import com.campuslostfound.domain.Flag;
import com.campuslostfound.domain.Listing;
import com.campuslostfound.domain.MatchCandidate;
import com.campuslostfound.domain.User;
import com.campuslostfound.web.dto.AuthDtos.UserResponse;
import com.campuslostfound.web.dto.ClaimDtos.ClaimResponse;
import com.campuslostfound.web.dto.FlagDtos.FlagResponse;
import com.campuslostfound.web.dto.ListingDtos.AttributeResponse;
import com.campuslostfound.web.dto.ListingDtos.DetailResponse;
import com.campuslostfound.web.dto.ListingDtos.ReporterResponse;
import com.campuslostfound.web.dto.ListingDtos.SummaryResponse;
import com.campuslostfound.web.dto.MatchDtos.CandidateResponse;
import com.campuslostfound.web.dto.MatchDtos.OtherListingResponse;
import com.campuslostfound.web.dto.MatchDtos.ReasonResponse;
import com.campuslostfound.web.dto.MessageDtos.MessageResponse;
import java.util.List;

/** Domain -> response DTO conversions. Response shapes never leak private data by default. */
public final class Mappers {

    private Mappers() {
    }

    public static UserResponse user(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole().name(),
                u.isEmailVerified(), u.getCreatedAt());
    }

    public static SummaryResponse summary(Listing l) {
        return new SummaryResponse(l.getId(), l.getKind(), l.getTitle(), l.getCategory(),
                l.getCategory().label(), l.getBuilding(), l.getArea(), l.getEventDate(),
                l.getStatus(), l.getCreatedAt());
    }

    /**
     * @param privileged the caller is the reporter or a moderator: include private details
     *                   and the reporter identity. Otherwise those fields are null.
     */
    public static DetailResponse detail(Listing l, boolean privileged, int suggestedMatchCount) {
        List<AttributeResponse> attrs = l.getAttributes().stream()
                .map(a -> new AttributeResponse(a.getId(), a.getKey(), a.getValue()))
                .toList();
        ReporterResponse reporter = privileged
                ? new ReporterResponse(l.getReporter().getId(), l.getReporter().getDisplayName())
                : null;
        return new DetailResponse(l.getId(), l.getKind(), l.getTitle(), l.getDescription(),
                l.getCategory(), l.getCategory().label(), l.getLocationText(), l.getBuilding(),
                l.getArea(), l.getEventDate(), l.getStatus(), attrs, suggestedMatchCount,
                privileged ? l.getPrivateDetails() : null, reporter,
                l.getCreatedAt(), l.getUpdatedAt());
    }

    public static CandidateResponse candidate(MatchCandidate c, Listing viewpoint) {
        Listing other = c.otherSide(viewpoint);
        OtherListingResponse otherDto = new OtherListingResponse(other.getId(), other.getKind(),
                other.getTitle(), other.getCategory(), other.getCategory().label(),
                other.getBuilding(), other.getArea(), other.getEventDate(), other.getStatus());
        List<ReasonResponse> reasons = c.getReasons().stream()
                .map(r -> new ReasonResponse(r.getSignal(), r.getDetail(), r.getContribution()))
                .toList();
        return new CandidateResponse(c.getId(), c.getScore(), c.getStatus(), c.getScorerVersion(),
                CandidateResponse.DISCLAIMER, otherDto, reasons, c.getCreatedAt());
    }

    public static ClaimResponse claim(Claim c) {
        return new ClaimResponse(c.getId(), c.getListing().getId(), c.getClaimantId(),
                c.getClaimant().getDisplayName(), c.getAnswerText(), c.getStatus(),
                c.getDecisionNote(), c.getDecidedAt(), c.getCreatedAt());
    }

    public static MessageResponse message(ContactMessage m, Long viewerId) {
        boolean inbox = m.getRecipient().getId().equals(viewerId);
        String counterparty = inbox ? m.getSender().getDisplayName() : m.getRecipient().getDisplayName();
        return new MessageResponse(m.getId(), m.getListing().getId(), m.getListing().getTitle(),
                inbox ? "INBOX" : "SENT", counterparty, m.getBody(), m.isRead(), m.getCreatedAt());
    }

    public static FlagResponse flag(Flag f) {
        return new FlagResponse(f.getId(), f.getListing().getId(), f.getListing().getTitle(),
                f.getReason(), f.getDetails(), f.getStatus(), f.getReporterId(),
                f.getResolutionNote(), f.getResolvedAt(), f.getCreatedAt());
    }
}
