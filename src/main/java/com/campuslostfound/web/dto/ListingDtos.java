package com.campuslostfound.web.dto;

import com.campuslostfound.domain.AttributeKey;
import com.campuslostfound.domain.Category;
import com.campuslostfound.domain.ListingKind;
import com.campuslostfound.domain.ListingStatus;
import com.campuslostfound.web.validation.SafeText;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Request/response shapes for listings and their attributes. */
public final class ListingDtos {

    private ListingDtos() {
    }

    public record AttributeInput(
            @NotNull AttributeKey key,
            @NotBlank @SafeText @Size(max = 60) String value) {
    }

    public record CreateRequest(
            @NotNull ListingKind kind,
            @NotBlank @SafeText @Size(min = 3, max = 120) String title,
            @NotBlank @SafeText @Size(min = 10, max = 2000) String description,
            @NotNull Category category,
            @SafeText @Size(max = 200) String locationText,
            @SafeText @Size(max = 80) String building,
            @SafeText @Size(max = 80) String area,
            @NotNull LocalDate eventDate,
            @SafeText @Size(max = 1000) String privateDetails,
            @Valid @Size(max = 20) List<AttributeInput> attributes) {
    }

    public record PatchRequest(
            @SafeText @Size(min = 3, max = 120) String title,
            @SafeText @Size(min = 10, max = 2000) String description,
            Category category,
            @SafeText @Size(max = 200) String locationText,
            @SafeText @Size(max = 80) String building,
            @SafeText @Size(max = 80) String area,
            LocalDate eventDate,
            @SafeText @Size(max = 1000) String privateDetails) {
    }

    public record StatusChangeRequest(@NotNull ListingStatus status) {
    }

    public record AttributeResponse(Long id, AttributeKey key, String value) {
    }

    public record ReporterResponse(Long id, String displayName) {
    }

    /** Public list row. Never carries private details or the reporter's identity. */
    public record SummaryResponse(
            Long id, ListingKind kind, String title, Category category, String categoryLabel,
            String building, String area, LocalDate eventDate, ListingStatus status,
            Instant createdAt) {
    }

    /**
     * Full listing. {@code privateDetails} and {@code reporter} are populated only when the
     * caller is the reporter or a moderator; otherwise null.
     */
    public record DetailResponse(
            Long id, ListingKind kind, String title, String description, Category category,
            String categoryLabel, String locationText, String building, String area,
            LocalDate eventDate, ListingStatus status, List<AttributeResponse> attributes,
            int suggestedMatchCount, String privateDetails, ReporterResponse reporter,
            Instant createdAt, Instant updatedAt) {
    }

    public record CategoryResponse(String value, String label) {
    }
}
