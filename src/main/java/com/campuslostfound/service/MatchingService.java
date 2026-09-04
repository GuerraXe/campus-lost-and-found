package com.campuslostfound.service;

import com.campuslostfound.config.MatchingProperties;
import com.campuslostfound.domain.Listing;
import com.campuslostfound.domain.ListingKind;
import com.campuslostfound.domain.ListingStatus;
import com.campuslostfound.domain.MatchCandidate;
import com.campuslostfound.domain.MatchReason;
import com.campuslostfound.domain.MatchStatus;
import com.campuslostfound.matching.MatchEngine;
import com.campuslostfound.matching.MatchResult;
import com.campuslostfound.repo.ListingRepository;
import com.campuslostfound.repo.MatchCandidateRepository;
import com.campuslostfound.security.AppPrincipal;
import com.campuslostfound.service.support.RateLimiter;
import com.campuslostfound.web.error.Exceptions;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the matching engine: decides which listings are even worth scoring
 * (a cheap SQL pre-filter), persists suggestions above the configured threshold, caps how
 * many are kept, and drives the human confirm / reject / unconfirm actions.
 *
 * <p>The engine never changes a listing's status. Only an explicit {@link #confirm} does,
 * and it can be reversed with {@link #unconfirm} so a mistaken confirmation does not
 * permanently strand other potential owners.
 */
@Service
public class MatchingService {

    private final ListingRepository listings;
    private final MatchCandidateRepository candidates;
    private final MatchEngine engine;
    private final MatchingProperties props;
    private final AccessGuard guard;
    private final RateLimiter rateLimiter;

    public MatchingService(ListingRepository listings, MatchCandidateRepository candidates,
                           MatchEngine engine, MatchingProperties props, AccessGuard guard,
                           RateLimiter rateLimiter) {
        this.listings = listings;
        this.candidates = candidates;
        this.engine = engine;
        this.props = props;
        this.guard = guard;
        this.rateLimiter = rateLimiter;
    }

    /** Run when a listing is created. Bounded by the pre-filter and the top-K cap. */
    @Transactional
    public List<MatchCandidate> onListingCreated(Listing listing) {
        return recompute(listing);
    }

    @Transactional
    public List<MatchCandidate> rescan(AppPrincipal actor, Long listingId) {
        Listing listing = listings.findById(listingId)
                .orElseThrow(() -> new Exceptions.NotFoundException("Listing not found."));
        guard.requireOwnerOrModerator(actor, listing.getReporterId(), "listing");
        rateLimiter.check(RateLimiter.Bucket.RESCAN, String.valueOf(actor.id()));
        return recompute(listing);
    }

    @Transactional(readOnly = true)
    public int suggestedCount(Long listingId) {
        return candidates.countSuggestedForListing(listingId);
    }

    @Transactional(readOnly = true)
    public List<MatchCandidate> matchesFor(AppPrincipal actor, Long listingId) {
        Listing listing = listings.findById(listingId)
                .orElseThrow(() -> new Exceptions.NotFoundException("Listing not found."));
        guard.requireOwnerOrModerator(actor, listing.getReporterId(), "listing's matches");
        return candidates.findForListing(listingId);
    }

    @Transactional
    public MatchCandidate confirm(AppPrincipal actor, Long candidateId) {
        MatchCandidate c = loadInvolved(actor, candidateId);
        if (c.getStatus() == MatchStatus.REJECTED) {
            throw new Exceptions.ValidationException("This match was rejected and cannot be confirmed.");
        }
        c.setStatus(MatchStatus.CONFIRMED);
        promoteToMatched(c.getLostListing());
        promoteToMatched(c.getFoundListing());
        return c;
    }

    @Transactional
    public MatchCandidate reject(AppPrincipal actor, Long candidateId) {
        MatchCandidate c = loadInvolved(actor, candidateId);
        c.setStatus(MatchStatus.REJECTED);
        demoteIfNoConfirmed(c.getLostListing());
        demoteIfNoConfirmed(c.getFoundListing());
        return c;
    }

    @Transactional
    public MatchCandidate unconfirm(AppPrincipal actor, Long candidateId) {
        MatchCandidate c = loadInvolved(actor, candidateId);
        if (c.getStatus() != MatchStatus.CONFIRMED) {
            throw new Exceptions.ValidationException("Only a confirmed match can be unconfirmed.");
        }
        c.setStatus(MatchStatus.SUGGESTED);
        demoteIfNoConfirmed(c.getLostListing());
        demoteIfNoConfirmed(c.getFoundListing());
        return c;
    }

    // --- internals ---------------------------------------------------------

    private List<MatchCandidate> recompute(Listing listing) {
        ListingKind oppositeKind = listing.getKind().opposite();
        LocalDate from = listing.getEventDate().minusDays(props.getPrefilterDays());
        LocalDate to = listing.getEventDate().plusDays(props.getPrefilterDays());

        List<Listing> pool = listings.findMatchPrefilter(
                oppositeKind, listing.getId(), listing.getCategory(), listing.getBuilding(), from, to);

        record Scored(Listing other, MatchResult result) {
        }
        List<Scored> scored = new ArrayList<>();
        for (Listing other : pool) {
            Listing lost = listing.getKind() == ListingKind.LOST ? listing : other;
            Listing found = listing.getKind() == ListingKind.FOUND ? listing : other;

            MatchCandidate existing = candidates
                    .findByLostListingIdAndFoundListingId(lost.getId(), found.getId())
                    .orElse(null);
            if (existing != null && existing.getStatus() == MatchStatus.REJECTED) {
                continue; // a rejected pair stays rejected
            }
            MatchResult result = engine.score(lost, found);
            if (result.score() >= props.getSuggestThreshold()) {
                scored.add(new Scored(other, result));
            }
        }
        scored.sort(Comparator.comparingInt((Scored s) -> s.result().score()).reversed());

        List<MatchCandidate> out = new ArrayList<>();
        for (Scored s : scored.stream().limit(props.getMaxCandidatesPerListing()).toList()) {
            Listing lost = listing.getKind() == ListingKind.LOST ? listing : s.other();
            Listing found = listing.getKind() == ListingKind.FOUND ? listing : s.other();
            out.add(upsert(lost, found, s.result()));
        }
        return out;
    }

    private MatchCandidate upsert(Listing lost, Listing found, MatchResult result) {
        MatchCandidate candidate = candidates
                .findByLostListingIdAndFoundListingId(lost.getId(), found.getId())
                .orElse(null);
        List<MatchReason> reasons = result.reasons().stream()
                .map(r -> new MatchReason(r.signal(), r.detail(), r.contribution()))
                .toList();
        if (candidate == null) {
            candidate = new MatchCandidate(lost, found, result.score(), props.getScorerVersion());
            reasons.forEach(candidate::addReason);
            return candidates.save(candidate);
        }
        candidate.rescore(result.score(), reasons, props.getScorerVersion());
        return candidate;
    }

    private MatchCandidate loadInvolved(AppPrincipal actor, Long candidateId) {
        MatchCandidate c = candidates.findById(candidateId)
                .orElseThrow(() -> new Exceptions.NotFoundException("Match not found."));
        boolean involved = actor.isModerator()
                || actor.id().equals(c.getLostListing().getReporterId())
                || actor.id().equals(c.getFoundListing().getReporterId());
        if (!involved) {
            throw new Exceptions.ForbiddenException("You are not part of this suggested match.");
        }
        return c;
    }

    private void promoteToMatched(Listing listing) {
        if (listing.getStatus() == ListingStatus.OPEN) {
            listing.setStatus(ListingStatus.MATCHED);
        }
    }

    private void demoteIfNoConfirmed(Listing listing) {
        if (listing.getStatus() != ListingStatus.MATCHED) {
            return;
        }
        boolean anyConfirmed = candidates.findForListing(listing.getId()).stream()
                .anyMatch(c -> c.getStatus() == MatchStatus.CONFIRMED);
        if (!anyConfirmed) {
            listing.setStatus(ListingStatus.OPEN);
        }
    }
}
