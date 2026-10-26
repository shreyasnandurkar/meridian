package com.meridian.shortlist;

import com.meridian.place.PlaceCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One shortlisted thing: a stored place, a pasted link or a dropped map pin, with who added it and why. */
@Entity
@Table(name = "shortlist_items")
public class ShortlistItem {

    public enum Kind { PLACE, LINK, PIN }

    @Id
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Column(name = "place_id")
    private UUID placeId;

    private String url;

    private String title;

    private Double lat;

    private Double lng;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlaceCategory category;

    @Column(name = "added_by", nullable = false)
    private UUID addedBy;

    private String reason;

    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ShortlistItem() {}

    private ShortlistItem(UUID tripId, Kind kind, PlaceCategory category, UUID addedBy, String reason, String notes) {
        this.id = UUID.randomUUID();
        this.tripId = tripId;
        this.kind = kind;
        this.category = category;
        this.addedBy = addedBy;
        this.reason = reason;
        this.notes = notes;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public static ShortlistItem place(UUID tripId, UUID placeId, PlaceCategory category, UUID addedBy, String reason,
            String notes) {
        ShortlistItem item = new ShortlistItem(tripId, Kind.PLACE, category, addedBy, reason, notes);
        item.placeId = placeId;
        return item;
    }

    public static ShortlistItem link(UUID tripId, String url, String title, PlaceCategory category, UUID addedBy,
            String reason, String notes) {
        ShortlistItem item = new ShortlistItem(tripId, Kind.LINK, category, addedBy, reason, notes);
        item.url = url;
        item.title = title;
        return item;
    }

    public static ShortlistItem pin(UUID tripId, String title, double lat, double lng, PlaceCategory category,
            UUID addedBy, String reason, String notes) {
        ShortlistItem item = new ShortlistItem(tripId, Kind.PIN, category, addedBy, reason, notes);
        item.title = title;
        item.lat = lat;
        item.lng = lng;
        return item;
    }

    /** Null arguments leave a field unchanged; blank reason/notes clear it. Titles only apply to links and pins. */
    public void edit(PlaceCategory category, String title, String reason, String notes) {
        if (category != null) this.category = category;
        if (title != null && !title.isBlank() && kind != Kind.PLACE) this.title = title.strip();
        if (reason != null) this.reason = reason.isBlank() ? null : reason.strip();
        if (notes != null) this.notes = notes.isBlank() ? null : notes.strip();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTripId() {
        return tripId;
    }

    public Kind getKind() {
        return kind;
    }

    public UUID getPlaceId() {
        return placeId;
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public Double getLat() {
        return lat;
    }

    public Double getLng() {
        return lng;
    }

    public PlaceCategory getCategory() {
        return category;
    }

    public UUID getAddedBy() {
        return addedBy;
    }

    public String getReason() {
        return reason;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
