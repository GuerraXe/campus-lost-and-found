package com.campuslostfound.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/** A report that a listing is suspicious or incorrect, queued for moderator review. */
@Entity
@Table(name = "flags")
public class Flag extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FlagReason reason;

    @Column(length = 1000)
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private FlagStatus status = FlagStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Flag() {
        // for JPA
    }

    public Flag(Listing listing, User reporter, FlagReason reason, String details) {
        this.listing = listing;
        this.reporter = reporter;
        this.reason = reason;
        this.details = details;
    }

    public Listing getListing() {
        return listing;
    }

    public User getReporter() {
        return reporter;
    }

    public Long getReporterId() {
        return reporter == null ? null : reporter.getId();
    }

    public FlagReason getReason() {
        return reason;
    }

    public String getDetails() {
        return details;
    }

    public FlagStatus getStatus() {
        return status;
    }

    public User getResolvedBy() {
        return resolvedBy;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public long getVersion() {
        return version;
    }

    public void review(User moderator) {
        this.status = FlagStatus.REVIEWED;
        this.resolvedBy = moderator;
    }

    public void resolve(FlagStatus outcome, User moderator, String note) {
        this.status = outcome;
        this.resolvedBy = moderator;
        this.resolutionNote = note;
        this.resolvedAt = Instant.now();
    }
}
