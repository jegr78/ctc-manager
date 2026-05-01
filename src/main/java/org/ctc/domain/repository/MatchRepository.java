package org.ctc.domain.repository;

import org.ctc.domain.model.Match;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {

	@EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
	List<Match> findByMatchdayId(UUID matchdayId);

	@EntityGraph(attributePaths = {"homeTeam", "awayTeam", "matchday"})
	@Query("SELECT m FROM Match m WHERE m.matchday.phase.season.id = :seasonId")
	List<Match> findByMatchdaySeasonId(UUID seasonId);

	@EntityGraph(attributePaths = {"homeTeam", "awayTeam", "matchday"})
	List<Match> findByMatchdayPhaseId(UUID phaseId);

	boolean existsByMatchdayIdAndHomeTeamIdAndAwayTeamId(UUID matchdayId, UUID homeTeamId, UUID awayTeamId);

	java.util.Optional<Match> findFirstByMatchdayIdAndHomeTeamIdAndAwayTeamId(UUID matchdayId, UUID homeTeamId, UUID awayTeamId);
}
