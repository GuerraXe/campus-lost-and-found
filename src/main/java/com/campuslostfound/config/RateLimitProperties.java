package com.campuslostfound.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code campus.ratelimit.*}. The limiter is an in-process fixed-window counter,
 * keyed by (user id or client IP) + endpoint class. It is per-instance and resets on
 * restart - adequate for a single-node deployment; a shared store (Redis) would be the
 * production choice (see docs/design-decisions.md DD-11).
 */
@ConfigurationProperties(prefix = "campus.ratelimit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int maxTrackedKeys = 20000;

    private Rule createListing = new Rule(10, 3600);
    private Rule contactMessage = new Rule(20, 3600);
    private Rule submitClaim = new Rule(10, 3600);
    private Rule submitFlag = new Rule(20, 3600);
    private Rule rescan = new Rule(5, 3600);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxTrackedKeys() {
        return maxTrackedKeys;
    }

    public void setMaxTrackedKeys(int maxTrackedKeys) {
        this.maxTrackedKeys = maxTrackedKeys;
    }

    public Rule getCreateListing() {
        return createListing;
    }

    public void setCreateListing(Rule createListing) {
        this.createListing = createListing;
    }

    public Rule getContactMessage() {
        return contactMessage;
    }

    public void setContactMessage(Rule contactMessage) {
        this.contactMessage = contactMessage;
    }

    public Rule getSubmitClaim() {
        return submitClaim;
    }

    public void setSubmitClaim(Rule submitClaim) {
        this.submitClaim = submitClaim;
    }

    public Rule getSubmitFlag() {
        return submitFlag;
    }

    public void setSubmitFlag(Rule submitFlag) {
        this.submitFlag = submitFlag;
    }

    public Rule getRescan() {
        return rescan;
    }

    public void setRescan(Rule rescan) {
        this.rescan = rescan;
    }

    /** A limit of {@code limit} requests per {@code windowSeconds}. */
    public static class Rule {
        private int limit;
        private int windowSeconds;

        public Rule() {
        }

        public Rule(int limit, int windowSeconds) {
            this.limit = limit;
            this.windowSeconds = windowSeconds;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }
}
