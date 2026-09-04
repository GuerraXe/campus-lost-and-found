package com.campuslostfound.domain;

/** State of an ownership claim against a FOUND listing. */
public enum ClaimStatus {
    PENDING,
    APPROVED,
    REJECTED,
    WITHDRAWN
}
