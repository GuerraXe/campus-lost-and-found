package com.campuslostfound.matching;

import com.campuslostfound.config.MatchingProperties;
import com.campuslostfound.domain.AttributeKey;
import com.campuslostfound.domain.Category;
import com.campuslostfound.domain.Listing;
import com.campuslostfound.domain.ListingAttribute;
import com.campuslostfound.domain.MatchSignal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The matching algorithm. Given a LOST listing and a FOUND listing it produces a 0-100
 * score and a human-readable breakdown. Deterministic and side-effect free - all state is
 * in the arguments and {@link MatchingProperties} - so it is unit-tested directly.
 *
 * <p>Five independent signals, each yielding a sub-score in [0, 1], are combined with
 * fixed weights that sum to 1.0:
 *
 * <pre>
 *   category    0.30   exact enum match (a partial credit if either side is OTHER)
 *   keywords    0.30   overlap coefficient of normalized tokens, tilted toward
 *                      distinctive (non-generic) shared words
 *   location    0.15   same building &gt; same area &gt; shared free-text location words
 *   date        0.15   linear decay to zero at `date-decay-days` apart; halved when the
 *                      found date precedes the lost date (you cannot find it first)
 *   attributes  0.10   fraction of shared structured attribute keys whose values agree
 * </pre>
 *
 * <p>{@code contribution = round(weight * sub * 100)} and the score is their sum, so the
 * reasons always add up to the score. A score never proves a match; a human confirms it.
 */
@Component
public class MatchEngine {

    private final TextNormalizer normalizer;
    private final MatchingProperties props;

    public MatchEngine(TextNormalizer normalizer, MatchingProperties props) {
        this.normalizer = normalizer;
        this.props = props;
    }

    public MatchResult score(Listing lost, Listing found) {
        List<MatchResult.Reason> reasons = new ArrayList<>();
        addReason(reasons, MatchSignal.CATEGORY, props.getWeightCategory(), category(lost, found));
        addReason(reasons, MatchSignal.KEYWORDS, props.getWeightKeywords(), keywords(lost, found));
        addReason(reasons, MatchSignal.LOCATION, props.getWeightLocation(), location(lost, found));
        addReason(reasons, MatchSignal.DATE, props.getWeightDate(), date(lost, found));
        addReason(reasons, MatchSignal.ATTRIBUTES, props.getWeightAttributes(), attributes(lost, found));

        int total = reasons.stream().mapToInt(MatchResult.Reason::contribution).sum();
        return new MatchResult(total, reasons);
    }

    private void addReason(List<MatchResult.Reason> out, MatchSignal signal, double weight, Scored s) {
        int contribution = (int) Math.round(weight * s.sub() * 100.0);
        if (contribution > 0) {
            out.add(new MatchResult.Reason(signal, s.detail(), contribution));
        }
    }

    // --- signals -----------------------------------------------------------

    private Scored category(Listing lost, Listing found) {
        Category a = lost.getCategory();
        Category b = found.getCategory();
        if (a == b) {
            return new Scored(1.0, "Same category: " + a.label());
        }
        if (a == Category.OTHER || b == Category.OTHER) {
            return new Scored(0.25, "One side is categorized as Other");
        }
        return Scored.zero();
    }

    private Scored keywords(Listing lost, Listing found) {
        Set<String> a = normalizer.tokens(lost.getTitle(), lost.getDescription(), lost.getPrivateDetails());
        Set<String> b = normalizer.tokens(found.getTitle(), found.getDescription(), found.getPrivateDetails());
        if (a.isEmpty() || b.isEmpty()) {
            return Scored.zero();
        }
        Set<String> shared = new HashSet<>(a);
        shared.retainAll(b);
        if (shared.isEmpty()) {
            return Scored.zero();
        }
        double overlapCoef = (double) shared.size() / Math.min(a.size(), b.size());
        long distinctive = shared.stream().filter(normalizer::isDistinctive).count();
        double distinctiveShare = (double) distinctive / shared.size();
        double sub = clamp(0.7 * overlapCoef + 0.3 * distinctiveShare);

        String sample = new TreeSet<>(shared).stream().limit(6).collect(Collectors.joining(", "));
        String detail = shared.size() + " shared keyword" + (shared.size() == 1 ? "" : "s") + ": " + sample;
        return new Scored(sub, detail);
    }

