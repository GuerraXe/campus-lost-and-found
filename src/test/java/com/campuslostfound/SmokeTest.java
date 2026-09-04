package com.campuslostfound;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Toolchain sanity check - no Spring context. */
class SmokeTest {

    @Test
    void arithmeticHolds() {
        assertThat(2 + 2).isEqualTo(4);
    }
}
