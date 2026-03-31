package org.ctc.domain.repository;

import org.ctc.domain.model.Playoff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlayoffRepository extends JpaRepository<Playoff, UUID> {

    Optional<Playoff> findBySeasonId(UUID seasonId);
}
