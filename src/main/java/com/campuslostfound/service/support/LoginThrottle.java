package com.campuslostfound.service.support;

import com.campuslostfound.config.AuthProperties;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Per-account failed-login throttle to blunt credential stuffing. After
 * {@code campus.auth.login-max-failures} consecutive failures an account is locked for
 * {@code campus.auth.login-lockout-minutes}; a success clears the counter. In-process and
 * bounded, same rationale as {@link RateLimiter}.
 */
@Component
public class LoginThrottle {

    private final AuthProperties props;
    private final Map<String, State> byEmail;

    public LoginThrottle(AuthProperties props) {
        this.props = props;
        this.byEmail = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, State> eldest) {
                return size() > 20_000;
            }
        };
    }

    public synchronized boolean isLocked(String email) {
        State s = byEmail.get(key(email));
        if (s == null) {
            return false;
        }
        if (s.lockedUntil > 0 && System.currentTimeMillis() < s.lockedUntil) {
            return true;
        }
        if (s.lockedUntil > 0) {
            byEmail.remove(key(email)); // lock expired
        }
        return false;
    }

    public synchronized void recordFailure(String email) {
        State s = byEmail.computeIfAbsent(key(email), k -> new State());
        s.failures++;
        if (s.failures >= props.getLoginMaxFailures()) {
            s.lockedUntil = System.currentTimeMillis() + props.getLoginLockoutMinutes() * 60_000L;
            s.failures = 0;
        }
    }

    public synchronized void recordSuccess(String email) {
        byEmail.remove(key(email));
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static final class State {
        int failures;
        long lockedUntil;
    }
}
