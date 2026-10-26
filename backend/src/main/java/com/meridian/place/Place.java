package com.meridian.place;

import com.meridian.place.PlacesClient.PlaceData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "places")
public class Place {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String source;

    @Column(name = "source_id", nullable = false)
    private String sourceId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlaceCategory category;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    private String address;

    private String website;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, String> tags = Map.of();

    @Column(name = "popularity_proxy", nullable = false)
    private int popularityProxy;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected Place() {}

    public Place(PlaceData data, Instant fetchedAt) {
        this.id = UUID.randomUUID();
        this.source = data.source();
        this.sourceId = data.sourceId();
        refresh(data, fetchedAt);
    }

    /** Overwrites provider fields with a newer answer. */
    public void refresh(PlaceData data, Instant fetchedAt) {
        this.name = data.name();
        this.category = data.category();
        this.lat = data.lat();
        this.lng = data.lng();
        this.address = data.address();
        this.website = data.website();
        this.tags = Map.copyOf(data.tags());
        this.popularityProxy = data.popularityProxy();
        this.fetchedAt = fetchedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getName() {
        return name;
    }

    public PlaceCategory getCategory() {
        return category;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public String getAddress() {
        return address;
    }

    public String getWebsite() {
        return website;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public int getPopularityProxy() {
        return popularityProxy;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
