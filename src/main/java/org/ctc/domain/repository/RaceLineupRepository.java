package org.ctc.domain.repository;

import org.ctc.domain.model.RaceLineup;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
