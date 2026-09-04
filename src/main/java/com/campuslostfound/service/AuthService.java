package com.campuslostfound.service;

import com.campuslostfound.config.AuthProperties;
import com.campuslostfound.domain.EmailVerificationToken;
import com.campuslostfound.domain.Role;
import com.campuslostfound.domain.User;
import com.campuslostfound.repo.EmailVerificationTokenRepository;
import com.campuslostfound.repo.UserRepository;
import com.campuslostfound.service.support.LoginThrottle;
import com.campuslostfound.service.support.Tokens;
import com.campuslostfound.security.JwtService;
import com.campuslostfound.web.error.Exceptions;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, email verification, and login. Passwords are hashed with BCrypt and never
 * stored or logged in clear. Login failures are uniform ("invalid email or password")
 * regardless of whether the account exists, and are throttled per account.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final EmailVerificationTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthProperties props;
    private final LoginThrottle throttle;

    public AuthService(UserRepository users, EmailVerificationTokenRepository tokens,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       AuthProperties props, LoginThrottle throttle) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.props = props;
        this.throttle = throttle;
    }

    @Transactional
    public Registration register(String rawEmail, String password, String displayName) {
        String email = normalize(rawEmail);
        if (users.existsByEmail(email)) {
            throw new Exceptions.ConflictException("That email address is already registered.");
        }
        User user = users.save(new User(email, displayName.trim(),
                passwordEncoder.encode(password), Role.USER));

        String raw = Tokens.random();
        tokens.save(new EmailVerificationToken(user, Tokens.sha256Hex(raw),
                Instant.now().plus(props.getVerificationTtlHours(), ChronoUnit.HOURS)));

        return new Registration(user.getId(), user.getEmail(), user.getDisplayName(),
                props.isExposeVerificationToken() ? raw : null);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        EmailVerificationToken token = tokens.findByTokenHash(Tokens.sha256Hex(rawToken))
                .orElseThrow(() -> new Exceptions.ValidationException(
                        "Verification token is invalid or has already been used."));
        if (token.isConsumed() || token.isExpired(Instant.now())) {
            throw new Exceptions.ValidationException(
                    "Verification token is invalid or has already been used.");
        }
        token.getUser().markEmailVerified();
        token.consume();
    }

    @Transactional
    public LoginResult login(String rawEmail, String password) {
        String email = normalize(rawEmail);
        if (throttle.isLocked(email)) {
            throw new Exceptions.RateLimitedException(props.getLoginLockoutMinutes() * 60L);
        }
        User user = users.findByEmail(email).orElse(null);
        if (user == null || user.isDeleted()
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throttle.recordFailure(email);
            throw new Exceptions.UnauthorizedException("Invalid email or password.");
        }
        throttle.recordSuccess(email);
        JwtService.IssuedToken issued = jwtService.issue(user);
        return new LoginResult(issued.token(), "Bearer", issued.expiresInSeconds(),
                user.isEmailVerified());
    }

    /** Invalidate every access token previously issued to this user. */
    @Transactional
    public void logoutEverywhere(Long userId) {
        users.findById(userId).ifPresent(User::invalidateExistingTokens);
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public record Registration(Long userId, String email, String displayName, String verificationToken) {
    }

    public record LoginResult(String accessToken, String tokenType, long expiresIn, boolean emailVerified) {
    }
}
