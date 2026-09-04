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

/**
 * An ownership claim: a user asserts a FOUND listing describes their item and answers the
 * "what identifies it" challenge. The finder or a moderator compares the answer with the
 * listing's private details and approves or rejects. An APPROVED claim is what authorizes
 * moving the listing to RECOVERED (a moderator may override). Multiple users may claim the
 * same listing; approving one does not auto-reject the others (see DD-5, DD-7).
 */
@Entity
@Table(name = "claims")
public class Claim extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claimant_id", nullable = false)
    private User claimant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_candidate_id")
    private MatchCandidate matchCandidate;

    @Column(name = "answer_text", nullable = false, length = 1000)
    private String answerText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ClaimStatus status = ClaimStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Column(name = "decision_note", length = 1000)
    private String decisionNote;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Claim() {
        // for JPA
    }

    public Claim(Listing listing, User claimant, String answerText, MatchCandidate matchCandidate) {
        this.listing = listing;
        this.claimant = claimant;
        this.answerText = answerText;
        this.matchCandidate = matchCandidate;
    }

    public Listing getListing() {
        return listing;
    }

    public User getClaimant() {
        return claimant;
    }

    public Long getClaimantId() {
        return claimant == null ? null : claimant.getId();
    }

    public MatchCandidate getMatchCandidate() {
        return matchCandidate;
    }

    public String getAnswerText() {
        return answerText;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public User getDecidedBy() {
        return decidedBy;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public long getVersion() {
        return version;
    }

    public void decide(ClaimStatus outcome, User moderatorOrFinder, String note) {
        this.status = outcome;
        this.decidedBy = moderatorOrFinder;
        this.decisionNote = note;
        this.decidedAt = Instant.now();
    }

    public void withdraw() {
        this.status = ClaimStatus.WITHDRAWN;
        this.decidedAt = Instant.now();
    }

    public boolean isPending() {
        return status == ClaimStatus.PENDING;
    }
}
