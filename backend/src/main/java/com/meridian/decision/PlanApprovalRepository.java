package com.meridian.decision;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanApprovalRepository extends JpaRepository<PlanApproval, PlanApproval.Key> {

    List<PlanApproval> findByIdItineraryId(UUID itineraryId);
}
