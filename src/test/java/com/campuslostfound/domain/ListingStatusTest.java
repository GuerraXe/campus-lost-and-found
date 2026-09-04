package com.campuslostfound.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ListingStatusTest {

    @ParameterizedTest
    @CsvSource({
            "OPEN,MATCHED,true",
            "OPEN,CLOSED,true",
            "OPEN,RECOVERED,true",
            "OPEN,REMOVED,true",
            "OPEN,OPEN,false",
            "MATCHED,OPEN,true",
            "MATCHED,RECOVERED,true",
            "MATCHED,REMOVED,true",
            "CLOSED,OPEN,true",
            "CLOSED,REMOVED,true",
            "CLOSED,MATCHED,false",
            "CLOSED,RECOVERED,false",
            "RECOVERED,OPEN,false",
            "RECOVERED,CLOSED,false",
            "REMOVED,OPEN,false",
    })
    void transitionRules(ListingStatus from, ListingStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @Test
    void recoveredAndRemovedAreTerminal() {
        assertThat(ListingStatus.RECOVERED.isTerminal()).isTrue();
        assertThat(ListingStatus.REMOVED.isTerminal()).isTrue();
        assertThat(ListingStatus.OPEN.isTerminal()).isFalse();
        assertThat(ListingStatus.CLOSED.isTerminal()).isFalse();
    }
}
