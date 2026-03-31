package org.ctc.domain.repository;

import org.ctc.domain.model.RaceResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RaceResultRepository extends JpaRepository<RaceResult, UUID> {

    List<RaceResult> findByRaceId(UUID raceId);

    List<RaceResult> findByDriverId(UUID driverId);

    List<RaceResult> findByRaceMatchdaySeasonId(UUID seasonId);

    List<RaceResult> findByRacePlayoffMatchupIsNull();
}
