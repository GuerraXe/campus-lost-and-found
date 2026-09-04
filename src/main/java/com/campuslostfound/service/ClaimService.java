package com.campuslostfound.service;

import com.campuslostfound.domain.Claim;
import com.campuslostfound.domain.ClaimStatus;
import com.campuslostfound.domain.Listing;
import com.campuslostfound.domain.ListingKind;
import com.campuslostfound.domain.MatchCandidate;
import com.campuslostfound.domain.User;
import com.campuslostfound.repo.ClaimRepository;
import com.campuslostfound.repo.ListingRepository;
import com.campuslostfound.repo.MatchCandidateRepository;
import com.campuslostfound.security.AppPrincipal;
import com.campuslostfound.service.support.RateLimiter;
import com.campuslostfound.web.error.Exceptions;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ownership-verification workflow. A user claims a FOUND listing by answering the
 * "what identifies it" challenge in their own words; the finder (or a moderator) compares
 * that answer with the listing's private details and approves or rejects. An approved
 * claim is the precondition for a non-moderator marking the listing RECOVERED.
 */
@Service
public class ClaimService {

    private final ClaimRepository claims;
    private final ListingRepository listings;
    private final MatchCandidateRepository candidates;
    private final AccessGuard guard;
    private final RateLimiter rateLimiter;

    public ClaimService(ClaimRepository claims, ListingRepository listings,
                        MatchCandidateRepository candidates, AccessGuard guard, RateLimiter rateLimiter) {
        this.claims = claims;
        this.listings = listings;
        this.candidates = candidates;
        this.guard = guard;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public Claim submit(AppPrincipal actor, Long listingId, String answer, Long matchCandidateId) {
        User claimant = guard.requireVerified(actor);
        rateLimiter.check(RateLimiter.Bucket.SUBMIT_CLAIM, String.valueOf(actor.id()));

        Listing listing = listings.findById(listingId)
                .orElseThrow(() -> new Exceptions.NotFoundException("Listing not found."));
        if (listing.getKind() != ListingKind.FOUND) {
            throw new Exceptions.ValidationException("You can only claim a FOUND listing.");
        }
        if (!listing.isActive()) {
            throw new Exceptions.ValidationException(
                    "This listing is " + listing.getStatus() + " and no longer accepts claims.");
        }
        if (actor.id().equals(listing.getReporterId())) {
            throw new Exceptions.ForbiddenException("You cannot claim your own listing.");
        }
        if (claims.existsPending(listingId, actor.id(), ClaimStatus.PENDING)) {
            throw new Exceptions.ConflictException("You already have a pending claim on this listing.");
        }

        MatchCandidate candidate = null;
        if (matchCandidateId != null) {
            candidate = candidates.findById(matchCandidateId)
                    .orElseThrow(() -> new Exceptions.NotFoundException("Match not found."));
            if (!candidate.involves(listingId)) {
                throw new Exceptions.ValidationException("That match does not involve this listing.");
            }
        }
        return claims.save(new Claim(listing, claimant, answer.trim(), candidate));
    }

    @Transactional(readOnly = true)
    public List<Claim> forListing(AppPrincipal actor, Long listingId) {
        Listing listing = listings.findById(listingId)
                .orElseThrow(() -> new Exceptions.NotFoundException("Listing not found."));
        guard.requireOwnerOrModerator(actor, listing.getReporterId(), "listing's claims");
        return claims.findForListing(listingId);
    }

    @Transactional(readOnly = true)
    public List<Claim> mine(Long userId) {
        return claims.findForClaimant(userId);
    }

    @Transactional
    public Claim decide(AppPrincipal actor, Long claimId, boolean approve, String note) {
        Claim claim = claims.findByIdWithRefs(claimId)
                .orElseThrow(() -> new Exceptions.NotFoundException("Claim not found."));
        guard.requireOwnerOrModerator(actor, claim.getListing().getReporterId(), "claim");
        if (!claim.isPending()) {
            throw new Exceptions.ValidationException("This claim has already been decided.");
        }
        User decider = guard.requireUser(actor.id());
        claim.decide(approve ? ClaimStatus.APPROVED : ClaimStatus.REJECTED, decider,
                note == null || note.isBlank() ? null : note.trim());
        return claim;
    }

    @Transactional
    public Claim withdraw(AppPrincipal actor, Long claimId) {
        Claim claim = claims.findByIdWithRefs(claimId)
                .orElseThrow(() -> new Exceptions.NotFoundException("Claim not found."));
        if (!actor.id().equals(claim.getClaimantId())) {
            throw new Exceptions.ForbiddenException("Only the claimant can withdraw a claim.");
        }
        if (!claim.isPending()) {
            throw new Exceptions.ValidationException("This claim has already been decided.");
        }
        claim.withdraw();
        return claim;
    }
}
