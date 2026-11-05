package org.ctc.domain.repository;

import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.RacePenaltyRevision;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RacePenaltyRevisionRepository extends JpaRepository<RacePenaltyRevision, UUID> {
    List<RacePenaltyRevision> findByRaceIdOrderByRevisionNumberAsc(UUID raceId);

    // Native locking reads retain current MariaDB visibility without upgrading immutable Hibernate entries.
    @Query(value = "select * from race_penalty_revisions where race_id = :raceId order by revision_number for update", nativeQuery = true)
    List<RacePenaltyRevision> findHistoryForUpdate(UUID raceId);

    @Query(value = "select * from race_penalty_revisions where playoff_matchup_id = :matchupId order by race_id, revision_number for update", nativeQuery = true)
    List<RacePenaltyRevision> findPlayoffHistoryForUpdate(UUID matchupId);

    @EntityGraph(attributePaths = {"race", "season", "phase", "matchday", "match", "playoffMatchup", "homeTeam", "awayTeam"})
    @Query("select p from RacePenaltyRevision p")
    List<RacePenaltyRevision> findAllForBackup();
}
