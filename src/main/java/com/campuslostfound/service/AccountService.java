package com.campuslostfound.service;

import com.campuslostfound.domain.Listing;
import com.campuslostfound.domain.ListingStatus;
import com.campuslostfound.domain.Role;
import com.campuslostfound.domain.User;
import com.campuslostfound.repo.ListingRepository;
import com.campuslostfound.repo.UserRepository;
import com.campuslostfound.security.AppPrincipal;
import com.campuslostfound.service.support.Tokens;
import com.campuslostfound.web.error.Exceptions;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account self-service and administration.
 *
 * <p>Deletion is an <em>anonymization</em>, not a row delete: the user's email and name are
 * replaced with tombstone values, the password is randomized, every access token is
 * invalidated, and their still-open listings are closed. Authored messages, claims, and
 * flags are retained (they belong to other people's threads) but now show "Deleted user".
 * This keeps referential integrity and other users' history intact while removing the
 * personal data (see docs/design-decisions.md DD-12).
 */
@Service
public class AccountService {

    private final UserRepository users;
    private final ListingRepository listings;
    private final PasswordEncoder passwordEncoder;
    private final AccessGuard guard;

    public AccountService(UserRepository users, ListingRepository listings,
                          PasswordEncoder passwordEncoder, AccessGuard guard) {
        this.users = users;
        this.listings = listings;
        this.passwordEncoder = passwordEncoder;
        this.guard = guard;
    }

    @Transactional
    public void deleteOwnAccount(AppPrincipal actor) {
        User user = guard.requireUser(actor.id());
        for (Listing listing : listings.findByReporterIdOrderByCreatedAtDesc(user.getId())) {
            if (listing.getStatus() == ListingStatus.OPEN || listing.getStatus() == ListingStatus.MATCHED) {
                listing.setStatus(ListingStatus.CLOSED);
            }
        }
        user.anonymize("deleted+" + user.getId() + "@invalid",
                passwordEncoder.encode(Tokens.random()));
    }

    @Transactional
    public User changeRole(AppPrincipal actor, Long userId, Role role) {
        if (!actor.isAdmin()) {
            throw new Exceptions.ForbiddenException("Administrator role required.");
        }
        User target = users.findById(userId)
                .orElseThrow(() -> new Exceptions.NotFoundException("User not found."));
        if (target.isDeleted()) {
            throw new Exceptions.ValidationException("Cannot change the role of a deleted account.");
        }
        target.setRole(role);
        target.invalidateExistingTokens(); // drop any token carrying the old role
        return target;
    }
}
