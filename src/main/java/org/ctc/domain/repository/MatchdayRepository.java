package org.ctc.domain.repository;

import org.ctc.domain.model.Matchday;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface MatchdayRepository extends JpaRepository<Matchday, UUID> {

	// Phase 61 MIGR-06: post-V6 the matchdays.season_id bridge column is gone.
	// findBySeasonIdOrderBySortIndexAsc now resolves via phase.season.id.
	@EntityGraph(attributePaths = {"phase"})
	@Query("SELECT m FROM Matchday m WHERE m.phase.season.id = :seasonId ORDER BY m.sortIndex ASC")
	List<Matchday> findBySeasonIdOrderBySortIndexAsc(UUID seasonId);

	// D-22: Phase-aware finders — MatchdayGeneratorService + SwissPairingService + MatchdayService
	@EntityGraph(attributePaths = {"phase"})
	List<Matchday> findByPhaseIdOrderBySortIndexAsc(UUID phaseId);

	// Phase 60: count-only variant (no ordering overhead) used in guard checks
	List<Matchday> findByPhaseId(UUID phaseId);

	@EntityGraph(attributePaths = {"phase", "group"})
	List<Matchday> findByPhaseIdAndGroupIdOrderBySortIndexAsc(UUID phaseId, UUID groupId);

	// D-18: Delete-guard — existsBy check used by SeasonManagementService.delete
	boolean existsByPhaseSeasonId(UUID seasonId);

	// Phase 60 D-28: Group delete-guard — check if matchdays exist for a given group
	List<Matchday> findByGroupId(UUID groupId);
}
