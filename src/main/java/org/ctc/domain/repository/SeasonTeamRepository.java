package org.ctc.domain.repository;

import org.ctc.domain.model.SeasonTeam;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeasonTeamRepository extends JpaRepository<SeasonTeam, UUID> {

    @EntityGraph(attributePaths = {"team"})
    List<SeasonTeam> findBySeasonId(UUID seasonId);

    Optional<SeasonTeam> findBySeasonIdAndTeamId(UUID seasonId, UUID teamId);

    @Transactional
    void deleteBySeasonIdAndTeamId(UUID seasonId, UUID teamId);
}
