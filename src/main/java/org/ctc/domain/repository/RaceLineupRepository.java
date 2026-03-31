package org.ctc.domain.repository;

import org.ctc.domain.model.RaceLineup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaceLineupRepository extends JpaRepository<RaceLineup, UUID> {

    List<RaceLineup> findByRaceId(UUID raceId);

    List<RaceLineup> findByRaceIdAndTeamId(UUID raceId, UUID teamId);

    Optional<RaceLineup> findByRaceIdAndDriverId(UUID raceId, UUID driverId);

    List<RaceLineup> findByRaceMatchdayId(UUID matchdayId);

    List<RaceLineup> findByTeamIdIn(List<UUID> teamIds);
}
