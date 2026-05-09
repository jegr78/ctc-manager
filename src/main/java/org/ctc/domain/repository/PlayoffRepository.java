package org.ctc.domain.repository;

import org.ctc.domain.model.Playoff;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface PlayoffRepository extends JpaRepository<Playoff, UUID> {

	@Query("SELECT p FROM Playoff p WHERE p.phase.season.id = :seasonId")
	Optional<Playoff> findBySeasonId(UUID seasonId);

	Optional<Playoff> findByPhaseId(UUID phaseId);

	boolean existsByPhaseSeasonId(UUID seasonId);
}
