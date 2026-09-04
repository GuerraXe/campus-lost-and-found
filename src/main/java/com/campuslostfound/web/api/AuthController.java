package com.campuslostfound.web.api;

import com.campuslostfound.security.AppPrincipal;
import com.campuslostfound.service.AccessGuard;
import com.campuslostfound.service.AccountService;
import com.campuslostfound.service.AuthService;
import com.campuslostfound.service.AuthService.LoginResult;
import com.campuslostfound.service.AuthService.Registration;
import com.campuslostfound.web.Mappers;
import com.campuslostfound.web.dto.AuthDtos.LoginRequest;
import com.campuslostfound.web.dto.AuthDtos.RegisterRequest;
import com.campuslostfound.web.dto.AuthDtos.RegisterResponse;
import com.campuslostfound.web.dto.AuthDtos.TokenResponse;
import com.campuslostfound.web.dto.AuthDtos.UserResponse;
import com.campuslostfound.web.dto.AuthDtos.VerifyEmailRequest;
import com.campuslostfound.web.error.Exceptions;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Registration, email verification, login, logout-all, and current-user endpoints. */
@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService auth;
    private final AccountService accounts;
    private final AccessGuard guard;

    public AuthController(AuthService auth, AccountService accounts, AccessGuard guard) {
        this.auth = auth;
        this.accounts = accounts;
        this.guard = guard;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest req) {
        Registration r = auth.register(req.email(), req.password(), req.displayName());
        return new RegisterResponse(r.userId(), r.email(), r.displayName(), r.verificationToken());
    }

    @PostMapping("/auth/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        LoginResult r = auth.login(req.email(), req.password());
        return new TokenResponse(r.accessToken(), r.tokenType(), r.expiresIn(), r.emailVerified());
    }

    @PostMapping("/auth/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@Valid @RequestBody VerifyEmailRequest req) {
        auth.verifyEmail(req.token());
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal AppPrincipal actor) {
        auth.logoutEverywhere(require(actor).id());
    }

    @GetMapping("/users/me")
    public UserResponse me(@AuthenticationPrincipal AppPrincipal actor) {
        return Mappers.user(guard.requireUser(require(actor).id()));
    }

    @DeleteMapping("/users/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@AuthenticationPrincipal AppPrincipal actor) {
        accounts.deleteOwnAccount(require(actor));
    }

    private static AppPrincipal require(AppPrincipal actor) {
        if (actor == null) {
            throw new Exceptions.UnauthorizedException("Authentication required.");
        }
        return actor;
    }
}
