package org.ctc.domain.repository;

import org.ctc.domain.model.PlayoffMatchup;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlayoffMatchupRepository extends JpaRepository<PlayoffMatchup, UUID> {

    @EntityGraph(attributePaths = {"team1", "team2", "winner"})
    List<PlayoffMatchup> findByRoundIdOrderByBracketPositionAsc(UUID roundId);

    @EntityGraph(attributePaths = {"team1", "team2", "winner", "round"})
    List<PlayoffMatchup> findByRoundPlayoffId(UUID playoffId);
}
