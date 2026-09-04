package com.campuslostfound.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code campus.matching.*}. Every knob of the algorithm lives here so it can be
 * tuned without a code change. The five weights should sum to 1.0; startup validates it.
 */
@ConfigurationProperties(prefix = "campus.matching")
public class MatchingProperties {

    private int suggestThreshold = 45;
    private int prefilterDays = 21;
    private int maxCandidatesPerListing = 25;
    private String scorerVersion = "v1";

    private double weightCategory = 0.30;
    private double weightKeywords = 0.30;
    private double weightLocation = 0.15;
    private double weightDate = 0.15;
    private double weightAttributes = 0.10;

    private int dateDecayDays = 14;

    public double weightSum() {
        return weightCategory + weightKeywords + weightLocation + weightDate + weightAttributes;
    }

    public int getSuggestThreshold() {
        return suggestThreshold;
    }

    public void setSuggestThreshold(int suggestThreshold) {
        this.suggestThreshold = suggestThreshold;
    }

    public int getPrefilterDays() {
        return prefilterDays;
    }

    public void setPrefilterDays(int prefilterDays) {
        this.prefilterDays = prefilterDays;
    }

    public int getMaxCandidatesPerListing() {
        return maxCandidatesPerListing;
    }

    public void setMaxCandidatesPerListing(int maxCandidatesPerListing) {
        this.maxCandidatesPerListing = maxCandidatesPerListing;
    }

    public String getScorerVersion() {
        return scorerVersion;
    }

    public void setScorerVersion(String scorerVersion) {
        this.scorerVersion = scorerVersion;
    }

    public double getWeightCategory() {
        return weightCategory;
    }

    public void setWeightCategory(double weightCategory) {
        this.weightCategory = weightCategory;
    }

    public double getWeightKeywords() {
        return weightKeywords;
    }

    public void setWeightKeywords(double weightKeywords) {
        this.weightKeywords = weightKeywords;
    }

    public double getWeightLocation() {
        return weightLocation;
    }

    public void setWeightLocation(double weightLocation) {
        this.weightLocation = weightLocation;
    }

    public double getWeightDate() {
        return weightDate;
    }

    public void setWeightDate(double weightDate) {
        this.weightDate = weightDate;
    }

    public double getWeightAttributes() {
        return weightAttributes;
    }

    public void setWeightAttributes(double weightAttributes) {
        this.weightAttributes = weightAttributes;
    }

    public int getDateDecayDays() {
        return dateDecayDays;
    }

    public void setDateDecayDays(int dateDecayDays) {
        this.dateDecayDays = dateDecayDays;
    }
}
