package com.campuslostfound.matching;

import static org.assertj.core.api.Assertions.assertThat;

import com.campuslostfound.config.MatchingProperties;
import com.campuslostfound.domain.AttributeKey;
import com.campuslostfound.domain.Category;
import com.campuslostfound.domain.Listing;
import com.campuslostfound.domain.ListingAttribute;
import com.campuslostfound.domain.ListingKind;
import com.campuslostfound.domain.MatchSignal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Unit tests for the scoring algorithm - deterministic, no Spring context. */
class MatchEngineTest {

    private final MatchingProperties props = new MatchingProperties();
    private final MatchEngine engine = new MatchEngine(new TextNormalizer(), props);

    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);

    private Listing lost(Category cat, String title, String desc, LocalDate date) {
        return new Listing(null, ListingKind.LOST, title, desc, cat, date);
    }

    private Listing found(Category cat, String title, String desc, LocalDate date) {
        return new Listing(null, ListingKind.FOUND, title, desc, cat, date);
    }

    @Test
    void contributionsAlwaysSumToScore() {
        Listing a = lost(Category.LAPTOP, "Lost Dell laptop",
                "Silver Dell XPS with a rainbow sticker on the lid", DAY);
        Listing b = found(Category.LAPTOP, "Found silver laptop",
                "Dell laptop, rainbow sticker, left in the library", DAY.plusDays(2));
        a.setBuilding("Main Library");
        b.setBuilding("main library");

        MatchResult r = engine.score(a, b);

        int sum = r.reasons().stream().mapToInt(MatchResult.Reason::contribution).sum();
        assertThat(sum).isEqualTo(r.score());
        assertThat(r.score()).isBetween(1, 100);
    }

    @Test
    void exactCategoryOnlyScoresThirtyPoints() {
        Listing a = lost(Category.KEYS, "aaaa", "bbbbbbbbbb", DAY);
        Listing b = found(Category.KEYS, "cccc", "dddddddddd", DAY.plusDays(60));

        MatchResult r = engine.score(a, b);

        assertThat(r.score()).isEqualTo(30);
        assertThat(r.reasons()).singleElement()
                .satisfies(reason -> {
                    assertThat(reason.signal()).isEqualTo(MatchSignal.CATEGORY);
                    assertThat(reason.contribution()).isEqualTo(30);
                });
    }

    @Test
    void differentCategoryNoOverlapScoresZero() {
        Listing a = lost(Category.KEYS, "brass key", "small brass key on a red lanyard", DAY);
        Listing b = found(Category.UMBRELLA, "golf umbrella", "large striped golf umbrella", DAY.plusDays(40));

        assertThat(engine.score(a, b).score()).isZero();
    }

    @Test
    void otherCategoryGivesPartialCredit() {
        Listing a = lost(Category.OTHER, "zzzz", "yyyyyyyyyy", DAY);
        Listing b = found(Category.KEYS, "wwww", "vvvvvvvvvv", DAY.plusDays(90));

        MatchResult r = engine.score(a, b);
        assertThat(r.score()).isEqualTo(8); // round(0.25 * 0.30 * 100)
    }

    @Test
    void dateSignalDecaysLinearlyToZeroAtTwoWeeks() {
        assertThat(dateOnlyScore(0)).isEqualTo(15);
        assertThat(dateOnlyScore(7)).isEqualTo(8);   // round(0.5 * 15)
        assertThat(dateOnlyScore(14)).isZero();
        assertThat(dateOnlyScore(21)).isZero();
    }

    @Test
    void foundBeforeLostHalvesTheDateSignal() {
        Listing a = lost(Category.OTHER, "q1", "q1q1q1q1q1", DAY);
        Listing bAfter = found(Category.MEDICAL, "q2", "q2q2q2q2q2", DAY.plusDays(4));
        Listing bBefore = found(Category.MEDICAL, "q2", "q2q2q2q2q2", DAY.minusDays(4));

        int after = signal(engine.score(a, bAfter), MatchSignal.DATE);
        int before = signal(engine.score(a, bBefore), MatchSignal.DATE);

        assertThat(before).isLessThan(after);
    }

    @Test
    void sameBuildingScoresMoreThanSameAreaOnly() {
        Listing a = lost(Category.OTHER, "m", "mmmmmmmmmm", DAY);
        Listing b = found(Category.PET, "n", "nnnnnnnnnn", DAY.plusDays(50));
        a.setBuilding("Rec Center");
        b.setBuilding("rec center");
        int building = signal(engine.score(a, b), MatchSignal.LOCATION);

        Listing c = lost(Category.OTHER, "m", "mmmmmmmmmm", DAY);
        Listing d = found(Category.PET, "n", "nnnnnnnnnn", DAY.plusDays(50));
        c.setArea("Second floor");
        d.setArea("second floor");
        int area = signal(engine.score(c, d), MatchSignal.LOCATION);

        assertThat(building).isEqualTo(15);
        assertThat(area).isEqualTo(9); // round(0.6 * 15)
        assertThat(building).isGreaterThan(area);
    }

    @Test
    void keywordSignalRewardsDistinctiveSharedTerms() {
        // "black" is a common word; "kryptonite" is distinctive
        Listing common = lost(Category.OTHER, "black bag", "a black bag with black straps", DAY.plusDays(40));
        Listing distinctive = lost(Category.OTHER, "kryptonite lock",
                "kryptonite bike lock serial partial", DAY.plusDays(40));
        Listing foundCommon = found(Category.MEDICAL, "black bag", "black bag black straps", DAY.plusDays(40));
        Listing foundDistinctive = found(Category.MEDICAL, "kryptonite lock",
                "kryptonite bike lock", DAY.plusDays(40));

        int commonScore = signal(engine.score(common, foundCommon), MatchSignal.KEYWORDS);
        int distinctiveScore = signal(engine.score(distinctive, foundDistinctive), MatchSignal.KEYWORDS);

        assertThat(distinctiveScore).isGreaterThan(commonScore);
    }

    @Test
    void attributesAgreementContributes() {
        Listing a = lost(Category.BAGS, "backpack", "a sturdy backpack", DAY.plusDays(40));
        Listing b = found(Category.MEDICAL, "backpack", "a backpack found outside", DAY.plusDays(40));
        a.addAttribute(new ListingAttribute(AttributeKey.BRAND, "Fjallraven"));
        b.addAttribute(new ListingAttribute(AttributeKey.BRAND, "fjallraven"));

        int attrs = signal(engine.score(a, b), MatchSignal.ATTRIBUTES);
        assertThat(attrs).isEqualTo(10);
    }

    @Test
    void keywordDetailListsSharedTermsNotTheWholeDescription() {
        Listing a = lost(Category.LAPTOP, "grey laptop", "dell xps silver rainbow sticker", DAY);
        Listing b = found(Category.LAPTOP, "a laptop", "dell xps rainbow sticker library", DAY);

        String detail = engine.score(a, b).reasons().stream()
                .filter(r -> r.signal() == MatchSignal.KEYWORDS)
                .findFirst().orElseThrow().detail();

        // "laptop" is shared via the titles too -> 5 shared terms; the detail names the
        // shared words only, never the full descriptions.
        assertThat(detail).startsWith("5 shared keywords:");
        assertThat(detail).contains("dell", "rainbow", "sticker", "xps", "laptop");
        assertThat(detail).doesNotContain("silver", "library");
    }

    private int dateOnlyScore(int daysApart) {
        // KEYS vs UMBRELLA: different, neither OTHER -> category contributes nothing,
        // so score() is the date signal alone.
        Listing a = lost(Category.KEYS, "x", "xxxxxxxxxx", DAY);
        Listing b = found(Category.UMBRELLA, "y", "yyyyyyyyyy", DAY.plusDays(daysApart));
        return engine.score(a, b).score();
    }

    private static int signal(MatchResult r, MatchSignal s) {
        return r.reasons().stream().filter(x -> x.signal() == s)
                .mapToInt(MatchResult.Reason::contribution).sum();
    }
}
