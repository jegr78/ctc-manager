package org.ctc.domain.repository;

import org.ctc.domain.model.Race;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface RaceRepository extends JpaRepository<Race, UUID> {

	@EntityGraph(attributePaths = {"matchday", "match", "track", "car"})
	List<Race> findByMatchdayId(UUID matchdayId);

	@EntityGraph(attributePaths = {"matchday", "match", "track", "car"})
	@Query("SELECT r FROM Race r WHERE r.matchday.phase.season.id = :seasonId")
	List<Race> findByMatchdaySeasonId(UUID seasonId);

	@EntityGraph(attributePaths = {"matchday", "match", "track", "car"})
	@Query("SELECT r FROM Race r WHERE r.matchday.phase.season.id = :seasonId AND r.playoffMatchup IS NULL")
	List<Race> findByMatchdaySeasonIdAndPlayoffMatchupIsNull(UUID seasonId);

	@EntityGraph(attributePaths = {"playoffMatchup", "track", "car"})
	List<Race> findByPlayoffMatchupId(UUID matchupId);

	@EntityGraph(attributePaths = {"playoffMatchup", "matchday", "track", "car"})
	List<Race> findByPlayoffMatchupRoundPlayoffId(UUID playoffId);

	@EntityGraph(attributePaths = {"matchday", "match", "track", "car"})
	List<Race> findByPlayoffMatchupIsNull();

	List<Race> findByMatchId(UUID matchId);

	boolean existsByCarId(UUID carId);

	boolean existsByTrackId(UUID trackId);

	/**
	 * Phase 73-02: full-table finder used by {@code BackupExportService}.
	 *
	 * <p>Eager-fetches all seven {@code @ManyToOne} associations: {@code matchday},
	 * {@code match}, {@code track}, {@code car}, {@code playoffMatchup},
	 * {@code homeTeamOverride}, {@code awayTeamOverride}. {@code Race} is the deepest
	 * aggregate root in the export graph (RESEARCH §EntityGraph Fetch Map row 20).
	 */
	@EntityGraph(attributePaths = {"matchday", "match", "track", "car", "playoffMatchup",
			"homeTeamOverride", "awayTeamOverride"})
	@Query("SELECT e FROM Race e")
	List<Race> findAllForBackup();
}
