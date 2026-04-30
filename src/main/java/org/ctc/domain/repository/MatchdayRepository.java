package org.ctc.domain.repository;

import org.ctc.domain.model.Matchday;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MatchdayRepository extends JpaRepository<Matchday, UUID> {

	@EntityGraph(attributePaths = {"season"})
	List<Matchday> findBySeasonIdOrderBySortIndexAsc(UUID seasonId);

	// D-22: Phase-aware finders — MatchdayGeneratorService + SwissPairingService + MatchdayService
	@EntityGraph(attributePaths = {"season", "phase"})
	List<Matchday> findByPhaseIdOrderBySortIndexAsc(UUID phaseId);

	// Phase 60: count-only variant (no ordering overhead) used in guard checks
	List<Matchday> findByPhaseId(UUID phaseId);

	@EntityGraph(attributePaths = {"season", "phase", "group"})
	List<Matchday> findByPhaseIdAndGroupIdOrderBySortIndexAsc(UUID phaseId, UUID groupId);

	// D-18: Delete-guard — existsBy check used by SeasonManagementService.delete
	boolean existsByPhaseSeasonId(UUID seasonId);

	// Phase 60 D-28: Group delete-guard — check if matchdays exist for a given group
	List<Matchday> findByGroupId(UUID groupId);
}
