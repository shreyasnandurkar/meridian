package com.meridian.itinerary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    List<Comment> findByTripIdOrderByCreatedAt(UUID tripId);

    Optional<Comment> findByIdAndTripId(UUID id, UUID tripId);

    void deleteByTripIdAndTargetTypeAndTargetId(UUID tripId, Comment.Target targetType, UUID targetId);
}
