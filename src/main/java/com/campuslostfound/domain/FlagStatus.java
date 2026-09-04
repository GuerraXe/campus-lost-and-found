package com.campuslostfound.domain;

/** Moderation state of a flag. */
public enum FlagStatus {
    OPEN,
    REVIEWED,
    ACTIONED,
    DISMISSED;

    public boolean isResolved() {
        return this == ACTIONED || this == DISMISSED;
    }
}
