package com.meridian.profile;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelerProfileRepository extends JpaRepository<TravelerProfileEntity, UUID> {}
