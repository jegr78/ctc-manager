package org.ctc.domain.repository;

import org.ctc.domain.model.Matchday;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface MatchdayRepository extends JpaRepository<Matchday, UUID> {

	/**
	 * Returns ALL matchdays for the season across ALL phases (REGULAR + PLAYOFF + PLACEMENT),
	 * ordered by {@code sortIndex} ascending.
	 *
	 * <p>For sortIndex computation or duplicate-label checks scoped to a single phase, use
	 * {@link #findByPhaseIdOrderBySortIndexAsc(UUID)} instead — a season-wide query lets
	 * PLAYOFF sortIndex (&gt;= 100) poison the next REGULAR sortIndex and lets a label-equality
	 * match accidentally pick up a PLAYOFF matchday with the same label.
	 */
	@EntityGraph(attributePaths = {"phase"})
	@Query("SELECT m FROM Matchday m WHERE m.phase.season.id = :seasonId ORDER BY m.sortIndex ASC")
	List<Matchday> findBySeasonIdOrderBySortIndexAsc(UUID seasonId);

	/**
	 * Returns matchdays scoped to a single {@link org.ctc.domain.model.SeasonPhase},
	 * ordered by {@code sortIndex} ascending. Preferred over the season-wide finder for
	 * phase-bounded computations (sortIndex, duplicate-label).
	 */
	@EntityGraph(attributePaths = {"phase"})
	List<Matchday> findByPhaseIdOrderBySortIndexAsc(UUID phaseId);

	List<Matchday> findByPhaseId(UUID phaseId);

	@EntityGraph(attributePaths = {"phase", "group"})
	List<Matchday> findByPhaseIdAndGroupIdOrderBySortIndexAsc(UUID phaseId, UUID groupId);

	boolean existsByPhaseSeasonId(UUID seasonId);

	List<Matchday> findByGroupId(UUID groupId);
}
