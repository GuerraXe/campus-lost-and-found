package com.campuslostfound.domain;

/** Whether a listing reports something LOST by its owner or FOUND by someone else. */
public enum ListingKind {
    LOST,
    FOUND;

    public ListingKind opposite() {
        return this == LOST ? FOUND : LOST;
    }
}
