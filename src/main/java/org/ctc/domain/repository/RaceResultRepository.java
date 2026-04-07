package org.ctc.domain.repository;

import org.ctc.domain.model.RaceResult;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
    List<RaceResult> findByRaceMatchdaySeasonId(UUID seasonId);

    @EntityGraph(attributePaths = {"driver", "race"})
    List<RaceResult> findByRacePlayoffMatchupIsNull();
}
