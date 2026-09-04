package com.campuslostfound.service;

import com.campuslostfound.domain.AttributeKey;
import com.campuslostfound.domain.Category;
import com.campuslostfound.domain.ClaimStatus;
import com.campuslostfound.domain.Listing;
import com.campuslostfound.domain.ListingAttribute;
import com.campuslostfound.domain.ListingKind;
import com.campuslostfound.domain.ListingStatus;
import com.campuslostfound.domain.User;
import com.campuslostfound.repo.ClaimRepository;
import com.campuslostfound.repo.ListingRepository;
import com.campuslostfound.security.AppPrincipal;
import com.campuslostfound.service.support.RateLimiter;
import com.campuslostfound.web.error.Exceptions;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listings: create, read (with field-level visibility), search/filter, patch, status
 * transitions, and attribute edits. All business rules live here; controllers only do
 * transport and coarse role gating.
 */
@Service
public class ListingService {

    private final ListingRepository listings;
    private final ClaimRepository claims;
    private final AccessGuard guard;
    private final MatchingService matching;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public ListingService(ListingRepository listings, ClaimRepository claims, AccessGuard guard,
                          MatchingService matching, RateLimiter rateLimiter, Clock clock) {
        this.listings = listings;
        this.claims = claims;
        this.guard = guard;
        this.matching = matching;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Transactional
    public Listing create(AppPrincipal actor, CreateCommand cmd) {
        User reporter = guard.requireVerified(actor);
        rateLimiter.check(RateLimiter.Bucket.CREATE_LISTING, String.valueOf(actor.id()));
        requireNotFuture(cmd.eventDate());

        Listing listing = new Listing(reporter, cmd.kind(), cmd.title().trim(),
                cmd.description().trim(), cmd.category(), cmd.eventDate());
        listing.setLocationText(blankToNull(cmd.locationText()));
        listing.setBuilding(blankToNull(cmd.building()));
        listing.setArea(blankToNull(cmd.area()));
        listing.setPrivateDetails(blankToNull(cmd.privateDetails()));
        for (AttributeCommand a : cmd.attributes()) {
            listing.addAttribute(new ListingAttribute(a.key(), a.value().trim()));
        }
        Listing saved = listings.save(listing);
        matching.onListingCreated(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public View getForView(Long id, AppPrincipal actorOrNull) {
        Listing listing = listings.findById(id)
                .orElseThrow(() -> new Exceptions.NotFoundException("Listing not found."));
        boolean privileged = actorOrNull != null
                && (actorOrNull.id().equals(listing.getReporterId()) || actorOrNull.isModerator());
        if (listing.getStatus() == ListingStatus.REMOVED && !privileged) {
            throw new Exceptions.NotFoundException("Listing not found.");
        }
        return new View(listing, privileged);
    }

    @Transactional(readOnly = true)
    public Listing getEntity(Long id) {
        return listings.findById(id)
                .orElseThrow(() -> new Exceptions.NotFoundException("Listing not found."));
    }

    @Transactional(readOnly = true)
    public Page<Listing> search(SearchQuery q, Pageable pageable) {
        return listings.findAll(specFor(q), pageable);
    }

    @Transactional
    public Listing patch(AppPrincipal actor, Long id, PatchCommand cmd) {
        Listing listing = getEntity(id);
        guard.requireOwnerOrModerator(actor, listing.getReporterId(), "listing");
        if (listing.getStatus().isTerminal()) {
            throw new Exceptions.ValidationException(
                    "A " + listing.getStatus() + " listing can no longer be edited.");
        }
        if (cmd.title() != null) {
            listing.setTitle(cmd.title().trim());
        }
        if (cmd.description() != null) {
            listing.setDescription(cmd.description().trim());
        }
        if (cmd.category() != null) {
            listing.setCategory(cmd.category());
        }
        if (cmd.locationText() != null) {
            listing.setLocationText(blankToNull(cmd.locationText()));
        }
        if (cmd.building() != null) {
            listing.setBuilding(blankToNull(cmd.building()));
        }
        if (cmd.area() != null) {
            listing.setArea(blankToNull(cmd.area()));
        }
        if (cmd.privateDetails() != null) {
            listing.setPrivateDetails(blankToNull(cmd.privateDetails()));
        }
        if (cmd.eventDate() != null) {
            requireNotFuture(cmd.eventDate());
            listing.setEventDate(cmd.eventDate());
        }
        return listing;
    }

    @Transactional
    public Listing changeStatus(AppPrincipal actor, Long id, ListingStatus target) {
        Listing listing = getEntity(id);

        if (target == ListingStatus.REMOVED) {
            guard.requireModerator(actor);
        } else {
            guard.requireOwnerOrModerator(actor, listing.getReporterId(), "listing");
        }
        if (!listing.getStatus().canTransitionTo(target)) {
            throw new Exceptions.ValidationException(
                    "Cannot change status from " + listing.getStatus() + " to " + target + ".");
        }
        if (target == ListingStatus.RECOVERED && !actor.isModerator()) {
            boolean hasApprovedClaim = claims.existsWithStatus(id, ClaimStatus.APPROVED);
            if (!hasApprovedClaim) {
                throw new Exceptions.ValidationException(
                        "Mark as recovered only after a claim on this listing has been approved, "
                                + "or ask a moderator.");
            }
        }
        listing.setStatus(target);
        return listing;
    }

    @Transactional
    public ListingAttribute addAttribute(AppPrincipal actor, Long id, AttributeKey key, String value) {
        Listing listing = getEntity(id);
        guard.requireOwnerOrModerator(actor, listing.getReporterId(), "listing");
        String trimmed = value.trim();
        boolean exists = listing.getAttributes().stream()
                .anyMatch(a -> a.getKey() == key && a.getValue().equalsIgnoreCase(trimmed));
        if (exists) {
            throw new Exceptions.ConflictException("That attribute is already set on the listing.");
        }
        ListingAttribute attr = new ListingAttribute(key, trimmed);
        listing.addAttribute(attr);
        listings.flush();
        return attr;
    }

    @Transactional
    public void removeAttribute(AppPrincipal actor, Long id, Long attributeId) {
        Listing listing = getEntity(id);
        guard.requireOwnerOrModerator(actor, listing.getReporterId(), "listing");
        ListingAttribute attr = listing.getAttributes().stream()
                .filter(a -> a.getId().equals(attributeId))
                .findFirst()
                .orElseThrow(() -> new Exceptions.NotFoundException("Attribute not found on this listing."));
        listing.removeAttribute(attr);
    }

    @Transactional(readOnly = true)
    public List<Listing> myListings(Long userId) {
        return listings.findByReporter(userId);
    }

    // --- helpers ---------------------------------------------------------------

    private void requireNotFuture(LocalDate date) {
        if (date.isAfter(LocalDate.now(clock))) {
            throw new Exceptions.ValidationException("The date cannot be in the future.");
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static Specification<Listing> specFor(SearchQuery q) {
        return (root, query, cb) -> {
            var p = cb.conjunction();
            // Public search never shows removed listings.
            p = cb.and(p, cb.notEqual(root.get("status"), ListingStatus.REMOVED));
            if (q.kind() != null) {
                p = cb.and(p, cb.equal(root.get("kind"), q.kind()));
            }
            if (q.category() != null) {
                p = cb.and(p, cb.equal(root.get("category"), q.category()));
            }
            if (q.status() != null) {
                p = cb.and(p, cb.equal(root.get("status"), q.status()));
            }
            if (q.building() != null && !q.building().isBlank()) {
                p = cb.and(p, cb.equal(cb.lower(root.get("building")), q.building().trim().toLowerCase()));
            }
            if (q.dateFrom() != null) {
                p = cb.and(p, cb.greaterThanOrEqualTo(root.get("eventDate"), q.dateFrom()));
            }
            if (q.dateTo() != null) {
                p = cb.and(p, cb.lessThanOrEqualTo(root.get("eventDate"), q.dateTo()));
            }
            if (q.text() != null && !q.text().isBlank()) {
                for (String term : q.text().toLowerCase().split("\\s+")) {
                    if (term.isBlank()) {
                        continue;
                    }
                    String like = "%" + term + "%";
                    p = cb.and(p, cb.or(
                            cb.like(cb.lower(root.get("title")), like),
                            cb.like(cb.lower(root.get("description")), like),
                            cb.like(cb.lower(cb.coalesce(root.get("locationText"), "")), like),
                            cb.like(cb.lower(cb.coalesce(root.get("building"), "")), like),
                            cb.like(cb.lower(cb.coalesce(root.get("area"), "")), like)));
                }
            }
            return p;
        };
    }

    // --- commands / queries --------------------------------------------------

    public record AttributeCommand(AttributeKey key, String value) {
    }

    public record CreateCommand(ListingKind kind, String title, String description, Category category,
                                String locationText, String building, String area, LocalDate eventDate,
                                String privateDetails, List<AttributeCommand> attributes) {
    }

    public record PatchCommand(String title, String description, Category category, String locationText,
                               String building, String area, LocalDate eventDate, String privateDetails) {
    }

    public record SearchQuery(ListingKind kind, Category category, ListingStatus status, String building,
                              String text, LocalDate dateFrom, LocalDate dateTo) {
    }

    /** A listing plus whether the caller may see its private fields. */
    public record View(Listing listing, boolean privileged) {
    }
}
