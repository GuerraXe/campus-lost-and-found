package com.campuslostfound.web.api;

import com.campuslostfound.domain.Role;
import com.campuslostfound.security.AppPrincipal;
import com.campuslostfound.service.AccountService;
import com.campuslostfound.web.Mappers;
import com.campuslostfound.web.dto.AuthDtos.UserResponse;
import com.campuslostfound.web.validation.SafeText;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administrator-only user management. */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AccountService accounts;

    public AdminController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PutMapping("/users/{userId}/role")
    public UserResponse changeRole(@AuthenticationPrincipal AppPrincipal actor,
                                   @PathVariable Long userId,
                                   @jakarta.validation.Valid @RequestBody RoleChangeRequest req) {
        return Mappers.user(accounts.changeRole(actor, userId, req.role()));
    }

    public record RoleChangeRequest(@NotNull Role role, @SafeText String reason) {
    }
}
