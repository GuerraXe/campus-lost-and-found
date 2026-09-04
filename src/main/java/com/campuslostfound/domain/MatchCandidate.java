package com.campuslostfound.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A potential relationship between a LOST listing and a FOUND listing, as proposed by the
 * matching engine. {@code score} equals the sum of the {@link MatchReason#getContribution()}
 * values, so the explanation always adds up. {@code scorerVersion} records which weight
 * set produced the score (see docs/design-decisions.md DD-9).
 */
@Entity
@Table(name = "match_candidates")
public class MatchCandidate extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lost_listing_id", nullable = false)
    private Listing lostListing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "found_listing_id", nullable = false)
    private Listing foundListing;

    @Column(nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private MatchStatus status = MatchStatus.SUGGESTED;

    @Column(name = "scorer_version", nullable = false, length = 16)
    private String scorerVersion;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MatchReason> reasons = new ArrayList<>();

    protected MatchCandidate() {
        // for JPA
    }

    public MatchCandidate(Listing lostListing, Listing foundListing, int score, String scorerVersion) {
        this.lostListing = lostListing;
        this.foundListing = foundListing;
        this.score = score;
        this.scorerVersion = scorerVersion;
    }

    public Listing getLostListing() {
        return lostListing;
    }

    public Listing getFoundListing() {
        return foundListing;
    }

    public Listing otherSide(Listing side) {
        return side.getId().equals(lostListing.getId()) ? foundListing : lostListing;
    }

    public boolean involves(Long listingId) {
        return lostListing.getId().equals(listingId) || foundListing.getId().equals(listingId);
    }

    public int getScore() {
        return score;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public void setStatus(MatchStatus status) {
        this.status = status;
    }

    public String getScorerVersion() {
        return scorerVersion;
    }

    public long getVersion() {
        return version;
    }

    public List<MatchReason> getReasons() {
        return Collections.unmodifiableList(reasons);
    }

    public void addReason(MatchReason reason) {
        reason.setCandidate(this);
        this.reasons.add(reason);
    }

    /** Replace score + reasons in place (used by a rescan). */
    public void rescore(int newScore, List<MatchReason> newReasons, String scorerVersion) {
        this.score = newScore;
        this.scorerVersion = scorerVersion;
        this.reasons.clear();
        newReasons.forEach(this::addReason);
    }
}
