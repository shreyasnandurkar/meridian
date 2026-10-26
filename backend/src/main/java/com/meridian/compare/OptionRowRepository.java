package com.meridian.compare;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OptionRowRepository extends JpaRepository<OptionRow, UUID> {

    List<OptionRow> findByComparisonIdIn(Collection<UUID> comparisonIds);

    Optional<OptionRow> findByIdAndComparisonId(UUID id, UUID comparisonId);
}
