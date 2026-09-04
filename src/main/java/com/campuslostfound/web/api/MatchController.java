package com.campuslostfound.web.api;

import com.campuslostfound.domain.Listing;
import com.campuslostfound.domain.MatchCandidate;
import com.campuslostfound.security.AppPrincipal;
import com.campuslostfound.service.ListingService;
import com.campuslostfound.service.MatchingService;
import com.campuslostfound.web.Mappers;
import com.campuslostfound.web.dto.MatchDtos.CandidateResponse;
import com.campuslostfound.web.error.Exceptions;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reading and acting on suggested matches. All of these require authentication - unlike
 * listing reads - because a match reveals information (shared location, date proximity)
 * about the other party's listing (see docs/design-decisions.md DD-10).
 */
@RestController
@RequestMapping("/api/v1")
public class MatchController {

    private final MatchingService matching;
    private final ListingService listings;

    public MatchController(MatchingService matching, ListingService listings) {
        this.matching = matching;
        this.listings = listings;
    }

    @GetMapping("/listings/{id}/matches")
    public List<CandidateResponse> forListing(@AuthenticationPrincipal AppPrincipal actor,
                                              @PathVariable Long id) {
        require(actor);
        Listing viewpoint = listings.getEntity(id);
        return matching.matchesFor(actor, id).stream()
                .map(c -> Mappers.candidate(c, viewpoint))
                .toList();
    }

    @PostMapping("/listings/{id}/matches/rescan")
    public List<CandidateResponse> rescan(@AuthenticationPrincipal AppPrincipal actor,
                                          @PathVariable Long id) {
        require(actor);
        Listing viewpoint = listings.getEntity(id);
        return matching.rescan(actor, id).stream()
                .map(c -> Mappers.candidate(c, viewpoint))
                .toList();
    }

    @PostMapping("/matches/{candidateId}/confirm")
    public CandidateResponse confirm(@AuthenticationPrincipal AppPrincipal actor,
                                     @PathVariable Long candidateId) {
        require(actor);
        return view(matching.confirm(actor, candidateId));
    }

    @PostMapping("/matches/{candidateId}/reject")
    public CandidateResponse reject(@AuthenticationPrincipal AppPrincipal actor,
                                    @PathVariable Long candidateId) {
        require(actor);
        return view(matching.reject(actor, candidateId));
    }

    @PostMapping("/matches/{candidateId}/unconfirm")
    public CandidateResponse unconfirm(@AuthenticationPrincipal AppPrincipal actor,
                                       @PathVariable Long candidateId) {
        require(actor);
        return view(matching.unconfirm(actor, candidateId));
    }

    private static CandidateResponse view(MatchCandidate c) {
        return Mappers.candidate(c, c.getLostListing());
    }

    private static void require(AppPrincipal actor) {
        if (actor == null) {
            throw new Exceptions.UnauthorizedException("Authentication required.");
        }
    }
}
