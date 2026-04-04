package org.ctc.domain.repository;

import org.ctc.domain.model.Race;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RaceRepository extends JpaRepository<Race, UUID> {

    @EntityGraph(attributePaths = {"matchday", "match", "track", "car"})
    List<Race> findByMatchdayId(UUID matchdayId);

    @EntityGraph(attributePaths = {"matchday", "match", "track", "car"})
    List<Race> findByMatchdaySeasonId(UUID seasonId);

    @EntityGraph(attributePaths = {"matchday", "match", "track", "car"})
    List<Race> findByMatchdaySeasonIdAndPlayoffMatchupIsNull(UUID seasonId);

    @EntityGraph(attributePaths = {"playoffMatchup", "track", "car"})
    List<Race> findByPlayoffMatchupId(UUID matchupId);

    @EntityGraph(attributePaths = {"playoffMatchup", "matchday", "track", "car"})
    List<Race> findByPlayoffMatchupRoundPlayoffId(UUID playoffId);

    @EntityGraph(attributePaths = {"matchday", "match", "track", "car"})
    List<Race> findByPlayoffMatchupIsNull();

    boolean existsByCarId(UUID carId);

    boolean existsByTrackId(UUID trackId);
}
