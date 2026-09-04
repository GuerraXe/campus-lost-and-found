package com.campuslostfound.matching;

import com.campuslostfound.domain.MatchSignal;
import java.util.List;

/**
 * Outcome of scoring one (lost, found) pair.
 *
 * <p>{@code score} is exactly the sum of the {@link Reason#contribution()} values, so the
 * explanation always reconstructs the number. A match is a <em>suggestion</em>: it never
 * asserts the two items are the same.
 */
public record MatchResult(int score, List<Reason> reasons) {

    public record Reason(MatchSignal signal, String detail, int contribution) {
    }

    public static MatchResult none() {
        return new MatchResult(0, List.of());
    }
}
