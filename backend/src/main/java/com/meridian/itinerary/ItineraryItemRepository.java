package com.meridian.itinerary;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryItemRepository extends JpaRepository<ItineraryItem, UUID> {

    List<ItineraryItem> findByItineraryIdOrderByPosition(UUID itineraryId);

    List<ItineraryItem> findByDayIdOrderByPosition(UUID dayId);

    void deleteByDayId(UUID dayId);

    void deleteByItineraryId(UUID itineraryId);

    boolean existsByShortlistItemId(UUID shortlistItemId);
}
