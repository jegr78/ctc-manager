package org.ctc.domain.repository;

import org.ctc.domain.model.Playoff;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface PlayoffRepository extends JpaRepository<Playoff, UUID> {

	// Phase 61 MIGR-06: post-V6 the playoffs.season_id bridge column is gone.
	// findBySeasonId now resolves via phase.season.id (Convenience-Getter delegate path).
	@Query("SELECT p FROM Playoff p WHERE p.phase.season.id = :seasonId")
	Optional<Playoff> findBySeasonId(UUID seasonId);

	// D-22: phase-aware finder (Phase 60 UI cutover will switch over)
	Optional<Playoff> findByPhaseId(UUID phaseId);

	// D-18 prep (Plan 58-06 delete-guard): "season has any playoff under any phase?"
	boolean existsByPhaseSeasonId(UUID seasonId);
}
