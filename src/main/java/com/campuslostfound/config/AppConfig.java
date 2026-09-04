package com.campuslostfound.config;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Wires the typed configuration and fails fast on a misconfigured deployment:
 * a too-short JWT secret or matching weights that do not sum to 1.0.
 */
@Configuration
@EnableConfigurationProperties({AuthProperties.class, MatchingProperties.class,
        RateLimitProperties.class, SecurityConfig.CorsProperties.class})
public class AppConfig {

    private final AuthProperties auth;
    private final MatchingProperties matching;

    public AppConfig(AuthProperties auth, MatchingProperties matching) {
        this.auth = auth;
        this.matching = matching;
    }

    @PostConstruct
    void validate() {
        String secret = auth.getJwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "campus.auth.jwt-secret must be set and at least 32 bytes long");
        }
        double sum = matching.weightSum();
        if (Math.abs(sum - 1.0) > 1e-6) {
            throw new IllegalStateException(
                    "campus.matching weights must sum to 1.0 but sum to " + sum);
        }
        if (matching.getSuggestThreshold() < 0 || matching.getSuggestThreshold() > 100) {
            throw new IllegalStateException("campus.matching.suggest-threshold must be 0..100");
        }
    }

    /** BCrypt at cost 12. Plaintext passwords are never stored or logged. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Injected wherever "now" is needed so tests can pin the date. */
    @Bean
    java.time.Clock clock() {
        return java.time.Clock.systemUTC();
    }
}
