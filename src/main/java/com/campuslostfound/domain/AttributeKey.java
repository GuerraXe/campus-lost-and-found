package com.campuslostfound.domain;

/**
 * Controlled key for a structured listing attribute. This is the public, matchable
 * description surface. Deliberately excludes serial numbers and other secrets that
 * should prove ownership - those go in {@code Listing.privateDetails} and the claim
 * workflow, never here (see docs/design-decisions.md DD-4).
 */
public enum AttributeKey {
    COLOR,
    BRAND,
    MATERIAL,
    SIZE,
    MODEL,
    PATTERN,
    OTHER
}
