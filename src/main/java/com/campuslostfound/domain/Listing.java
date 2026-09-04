package com.campuslostfound.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A lost- or found-item report.
 *
 * <p>{@code privateDetails} holds identifying information the reporter deliberately keeps
 * out of the public view (a scratch, an engraving, what was in the front pocket). It is
 * returned only to the reporter and to moderators; the matching engine reads it
 * server-side but never echoes it. A claimant proves ownership by describing it through
 * the claim workflow (see docs/design-decisions.md DD-4, DD-5).
 */
@Entity
@Table(name = "listings")
public class Listing extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ListingKind kind;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Category category;

    @Column(name = "location_text", length = 200)
    private String locationText;

    @Column(length = 80)
    private String building;

    @Column(length = 80)
    private String area;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "private_details", length = 1000)
    private String privateDetails;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ListingStatus status = ListingStatus.OPEN;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "listing", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ListingAttribute> attributes = new ArrayList<>();

    protected Listing() {
        // for JPA
    }

    public Listing(User reporter, ListingKind kind, String title, String description,
                   Category category, LocalDate eventDate) {
        this.reporter = reporter;
        this.kind = kind;
        this.title = title;
        this.description = description;
        this.category = category;
        this.eventDate = eventDate;
    }

    public User getReporter() {
        return reporter;
    }

    public Long getReporterId() {
        return reporter == null ? null : reporter.getId();
    }

    public ListingKind getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getLocationText() {
        return locationText;
    }

    public void setLocationText(String locationText) {
        this.locationText = locationText;
    }

    public String getBuilding() {
        return building;
    }

    public void setBuilding(String building) {
        this.building = building;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public String getPrivateDetails() {
        return privateDetails;
    }

    public void setPrivateDetails(String privateDetails) {
        this.privateDetails = privateDetails;
    }

    public ListingStatus getStatus() {
        return status;
    }

    public void setStatus(ListingStatus status) {
        this.status = status;
    }

    public long getVersion() {
        return version;
    }

    public List<ListingAttribute> getAttributes() {
        return Collections.unmodifiableList(attributes);
    }

    public void addAttribute(ListingAttribute attribute) {
        attribute.setListing(this);
        this.attributes.add(attribute);
    }

    public boolean removeAttribute(ListingAttribute attribute) {
        return this.attributes.remove(attribute);
    }

    public boolean isActive() {
        return status == ListingStatus.OPEN || status == ListingStatus.MATCHED;
    }
}
