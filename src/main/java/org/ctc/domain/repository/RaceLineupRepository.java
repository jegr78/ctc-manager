package org.ctc.domain.repository;

import org.ctc.domain.model.RaceLineup;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaceLineupRepository extends JpaRepository<RaceLineup, UUID> {

	@EntityGraph(attributePaths = {"driver", "team"})
	List<RaceLineup> findByRaceId(UUID raceId);

	@EntityGraph(attributePaths = {"driver", "team"})
	List<RaceLineup> findByRaceIdAndTeamId(UUID raceId, UUID teamId);

	Optional<RaceLineup> findByRaceIdAndDriverId(UUID raceId, UUID driverId);

	List<RaceLineup> findByDriverId(UUID driverId);

	@EntityGraph(attributePaths = {"driver", "team", "race"})
	List<RaceLineup> findByRaceMatchdayId(UUID matchdayId);

	@EntityGraph(attributePaths = {"driver", "team"})
	List<RaceLineup> findByTeamIdIn(List<UUID> teamIds);

	// Phase 61 MIGR-06: post-V6 matchdays.season_id is gone — resolve via matchday.phase.season.id.
	@EntityGraph(attributePaths = {"driver", "team"})
	@Query("SELECT rl FROM RaceLineup rl WHERE rl.race.matchday.phase.season.id = :seasonId")
	List<RaceLineup> findByRaceMatchdaySeasonId(UUID seasonId);

	/**
	 * D-10 fallback: returns RaceLineup entries for a specific driver in a specific season.
	 * Used by DriverRankingService.attributeTeamFromRegularOrLineup to attribute stand-in
	 * drivers who have no REGULAR-phase PhaseTeam row.
	 */
	@EntityGraph(attributePaths = {"driver", "team"})
	@Query("SELECT rl FROM RaceLineup rl WHERE rl.driver.id = :driverId AND rl.race.matchday.phase.season.id = :seasonId")
	List<RaceLineup> findByDriverIdAndRaceMatchdaySeasonId(UUID driverId, UUID seasonId);
}
