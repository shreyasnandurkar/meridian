package com.meridian.decision;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteRepository extends JpaRepository<Vote, UUID> {

    List<Vote> findByProposalIdOrderByVotedAt(UUID proposalId);

    List<Vote> findByProposalIdIn(Collection<UUID> proposalIds);

    Optional<Vote> findByProposalIdAndUserId(UUID proposalId, UUID userId);
}
