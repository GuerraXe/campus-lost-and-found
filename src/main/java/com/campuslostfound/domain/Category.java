package com.campuslostfound.domain;

/**
 * Fixed item taxonomy. Kept as an enum (mirrored by a CHECK constraint) rather than a
 * lookup table: the set is small, rarely changes, and a code + migration change is an
 * acceptable price for compile-time safety (see docs/design-decisions.md DD-3).
 */
public enum Category {
    ELECTRONICS("Electronics"),
    PHONE("Phone"),
    LAPTOP("Laptop"),
    TABLET("Tablet"),
    HEADPHONES("Headphones / earbuds"),
    CHARGER_CABLE("Charger / cable"),
    CAMERA("Camera"),
    BAGS("Bag / backpack"),
    WALLET_PURSE("Wallet / purse"),
    KEYS("Keys"),
    ID_CARD("ID / access card"),
    CLOTHING("Clothing"),
    JEWELRY("Jewelry / watch"),
    EYEWEAR("Glasses / sunglasses"),
    WATER_BOTTLE("Water bottle / flask"),
    BOOKS_NOTES("Books / notes"),
    STATIONERY("Stationery"),
    SPORTS_EQUIPMENT("Sports equipment"),
    UMBRELLA("Umbrella"),
    MUSICAL_INSTRUMENT("Musical instrument"),
    MEDICAL("Medical item"),
    PET("Pet"),
    OTHER("Other");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
