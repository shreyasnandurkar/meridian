package com.meridian.place;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceSearchCacheRepository extends JpaRepository<PlaceSearchCacheEntry, String> {}
