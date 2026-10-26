package com.meridian.shortlist;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReactionRepository extends JpaRepository<Reaction, Reaction.Key> {

    List<Reaction> findByIdItemIdIn(Collection<UUID> itemIds);
}
