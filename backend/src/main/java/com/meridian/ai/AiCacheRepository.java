package com.meridian.ai;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiCacheRepository extends JpaRepository<AiCacheEntry, String> {}
