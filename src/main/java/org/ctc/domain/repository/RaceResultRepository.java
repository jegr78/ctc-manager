package org.ctc.domain.repository;

import org.ctc.domain.model.RaceResult;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaceResultRepository extends JpaRepository<RaceResult, UUID> {

	@EntityGraph(attributePaths = {"driver"})
	List<RaceResult> findByRaceId(UUID raceId);

	@EntityGraph(attributePaths = {"driver", "race"})
	List<RaceResult> findByDriverId(UUID driverId);

	Optional<RaceResult> findByRaceIdAndDriverId(UUID raceId, UUID driverId);

	@EntityGraph(attributePaths = {"driver", "race"})
	@Query("SELECT rr FROM RaceResult rr WHERE rr.race.matchday.phase.season.id = :seasonId")
	List<RaceResult> findByRaceMatchdaySeasonId(UUID seasonId);

	@EntityGraph(attributePaths = {"driver", "race"})
	List<RaceResult> findByRacePlayoffMatchupIsNull();

	@EntityGraph(attributePaths = {"driver", "race"})
	@Query("SELECT rr FROM RaceResult rr WHERE rr.race.playoffMatchup IS NULL AND rr.race.matchday.phase.season.id IN :seasonIds")
	List<RaceResult> findByRacePlayoffMatchupIsNullAndRaceMatchdaySeasonIdIn(List<UUID> seasonIds);

	@EntityGraph(attributePaths = {"driver", "race"})
	List<RaceResult> findByRaceMatchdayPhaseId(UUID phaseId);

	@EntityGraph(attributePaths = {"driver", "race"})
	List<RaceResult> findByRacePlayoffMatchupRoundPlayoffPhaseId(UUID phaseId);
}
