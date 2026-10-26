package com.meridian.trip;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InviteRepository extends JpaRepository<Invite, UUID> {

    Optional<Invite> findByToken(String token);

    List<Invite> findByTripIdAndRevokedAtIsNullOrderByCreatedAtDesc(UUID tripId);
}
