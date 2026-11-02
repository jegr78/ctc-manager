package org.ctc.domain.repository;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import java.util.UUID;
import org.ctc.domain.model.MatchdayPick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface MatchdayPickRepository extends JpaRepository<MatchdayPick, UUID> {
    @EntityGraph(attributePaths = {"race", "season", "team", "originalMatchday", "originalAwayTeam"})
    @Query("select e from MatchdayPick e")
    List<MatchdayPick> findAllForBackup();

    List<MatchdayPick> findBySeasonId(UUID seasonId);

    @Query("select p from MatchdayPick p where p.race.id = :raceId")
    Optional<MatchdayPick> findSnapshotByRaceId(UUID raceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from MatchdayPick p where p.season.id = :seasonId order by p.id")
    List<MatchdayPick> findBySeasonIdForUpdate(UUID seasonId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MatchdayPick> findByRaceId(UUID raceId);
}
