package com.campuslostfound.security;

import com.campuslostfound.domain.Role;
import com.campuslostfound.domain.User;

/**
 * The authenticated caller, derived from a verified JWT plus the current user row.
 * Exposed to controllers via {@code @AuthenticationPrincipal AppPrincipal}.
 */
public record AppPrincipal(Long id, String email, Role role, boolean emailVerified) {

    public static AppPrincipal of(User user) {
        return new AppPrincipal(user.getId(), user.getEmail(), user.getRole(), user.isEmailVerified());
    }

    public boolean isModerator() {
        return role == Role.MODERATOR || role == Role.ADMIN;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
