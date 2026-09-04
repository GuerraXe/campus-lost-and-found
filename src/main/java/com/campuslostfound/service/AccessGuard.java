package com.campuslostfound.service;

import com.campuslostfound.domain.User;
import com.campuslostfound.repo.UserRepository;
import com.campuslostfound.security.AppPrincipal;
import com.campuslostfound.web.error.Exceptions;
import org.springframework.stereotype.Component;

/** Small helpers shared by services: load the acting user, assert ownership / verification. */
@Component
public class AccessGuard {

    private final UserRepository users;

    public AccessGuard(UserRepository users) {
        this.users = users;
    }

    public User requireUser(Long id) {
        return users.findById(id)
                .orElseThrow(() -> new Exceptions.UnauthorizedException("Account not found."));
    }

    public User requireVerified(AppPrincipal actor) {
        User user = requireUser(actor.id());
        if (!user.isEmailVerified()) {
            throw new Exceptions.EmailNotVerifiedException();
        }
        return user;
    }

    public void requireOwnerOrModerator(AppPrincipal actor, Long ownerId, String what) {
        if (!actor.id().equals(ownerId) && !actor.isModerator()) {
            throw new Exceptions.ForbiddenException("You may not modify this " + what + ".");
        }
    }

    public void requireModerator(AppPrincipal actor) {
        if (!actor.isModerator()) {
            throw new Exceptions.ForbiddenException("Moderator role required.");
        }
    }
}
