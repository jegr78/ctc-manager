package de.ctc.domain.repository;

import de.ctc.domain.model.Race;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RaceRepository extends JpaRepository<Race, UUID> {

    List<Race> findByMatchdayId(UUID matchdayId);

    List<Race> findByMatchdaySeasonId(UUID seasonId);

    List<Race> findByMatchdaySeasonIdAndPlayoffMatchupIsNull(UUID seasonId);

    List<Race> findByPlayoffMatchupId(UUID matchupId);

    List<Race> findByPlayoffMatchupRoundPlayoffId(UUID playoffId);

    List<Race> findByPlayoffMatchupIsNull();
}
