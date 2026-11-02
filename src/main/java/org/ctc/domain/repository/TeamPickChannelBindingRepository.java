package org.ctc.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.ctc.domain.model.TeamPickChannelBinding;

public interface TeamPickChannelBindingRepository extends JpaRepository<TeamPickChannelBinding, UUID> {
    @EntityGraph(attributePaths = {"season", "team"})
    @Query("select b from TeamPickChannelBinding b")
    List<TeamPickChannelBinding> findAllForBackup();

    @EntityGraph(attributePaths = {"season", "team"})
    List<TeamPickChannelBinding> findAllByOrderByGuildIdAscChannelIdAsc();

    Optional<TeamPickChannelBinding> findBySeasonIdAndTeamIdAndGuildId(UUID seasonId, UUID teamId, String guildId);
    Optional<TeamPickChannelBinding> findBySeasonIdAndGuildIdAndChannelId(UUID seasonId, String guildId, String channelId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from TeamPickChannelBinding b where b.season.id = :seasonId and b.team.id = :teamId and b.guildId = :guildId and b.channelId = :channelId")
    Optional<TeamPickChannelBinding> findCurrentForUpdate(UUID seasonId, UUID teamId, String guildId, String channelId);

    @EntityGraph(attributePaths = {"season", "team"})
    List<TeamPickChannelBinding> findByGuildIdAndChannelId(String guildId, String channelId);
}
