package com.campuslostfound.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One line of a match explanation: which signal fired, a human sentence, and its points. */
@Entity
@Table(name = "match_reasons")
public class MatchReason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private MatchCandidate candidate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchSignal signal;

    @Column(nullable = false, length = 300)
    private String detail;

    @Column(nullable = false)
    private int contribution;

    protected MatchReason() {
        // for JPA
    }

    public MatchReason(MatchSignal signal, String detail, int contribution) {
        this.signal = signal;
        this.detail = detail;
        this.contribution = contribution;
    }

    public Long getId() {
        return id;
    }

    public MatchCandidate getCandidate() {
        return candidate;
    }

    void setCandidate(MatchCandidate candidate) {
        this.candidate = candidate;
    }

    public MatchSignal getSignal() {
        return signal;
    }

    public String getDetail() {
        return detail;
    }

    public int getContribution() {
        return contribution;
    }
}
