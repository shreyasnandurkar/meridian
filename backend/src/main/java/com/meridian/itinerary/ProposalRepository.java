package com.meridian.itinerary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProposalRepository extends JpaRepository<Proposal, UUID> {

    List<Proposal> findByTripIdOrderByCreatedAtDesc(UUID tripId);

    List<Proposal> findByItineraryIdAndStatus(UUID itineraryId, Proposal.Status status);

    Optional<Proposal> findByIdAndTripId(UUID id, UUID tripId);
}
