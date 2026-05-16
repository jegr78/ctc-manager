package org.ctc.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.RaceLineup;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

	@EntityGraph(attributePaths = {"driver", "team"})
	@Query("SELECT rl FROM RaceLineup rl WHERE rl.race.matchday.phase.season.id = :seasonId")
	List<RaceLineup> findByRaceMatchdaySeasonId(UUID seasonId);

	/**
	 * Returns RaceLineup entries for a specific driver in a specific season.
	 * Used to attribute stand-in drivers who have no REGULAR-phase PhaseTeam row.
	 */
	@EntityGraph(attributePaths = {"driver", "team"})
	@Query("SELECT rl FROM RaceLineup rl WHERE rl.driver.id = :driverId AND rl.race.matchday.phase.season.id = :seasonId")
	List<RaceLineup> findByDriverIdAndRaceMatchdaySeasonId(UUID driverId, UUID seasonId);

	/**
	 * Full-table finder used by {@code BackupExportService}.
	 *
	 * <p>Eager-fetches the three {@code @ManyToOne} associations: {@code race},
	 * {@code driver}, {@code team}.
	 */
	@EntityGraph(attributePaths = {"race", "driver", "team"})
	@Query("SELECT e FROM RaceLineup e")
	List<RaceLineup> findAllForBackup();
}
