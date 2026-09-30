package org.ctc.domain.repository;

import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.PlayoffMatchup;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlayoffMatchupRepository extends JpaRepository<PlayoffMatchup, UUID> {

	@EntityGraph(attributePaths = {"team1", "team2", "winner"})
	List<PlayoffMatchup> findByRoundIdOrderByBracketPositionAsc(UUID roundId);

	@EntityGraph(attributePaths = {"team1", "team2", "winner", "round"})
	List<PlayoffMatchup> findByRoundPlayoffId(UUID playoffId);

	@Query("SELECT COUNT(pm) > 0 FROM PlayoffMatchup pm LEFT JOIN pm.team1 t1 LEFT JOIN pm.team2 t2 "
			+ "WHERE pm.round.playoff.phase.season.id = :seasonId AND (t1.id = :teamId OR t2.id = :teamId)")
	boolean existsInSeasonForTeam(UUID seasonId, UUID teamId);

	List<PlayoffMatchup> findByRoundPlayoffPhaseIdAndWinnerIsNotNull(UUID phaseId);

	/**
	 * Full-table finder used by {@code BackupExportService}.
	 *
	 * <p>Eager-fetches the five {@code @ManyToOne} associations: {@code round},
	 * {@code team1}, {@code team2}, {@code winner}, {@code nextMatchup} (self-FK).
	 */
	@EntityGraph(attributePaths = {"round", "team1", "team2", "winner", "nextMatchup"})
	@Query("SELECT e FROM PlayoffMatchup e")
	List<PlayoffMatchup> findAllForBackup();
}
