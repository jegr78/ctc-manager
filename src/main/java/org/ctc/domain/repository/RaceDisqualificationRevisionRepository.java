package org.ctc.domain.repository;

import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.RaceDisqualificationRevision;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RaceDisqualificationRevisionRepository extends JpaRepository<RaceDisqualificationRevision, UUID> {
    // Current native reads also protect initialized empty collections after MariaDB lock waits.
    @Query(value = "select * from race_disqualification_revisions where result_id = :resultId order by revision_number for update", nativeQuery = true)
    List<RaceDisqualificationRevision> findResultHistoryForUpdate(UUID resultId);
    @Query(value = "select * from race_disqualification_revisions where race_id = :raceId order by result_id, revision_number for update", nativeQuery = true)
    List<RaceDisqualificationRevision> findRaceHistoryForUpdate(UUID raceId);
    @Query(value = "select * from race_disqualification_revisions where playoff_matchup_id = :matchupId order by result_id, revision_number for update", nativeQuery = true)
    List<RaceDisqualificationRevision> findPlayoffHistoryForUpdate(UUID matchupId);
    @Query(value = "select * from race_disqualification_revisions where driver_id = :driverId order by result_id, revision_number for update", nativeQuery = true)
    List<RaceDisqualificationRevision> findDriverHistoryForUpdate(UUID driverId);
    @EntityGraph(attributePaths = {"result", "race", "driver", "season", "phase", "matchday", "match", "playoffMatchup", "homeTeam", "awayTeam"})
    @Query("select d from RaceDisqualificationRevision d")
    List<RaceDisqualificationRevision> findAllForBackup();
    List<RaceDisqualificationRevision> findBySeasonId(UUID seasonId);
}
