package org.ctc.domain.repository;

import org.ctc.domain.model.Matchday;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface MatchdayRepository extends JpaRepository<Matchday, UUID> {

	@EntityGraph(attributePaths = {"phase"})
	@Query("SELECT m FROM Matchday m WHERE m.phase.season.id = :seasonId ORDER BY m.sortIndex ASC")
	List<Matchday> findBySeasonIdOrderBySortIndexAsc(UUID seasonId);

	@EntityGraph(attributePaths = {"phase"})
	List<Matchday> findByPhaseIdOrderBySortIndexAsc(UUID phaseId);

	List<Matchday> findByPhaseId(UUID phaseId);

	@EntityGraph(attributePaths = {"phase", "group"})
	List<Matchday> findByPhaseIdAndGroupIdOrderBySortIndexAsc(UUID phaseId, UUID groupId);

	boolean existsByPhaseSeasonId(UUID seasonId);

	List<Matchday> findByGroupId(UUID groupId);
}
