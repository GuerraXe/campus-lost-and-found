package com.campuslostfound.service.support;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campuslostfound.config.RateLimitProperties;
import com.campuslostfound.web.error.Exceptions;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private RateLimiter limiter(int limit, int windowSeconds, boolean enabled) {
        RateLimitProperties p = new RateLimitProperties();
        p.setEnabled(enabled);
        p.setSubmitFlag(new RateLimitProperties.Rule(limit, windowSeconds));
        return new RateLimiter(p);
    }

    @Test
    void allowsUpToTheLimitThenRejects() {
        RateLimiter rl = limiter(3, 3600, true);
        for (int i = 0; i < 3; i++) {
            rl.check(RateLimiter.Bucket.SUBMIT_FLAG, "user-1");
        }
        assertThatThrownBy(() -> rl.check(RateLimiter.Bucket.SUBMIT_FLAG, "user-1"))
                .isInstanceOf(Exceptions.RateLimitedException.class)
                .satisfies(e -> {
                    long retry = ((Exceptions.RateLimitedException) e).getRetryAfterSeconds();
                    org.assertj.core.api.Assertions.assertThat(retry).isPositive();
                });
    }

    @Test
    void limitIsPerCaller() {
        RateLimiter rl = limiter(1, 3600, true);
        rl.check(RateLimiter.Bucket.SUBMIT_FLAG, "user-1");
        assertThatCode(() -> rl.check(RateLimiter.Bucket.SUBMIT_FLAG, "user-2")).doesNotThrowAnyException();
    }

    @Test
    void windowResetsAfterExpiry() throws InterruptedException {
        RateLimiter rl = limiter(1, 1, true);
        rl.check(RateLimiter.Bucket.SUBMIT_FLAG, "user-1");
        Thread.sleep(1100);
        assertThatCode(() -> rl.check(RateLimiter.Bucket.SUBMIT_FLAG, "user-1")).doesNotThrowAnyException();
    }

    @Test
    void disabledLimiterNeverThrows() {
        RateLimiter rl = limiter(1, 3600, false);
        for (int i = 0; i < 50; i++) {
            rl.check(RateLimiter.Bucket.SUBMIT_FLAG, "user-1");
        }
    }
}
