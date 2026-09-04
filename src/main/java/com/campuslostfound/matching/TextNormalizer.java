package com.campuslostfound.matching;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Turns free text into a bag of comparable keyword tokens: lower-cased, punctuation
 * stripped, split on whitespace, tokens shorter than two characters and a small English
 * stop-word list removed. No stemming in v1 (see docs/matching.md - a known limitation).
 */
@Component
public class TextNormalizer {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "and", "or", "but", "of", "to", "in", "on", "at", "for",
            "with", "is", "was", "are", "were", "be", "been", "it", "its", "this", "that",
            "these", "those", "i", "my", "me", "we", "our", "you", "your", "he", "she",
            "they", "them", "his", "her", "their", "as", "by", "from", "up", "out", "if",
            "then", "so", "than", "too", "very", "can", "will", "just", "not", "no",
            "have", "has", "had", "do", "does", "did", "about", "into", "over", "after",
            "before", "near", "around", "please", "thanks", "thank");

    /** Words that carry little identifying weight in a lost-and-found context. */
    private static final Set<String> COMMON_WORDS = Set.of(
            "lost", "found", "item", "items", "thing", "stuff", "today", "yesterday",
            "morning", "afternoon", "evening", "night", "campus", "university", "college",
            "reward", "contact", "message", "email", "call", "text", "please", "help",
            "think", "maybe", "somewhere", "left", "dropped", "missing", "reunite",
            "belongs", "owner", "return", "returned", "black", "blue", "grey", "gray",
            "white", "small", "large", "big", "new", "old");

    public Set<String> tokens(String... parts) {
        Set<String> out = new LinkedHashSet<>();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            for (String raw : part.toLowerCase().split("[^a-z0-9]+")) {
                if (raw.length() >= 2 && !STOP_WORDS.contains(raw)) {
                    out.add(raw);
                }
            }
        }
        return out;
    }

    public boolean isDistinctive(String token) {
        return !COMMON_WORDS.contains(token);
    }

    public List<String> tokenList(String... parts) {
        return List.copyOf(tokens(parts));
    }
}