    private Scored location(Listing lost, Listing found) {
        if (bothPresentEqual(lost.getBuilding(), found.getBuilding())) {
            return new Scored(1.0, "Both in building '" + lost.getBuilding().trim() + "'");
        }
        if (bothPresentEqual(lost.getArea(), found.getArea())) {
            return new Scored(0.6, "Both in area '" + lost.getArea().trim() + "'");
        }
        Set<String> a = normalizer.tokens(lost.getLocationText());
        Set<String> b = normalizer.tokens(found.getLocationText());
        if (!a.isEmpty() && !b.isEmpty()) {
            Set<String> shared = new HashSet<>(a);
            shared.retainAll(b);
            if (!shared.isEmpty()) {
                double coef = (double) shared.size() / Math.min(a.size(), b.size());
                return new Scored(clamp(0.5 * coef),
                        "Location descriptions share: " + new TreeSet<>(shared).stream()
                                .limit(4).collect(Collectors.joining(", ")));
            }
        }
        return Scored.zero();
    }

    private Scored date(Listing lost, Listing found) {
        long delta = Math.abs(ChronoUnit.DAYS.between(lost.getEventDate(), found.getEventDate()));
        double base = Math.max(0.0, 1.0 - (double) delta / props.getDateDecayDays());
        boolean foundBeforeLost = found.getEventDate().isBefore(lost.getEventDate().minusDays(1));
        if (foundBeforeLost) {
            base *= 0.5;
        }
        if (base <= 0.0) {
            return Scored.zero();
        }
        String detail = delta == 0
                ? "Lost and found reported on the same day"
                : "Reported " + delta + " day" + (delta == 1 ? "" : "s") + " apart";
        if (foundBeforeLost) {
            detail += " (found date precedes lost date)";
        }
        return new Scored(base, detail);
    }

    private Scored attributes(Listing lost, Listing found) {
        Map<AttributeKey, Set<String>> a = attrMap(lost);
        Map<AttributeKey, Set<String>> b = attrMap(found);
        Set<AttributeKey> sharedKeys = new HashSet<>(a.keySet());
        sharedKeys.retainAll(b.keySet());
        if (sharedKeys.isEmpty()) {
            return Scored.zero();
        }
        List<String> matched = new ArrayList<>();
        for (AttributeKey key : sharedKeys) {
            Set<String> values = new HashSet<>(a.get(key));
            values.retainAll(b.get(key));
            if (!values.isEmpty()) {
                matched.add(key.name() + "=" + String.join("/", new TreeSet<>(values)));
            }
        }
        if (matched.isEmpty()) {
            return Scored.zero();
        }
        double sub = (double) matched.size() / sharedKeys.size();
        return new Scored(sub, "Attributes agree: " + String.join(", ", new TreeSet<>(matched)));
    }

    private static Map<AttributeKey, Set<String>> attrMap(Listing listing) {
        Map<AttributeKey, Set<String>> map = new HashMap<>();
        for (ListingAttribute attr : listing.getAttributes()) {
            map.computeIfAbsent(attr.getKey(), k -> new HashSet<>())
                    .add(attr.getValue().trim().toLowerCase(Locale.ROOT));
        }
        return map;
    }

    private static boolean bothPresentEqual(String x, String y) {
        return x != null && y != null && !x.isBlank() && !y.isBlank()
                && x.trim().equalsIgnoreCase(y.trim());
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private record Scored(double sub, String detail) {
        static Scored zero() {
            return new Scored(0.0, "");
        }
    }
}
