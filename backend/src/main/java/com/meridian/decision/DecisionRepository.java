package com.meridian.decision;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DecisionRepository extends JpaRepository<DecisionEntry, UUID> {

    List<DecisionEntry> findByTripIdOrderBySeqDesc(UUID tripId);

    Optional<DecisionEntry> findByIdAndTripId(UUID id, UUID tripId);

    @Query("select coalesce(max(d.seq), 0) from DecisionEntry d where d.tripId = :tripId")
    int maxSeq(UUID tripId);
}
