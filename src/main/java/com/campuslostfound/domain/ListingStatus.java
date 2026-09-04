package com.campuslostfound.domain;

import java.util.Set;

/**
 * Lifecycle of a listing. Transitions are enforced in {@code ListingService}
 * (see docs/design-decisions.md DD-6):
 *
 * <pre>
 *   OPEN     -> MATCHED | CLOSED | RECOVERED | REMOVED
 *   MATCHED  -> OPEN | CLOSED | RECOVERED | REMOVED
 *   CLOSED   -> OPEN | REMOVED
 *   RECOVERED-> (terminal)
 *   REMOVED  -> (terminal)
 * </pre>
 *
 * A move to RECOVERED additionally requires an APPROVED claim, unless performed by a
 * moderator (DD-5).
 */
public enum ListingStatus {
    OPEN,
    MATCHED,
    RECOVERED,
    CLOSED,
    REMOVED;

    private static final Set<ListingStatus> TERMINAL = Set.of(RECOVERED, REMOVED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean canTransitionTo(ListingStatus target) {
        if (this == target) {
            return false;
        }
        return switch (this) {
            case OPEN -> target == MATCHED || target == CLOSED || target == RECOVERED || target == REMOVED;
            case MATCHED -> target == OPEN || target == CLOSED || target == RECOVERED || target == REMOVED;
            case CLOSED -> target == OPEN || target == REMOVED;
            case RECOVERED, REMOVED -> false;
        };
    }
}
