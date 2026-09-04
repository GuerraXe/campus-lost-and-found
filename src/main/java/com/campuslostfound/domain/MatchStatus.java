package com.campuslostfound.domain;

/**
 * State of a suggested match. The algorithm only ever produces SUGGESTED; a human
 * moves it to CONFIRMED or REJECTED. CONFIRMED can be reverted to SUGGESTED
 * ("unconfirm") so a mistaken confirmation does not strand other claimants (DD-7).
 * REJECTED is sticky: a rescan will not re-create a rejected pair.
 */
public enum MatchStatus {
    SUGGESTED,
    CONFIRMED,
    REJECTED
}
