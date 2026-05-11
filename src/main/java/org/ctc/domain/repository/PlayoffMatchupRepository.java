package org.ctc.domain.repository;

import org.ctc.domain.model.PlayoffMatchup;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PlayoffMatchupRepository extends JpaRepository<PlayoffMatchup, UUID> {

	@EntityGraph(attributePaths = {"team1", "team2", "winner"})
	List<PlayoffMatchup> findByRoundIdOrderByBracketPositionAsc(UUID roundId);

	@EntityGraph(attributePaths = {"team1", "team2", "winner", "round"})
	List<PlayoffMatchup> findByRoundPlayoffId(UUID playoffId);

	/**
	 * Phase 73-02: full-table finder used by {@code BackupExportService}.
	 *
	 * <p>Eager-fetches the five {@code @ManyToOne} associations: {@code round},
	 * {@code team1}, {@code team2}, {@code winner}, {@code nextMatchup} (self-FK).
	 */
	@EntityGraph(attributePaths = {"round", "team1", "team2", "winner", "nextMatchup"})
	@Query("SELECT e FROM PlayoffMatchup e")
	List<PlayoffMatchup> findAllForBackup();
}
