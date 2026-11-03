package org.ctc.domain.repository;

import java.util.UUID;
import java.util.List;
import org.ctc.domain.model.IncidentMatchContext;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IncidentMatchContextRepository extends JpaRepository<IncidentMatchContext, UUID> {
    List<IncidentMatchContext> findBySeasonId(UUID seasonId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    List<IncidentMatchContext> findByChannelId(String channelId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from IncidentMatchContext e where e.match.id = :matchId")
    Optional<IncidentMatchContext> findByMatchIdForUpdate(UUID matchId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from IncidentMatchContext e where e.playoffMatchup.id = :playoffMatchupId")
    Optional<IncidentMatchContext> findByPlayoffMatchupIdForUpdate(UUID playoffMatchupId);
    Optional<IncidentMatchContext> findByMatchId(UUID matchId);
    Optional<IncidentMatchContext> findByPlayoffMatchupId(UUID playoffMatchupId);
    Optional<IncidentMatchContext> findByGuildIdAndChannelId(String guildId, String channelId);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"match", "playoffMatchup", "season", "homeTeam", "awayTeam"})
    @org.springframework.data.jpa.repository.Query("select e from IncidentMatchContext e")
    List<IncidentMatchContext> findAllForBackup();
}
