package com.campuslostfound.security;

import com.campuslostfound.config.AuthProperties;
import com.campuslostfound.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies stateless HS256 access tokens.
 *
 * <p>Claims: {@code sub} = user id, {@code role}, {@code pca} = the user's
 * {@code passwordChangedAt} epoch-second at issue time. A token whose {@code pca} is older
 * than the user's current {@code passwordChangedAt} is rejected, so a password change (or
 * an explicit logout-all) invalidates every previously issued token.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long ttlSeconds;

    public JwtService(AuthProperties props) {
        this.key = Keys.hmacShaKeyFor(props.getJwtSecret().getBytes(StandardCharsets.UTF_8));
        this.ttlSeconds = props.getJwtTtlSeconds();
    }

    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds);
        String jwt = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                // epoch millis, not seconds: a logout-all bumps passwordChangedAt and must
                // invalidate a token issued moments earlier in the same wall-clock second.
                .claim("pca", user.getPasswordChangedAt().toEpochMilli())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
        return new IssuedToken(jwt, ttlSeconds);
    }

    /** @throws JwtException if the token is malformed, tampered, or expired. */
    public ParsedToken parse(String token) {
        Claims c = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        long pcaMillis = c.get("pca", Number.class).longValue();
        return new ParsedToken(Long.parseLong(c.getSubject()), c.get("role", String.class), pcaMillis);
    }

    public record IssuedToken(String token, long expiresInSeconds) {
    }

    /** {@code passwordChangedAtMillis} is the user's password-change cutoff at issue time. */
    public record ParsedToken(Long userId, String role, long passwordChangedAtMillis) {
    }
}
