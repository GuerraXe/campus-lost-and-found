package com.campuslostfound.service.support;

import com.campuslostfound.config.RateLimitProperties;
import com.campuslostfound.web.error.Exceptions;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * In-process fixed-window rate limiter. State is a bounded LRU map keyed by
 * {@code "<bucket>|<caller>"}; the oldest entry is evicted past
 * {@code campus.ratelimit.max-tracked-keys}, so an attacker cycling identifiers cannot
 * grow it without bound. Per-instance and non-durable by design (see DD-11).
 */
@Component
public class RateLimiter {

    /** Endpoint classes with independent limits. */
    public enum Bucket {
        CREATE_LISTING, CONTACT_MESSAGE, SUBMIT_CLAIM, SUBMIT_FLAG, RESCAN
    }

    private final RateLimitProperties props;
    private final Map<String, Window> windows;

    public RateLimiter(RateLimitProperties props) {
        this.props = props;
        int cap = Math.max(1000, props.getMaxTrackedKeys());
        this.windows = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Window> eldest) {
                return size() > cap;
            }
        };
    }

    /**
     * Records one hit for {@code caller} against {@code bucket}.
     *
     * @throws Exceptions.RateLimitedException if the limit for the current window is exceeded
     */
    public synchronized void check(Bucket bucket, String caller) {
        if (!props.isEnabled()) {
            return;
        }
        RateLimitProperties.Rule rule = ruleFor(bucket);
        long now = System.currentTimeMillis();
        long windowMs = rule.getWindowSeconds() * 1000L;
        String key = bucket.name() + '|' + caller;

        Window w = windows.get(key);
        if (w == null || now - w.startedAt >= windowMs) {
            windows.put(key, new Window(now, 1));
            return;
        }
        if (w.count >= rule.getLimit()) {
            long retryAfter = Math.max(1, (w.startedAt + windowMs - now) / 1000);
            throw new Exceptions.RateLimitedException(retryAfter);
        }
        w.count++;
    }

    private RateLimitProperties.Rule ruleFor(Bucket bucket) {
        return switch (bucket) {
            case CREATE_LISTING -> props.getCreateListing();
            case CONTACT_MESSAGE -> props.getContactMessage();
            case SUBMIT_CLAIM -> props.getSubmitClaim();
            case SUBMIT_FLAG -> props.getSubmitFlag();
            case RESCAN -> props.getRescan();
        };
    }

    private static final class Window {
        final long startedAt;
        int count;

        Window(long startedAt, int count) {
            this.startedAt = startedAt;
            this.count = count;
        }
    }
}
