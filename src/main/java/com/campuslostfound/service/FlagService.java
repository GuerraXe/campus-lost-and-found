package com.campuslostfound.service;

import com.campuslostfound.domain.Flag;
import com.campuslostfound.domain.FlagReason;
import com.campuslostfound.domain.FlagStatus;
import com.campuslostfound.domain.Listing;
import com.campuslostfound.domain.ListingStatus;
import com.campuslostfound.domain.User;
import com.campuslostfound.repo.FlagRepository;
import com.campuslostfound.repo.ListingRepository;
import com.campuslostfound.security.AppPrincipal;
import com.campuslostfound.service.support.RateLimiter;
import com.campuslostfound.web.error.Exceptions;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reporting suspicious or incorrect listings and the moderator queue that works them.
 * A user may hold only one unresolved flag per listing at a time, but may flag again after
 * a previous flag is resolved.
 */
@Service
public class FlagService {

    private static final List<FlagStatus> UNRESOLVED = List.of(FlagStatus.OPEN, FlagStatus.REVIEWED);

    private final FlagRepository flags;
    private final ListingRepository listings;
    private final AccessGuard guard;
    private final RateLimiter rateLimiter;

    public FlagService(FlagRepository flags, ListingRepository listings, AccessGuard guard,
                       RateLimiter rateLimiter) {
        this.flags = flags;
        this.listings = listings;
        this.guard = guard;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public Flag submit(AppPrincipal actor, Long listingId, FlagReason reason, String details) {
        User reporter = guard.requireVerified(actor);
        rateLimiter.check(RateLimiter.Bucket.SUBMIT_FLAG, String.valueOf(actor.id()));

        Listing listing = listings.findById(listingId)
                .orElseThrow(() -> new Exceptions.NotFoundException("Listing not found."));
        if (listing.getStatus() == ListingStatus.REMOVED) {
            throw new Exceptions.NotFoundException("Listing not found.");
        }
        if (flags.existsByListingIdAndReporterIdAndStatusIn(listingId, actor.id(), UNRESOLVED)) {
            throw new Exceptions.ConflictException(
                    "You already have an open report on this listing.");
        }
        return flags.save(new Flag(listing, reporter, reason,
                details == null || details.isBlank() ? null : details.trim()));
    }

    @Transactional(readOnly = true)
    public Page<Flag> queue(FlagStatus status, Pageable pageable) {
        return status == null
                ? flags.findByOrderByCreatedAtAsc(pageable)
                : flags.findByStatusOrderByCreatedAtAsc(status, pageable);
    }

    @Transactional
    public Flag resolve(AppPrincipal actor, Long flagId, Outcome outcome, String note) {
        guard.requireModerator(actor);
        Flag flag = flags.findById(flagId)
                .orElseThrow(() -> new Exceptions.NotFoundException("Flag not found."));
        User moderator = guard.requireUser(actor.id());
        String trimmedNote = note == null || note.isBlank() ? null : note.trim();
        switch (outcome) {
            case REVIEW -> flag.review(moderator);
            case ACTION -> flag.resolve(FlagStatus.ACTIONED, moderator, trimmedNote);
            case DISMISS -> flag.resolve(FlagStatus.DISMISSED, moderator, trimmedNote);
        }
        return flag;
    }

    public enum Outcome { REVIEW, ACTION, DISMISS }
}
