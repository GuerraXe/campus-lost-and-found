package com.campuslostfound.matching;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextNormalizerTest {

    private final TextNormalizer normalizer = new TextNormalizer();

    @Test
    void lowercasesSplitsAndDropsPunctuationAndShortTokens() {
        assertThat(normalizer.tokens("Dell XPS-13, silver. A rainbow sticker!"))
                .containsExactlyInAnyOrder("dell", "xps", "13", "silver", "rainbow", "sticker");
    }

    @Test
    void dropsStopWords() {
        assertThat(normalizer.tokens("the bag is on the bench near the door"))
                .containsExactlyInAnyOrder("bag", "bench", "door");
    }

    @Test
    void nullAndBlankPartsAreIgnored() {
        assertThat(normalizer.tokens(null, "", "  ", "keys")).containsExactly("keys");
    }

    @Test
    void distinctiveVsCommon() {
        assertThat(normalizer.isDistinctive("kryptonite")).isTrue();
        assertThat(normalizer.isDistinctive("black")).isFalse();
        assertThat(normalizer.isDistinctive("lost")).isFalse();
    }
}
