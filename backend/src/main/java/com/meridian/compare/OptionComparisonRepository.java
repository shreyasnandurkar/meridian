package com.meridian.compare;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OptionComparisonRepository extends JpaRepository<OptionComparison, UUID> {

    List<OptionComparison> findByTripIdOrderByCreatedAt(UUID tripId);

    Optional<OptionComparison> findByIdAndTripId(UUID id, UUID tripId);
}
