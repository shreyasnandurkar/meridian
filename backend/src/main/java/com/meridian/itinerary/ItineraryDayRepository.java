package com.meridian.itinerary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryDayRepository extends JpaRepository<ItineraryDay, UUID> {

    List<ItineraryDay> findByItineraryIdOrderByDayIndex(UUID itineraryId);

    Optional<ItineraryDay> findByItineraryIdAndDayIndex(UUID itineraryId, int dayIndex);

    void deleteByItineraryId(UUID itineraryId);
}
