package com.meridian.place;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, UUID> {

    List<Place> findBySourceAndSourceIdIn(String source, Collection<String> sourceIds);

    List<Place> findByCategoryAndLatBetweenAndLngBetween(PlaceCategory category, double minLat, double maxLat,
            double minLng, double maxLng);
}
