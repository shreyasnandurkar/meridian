package com.meridian.place;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The places one provider query returned, in the provider's order. */
@Entity
@Table(name = "place_search_cache")
public class PlaceSearchCacheEntry {

    @Id
    @Column(name = "cache_key")
    private String cacheKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "place_ids", nullable = false)
    private List<UUID> placeIds;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected PlaceSearchCacheEntry() {}

    public PlaceSearchCacheEntry(String cacheKey) {
        this.cacheKey = cacheKey;
    }

    public void update(List<UUID> placeIds, Instant fetchedAt) {
        this.placeIds = List.copyOf(placeIds);
        this.fetchedAt = fetchedAt;
    }

    public List<UUID> getPlaceIds() {
        return placeIds;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
