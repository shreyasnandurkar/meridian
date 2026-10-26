package com.meridian.shortlist;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortlistItemRepository extends JpaRepository<ShortlistItem, UUID> {

    List<ShortlistItem> findByTripIdOrderByCreatedAtDesc(UUID tripId);

    Optional<ShortlistItem> findByIdAndTripId(UUID id, UUID tripId);

    Optional<ShortlistItem> findByTripIdAndPlaceId(UUID tripId, UUID placeId);

    Optional<ShortlistItem> findByTripIdAndUrl(UUID tripId, String url);
}
